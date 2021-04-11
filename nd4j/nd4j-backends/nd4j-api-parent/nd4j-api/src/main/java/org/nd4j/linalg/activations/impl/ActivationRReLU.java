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

package org.nd4j.linalg.activations.impl;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.nd4j.linalg.api.memory.MemoryWorkspace;
import org.nd4j.common.primitives.Pair;
import org.nd4j.linalg.activations.BaseActivationFunction;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.scalar.RectifiedLinear;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.BooleanIndexing;
import org.nd4j.linalg.indexing.conditions.Conditions;
import org.nd4j.shade.jackson.annotation.JsonIgnoreProperties;

@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties({"alpha"})
@Getter
public class ActivationRReLU extends BaseActivationFunction {
    public static final double DEFAULT_L = 1.0 / 8;
    public static final double DEFAULT_U = 1.0 / 3;

    private double l, u;
    private transient INDArray alpha; //don't need to write to json, when streaming

    public ActivationRReLU() {
        this(DEFAULT_L, DEFAULT_U);
    }

    public ActivationRReLU(double l, double u) {
        if (l > u) {
            throw new IllegalArgumentException("Cannot have lower value (" + l + ") greater than upper (" + u + ")");
        }
        this.l = l;
        this.u = u;
    }

    @Override
    public INDArray getActivation(INDArray in, boolean training) {
        if (training) {
            try(MemoryWorkspace ignored = Nd4j.getWorkspaceManager().scopeOutOfWorkspaces()) {
                this.alpha = Nd4j.rand(l, u, Nd4j.getRandom(), in.shape());
            }
            INDArray inTimesAlpha = in.mul(alpha);
            BooleanIndexing.replaceWhere(in, inTimesAlpha, Conditions.lessThan(0));
        } else {
            this.alpha = null;
            double a = 0.5 * (l + u);
            return Nd4j.getExecutioner().exec(new RectifiedLinear(in, a));
        }

        return in;
    }

    @Override
    public Pair<INDArray, INDArray> backprop(INDArray in, INDArray epsilon) {
        assertShape(in, epsilon);
        INDArray dLdz = Nd4j.ones(in.shape());
        BooleanIndexing.replaceWhere(dLdz, alpha, Conditions.lessThanOrEqual(0.0));
        dLdz.muli(epsilon);

        return new Pair<>(dLdz, null);
    }

    @Override
    public String toString() {
        return "rrelu(l=" + l + ", u=" + u + ")";
    }

}
