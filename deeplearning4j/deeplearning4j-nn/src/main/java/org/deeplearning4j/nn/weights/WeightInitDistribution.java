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

import lombok.EqualsAndHashCode;
import org.deeplearning4j.nn.conf.distribution.Distribution;
import org.deeplearning4j.nn.conf.distribution.Distributions;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.distribution.impl.OrthogonalDistribution;
import org.nd4j.shade.jackson.annotation.JsonProperty;

@EqualsAndHashCode
public class WeightInitDistribution implements IWeightInit {

    private final Distribution distribution;

    public WeightInitDistribution(@JsonProperty("distribution") Distribution distribution) {
        if(distribution == null) {
            // Would fail later below otherwise
            throw new IllegalArgumentException("Must set distribution!");
        }
        this.distribution = distribution;
    }

    @Override
    public INDArray init(double fanIn, double fanOut, long[] shape, char order, INDArray paramView) {
        //org.nd4j.linalg.api.rng.distribution.Distribution not serializable
        org.nd4j.linalg.api.rng.distribution.Distribution dist = Distributions.createDistribution(distribution);
        if (dist instanceof OrthogonalDistribution) {
            dist.sample(paramView.reshape(order, shape));
        } else {
            dist.sample(paramView);
        }
        return paramView.reshape(order, shape);
    }
}
