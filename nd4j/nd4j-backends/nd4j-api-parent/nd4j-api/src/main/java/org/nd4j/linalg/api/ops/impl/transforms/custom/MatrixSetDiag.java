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
import org.nd4j.linalg.api.ops.DynamicCustomOp;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MatrixSetDiag extends DynamicCustomOp {

    public MatrixSetDiag(SameDiff sameDiff, SDVariable in, SDVariable diag, boolean inPlace) {
        super(null, sameDiff, new SDVariable[]{in, diag}, inPlace);
    }

    public MatrixSetDiag(SameDiff sameDiff, SDVariable in, SDVariable diag) {
        this(sameDiff, in, diag, false);
    }

    public MatrixSetDiag(@NonNull INDArray in, @NonNull INDArray diag){
        super(new INDArray[]{in, diag}, null);
    }

    public MatrixSetDiag(){ }

    @Override
    public String[] tensorflowNames() {
        return new String[]{"MatrixSetDiag", "BatchMatrixSetDiag"};
    }

    @Override
    public String opName() {
        return "matrix_set_diag";
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> i_v) {
        SDVariable grad = i_v.get(0);
        SDVariable in1Grad = sameDiff.math.setDiag(grad, sameDiff.zerosLike(arg(1)));
        SDVariable in2Grad = sameDiff.math.diagPart(grad);
        return Arrays.asList(in1Grad, in2Grad);
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> dataTypes){
        Preconditions.checkState(dataTypes != null && dataTypes.size() == 2, "Expected exactly 2 input datatypes for %s, got %s", getClass(), dataTypes);
        Preconditions.checkState(dataTypes.get(0) == dataTypes.get(1), "Input datatypes must be same type, got %s", dataTypes);
        return Collections.singletonList(dataTypes.get(0));
    }
}
