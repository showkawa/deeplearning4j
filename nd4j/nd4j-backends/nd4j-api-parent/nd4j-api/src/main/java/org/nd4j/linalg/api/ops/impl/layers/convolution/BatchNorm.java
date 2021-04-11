/*
 *  ******************************************************************************
 *  *
 *  *
 *  * This program and the accompanying materials are made available under the
 *  * terms of the Apache License, Version 2.0 which is available at
 *  * https://www.apache.org/licenses/LICENSE-2.0.
 *  *
 *  *  See the NOTICE file distributed with this work for additional
 *  *  information regarding copyright ownership.
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations
 *  * under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *  *****************************************************************************
 */

package org.nd4j.linalg.api.ops.impl.layers.convolution;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import onnx.Onnx;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.autodiff.samediff.internal.SameDiffOp;
import org.nd4j.common.base.Preconditions;
import org.nd4j.imports.graphmapper.tf.TFGraphMapper;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.api.ops.impl.layers.convolution.config.Conv1DConfig;
import org.nd4j.linalg.api.ops.impl.layers.convolution.config.Conv2DConfig;
import org.nd4j.linalg.api.ops.impl.layers.convolution.config.Conv3DConfig;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.common.util.ArrayUtil;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.*;


@Slf4j
@Getter
@NoArgsConstructor
public class BatchNorm extends DynamicCustomOp {

    private boolean applyGamma;
    private boolean applyBeta;
    private double epsilon;
    private int[] jaxis;

    @Builder(builderMethodName = "builder")
    public BatchNorm(SameDiff sameDiff, SDVariable[] inputFunctions, INDArray[] inputArrays, INDArray[]
            outputArrays, boolean inPlace, boolean applyGamma, boolean applyBeta, double epsilon, int[] axis) {
        super(null,sameDiff, inputFunctions, inPlace);
        Preconditions.checkState(axis != null && axis.length > 0, "Invalid axis argument: axis must be specified" +
                "and length > 0. Got %s", axis);
        this.sameDiff = sameDiff;

        this.applyGamma = applyGamma;
        this.applyBeta = applyBeta;
        this.epsilon = epsilon;
        this.jaxis = axis;
        if(inputArrays != null) {
            addInputArgument(inputArrays);
        }
        if(outputArrays != null) {
            addOutputArgument(outputArrays);
        }
        addArgs();
    }

    public BatchNorm(SameDiff sameDiff, SDVariable input, SDVariable mean, SDVariable variance,
                     SDVariable gamma, SDVariable beta, double epsilon, int[] axis) {
        super(null,sameDiff, wrapFilterNull(input, mean, variance, gamma, beta), false);
        Preconditions.checkState(axis != null && axis.length > 0, "Invalid axis argument: axis must be specified" +
                "and length > 0. Got %s", axis);
        this.sameDiff = sameDiff;
        this.applyBeta = beta != null;
        this.applyGamma = gamma != null;
        this.epsilon = epsilon;
        this.jaxis = axis;
        addArgs();
    }

    public BatchNorm(INDArray input, INDArray mean, INDArray variance, INDArray gamma, INDArray beta, double epsilon, int... axis){
        super(wrapFilterNull(input, mean, variance, gamma, beta), null);
        this.jaxis = axis;
        this.applyBeta = beta != null;
        this.applyGamma = gamma != null;
        this.epsilon = epsilon;
        addArgs();
    }

    public void addArgs() {
        addIArgument(ArrayUtil.fromBoolean(applyGamma));
        addIArgument(ArrayUtil.fromBoolean(applyBeta));
        if(jaxis != null) {
            //If null: op defaults to last dimension
            axis.clear();
            for (val v:jaxis) {
                axis.add(v);
            }
            addIArgument(jaxis);
        }
        addTArgument(epsilon);
    }


    @Override
    public Map<String, Object> propertiesForFunction() {
        Map<String,Object> ret = new LinkedHashMap<>();
        ret.put("applyGamma", applyGamma);
        ret.put("applyBeta", applyBeta);
        ret.put("epsilon", epsilon);
        ret.put("axis", axis);
        return ret;
    }

    @Override
    public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {
        TFGraphMapper.initFunctionFromProperties(nodeDef.getOp(), this, attributesForNode, nodeDef, graph);
        //Switch order: TF uses [input, gamma, beta, mean, variance]; libnd4j expects [input, mean, variance, gamma, beta]
        SameDiffOp op = initWith.getOps().get(this.getOwnName());
        List<String> list = op.getInputsToOp();
        List<String> newList = Arrays.asList(list.get(0), list.get(3), list.get(4), list.get(1), list.get(2));
        op.setInputsToOp(newList);

        this.applyGamma = true;
        this.applyBeta = true;
        this.epsilon = attributesForNode.get("epsilon").getF();

        if(attributesForNode.containsKey("data_format")){
            String dataFormat = attributesForNode.get("data_format").getS().toStringUtf8();
            //TODO not sure if these conv1d/3d cases appear. But BN definitely uses "NCHW" or "NHWC"
            if(dataFormat.equalsIgnoreCase(Conv2DConfig.NCHW) || dataFormat.equalsIgnoreCase(Conv1DConfig.NCW) || dataFormat.equalsIgnoreCase(Conv3DConfig.NCDHW)){
                jaxis = new int[]{1};
            } else if(dataFormat.equalsIgnoreCase(Conv2DConfig.NHWC)){
                jaxis = new int[]{3};
            } else if(dataFormat.equalsIgnoreCase(Conv1DConfig.NWC)){
                jaxis = new int[]{2};
            } else if(dataFormat.equalsIgnoreCase(Conv3DConfig.NDHWC)){
                jaxis = new int[]{4};
            } else {
                throw new IllegalStateException("Unknown data format: \"" + dataFormat + "\"" );
            }
        }



        addArgs();
    }

    @Override
    public void initFromOnnx(Onnx.NodeProto node, SameDiff initWith, Map<String, Onnx.AttributeProto> attributesForNode, Onnx.GraphProto graph) {

    }

    @Override
    public String opName() {
        return "batchnorm";
    }

    @Override
    public String onnxName() {
        return "BatchNormalization";
    }

    @Override
    public String tensorflowName() {
        return "FusedBatchNorm";
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        List<SDVariable> ret = new ArrayList<>();
        List<SDVariable> inputs = new ArrayList<>(Arrays.asList(args()));
        inputs.add(f1.get(0));
        BatchNormDerivative batchNormDerivative = BatchNormDerivative.derivativeBuilder()
                .sameDiff(sameDiff)
                .inputFunctions(inputs.toArray(new SDVariable[inputs.size()]))
                .applyGamma(applyGamma)
                .applyBeta(applyBeta)
                .epsilon(epsilon)
                .axis(jaxis)
                .build();
        ret.addAll(Arrays.asList(batchNormDerivative.outputVariables()));
        return ret;
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> inputDataTypes){
        Preconditions.checkState(inputDataTypes != null && inputDataTypes.size() >= 3 && inputDataTypes.size() <= 5,
                "Expected 3 to 5 input datatypes for %s, got %s", getClass(), inputDataTypes);
        if(inputDataTypes.get(0).isFPType())
            return Collections.singletonList(inputDataTypes.get(0));
        return Collections.singletonList(Nd4j.defaultFloatingPointType());
    }
}
