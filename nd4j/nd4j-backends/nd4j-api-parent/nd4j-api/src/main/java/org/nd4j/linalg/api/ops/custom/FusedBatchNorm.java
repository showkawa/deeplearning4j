/* ******************************************************************************
 * Copyright (c) 2019 Konduit K.K.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/
package org.nd4j.linalg.api.ops.custom;

import lombok.NonNull;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.imports.graphmapper.tf.TFGraphMapper;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FusedBatchNorm extends DynamicCustomOp {

    private DataType outputDataType;

    public FusedBatchNorm() {}

    public FusedBatchNorm(@NonNull INDArray x, @NonNull INDArray scale, @NonNull INDArray offset,
                          int dataFormat, int isTraining,
                          INDArray yOut, INDArray batchMeanOut, INDArray batchMeanVar) {
        addInputArgument(x, scale, offset);
        addIArgument(dataFormat, isTraining);
        if (yOut != null && batchMeanOut != null && batchMeanVar != null) {
            addOutputArgument(yOut, batchMeanOut, batchMeanVar);
        }
        this.outputDataType = x.dataType();
    }

    public FusedBatchNorm(@NonNull SameDiff sameDiff, @NonNull SDVariable x, @NonNull SDVariable scale, @NonNull SDVariable offset,
                          @NonNull SDVariable dataFormat, @NonNull SDVariable isTraining) {
        super("", sameDiff, new SDVariable[]{x, scale, offset, dataFormat, isTraining});
    }

    @Override
    public String opName() {
        return "fused_batch_norm";
    }

    @Override
    public String[] tensorflowNames() {
        return new String[]{"FusedBatchNormV2","FusedBatchNormV3"};
    }

    @Override
    public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {
        boolean isNchw = attributesForNode.containsKey("data_format") && attributesForNode.get("data_format").getS().toStringUtf8().equalsIgnoreCase("NCHW");
        boolean training = !attributesForNode.containsKey("is_training") ? true : attributesForNode.get("is_training").getB();
        addIArgument(isNchw ? 1 : 0);
        addIArgument(training ? 1 : 0);
        if(attributesForNode.containsKey("T")){
            outputDataType = TFGraphMapper.convertType(attributesForNode.get("T").getType());
        }
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> inputDataTypes){
        int n = args().length;
        Preconditions.checkState(inputDataTypes != null && inputDataTypes.size() == n, "Expected %s input data types for %s, got %s", n, getClass(), inputDataTypes);
        return Arrays.asList(outputDataType, DataType.FLOAT, DataType.FLOAT);   //Activations may be half, bfloat16, float32; mean/var is always float
    }
}
