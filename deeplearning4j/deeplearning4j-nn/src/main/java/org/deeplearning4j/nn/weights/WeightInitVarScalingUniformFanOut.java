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

@Data
@NoArgsConstructor
public class WeightInitVarScalingUniformFanOut implements IWeightInit {

    private Double scale;

    public WeightInitVarScalingUniformFanOut(Double scale){
        this.scale = scale;
    }

    @Override
    public INDArray init(double fanIn, double fanOut, long[] shape, char order, INDArray paramView) {
        double scalingFanOut = 3.0 / Math.sqrt(fanOut);
        if(scale != null)
            scalingFanOut *= scale;
        Nd4j.rand(paramView, Nd4j.getDistributions().createUniform(-scalingFanOut, scalingFanOut));
        return paramView.reshape(order, shape);
    }
}
