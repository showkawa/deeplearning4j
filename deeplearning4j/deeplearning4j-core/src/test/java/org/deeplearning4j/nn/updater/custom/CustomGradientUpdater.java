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

package org.deeplearning4j.nn.updater.custom;

import lombok.AllArgsConstructor;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.learning.GradientUpdater;

import java.util.Map;

@AllArgsConstructor
public class CustomGradientUpdater implements GradientUpdater<CustomIUpdater> {

    private CustomIUpdater config;

    @Override
    public CustomIUpdater getConfig() {
        return config;
    }

    @Override
    public void setState(Map<String, INDArray> stateMap, boolean initialize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, INDArray> getState() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setStateViewArray(INDArray viewArray, long[] gradientShape, char gradientOrder, boolean initialize) {
        //No op
    }

    @Override
    public void applyUpdater(INDArray gradient, int iteration, int epoch) {
        gradient.muli(config.getLearningRate());
    }
}
