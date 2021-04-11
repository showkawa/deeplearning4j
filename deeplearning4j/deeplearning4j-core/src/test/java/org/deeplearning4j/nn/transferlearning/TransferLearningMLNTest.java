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
package org.deeplearning4j.nn.transferlearning;

import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.TestUtils;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.BackpropType;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.constraint.UnitNormConstraint;
import org.deeplearning4j.nn.conf.distribution.ConstantDistribution;
import org.deeplearning4j.nn.conf.distribution.NormalDistribution;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.conf.preprocessor.CnnToFeedForwardPreProcessor;
import org.deeplearning4j.nn.conf.preprocessor.FeedForwardToRnnPreProcessor;
import org.deeplearning4j.nn.conf.preprocessor.RnnToCnnPreProcessor;
import org.deeplearning4j.nn.conf.serde.JsonMappers;
import org.deeplearning4j.nn.conf.weightnoise.DropConnect;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.nn.weights.WeightInitDistribution;
import org.deeplearning4j.nn.weights.WeightInitRelu;
import org.deeplearning4j.nn.weights.WeightInitXavier;
import org.junit.jupiter.api.Test;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.*;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.shade.jackson.core.JsonProcessingException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

@Slf4j
@DisplayName("Transfer Learning MLN Test")
class TransferLearningMLNTest extends BaseDL4JTest {

