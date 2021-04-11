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
import lombok.NonNull;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.learning.config.AdaBelief;

import java.util.HashMap;
import java.util.Map;

//https://arxiv.org/pdf/2010.07468.pdf


@Data
public class AdaBeliefUpdater implements GradientUpdater<AdaBelief> {
    public static final String M_STATE = "M";
    public static final String S_STATE = "S";

    private AdaBelief config;
    private INDArray m, s; // moving avg & sqrd gradients

    private char gradientReshapeOrder;

    public AdaBeliefUpdater(AdaBelief config) {
        this.config = config;
    }


    @Override
    public void setState(@NonNull Map<String, INDArray> stateMap, boolean initialize) {
        if(!stateMap.containsKey(M_STATE) || !stateMap.containsKey(S_STATE) || stateMap.size() != 2){
            throw new IllegalStateException("State map should contain only keys [" + M_STATE + "," + S_STATE + "] but has keys " + stateMap.keySet());
        }
        this.m = stateMap.get(M_STATE);
        this.s = stateMap.get(S_STATE);
    }

    @Override
    public Map<String, INDArray> getState() {
        Map<String,INDArray> r = new HashMap<>();
        r.put(M_STATE, m);
        r.put(S_STATE, s);
        return r;
    }

    @Override
    public void setStateViewArray(INDArray viewArray, long[] gradientShape, char gradientOrder, boolean initialize) {
        if (!viewArray.isRowVector())
            throw new IllegalArgumentException("Invalid input: expect row vector input");
        if (initialize)
            viewArray.assign(0);
        long length = viewArray.length();
        this.m = viewArray.get(NDArrayIndex.point(0), NDArrayIndex.interval(0, length / 2));
        this.s = viewArray.get(NDArrayIndex.point(0), NDArrayIndex.interval(length / 2, length));

        //Reshape to match the expected shape of the input gradient arrays
        this.m = Shape.newShapeNoCopy(this.m, gradientShape, gradientOrder == 'f');
        this.s = Shape.newShapeNoCopy(this.s, gradientShape, gradientOrder == 'f');
        if (m == null || s == null)
            throw new IllegalStateException("Could not correctly reshape gradient view arrays");

        this.gradientReshapeOrder = gradientOrder;
    }

    /**
     * Calculate the update based on the given gradient
     *
     * @param gradient  the gradient to get the update for
     * @param iteration
     * @return the gradient
     */
    @Override
    public void applyUpdater(INDArray gradient, int iteration, int epoch) {
        if (m == null || s == null)
            throw new IllegalStateException("Updater has not been initialized with view state");

        double beta1 = config.getBeta1();
        double beta2 = config.getBeta2();
        double learningRate = config.getLearningRate(iteration, epoch);
        double epsilon = config.getEpsilon();

        Nd4j.exec(new org.nd4j.linalg.api.ops.impl.updaters.AdaBeliefUpdater(gradient, s, m, learningRate, beta1, beta2, epsilon, iteration));
    }
}
