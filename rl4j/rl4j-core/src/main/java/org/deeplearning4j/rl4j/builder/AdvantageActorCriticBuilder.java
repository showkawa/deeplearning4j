/*******************************************************************************
 * Copyright (c) 2020 Konduit K.K.
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
package org.deeplearning4j.rl4j.builder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.builder.Builder;
import org.deeplearning4j.rl4j.agent.IAgentLearner;
import org.deeplearning4j.rl4j.agent.learning.algorithm.actorcritic.AdvantageActorCritic;
import org.deeplearning4j.rl4j.agent.learning.algorithm.IUpdateAlgorithm;
import org.deeplearning4j.rl4j.agent.learning.update.Gradients;
import org.deeplearning4j.rl4j.agent.learning.update.updater.async.AsyncSharedNetworksUpdateHandler;
import org.deeplearning4j.rl4j.environment.Environment;
import org.deeplearning4j.rl4j.environment.IActionSchema;
import org.deeplearning4j.rl4j.experience.StateActionPair;
import org.deeplearning4j.rl4j.network.ITrainableNeuralNet;
import org.deeplearning4j.rl4j.observation.transform.TransformProcess;
import org.deeplearning4j.rl4j.policy.ACPolicy;
import org.deeplearning4j.rl4j.policy.IPolicy;
import org.nd4j.linalg.api.rng.Random;

/**
 * A {@link IAgentLearner} builder that will setup a {@link AdvantageActorCritic Advantage Actor Critic} algorithm with these:
 * <li>a specialized actor critic policy</li>
 * <li>a n-step state-action-reward experience handler</li>
 * <li>a neural net updater that expects gradient update data</li>
 * <li>a advantage actor critic gradient conputation algorithm</li>
 * <p />
 * Note: The network needs the {@link org.deeplearning4j.rl4j.network.ac.ActorCriticLoss ActorCriticLoss} as the
 * policy network loss function
 */
public class AdvantageActorCriticBuilder extends BaseAsyncAgentLearnerBuilder<AdvantageActorCriticBuilder.Configuration> {

    private final Random rnd;

    public AdvantageActorCriticBuilder(@NonNull Configuration configuration,
                                       @NonNull ITrainableNeuralNet neuralNet,
                                       @NonNull Builder<Environment<Integer>> environmentBuilder,
                                       @NonNull Builder<TransformProcess> transformProcessBuilder,
                                       Random rnd) {
        super(configuration, neuralNet, environmentBuilder, transformProcessBuilder);
        this.rnd = rnd;
    }

    @Override
    protected IPolicy<Integer> buildPolicy() {
        return ACPolicy.builder()
            .neuralNet(networks.getThreadCurrentNetwork())
            .isTraining(true)
            .rnd(rnd)
            .build();
    }

    @Override
    protected IUpdateAlgorithm<Gradients, StateActionPair<Integer>> buildUpdateAlgorithm() {
        IActionSchema<Integer> actionSchema = getEnvironment().getSchema().getActionSchema();
        return new AdvantageActorCritic(networks.getThreadCurrentNetwork(), actionSchema.getActionSpaceSize(), configuration.getAdvantageActorCriticConfiguration());
    }

    @Override
    protected AsyncSharedNetworksUpdateHandler buildAsyncSharedNetworksUpdateHandler() {
        return new AsyncSharedNetworksUpdateHandler(networks.getGlobalCurrentNetwork(), configuration.getNeuralNetUpdaterConfiguration());
    }

    @EqualsAndHashCode(callSuper = true)
    @SuperBuilder
    @Data
    public static class Configuration extends BaseAsyncAgentLearnerBuilder.Configuration {
        AdvantageActorCritic.Configuration advantageActorCriticConfiguration;
    }

}
