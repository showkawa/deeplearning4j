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

import lombok.NonNull;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.common.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.transforms.BaseDynamicTransformOp;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Max extends BaseDynamicTransformOp {
    public Max() {}

    public Max(SameDiff sameDiff, @NonNull SDVariable first, @NonNull SDVariable second){
        this(sameDiff, new SDVariable[]{first, second}, false);
    }

    public Max( SameDiff sameDiff, SDVariable[] args, boolean inPlace) {
        super(sameDiff, args, inPlace);
    }

    public Max( INDArray first, INDArray second, INDArray out){
        super(new INDArray[]{first, second}, out == null ? null : new INDArray[]{out});
    }

    public Max( INDArray first, INDArray second){
        this(first, second, null);
    }

    public Max( INDArray[] inputs, INDArray[] outputs) {
        super(inputs, outputs);
    }

  @Override
    public String opName() {
        return "maximum";
    }

    @Override
    public String onnxName() {
       return "Max";
    }

    @Override
    public String tensorflowName() {
        return "Maximum";
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        return Arrays.asList(new MaximumBp(sameDiff, arg(0), arg(1), f1.get(0)).outputVariables());
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> dataTypes){
        Preconditions.checkState(dataTypes != null && dataTypes.size() == 2, "Expected exactly 2 input datatypes for %s, got %s", getClass(), dataTypes);
        Preconditions.checkState(dataTypes.get(0) == dataTypes.get(1), "Input datatypes must be the same, got %s", dataTypes);
        return Collections.singletonList(dataTypes.get(0));
    }
}
