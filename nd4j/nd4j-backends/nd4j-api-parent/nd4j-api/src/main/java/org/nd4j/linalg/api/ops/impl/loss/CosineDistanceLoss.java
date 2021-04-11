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

package org.nd4j.linalg.api.ops.impl.loss;

import org.nd4j.autodiff.loss.LossReduce;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.loss.bp.CosineDistanceLossBp;
import java.util.List;

public class CosineDistanceLoss extends BaseLoss {

    private int dimension;

    public CosineDistanceLoss(SameDiff sameDiff, LossReduce lossReduce, SDVariable predictions, SDVariable weights, SDVariable labels, int dimension){
        super(sameDiff, lossReduce, predictions, weights, labels);
        this.dimension = dimension;
        this.addIArgument(dimension);
    }

    public CosineDistanceLoss(SameDiff sameDiff, SDVariable labels, SDVariable predictions, SDVariable weights,
                            LossReduce lossReduce, int dimension) {
        this(sameDiff, lossReduce, predictions, weights, labels, dimension);
    }

    public CosineDistanceLoss(INDArray labels, INDArray predictions, INDArray weights, LossReduce lossReduce, int dimension){
        super(lossReduce, predictions, weights, labels);
        this.dimension = dimension;
        this.addIArgument(dimension);
    }

    public CosineDistanceLoss(){ }

    @Override
    public String opName() {
        return "cosine_distance_loss";
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> grad){
        //No external gradient.
        //Args are: predictions, weights, label
        return new CosineDistanceLossBp(sameDiff, lossReduce, arg(0), arg(1), arg(2), dimension).outputs();
    }

}
