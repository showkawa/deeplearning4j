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
package org.nd4j.linalg.api.ops.custom;

import lombok.NonNull;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.common.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;

import java.util.Collections;
import java.util.List;

public class AdjustSaturation extends DynamicCustomOp {

    public AdjustSaturation() {}

    public AdjustSaturation(@NonNull INDArray in, double factor, INDArray out) {
        this(in, factor);
        if (out != null) {
            outputArguments.add(out);
        }
    }

    public AdjustSaturation(@NonNull INDArray in, double factor) {
        Preconditions.checkArgument(in.rank() >= 3,
                "AdjustSaturation: op expects rank of input array to be >= 3, but got %s instead", in.rank());
        inputArguments.add(in);

        addTArgument(factor);
    }

    public AdjustSaturation(@NonNull SameDiff sameDiff, @NonNull SDVariable in, @NonNull SDVariable factor) {
        super(sameDiff, new SDVariable[]{in, factor});
    }

    public AdjustSaturation(@NonNull SameDiff sameDiff, @NonNull SDVariable in, double factor) {
        super(sameDiff, new SDVariable[]{in});
        addTArgument(factor);
    }

    @Override
    public String opName() {
        return "adjust_saturation";
    }

    @Override
    public String tensorflowName() {
        return "AdjustSaturation";
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> inputDataTypes){
        int n = args().length;
        Preconditions.checkState(inputDataTypes != null && inputDataTypes.size() == n, "Expected %s input data types for %s, got %s", n, getClass(), inputDataTypes);
        return Collections.singletonList(inputDataTypes.get(0));
    }
}
