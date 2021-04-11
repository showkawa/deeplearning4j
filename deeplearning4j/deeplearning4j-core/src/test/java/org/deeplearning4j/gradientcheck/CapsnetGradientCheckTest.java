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
package org.deeplearning4j.gradientcheck;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.TestUtils;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.distribution.UniformDistribution;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.ActivationLayer;
import org.deeplearning4j.nn.conf.layers.CapsuleLayer;
import org.deeplearning4j.nn.conf.layers.CapsuleStrengthLayer;
import org.deeplearning4j.nn.conf.layers.LossLayer;
import org.deeplearning4j.nn.conf.layers.PrimaryCapsules;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInitDistribution;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.activations.impl.ActivationSoftmax;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.NoOp;
import org.nd4j.linalg.lossfunctions.impl.LossNegativeLogLikelihood;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

@Disabled
@DisplayName("Capsnet Gradient Check Test")
@Tag(TagNames.NDARRAY_ETL)
@Tag(TagNames.TRAINING)
@Tag(TagNames.DL4J_OLD_API)
@NativeTag
class CapsnetGradientCheckTest extends BaseDL4JTest {

    @Override
    public long getTimeoutMilliseconds() {
        return 90000L;
    }

    @Test
    @DisplayName("Test Caps Net")
    void testCapsNet() {
        int[] minibatchSizes = { 8, 16 };
        int width = 6;
        int height = 6;
        int inputDepth = 4;
        int[] primaryCapsDims = { 2, 4 };
        int[] primaryCapsChannels = { 8 };
        int[] capsules = { 5 };
        int[] capsuleDims = { 4, 8 };
        int[] routings = { 1 };
        Nd4j.getRandom().setSeed(12345);
        for (int routing : routings) {
            for (int primaryCapsDim : primaryCapsDims) {
                for (int primarpCapsChannel : primaryCapsChannels) {
                    for (int capsule : capsules) {
                        for (int capsuleDim : capsuleDims) {
                            for (int minibatchSize : minibatchSizes) {
                                INDArray input = Nd4j.rand(minibatchSize, inputDepth * height * width).mul(10).reshape(-1, inputDepth, height, width);
                                INDArray labels = Nd4j.zeros(minibatchSize, capsule);
                                for (int i = 0; i < minibatchSize; i++) {
                                    labels.putScalar(new int[] { i, i % capsule }, 1.0);
                                }
                                MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().dataType(DataType.DOUBLE).seed(123).updater(new NoOp()).weightInit(new WeightInitDistribution(new UniformDistribution(-6, 6))).list().layer(new PrimaryCapsules.Builder(primaryCapsDim, primarpCapsChannel).kernelSize(3, 3).stride(2, 2).build()).layer(new CapsuleLayer.Builder(capsule, capsuleDim, routing).build()).layer(new CapsuleStrengthLayer.Builder().build()).layer(new ActivationLayer.Builder(new ActivationSoftmax()).build()).layer(new LossLayer.Builder(new LossNegativeLogLikelihood()).build()).setInputType(InputType.convolutional(height, width, inputDepth)).build();
                                MultiLayerNetwork net = new MultiLayerNetwork(conf);
                                net.init();
                                for (int i = 0; i < 4; i++) {
                                    System.out.println("nParams, layer " + i + ": " + net.getLayer(i).numParams());
                                }
                                String msg = "minibatch=" + minibatchSize + ", PrimaryCaps: " + primarpCapsChannel + " channels, " + primaryCapsDim + " dimensions, Capsules: " + capsule + " capsules with " + capsuleDim + " dimensions and " + routing + " routings";
                                System.out.println(msg);
                                boolean gradOK = GradientCheckUtil.checkGradients(new GradientCheckUtil.MLNConfig().net(net).input(input).labels(labels).subset(true).maxPerParam(100));
                                assertTrue(gradOK,msg);
                                TestUtils.testModelSerialization(net);
                            }
                        }
                    }
                }
            }
        }
    }
}
