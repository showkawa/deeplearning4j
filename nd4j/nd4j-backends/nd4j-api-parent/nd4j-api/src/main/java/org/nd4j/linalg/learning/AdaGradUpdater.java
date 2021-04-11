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

package org.nd4j.linalg.learning;


import lombok.Data;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.AdaGrad;

import java.util.Collections;
import java.util.Map;


@Data
public class AdaGradUpdater implements GradientUpdater<AdaGrad> {
    public static final String GRAD_STATE = "grad";
    public INDArray historicalGradient;
    public int[] shape;
    protected double learningRate = 1e-1; // learning rate
    protected int numIterations = 0;
    private double epsilon = AdaGrad.DEFAULT_ADAGRAD_EPSILON;

    private char gradientReshapeOrder;

    private AdaGrad config;

    public AdaGradUpdater(AdaGrad config) {
        this.config = config;
    }

    @Override
    public void setState(Map<String, INDArray> stateMap, boolean initialize) {
        if(!stateMap.containsKey(GRAD_STATE) || stateMap.size() != 1){
            throw new IllegalStateException("State map should contain only key [" + GRAD_STATE + "] but has keys " + stateMap.keySet());
        }
        this.historicalGradient = stateMap.get(GRAD_STATE);
    }

    @Override
    public Map<String, INDArray> getState() {
        return Collections.singletonMap(GRAD_STATE, historicalGradient);
    }

    @Override
    public void setStateViewArray(INDArray viewArray, long[] gradientShape, char gradientOrder, boolean initialize) {
        if (!viewArray.isRowVectorOrScalar())
            throw new IllegalArgumentException("Invalid input: expect row vector input");
        if (initialize)
            viewArray.assign(epsilon);
        this.historicalGradient = viewArray;
        //Reshape to match the expected shape of the input gradient arrays
        this.historicalGradient = Shape.newShapeNoCopy(this.historicalGradient, gradientShape, gradientOrder == 'f');
        if (historicalGradient == null)
            throw new IllegalStateException("Could not correctly reshape gradient view array");

        this.gradientReshapeOrder = gradientOrder;
    }

    /**
     * Gets feature specific learning rates
     * Adagrad keeps a history of gradients being passed in.
     * Note that each gradient passed in becomes adapted over time, hence the opName adagrad
     *
     * @param gradient  the gradient to get learning rates for
     * @param iteration
     */
    @Override
    public void applyUpdater(INDArray gradient, int iteration, int epoch) {
        if (historicalGradient == null)
            throw new IllegalStateException("Updater has not been initialized with view state");

        double learningRate = config.getLearningRate(iteration, epoch);
        double epsilon = config.getEpsilon();

        Nd4j.exec(new org.nd4j.linalg.api.ops.impl.updaters.AdaGradUpdater(gradient, historicalGradient, learningRate, epsilon));
    }
}
