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
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.common.base.Preconditions;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.layers.convolution.config.Conv2DConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


@Slf4j
public class SConv2D extends Conv2D {

    @Builder(builderMethodName = "sameDiffSBuilder")
    public SConv2D(SameDiff sameDiff, SDVariable[] inputFunctions, Conv2DConfig conv2DConfig) {
        super(sameDiff, inputFunctions, conv2DConfig);
    }

    public SConv2D(@NonNull SameDiff sameDiff, @NonNull SDVariable layerInput, @NonNull SDVariable depthWeights,
                   SDVariable pointWeights, SDVariable bias, @NonNull Conv2DConfig conv2DConfig) {
        this(sameDiff, wrapFilterNull(layerInput, depthWeights, pointWeights, bias), conv2DConfig);
    }

    public SConv2D(INDArray[] inputs, INDArray[] outputs, Conv2DConfig config){
        super(inputs, outputs, config);
    }

    public SConv2D(@NonNull INDArray layerInput, @NonNull INDArray depthWeights, INDArray pointWeights, @NonNull Conv2DConfig Conv2DConfig){
        this(wrapFilterNull(layerInput, depthWeights, pointWeights), null, Conv2DConfig);
    }

    public SConv2D(INDArray layerInput, INDArray depthWeights, INDArray pointWeights, INDArray bias, Conv2DConfig config) {
        this(wrapFilterNull(layerInput, depthWeights, pointWeights, bias), null, config);
    }

    public SConv2D() {}



    @Override
    public String opName() {
        return "sconv2d";
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        //Args at libnd4j level: in, gradAtOut, wD, wP, bias
        //Args for SConv2d libnd4j: input, wD, wP, bias
        List<SDVariable> inputs = new ArrayList<>();
        inputs.add(arg(0));
        inputs.add(f1.get(0));
        SDVariable[] args = args();
        for( int i=1; i<args.length; i++ ){ //Skip input, already added
            inputs.add(args[i]);
        }
        SConv2DDerivative conv2DDerivative = SConv2DDerivative.sDerviativeBuilder()
                .conv2DConfig(config)
                .inputFunctions(inputs.toArray(new SDVariable[inputs.size()]))
                .sameDiff(sameDiff)
                .build();
        List<SDVariable> ret = Arrays.asList(conv2DDerivative.outputVariables());
        return ret;
    }

    @Override
    public long[] iArgs() {
        if (iArguments.size() == 0)
            addArgs();

        return super.iArgs();
    }

    @Override
    public boolean isConfigProperties() {
        return true;
    }

    @Override
    public String configFieldName() {
        return "config";
    }


    @Override
    public String[] tensorflowNames() {
        throw new NoOpNameFoundException("No op name found for " + opName());
    }

    @Override
    public String onnxName() {
        throw new NoOpNameFoundException("No onnx op opName found for op " + opName());
    }

    @Override
    public String tensorflowName() {
        return "separable_conv2d";
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> inputDataTypes){
        int n = args().length;
        Preconditions.checkState(inputDataTypes != null && inputDataTypes.size() == n, "Expected %s input data types for %s, got %s", n, getClass(), inputDataTypes);
        return Collections.singletonList(inputDataTypes.get(0));
    }
}
