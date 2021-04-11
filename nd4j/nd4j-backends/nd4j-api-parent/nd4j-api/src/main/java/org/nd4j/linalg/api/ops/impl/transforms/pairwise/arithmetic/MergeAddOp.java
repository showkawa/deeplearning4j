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

package org.nd4j.linalg.api.ops.impl.transforms.pairwise.arithmetic;

import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.common.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.transforms.BaseDynamicTransformOp;
import org.nd4j.linalg.api.ops.impl.transforms.pairwise.arithmetic.bp.MergeAddBp;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@NoArgsConstructor
public class MergeAddOp extends BaseDynamicTransformOp {

    public MergeAddOp(SameDiff sameDiff, SDVariable[] args, boolean inPlace) {
        super(sameDiff, args, inPlace);
    }

    public MergeAddOp(SameDiff sameDiff, SDVariable[] args) {
        this(sameDiff, args, false);
    }

    public MergeAddOp(@NonNull INDArray... inputs){
        this(inputs, null);
    }

    public MergeAddOp(INDArray[] inputs, INDArray[] outputs) {
        super(inputs, outputs);
    }

    @Override
    public String opName() {
        return "mergeadd";
    }

    @Override
    public String onnxName() {
        return "mergeadd";
    }

    @Override
    public String[] tensorflowNames(){
        return new String[]{"add_n", "AccumulateNV2"};
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> i_v) {
         return Arrays.asList(new MergeAddBp(sameDiff, args(), i_v.get(0)).outputVariables());

    }


    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> dataTypes) {
        DataType first = dataTypes.get(0);
        for( int i = 1; i < dataTypes.size(); i++) {
            Preconditions.checkState(first == dataTypes.get(i), "Expected all input datatypes to be the same: first input is %s, input %s is %s", first, i, dataTypes.get(i));
        }
        return Collections.singletonList(first);
    }

}