    @Test
    @DisplayName("Simple Fine Tune")
    void simpleFineTune() {
        long rng = 12345L;
        Nd4j.getRandom().setSeed(rng);
        DataSet randomData = new DataSet(Nd4j.rand(DataType.FLOAT, 10, 4), TestUtils.randomOneHot(DataType.FLOAT, 10, 3));
        // original conf
        NeuralNetConfiguration.Builder confToChange = new NeuralNetConfiguration.Builder().seed(rng).optimizationAlgo(OptimizationAlgorithm.LBFGS).updater(new Nesterovs(0.01, 0.99));
        MultiLayerNetwork modelToFineTune = new MultiLayerNetwork(confToChange.list().layer(0, new DenseLayer.Builder().nIn(4).nOut(3).build()).layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(3).nOut(3).build()).build());
        modelToFineTune.init();
        // model after applying changes with transfer learning
        MultiLayerNetwork modelNow = new TransferLearning.Builder(modelToFineTune).fineTuneConfiguration(new FineTuneConfiguration.Builder().seed(rng).optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).updater(// Intent: override both weight and bias LR, unless bias LR is manually set also
        new RmsProp(0.5)).l2(0.4).build()).build();
        for (org.deeplearning4j.nn.api.Layer l : modelNow.getLayers()) {
            BaseLayer bl = ((BaseLayer) l.conf().getLayer());
            assertEquals(new RmsProp(0.5), bl.getIUpdater());
        }
        NeuralNetConfiguration.Builder confSet = new NeuralNetConfiguration.Builder().seed(rng).optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).updater(new RmsProp(0.5)).l2(0.4);
        MultiLayerNetwork expectedModel = new MultiLayerNetwork(confSet.list().layer(0, new DenseLayer.Builder().nIn(4).nOut(3).build()).layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(3).nOut(3).build()).build());
        expectedModel.init();
        expectedModel.setParams(modelToFineTune.params().dup());
        assertEquals(expectedModel.params(), modelNow.params());
        // Check json
        MultiLayerConfiguration expectedConf = expectedModel.getLayerWiseConfigurations();
        assertEquals(expectedConf.toJson(), modelNow.getLayerWiseConfigurations().toJson());
        // Check params after fit
        modelNow.fit(randomData);
        expectedModel.fit(randomData);
        assertEquals(modelNow.score(), expectedModel.score(), 1e-6);
        INDArray pExp = expectedModel.params();
        INDArray pNow = modelNow.params();
        assertEquals(pExp, pNow);
    }

    @Test
    @DisplayName("Test Nout Changes")
    void testNoutChanges() {
        Nd4j.getRandom().setSeed(12345);
        DataSet randomData = new DataSet(Nd4j.rand(DataType.FLOAT, 10, 4), TestUtils.randomOneHot(DataType.FLOAT, 10, 2));
        NeuralNetConfiguration.Builder equivalentConf = new NeuralNetConfiguration.Builder().updater(new Sgd(0.1));
        FineTuneConfiguration overallConf = new FineTuneConfiguration.Builder().updater(new Sgd(0.1)).build();
        MultiLayerNetwork modelToFineTune = new MultiLayerNetwork(equivalentConf.list().layer(0, new DenseLayer.Builder().nIn(4).nOut(5).build()).layer(1, new DenseLayer.Builder().nIn(3).nOut(2).build()).layer(2, new DenseLayer.Builder().nIn(2).nOut(3).build()).layer(3, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(3).nOut(3).build()).build());
        modelToFineTune.init();
        MultiLayerNetwork modelNow = new TransferLearning.Builder(modelToFineTune).fineTuneConfiguration(overallConf).nOutReplace(3, 2, WeightInit.XAVIER, WeightInit.XAVIER).nOutReplace(0, 3, WeightInit.XAVIER, new NormalDistribution(1, 1e-1)).build();
        MultiLayerNetwork modelExpectedArch = new MultiLayerNetwork(equivalentConf.list().layer(0, new DenseLayer.Builder().nIn(4).nOut(3).build()).layer(1, new DenseLayer.Builder().nIn(3).nOut(2).build()).layer(2, new DenseLayer.Builder().nIn(2).nOut(3).build()).layer(3, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(3).nOut(2).build()).build());
        modelExpectedArch.init();
        // Will fail - expected because of dist and weight init changes
        // assertEquals(modelExpectedArch.getLayerWiseConfigurations().toJson(), modelNow.getLayerWiseConfigurations().toJson());
        BaseLayer bl0 = ((BaseLayer) modelNow.getLayerWiseConfigurations().getConf(0).getLayer());
        BaseLayer bl1 = ((BaseLayer) modelNow.getLayerWiseConfigurations().getConf(1).getLayer());
        BaseLayer bl3 = ((BaseLayer) modelNow.getLayerWiseConfigurations().getConf(3).getLayer());
        assertEquals(bl0.getWeightInitFn().getClass(), WeightInitXavier.class);
        try {
            assertEquals(JsonMappers.getMapper().writeValueAsString(bl1.getWeightInitFn()), JsonMappers.getMapper().writeValueAsString(new WeightInitDistribution(new NormalDistribution(1, 1e-1))));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        assertEquals(bl3.getWeightInitFn(), new WeightInitXavier());
        // modelNow should have the same architecture as modelExpectedArch
        assertArrayEquals(modelExpectedArch.params().shape(), modelNow.params().shape());
        assertArrayEquals(modelExpectedArch.getLayer(0).params().shape(), modelNow.getLayer(0).params().shape());
        assertArrayEquals(modelExpectedArch.getLayer(1).params().shape(), modelNow.getLayer(1).params().shape());
        assertArrayEquals(modelExpectedArch.getLayer(2).params().shape(), modelNow.getLayer(2).params().shape());
        assertArrayEquals(modelExpectedArch.getLayer(3).params().shape(), modelNow.getLayer(3).params().shape());
        modelNow.setParams(modelExpectedArch.params());
        // fit should give the same results
        modelExpectedArch.fit(randomData);
        modelNow.fit(randomData);
        assertEquals(modelExpectedArch.score(), modelNow.score(), 0.000001);
        assertEquals(modelExpectedArch.params(), modelNow.params());
    }

    @Test
    @DisplayName("Test Remove And Add")
    void testRemoveAndAdd() {
        Nd4j.getRandom().setSeed(12345);
        DataSet randomData = new DataSet(Nd4j.rand(DataType.FLOAT, 10, 4), TestUtils.randomOneHot(DataType.FLOAT, 10, 3));
        NeuralNetConfiguration.Builder equivalentConf = new NeuralNetConfiguration.Builder().updater(new Sgd(0.1));
        FineTuneConfiguration overallConf = new FineTuneConfiguration.Builder().updater(new Sgd(0.1)).build();
        MultiLayerNetwork modelToFineTune = new // overallConf.list()
        MultiLayerNetwork(equivalentConf.list().layer(0, new DenseLayer.Builder().nIn(4).nOut(5).build()).layer(1, new DenseLayer.Builder().nIn(5).nOut(2).build()).layer(2, new DenseLayer.Builder().nIn(2).nOut(3).build()).layer(3, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(3).nOut(3).build()).build());
        modelToFineTune.init();
        MultiLayerNetwork modelNow = new TransferLearning.Builder(modelToFineTune).fineTuneConfiguration(overallConf).nOutReplace(0, 7, WeightInit.XAVIER, WeightInit.XAVIER).nOutReplace(2, 5, WeightInit.XAVIER).removeOutputLayer().addLayer(new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).nIn(5).nOut(3).updater(new Sgd(0.5)).activation(Activation.SOFTMAX).build()).build();
        MultiLayerNetwork modelExpectedArch = new MultiLayerNetwork(equivalentConf.list().layer(0, new DenseLayer.Builder().nIn(4).nOut(7).build()).layer(1, new DenseLayer.Builder().nIn(7).nOut(2).build()).layer(2, new DenseLayer.Builder().nIn(2).nOut(5).build()).layer(3, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).updater(new Sgd(0.5)).nIn(5).nOut(3).build()).build());
        modelExpectedArch.init();
        // modelNow should have the same architecture as modelExpectedArch
        assertArrayEquals(modelExpectedArch.params().shape(), modelNow.params().shape());
        assertArrayEquals(modelExpectedArch.getLayer(0).params().shape(), modelNow.getLayer(0).params().shape());
        assertArrayEquals(modelExpectedArch.getLayer(1).params().shape(), modelNow.getLayer(1).params().shape());
        assertArrayEquals(modelExpectedArch.getLayer(2).params().shape(), modelNow.getLayer(2).params().shape());
        assertArrayEquals(modelExpectedArch.getLayer(3).params().shape(), modelNow.getLayer(3).params().shape());
        modelNow.setParams(modelExpectedArch.params());
        // fit should give the same results
        modelExpectedArch.fit(randomData);
        modelNow.fit(randomData);
        double scoreExpected = modelExpectedArch.score();
        double scoreActual = modelNow.score();
        assertEquals(scoreExpected, scoreActual, 1e-4);
        assertEquals(modelExpectedArch.params(), modelNow.params());
    }

    @Test
    @DisplayName("Test Remove And Processing")
    void testRemoveAndProcessing() {
        int V_WIDTH = 130;
        int V_HEIGHT = 130;
        int V_NFRAMES = 150;
        MultiLayerConfiguration confForArchitecture = // l2 regularization on all layers
        new NeuralNetConfiguration.Builder().seed(12345).l2(0.001).optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).updater(new AdaGrad(0.4)).list().layer(0, // 3 channels: RGB
        new ConvolutionLayer.Builder(10, 10).nIn(3).nOut(30).stride(4, 4).activation(Activation.RELU).weightInit(WeightInit.RELU).build()).layer(1, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX).kernelSize(3, 3).stride(2, 2).build()).layer(2, new ConvolutionLayer.Builder(3, 3).nIn(30).nOut(10).stride(2, 2).activation(Activation.RELU).weightInit(WeightInit.RELU).build()).layer(3, new DenseLayer.Builder().activation(Activation.RELU).nIn(490).nOut(50).weightInit(WeightInit.RELU).updater(new AdaGrad(0.5)).gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue).gradientNormalizationThreshold(10).build()).layer(4, new GravesLSTM.Builder().activation(Activation.SOFTSIGN).nIn(50).nOut(50).weightInit(WeightInit.XAVIER).updater(new AdaGrad(0.6)).gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue).gradientNormalizationThreshold(10).build()).layer(5, new RnnOutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(50).nOut(// 4 possible shapes: circle, square, arc, line
        4).weightInit(WeightInit.XAVIER).gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue).gradientNormalizationThreshold(10).build()).inputPreProcessor(0, new RnnToCnnPreProcessor(V_HEIGHT, V_WIDTH, 3)).inputPreProcessor(3, new CnnToFeedForwardPreProcessor(7, 7, 10)).inputPreProcessor(4, new FeedForwardToRnnPreProcessor()).backpropType(BackpropType.TruncatedBPTT).tBPTTForwardLength(V_NFRAMES / 5).tBPTTBackwardLength(V_NFRAMES / 5).build();
        MultiLayerNetwork modelExpectedArch = new MultiLayerNetwork(confForArchitecture);
        modelExpectedArch.init();
        MultiLayerNetwork modelToTweak = new MultiLayerNetwork(new NeuralNetConfiguration.Builder().seed(12345).updater(new RmsProp(0.1)).list().layer(0, // Only keep the first layer the same
        new ConvolutionLayer.Builder(10, 10).nIn(// 3 channels: RGB
        3).nOut(30).stride(4, 4).activation(Activation.RELU).weightInit(WeightInit.RELU).updater(new AdaGrad(0.1)).build()).layer(1, new SubsamplingLayer.Builder(// change kernel size
        SubsamplingLayer.PoolingType.MAX).kernelSize(5, 5).stride(2, 2).build()).layer(2, // change here
        new ConvolutionLayer.Builder(6, 6).nIn(30).nOut(10).stride(2, 2).activation(Activation.RELU).weightInit(WeightInit.RELU).build()).layer(3, // change here
        new DenseLayer.Builder().activation(Activation.RELU).nIn(250).nOut(50).weightInit(WeightInit.RELU).gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue).gradientNormalizationThreshold(10).updater(new RmsProp(0.01)).build()).layer(4, // change here
        new GravesLSTM.Builder().activation(Activation.SOFTSIGN).nIn(50).nOut(25).weightInit(WeightInit.XAVIER).build()).layer(5, new RnnOutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(25).nOut(4).weightInit(WeightInit.XAVIER).gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue).gradientNormalizationThreshold(10).build()).inputPreProcessor(0, new RnnToCnnPreProcessor(V_HEIGHT, V_WIDTH, 3)).inputPreProcessor(3, new CnnToFeedForwardPreProcessor(5, 5, 10)).inputPreProcessor(4, new FeedForwardToRnnPreProcessor()).backpropType(BackpropType.TruncatedBPTT).tBPTTForwardLength(V_NFRAMES / 5).tBPTTBackwardLength(V_NFRAMES / 5).build());
        modelToTweak.init();
        MultiLayerNetwork modelNow = new TransferLearning.Builder(modelToTweak).fineTuneConfiguration(// l2 regularization on all layers
        new FineTuneConfiguration.Builder().seed(12345).l2(0.001).updater(new AdaGrad(0.4)).weightInit(WeightInit.RELU).build()).removeLayersFromOutput(5).addLayer(new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX).kernelSize(3, 3).stride(2, 2).build()).addLayer(new ConvolutionLayer.Builder(3, 3).nIn(30).nOut(10).stride(2, 2).activation(Activation.RELU).weightInit(WeightInit.RELU).build()).addLayer(new DenseLayer.Builder().activation(Activation.RELU).nIn(490).nOut(50).weightInit(WeightInit.RELU).updater(new AdaGrad(0.5)).gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue).gradientNormalizationThreshold(10).build()).addLayer(new GravesLSTM.Builder().activation(Activation.SOFTSIGN).nIn(50).nOut(50).weightInit(WeightInit.XAVIER).updater(new AdaGrad(0.6)).gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue).gradientNormalizationThreshold(10).build()).addLayer(new RnnOutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(50).nOut(// 4 possible shapes: circle, square, arc, line
        4).weightInit(WeightInit.XAVIER).gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue).gradientNormalizationThreshold(10).build()).setInputPreProcessor(3, new CnnToFeedForwardPreProcessor(7, 7, 10)).setInputPreProcessor(4, new FeedForwardToRnnPreProcessor()).build();
        // modelNow should have the same architecture as modelExpectedArch
        assertEquals(modelExpectedArch.getLayerWiseConfigurations().getConf(0).toJson(), modelNow.getLayerWiseConfigurations().getConf(0).toJson());
        // some learning related info the subsampling layer will not be overwritten
        // assertTrue(modelExpectedArch.getLayerWiseConfigurations().getConf(1).toJson().equals(modelNow.getLayerWiseConfigurations().getConf(1).toJson()));
        assertEquals(modelExpectedArch.getLayerWiseConfigurations().getConf(2).toJson(), modelNow.getLayerWiseConfigurations().getConf(2).toJson());
        assertEquals(modelExpectedArch.getLayerWiseConfigurations().getConf(3).toJson(), modelNow.getLayerWiseConfigurations().getConf(3).toJson());
        assertEquals(modelExpectedArch.getLayerWiseConfigurations().getConf(4).toJson(), modelNow.getLayerWiseConfigurations().getConf(4).toJson());
        assertEquals(modelExpectedArch.getLayerWiseConfigurations().getConf(5).toJson(), modelNow.getLayerWiseConfigurations().getConf(5).toJson());
        assertArrayEquals(modelExpectedArch.params().shape(), modelNow.params().shape());
        assertArrayEquals(modelExpectedArch.getLayer(0).params().shape(), modelNow.getLayer(0).params().shape());
        // subsampling has no params
        // assertArrayEquals(modelExpectedArch.getLayer(1).params().shape(), modelNow.getLayer(1).params().shape());
        assertArrayEquals(modelExpectedArch.getLayer(2).params().shape(), modelNow.getLayer(2).params().shape());
        assertArrayEquals(modelExpectedArch.getLayer(3).params().shape(), modelNow.getLayer(3).params().shape());
        assertArrayEquals(modelExpectedArch.getLayer(4).params().shape(), modelNow.getLayer(4).params().shape());
        assertArrayEquals(modelExpectedArch.getLayer(5).params().shape(), modelNow.getLayer(5).params().shape());
    }

    @Test
    @DisplayName("Test All With CNN")
    void testAllWithCNN() {
        Nd4j.getRandom().setSeed(12345);
        DataSet randomData = new DataSet(Nd4j.rand(DataType.FLOAT, 10, 28 * 28 * 3).reshape(10, 3, 28, 28), TestUtils.randomOneHot(DataType.FLOAT, 10, 10));
        MultiLayerNetwork modelToFineTune = new MultiLayerNetwork(new NeuralNetConfiguration.Builder().seed(123).weightInit(WeightInit.XAVIER).updater(new Nesterovs(0.01, 0.9)).list().layer(0, new ConvolutionLayer.Builder(5, 5).nIn(3).stride(1, 1).nOut(20).activation(Activation.IDENTITY).build()).layer(1, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX).kernelSize(2, 2).stride(2, 2).build()).layer(2, new ConvolutionLayer.Builder(5, 5).stride(1, 1).nOut(50).activation(Activation.IDENTITY).build()).layer(3, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX).kernelSize(2, 2).stride(2, 2).build()).layer(4, new DenseLayer.Builder().activation(Activation.RELU).nOut(500).build()).layer(5, new DenseLayer.Builder().activation(Activation.RELU).nOut(250).build()).layer(6, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).nOut(100).activation(Activation.SOFTMAX).build()).setInputType(InputType.convolutionalFlat(28, 28, 3)).build());
        modelToFineTune.init();
        // 10x20x12x12
        INDArray asFrozenFeatures = modelToFineTune.feedForwardToLayer(2, randomData.getFeatures(), false).get(2);
        NeuralNetConfiguration.Builder equivalentConf = new NeuralNetConfiguration.Builder().updater(new Sgd(0.2)).optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT);
        FineTuneConfiguration overallConf = new FineTuneConfiguration.Builder().updater(new Sgd(0.2)).build();
        MultiLayerNetwork modelNow = new TransferLearning.Builder(modelToFineTune).fineTuneConfiguration(overallConf).setFeatureExtractor(1).nOutReplace(4, 600, WeightInit.XAVIER).removeLayersFromOutput(2).addLayer(new DenseLayer.Builder().activation(Activation.RELU).nIn(600).nOut(300).build()).addLayer(new DenseLayer.Builder().activation(Activation.RELU).nIn(300).nOut(150).build()).addLayer(new DenseLayer.Builder().activation(Activation.RELU).nIn(150).nOut(50).build()).addLayer(new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).activation(Activation.SOFTMAX).nIn(50).nOut(10).build()).build();
        MultiLayerNetwork notFrozen = new MultiLayerNetwork(equivalentConf.list().layer(0, new ConvolutionLayer.Builder(5, 5).stride(1, 1).nOut(50).activation(Activation.IDENTITY).build()).layer(1, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX).kernelSize(2, 2).stride(2, 2).build()).layer(2, new DenseLayer.Builder().activation(Activation.RELU).nOut(600).build()).layer(3, new DenseLayer.Builder().activation(Activation.RELU).nOut(300).build()).layer(4, new DenseLayer.Builder().activation(Activation.RELU).nOut(150).build()).layer(5, new DenseLayer.Builder().activation(Activation.RELU).nOut(50).build()).layer(6, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).nOut(10).activation(Activation.SOFTMAX).build()).setInputType(InputType.convolutionalFlat(12, 12, 20)).build());
        notFrozen.init();
        assertArrayEquals(modelToFineTune.getLayer(0).params().shape(), modelNow.getLayer(0).params().shape());
        // subsampling has no params
        // assertArrayEquals(modelExpectedArch.getLayer(1).params().shape(), modelNow.getLayer(1).params().shape());
        assertArrayEquals(notFrozen.getLayer(0).params().shape(), modelNow.getLayer(2).params().shape());
        modelNow.getLayer(2).setParams(notFrozen.getLayer(0).params());
        // subsampling has no params
        // assertArrayEquals(notFrozen.getLayer(1).params().shape(), modelNow.getLayer(3).params().shape());
        assertArrayEquals(notFrozen.getLayer(2).params().shape(), modelNow.getLayer(4).params().shape());
        modelNow.getLayer(4).setParams(notFrozen.getLayer(2).params());
        assertArrayEquals(notFrozen.getLayer(3).params().shape(), modelNow.getLayer(5).params().shape());
        modelNow.getLayer(5).setParams(notFrozen.getLayer(3).params());
        assertArrayEquals(notFrozen.getLayer(4).params().shape(), modelNow.getLayer(6).params().shape());
        modelNow.getLayer(6).setParams(notFrozen.getLayer(4).params());
        assertArrayEquals(notFrozen.getLayer(5).params().shape(), modelNow.getLayer(7).params().shape());
        modelNow.getLayer(7).setParams(notFrozen.getLayer(5).params());
        assertArrayEquals(notFrozen.getLayer(6).params().shape(), modelNow.getLayer(8).params().shape());
        modelNow.getLayer(8).setParams(notFrozen.getLayer(6).params());
        int i = 0;
        while (i < 3) {
            notFrozen.fit(new DataSet(asFrozenFeatures, randomData.getLabels()));
            modelNow.fit(randomData);
            i++;
        }
        INDArray expectedParams = Nd4j.hstack(modelToFineTune.getLayer(0).params(), notFrozen.params());
        assertEquals(expectedParams, modelNow.params());
    }

    @Test
    @DisplayName("Test Fine Tune Override")
    void testFineTuneOverride() {
        // Check that fine-tune overrides are selective - i.e., if I only specify a new LR, only the LR should be modified
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().updater(new Adam(1e-4)).activation(Activation.TANH).weightInit(WeightInit.RELU).l1(0.1).l2(0.2).list().layer(0, new DenseLayer.Builder().nIn(10).nOut(5).build()).layer(1, new OutputLayer.Builder().nIn(5).nOut(4).activation(Activation.HARDSIGMOID).build()).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        MultiLayerNetwork net2 = new TransferLearning.Builder(net).fineTuneConfiguration(new FineTuneConfiguration.Builder().updater(new Adam(2e-2)).backpropType(// Should be set on MLC
        BackpropType.TruncatedBPTT).build()).build();
        // Check original net isn't modified:
        BaseLayer l0 = (BaseLayer) net.getLayer(0).conf().getLayer();
        assertEquals(new Adam(1e-4), l0.getIUpdater());
        assertEquals(Activation.TANH.getActivationFunction(), l0.getActivationFn());
        assertEquals(new WeightInitRelu(), l0.getWeightInitFn());
        assertEquals(0.1, TestUtils.getL1(l0), 1e-6);
        BaseLayer l1 = (BaseLayer) net.getLayer(1).conf().getLayer();
        assertEquals(new Adam(1e-4), l1.getIUpdater());
        assertEquals(Activation.HARDSIGMOID.getActivationFunction(), l1.getActivationFn());
        assertEquals(new WeightInitRelu(), l1.getWeightInitFn());
        assertEquals(0.2, TestUtils.getL2(l1), 1e-6);
        assertEquals(BackpropType.Standard, conf.getBackpropType());
        // Check new net has only the appropriate things modified (i.e., LR)
        l0 = (BaseLayer) net2.getLayer(0).conf().getLayer();
        assertEquals(new Adam(2e-2), l0.getIUpdater());
        assertEquals(Activation.TANH.getActivationFunction(), l0.getActivationFn());
        assertEquals(new WeightInitRelu(), l0.getWeightInitFn());
        assertEquals(0.1, TestUtils.getL1(l0), 1e-6);
        l1 = (BaseLayer) net2.getLayer(1).conf().getLayer();
        assertEquals(new Adam(2e-2), l1.getIUpdater());
        assertEquals(Activation.HARDSIGMOID.getActivationFunction(), l1.getActivationFn());
        assertEquals(new WeightInitRelu(), l1.getWeightInitFn());
        assertEquals(0.2, TestUtils.getL2(l1), 1e-6);
        assertEquals(BackpropType.TruncatedBPTT, net2.getLayerWiseConfigurations().getBackpropType());
    }

    @Test
    @DisplayName("Test All With CNN New")
    void testAllWithCNNNew() {
        Nd4j.getRandom().setSeed(12345);
        DataSet randomData = new DataSet(Nd4j.rand(DataType.FLOAT, 10, 28 * 28 * 3).reshape(10, 3, 28, 28), TestUtils.randomOneHot(10, 10));
        MultiLayerNetwork modelToFineTune = new MultiLayerNetwork(new NeuralNetConfiguration.Builder().seed(123).weightInit(WeightInit.XAVIER).updater(new Nesterovs(0.01, 0.9)).list().layer(0, new ConvolutionLayer.Builder(5, 5).nIn(3).stride(1, 1).nOut(20).activation(Activation.IDENTITY).build()).layer(1, new SubsamplingLayer.Builder(PoolingType.MAX).kernelSize(2, 2).stride(2, 2).build()).layer(2, new ConvolutionLayer.Builder(5, 5).stride(1, 1).nOut(50).activation(Activation.IDENTITY).build()).layer(3, new SubsamplingLayer.Builder(PoolingType.MAX).kernelSize(2, 2).stride(2, 2).build()).layer(4, new DenseLayer.Builder().activation(Activation.RELU).nOut(500).build()).layer(5, new DenseLayer.Builder().activation(Activation.RELU).nOut(250).build()).layer(6, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).nOut(100).activation(Activation.SOFTMAX).build()).setInputType(// See note below
        InputType.convolutionalFlat(28, 28, 3)).build());
        modelToFineTune.init();
        // 10x20x12x12
        INDArray asFrozenFeatures = modelToFineTune.feedForwardToLayer(2, randomData.getFeatures(), false).get(2);
        NeuralNetConfiguration.Builder equivalentConf = new NeuralNetConfiguration.Builder().updater(new Sgd(0.2));
        FineTuneConfiguration overallConf = new FineTuneConfiguration.Builder().updater(new Sgd(0.2)).build();
        MultiLayerNetwork modelNow = new TransferLearning.Builder(modelToFineTune).fineTuneConfiguration(overallConf).setFeatureExtractor(1).removeLayersFromOutput(5).addLayer(new DenseLayer.Builder().activation(Activation.RELU).nIn(12 * 12 * 20).nOut(300).build()).addLayer(new DenseLayer.Builder().activation(Activation.RELU).nIn(300).nOut(150).build()).addLayer(new DenseLayer.Builder().activation(Activation.RELU).nIn(150).nOut(50).build()).addLayer(new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).activation(Activation.SOFTMAX).nIn(50).nOut(10).build()).setInputPreProcessor(2, new CnnToFeedForwardPreProcessor(12, 12, 20)).build();
        MultiLayerNetwork notFrozen = new MultiLayerNetwork(equivalentConf.list().layer(0, new DenseLayer.Builder().activation(Activation.RELU).nIn(12 * 12 * 20).nOut(300).build()).layer(1, new DenseLayer.Builder().activation(Activation.RELU).nIn(300).nOut(150).build()).layer(2, new DenseLayer.Builder().activation(Activation.RELU).nIn(150).nOut(50).build()).layer(3, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).nIn(50).nOut(10).activation(Activation.SOFTMAX).build()).inputPreProcessor(0, new CnnToFeedForwardPreProcessor(12, 12, 20)).build());
        notFrozen.init();
        assertArrayEquals(modelToFineTune.getLayer(0).params().shape(), modelNow.getLayer(0).params().shape());
        // subsampling has no params
        // assertArrayEquals(modelExpectedArch.getLayer(1).params().shape(), modelNow.getLayer(1).params().shape());
        assertArrayEquals(notFrozen.getLayer(0).params().shape(), modelNow.getLayer(2).params().shape());
        modelNow.getLayer(2).setParams(notFrozen.getLayer(0).params());
        assertArrayEquals(notFrozen.getLayer(1).params().shape(), modelNow.getLayer(3).params().shape());
        modelNow.getLayer(3).setParams(notFrozen.getLayer(1).params());
        assertArrayEquals(notFrozen.getLayer(2).params().shape(), modelNow.getLayer(4).params().shape());
        modelNow.getLayer(4).setParams(notFrozen.getLayer(2).params());
        assertArrayEquals(notFrozen.getLayer(3).params().shape(), modelNow.getLayer(5).params().shape());
        modelNow.getLayer(5).setParams(notFrozen.getLayer(3).params());
        int i = 0;
        while (i < 3) {
            notFrozen.fit(new DataSet(asFrozenFeatures, randomData.getLabels()));
            modelNow.fit(randomData);
            i++;
        }
        INDArray expectedParams = Nd4j.hstack(modelToFineTune.getLayer(0).params(), notFrozen.params());
        assertEquals(expectedParams, modelNow.params());
    }

    @Test
    @DisplayName("Test Object Overrides")
    void testObjectOverrides() {
        // https://github.com/eclipse/deeplearning4j/issues/4368
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().dropOut(0.5).weightNoise(new DropConnect(0.5)).l2(0.5).constrainWeights(new UnitNormConstraint()).list().layer(new DenseLayer.Builder().nIn(10).nOut(10).build()).build();
        MultiLayerNetwork orig = new MultiLayerNetwork(conf);
        orig.init();
        FineTuneConfiguration ftc = new FineTuneConfiguration.Builder().dropOut(0).weightNoise(null).constraints(null).l2(0.0).build();
        MultiLayerNetwork transfer = new TransferLearning.Builder(orig).fineTuneConfiguration(ftc).build();
        DenseLayer l = (DenseLayer) transfer.getLayer(0).conf().getLayer();
        assertNull(l.getIDropout());
        assertNull(l.getWeightNoise());
        assertNull(l.getConstraints());
        assertNull(TestUtils.getL2Reg(l));
    }

    @Test
    @DisplayName("Test Transfer Learning Subsequent")
    void testTransferLearningSubsequent() {
        final INDArray input = Nd4j.create(6, 6, 6, 6);
        final MultiLayerNetwork net = new MultiLayerNetwork(new NeuralNetConfiguration.Builder().weightInit(new ConstantDistribution(666)).list().setInputType(InputType.inferInputTypes(input)[0]).layer(new Convolution2D.Builder(3, 3).nOut(10).build()).layer(new Convolution2D.Builder(1, 1).nOut(3).build()).layer(new OutputLayer.Builder().nOut(2).lossFunction(LossFunctions.LossFunction.MSE).build()).build());
        net.init();
        MultiLayerNetwork newGraph = new TransferLearning.Builder(net).fineTuneConfiguration(new FineTuneConfiguration.Builder().build()).nOutReplace(0, 7, new ConstantDistribution(333)).nOutReplace(1, 3, new ConstantDistribution(111)).removeLayersFromOutput(1).addLayer(new OutputLayer.Builder().nIn(48).nOut(2).lossFunction(LossFunctions.LossFunction.MSE).build()).setInputPreProcessor(2, new CnnToFeedForwardPreProcessor(4, 4, 3)).build();
        newGraph.init();
        assertEquals(7, newGraph.layerInputSize(1), "Incorrect # inputs");
        newGraph.output(input);
    }

    @Test
    @DisplayName("Test Change N Out N In")
    void testChangeNOutNIn() {
        INDArray input = Nd4j.create(new long[] { 1, 2, 4, 4 });
        MultiLayerNetwork net = new MultiLayerNetwork(new NeuralNetConfiguration.Builder().list().setInputType(InputType.inferInputTypes(input)[0]).layer(new Convolution2D.Builder(1, 1).nOut(10).build()).layer(new SubsamplingLayer.Builder(1, 1).build()).layer(new Convolution2D.Builder(1, 1).nOut(7).build()).layer(new OutputLayer.Builder().activation(Activation.SOFTMAX).nOut(2).build()).build());
        net.init();
        final MultiLayerNetwork newNet = new TransferLearning.Builder(net).nOutReplace(0, 5, WeightInit.XAVIER).nInReplace(2, 5, WeightInit.XAVIER).build();
        newNet.init();
        assertEquals(5, newNet.layerSize(0), "Incorrect number of outputs!");
        assertEquals(5, newNet.layerInputSize(2), "Incorrect number of inputs!");
        newNet.output(input);
    }

    @Test
    @DisplayName("Test Transfer Learning Same Diff Layers")
    void testTransferLearningSameDiffLayers() {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().dataType(DataType.DOUBLE).activation(Activation.TANH).updater(new Adam(0.01)).weightInit(WeightInit.XAVIER).list().layer(new LSTM.Builder().nOut(8).build()).layer(new SelfAttentionLayer.Builder().nOut(4).nHeads(2).projectInput(true).build()).layer(new GlobalPoolingLayer.Builder().poolingType(PoolingType.MAX).build()).layer(new OutputLayer.Builder().nOut(2).activation(Activation.SOFTMAX).lossFunction(LossFunctions.LossFunction.MCXENT).build()).setInputType(InputType.recurrent(4)).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        INDArray in = Nd4j.rand(DataType.FLOAT, 3, 4, 5);
        INDArray out = net.output(in);
        MultiLayerNetwork net2 = new TransferLearning.Builder(net).fineTuneConfiguration(FineTuneConfiguration.builder().updater(new Adam(0.01)).build()).removeLayersFromOutput(1).addLayer(new OutputLayer.Builder().nIn(4).nOut(2).activation(Activation.SOFTMAX).lossFunction(LossFunctions.LossFunction.MCXENT).build()).build();
        net2.setParam("3_W", net.getParam("3_W"));
        net2.setParam("3_b", net.getParam("3_b"));
        Map<String, INDArray> p1 = net.paramTable();
        Map<String, INDArray> p2 = net2.paramTable();
        for (String s : p1.keySet()) {
            INDArray i1 = p1.get(s);
            INDArray i2 = p2.get(s);
            assertEquals(i1, i2,s);
        }
        INDArray out2 = net2.output(in);
        assertEquals(out, out2);
    }
}
