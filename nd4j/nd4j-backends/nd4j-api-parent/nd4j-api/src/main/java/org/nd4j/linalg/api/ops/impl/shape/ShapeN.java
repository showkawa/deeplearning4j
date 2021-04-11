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

package org.nd4j.linalg.api.ops.impl.shape;

import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.imports.graphmapper.tf.TFGraphMapper;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShapeN extends DynamicCustomOp {

    protected DataType dataType;

    public ShapeN() {}

    public ShapeN(SameDiff sameDiff, SDVariable[] inputs, boolean inPlace) {
        super(null, sameDiff, inputs, inPlace);
    }

    @Override
    public String onnxName() {
        throw new NoOpNameFoundException("No onnx name found for shape " + opName());
    }


    @Override
    public String opName() {
        return "shapes_of";
    }

    @Override
    public String tensorflowName() {
        return "ShapeN";
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> i_v) {
        List<SDVariable> out = new ArrayList<>();
        for(SDVariable in : args()){
            out.add(sameDiff.zerosLike(in));
        }
        return out;
    }

    @Override
    public int getNumOutputs(){
        return args().length;
    }

    @Override
    public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {
        super.initFromTensorFlow(nodeDef, initWith, attributesForNode, graph);
        dataType = TFGraphMapper.convertType(nodeDef.getAttrOrThrow("out_type").getType());
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> dataTypes){
        //Output type is always long (i.e., shape of array) - for each input
        //TODO TF allows customizing int or long
        int n = getNumOutputs();
        List<DataType> outputTypes = new ArrayList<>(n);
        for(int i=0; i<n; i++ ){
            outputTypes.add(dataType == null ? DataType.LONG : dataType);
        }
        return outputTypes;
    }
}
