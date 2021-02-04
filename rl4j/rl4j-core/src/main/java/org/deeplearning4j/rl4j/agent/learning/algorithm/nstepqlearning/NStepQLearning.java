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
package org.deeplearning4j.rl4j.agent.learning.algorithm.nstepqlearning;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import org.deeplearning4j.rl4j.agent.learning.algorithm.IUpdateAlgorithm;
import org.deeplearning4j.rl4j.agent.learning.update.FeaturesLabels;
import org.deeplearning4j.rl4j.agent.learning.update.Gradients;
import org.deeplearning4j.rl4j.experience.StateActionPair;
import org.deeplearning4j.rl4j.network.CommonLabelNames;
import org.deeplearning4j.rl4j.network.CommonOutputNames;
import org.deeplearning4j.rl4j.network.IOutputNeuralNet;
import org.deeplearning4j.rl4j.network.ITrainableNeuralNet;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.List;

/**
 * This the "Algorithm S2 Asynchronous n-step Q-learning" of <i>Asynchronous Methods for Deep Reinforcement Learning</i>
 * @see <a href="https://arxiv.org/pdf/1602.01783.pdf">Asynchronous Methods for Deep Reinforcement Learning on arXiv</a>, page 14
 * <p/>
 * Note: The output of threadCurrent must contain a channel named "Q".
 */
public class NStepQLearning implements IUpdateAlgorithm<Gradients, StateActionPair<Integer>> {

    private final ITrainableNeuralNet threadCurrent;
    private final IOutputNeuralNet target;
    private final double gamma;
    private final NStepQLearningHelper algorithmHelper;

    /**
     * @param threadCurrent The &theta;' parameters (the thread-specific network)
     * @param target The &theta;<sup>&ndash;</sup> parameters (the global target network)
     * @param actionSpaceSize The numbers of possible actions that can be taken on the environment
     */
    public NStepQLearning(@NonNull ITrainableNeuralNet threadCurrent,
                          @NonNull IOutputNeuralNet target,
                          int actionSpaceSize,
                          @NonNull Configuration configuration) {
        this.threadCurrent = threadCurrent;
        this.target = target;
        this.gamma = configuration.getGamma();

        algorithmHelper = threadCurrent.isRecurrent()
                ? new RecurrentNStepQLearningHelper(actionSpaceSize)
                : new NonRecurrentNStepQLearningHelper(actionSpaceSize);
    }

    @Override
    public Gradients compute(List<StateActionPair<Integer>> trainingBatch) {
        int size = trainingBatch.size();

        StateActionPair<Integer> stateActionPair = trainingBatch.get(size - 1);

        INDArray features = algorithmHelper.createFeatures(trainingBatch);
        INDArray allExpectedQValues = threadCurrent.output(features).get(CommonOutputNames.QValues);

        INDArray labels = algorithmHelper.createLabels(size);

        double r;
        if (stateActionPair.isTerminal()) {
            r = 0;
        } else {
            INDArray expectedValuesOfLast = algorithmHelper.getTargetExpectedQValuesOfLast(target, trainingBatch, features);
            r = Nd4j.max(expectedValuesOfLast).getDouble(0);
        }

        for (int i = size - 1; i >= 0; --i) {
            stateActionPair = trainingBatch.get(i);

            r = stateActionPair.getReward() + gamma * r;
            INDArray expectedQValues = algorithmHelper.getExpectedQValues(allExpectedQValues, i);
            expectedQValues = expectedQValues.putScalar(stateActionPair.getAction(), r);

            algorithmHelper.setLabels(labels, i, expectedQValues);
        }

        FeaturesLabels featuresLabels = new FeaturesLabels(features);
        featuresLabels.putLabels(CommonLabelNames.QValues, labels);
        return threadCurrent.computeGradients(featuresLabels);
    }

    @SuperBuilder
    @Data
    public static class Configuration {
        /**
         * The discount factor (default is 0.99)
         */
        @Builder.Default
        double gamma = 0.99;
    }
}
