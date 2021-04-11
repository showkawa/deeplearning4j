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
package org.deeplearning4j.nn.layers.capsule;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.datasets.iterator.impl.MnistDataSetIterator;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.ActivationLayer;
import org.deeplearning4j.nn.conf.layers.CapsuleLayer;
import org.deeplearning4j.nn.conf.layers.CapsuleStrengthLayer;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.LossLayer;
import org.deeplearning4j.nn.conf.layers.PrimaryCapsules;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.evaluation.classification.Evaluation;
import org.nd4j.linalg.activations.impl.ActivationSoftmax;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.impl.LossNegativeLogLikelihood;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

@Disabled("AB - ignored due to excessive runtime. Keep for manual debugging when required")
@DisplayName("Caps Net MNIST Test")
@NativeTag
@Tag(TagNames.DL4J_OLD_API)
class CapsNetMNISTTest extends BaseDL4JTest {

    @Override
    public DataType getDataType() {
        return DataType.FLOAT;
    }

    @Test
    @DisplayName("Test Caps Net On MNIST")
    void testCapsNetOnMNIST() {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().seed(123).updater(new Adam()).list().layer(new ConvolutionLayer.Builder().nOut(16).kernelSize(9, 9).stride(3, 3).build()).layer(new PrimaryCapsules.Builder(8, 8).kernelSize(7, 7).stride(2, 2).build()).layer(new CapsuleLayer.Builder(10, 16, 3).build()).layer(new CapsuleStrengthLayer.Builder().build()).layer(new ActivationLayer.Builder(new ActivationSoftmax()).build()).layer(new LossLayer.Builder(new LossNegativeLogLikelihood()).build()).setInputType(InputType.convolutionalFlat(28, 28, 1)).build();
        MultiLayerNetwork model = new MultiLayerNetwork(conf);
        model.init();
        int rngSeed = 12345;
        try {
            MnistDataSetIterator mnistTrain = new MnistDataSetIterator(64, true, rngSeed);
            MnistDataSetIterator mnistTest = new MnistDataSetIterator(64, false, rngSeed);
            for (int i = 0; i < 2; i++) {
                model.fit(mnistTrain);
            }
            Evaluation eval = model.evaluate(mnistTest);
            assertTrue(eval.accuracy() > 0.95, "Accuracy not over 95%");
            assertTrue(eval.precision() > 0.95, "Precision not over 95%");
            assertTrue(eval.recall() > 0.95, "Recall not over 95%");
            assertTrue(eval.f1() > 0.95, "F1-score not over 95%");
        } catch (IOException e) {
            System.out.println("Could not load MNIST.");
        }
    }
}
