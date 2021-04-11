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

package org.deeplearning4j.nn.weights;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.math3.util.FastMath;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.random.impl.TruncatedNormalDistribution;
import org.nd4j.linalg.factory.Nd4j;

@Data
@NoArgsConstructor
public class WeightInitVarScalingNormalFanOut implements IWeightInit {

    private Double scale;

    public WeightInitVarScalingNormalFanOut(Double scale){
        this.scale = scale;
    }

    @Override
    public INDArray init(double fanIn, double fanOut, long[] shape, char order, INDArray paramView) {
        double std;
        if(scale == null){
            std = Math.sqrt(1.0 / fanOut);
        } else {
            std = Math.sqrt(scale / fanOut);
        }

        Nd4j.exec(new TruncatedNormalDistribution(paramView, 0.0, std));
        return paramView.reshape(order, shape);
    }
}
