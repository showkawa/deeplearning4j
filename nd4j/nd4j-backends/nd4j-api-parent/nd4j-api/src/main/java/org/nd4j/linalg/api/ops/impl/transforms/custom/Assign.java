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

package org.nd4j.linalg.api.ops.impl.transforms.custom;

import onnx.Onnx;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.common.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.api.ops.Op;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Assign extends DynamicCustomOp {

    public Assign(){

    }

    public Assign(INDArray[] inputs, INDArray[] outputs) {
        super(null,inputs, outputs);
    }

    public Assign(INDArray x, INDArray y ) {
        this( new INDArray[]{y ,x},new INDArray[]{y}); // TODO: Still check. y cannot be null, must be same shape as x.
    }

    @Override
    public void addIArgument(int... arg) {
        super.addIArgument(arg);
    }

    public Assign(SameDiff sameDiff, SDVariable x, SDVariable y){
        super(null, sameDiff, new SDVariable[]{x,y});
    }


    @Override
    public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {
        super.initFromTensorFlow(nodeDef, initWith, attributesForNode, graph);
    }

    @Override
    public void initFromOnnx(Onnx.NodeProto node, SameDiff initWith, Map<String, Onnx.AttributeProto> attributesForNode, Onnx.GraphProto graph) {
        super.initFromOnnx(node, initWith, attributesForNode, graph);
    }

    @Override
    public String opName() {
        return "assign";
    }

    @Override
    public String onnxName() {
        return "GivenTensorFill";
    }

    @Override
    public String tensorflowName() {
        return "Assign";
    }

    @Override
    public Op.Type opType() {
        return Op.Type.CUSTOM;
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1){
        //TODO replace with assign backprop op from libnd4j (that handles the broadcast case properly)
        return Arrays.asList(sameDiff.zerosLike(larg()), f1.get(0));
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> dataTypes){
        Preconditions.checkState(dataTypes != null && dataTypes.size() == 2, "Expected exactly 2 input datatypes for %s, got %s", getClass(), dataTypes);
        return Collections.singletonList(dataTypes.get(0));
    }

}
