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

package org.nd4j.linalg.api.ops.impl.broadcast;

import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.common.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.*;

/**
 * Bias addition gradient operation.
 */
@NoArgsConstructor
public class BiasAdd extends DynamicCustomOp {

    protected boolean nchw = true;

    public BiasAdd(SameDiff sameDiff, SDVariable input, SDVariable bias, boolean nchw) {
        super(null, sameDiff, new SDVariable[] {input, bias}, false);
        bArguments.clear();
        bArguments.add(nchw);
        this.nchw = nchw;
    }

    public BiasAdd(@NonNull INDArray input, @NonNull INDArray bias, boolean nchw){
        this(input, bias, null, nchw);
    }

    public BiasAdd(@NonNull INDArray input, @NonNull INDArray bias, INDArray output, boolean nchw){
        super(new INDArray[]{input, bias}, wrapOrNull(output));
        bArguments.clear();
        bArguments.add(nchw);
        this.nchw = nchw;
    }

    @Override
    public String opName() {
        return "biasadd";
    }

    @Override
    public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {
        super.initFromTensorFlow(nodeDef, initWith, attributesForNode, graph);
        if(attributesForNode.containsKey("data_format")){
            nchw = "NCHW".equalsIgnoreCase(attributesForNode.get("data_format").getS().toStringUtf8());
        } else {
            nchw = false;   //TF default is NHWC
        }
        bArguments.clear();
        bArguments.add(nchw);
    }

    @Override
    public String onnxName() {
        return "BiasAdd";
    }

    @Override
    public String[] tensorflowNames() {
        return new String[]{"BiasAdd","BiasAddV1"};
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> gradient){
        return new BiasAddGrad(sameDiff, arg(0), arg(1), gradient.get(0), nchw).outputs();
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> inputDataTypes){
        Preconditions.checkState(inputDataTypes != null && inputDataTypes.size() == 2, "Expected 2 input data types for %s, got %s", getClass(), inputDataTypes);
        return Collections.singletonList(inputDataTypes.get(0));
    }
}
