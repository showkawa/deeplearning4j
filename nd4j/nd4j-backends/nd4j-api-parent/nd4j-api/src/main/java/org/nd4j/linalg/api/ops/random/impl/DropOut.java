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

package org.nd4j.linalg.api.ops.random.impl;

import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.OpContext;
import org.nd4j.linalg.api.ops.random.BaseRandomOp;
import org.nd4j.linalg.api.shape.LongShapeDescriptor;

import java.util.Arrays;
import java.util.List;

@NoArgsConstructor
public class DropOut extends BaseRandomOp {

    private double p;

    public DropOut(SameDiff sameDiff, SDVariable input, double p) {
        super(sameDiff, input);
        this.p = p;
        this.extraArgs = new Object[] {p};

    }

    public DropOut(@NonNull INDArray x, double p) {
        this(x, x, p);
    }

    public DropOut(@NonNull INDArray x, @NonNull INDArray z, double p) {
        super(x,null,z);
        this.p = p;
        this.extraArgs = new Object[] {p};
    }

    @Override
    public int opNum() {
        return 1;
    }

    @Override
    public String opName() {
        return "dropout";
    }

    @Override
    public Type opType() {
        return Type.RANDOM ;
    }

    @Override
    public List<LongShapeDescriptor> calculateOutputShape(OpContext oc) {
        INDArray input = oc.getInputArray(0);
        return Arrays.asList(input.shapeDescriptor());
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        throw new UnsupportedOperationException("Not supported");   //We should only use *inverted* dropout with samediff
    }
}
