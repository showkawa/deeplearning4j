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

import onnx.Onnx;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.common.base.Preconditions;
import org.nd4j.imports.descriptors.properties.PropertyMapping;
import org.nd4j.imports.graphmapper.tf.TFGraphMapper;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.*;

public class ParallelStack extends DynamicCustomOp {

    public ParallelStack() {
    }

    public ParallelStack(SameDiff sameDiff, SDVariable[] values) {
        super(null, sameDiff, values, false);
    }

    public ParallelStack(INDArray[] inputs){
        super(inputs, null);
    }

    @Override
    public String opName() {
        return "parallel_stack";
    }

    @Override
    public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {
        TFGraphMapper.initFunctionFromProperties(nodeDef.getOp(), this, attributesForNode, nodeDef, graph);
    }

    @Override
    public void initFromOnnx(Onnx.NodeProto node, SameDiff initWith, Map<String, Onnx.AttributeProto> attributesForNode, Onnx.GraphProto graph) {
        throw new UnsupportedOperationException("No analog found for onnx for " + opName());
    }


    @Override
    public Map<String, Map<String, PropertyMapping>> mappingsForFunction() {
        Map<String, Map<String, PropertyMapping>> ret = new HashMap<>();
        Map<String, PropertyMapping> map = new HashMap<>();
        ret.put(tensorflowName(), map);
        return ret;
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> dataTypes){
        DataType first = dataTypes.get(0);
        for( int i=1; i<dataTypes.size(); i++ ){
            DataType dt = dataTypes.get(i);
            Preconditions.checkState(first == dt, "All inputs must have same datatype - got %s and %s for inputs 0 and %s respectively", first, dt, i);
        }
        //Output type is same as input types
        return Collections.singletonList(first);
    }
    
}
