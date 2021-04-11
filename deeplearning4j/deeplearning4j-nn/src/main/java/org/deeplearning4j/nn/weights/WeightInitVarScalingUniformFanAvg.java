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
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

/**
 * Uniform U[-a,a] with a=3.0/((fanIn + fanOut)/2)
 *
 * @author Adam Gibson
 */
@Data
@NoArgsConstructor
public class WeightInitVarScalingUniformFanAvg implements IWeightInit {

    private Double scale;

    public WeightInitVarScalingUniformFanAvg(Double scale){
        this.scale = scale;
    }

    @Override
    public INDArray init(double fanIn, double fanOut, long[] shape, char order, INDArray paramView) {
        double scalingFanAvg = 3.0 / Math.sqrt((fanIn + fanOut) / 2);
        if(scale != null)
            scalingFanAvg *= scale;

        Nd4j.rand(paramView, Nd4j.getDistributions().createUniform(-scalingFanAvg, scalingFanAvg));
        return paramView.reshape(order, shape);
    }
}
