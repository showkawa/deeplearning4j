/*******************************************************************************
 * Copyright (c) 2015-2019 Skymind, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

package org.nd4j.linalg.api.ops.impl.loss;

import org.nd4j.autodiff.loss.LossReduce;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.loss.bp.AbsoluteDifferenceLossBp;

import java.util.List;

/**
 * Absolute difference loss
 *
 * @author Alex Black
 */
public class AbsoluteDifferenceLoss extends BaseLoss {


    public AbsoluteDifferenceLoss(SameDiff sameDiff, LossReduce lossReduce, SDVariable predictions, SDVariable weights, SDVariable labels){
        super(sameDiff, lossReduce, predictions, weights, labels);
    }

    public AbsoluteDifferenceLoss(SameDiff sameDiff, SDVariable label, SDVariable predictions, SDVariable weights,
                                    LossReduce lossReduce) {
        this(sameDiff, lossReduce, predictions, weights, label);
    }

    public AbsoluteDifferenceLoss(INDArray labels, INDArray predictions, INDArray weights, LossReduce lossReduce){
        super(lossReduce, predictions, weights, labels);
    }

    public AbsoluteDifferenceLoss(){ }

    @Override
    public String opName() {
        return "absolute_difference_loss";
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> grad){
        //No external gradient
        //Args are: predictions, weights, label
        return new AbsoluteDifferenceLossBp(sameDiff, lossReduce, arg(0), arg(1), arg(2)).outputs();
    }
}
