/*
 *  ******************************************************************************
 *  *
 *  *
 *  * This program and the accompanying materials are made available under the
 *  * terms of the Apache License, Version 2.0 which is available at
 *  * https://www.apache.org/licenses/LICENSE-2.0.
 *  *
 *  * See the NOTICE file distributed with this work for additional
 *  * information regarding copyright ownership.
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations
 *  * under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *  *****************************************************************************
 */
package org.deeplearning4j.cuda.gradientcheck;

import lombok.val;
import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.cuda.TestUtils;
import org.deeplearning4j.datasets.iterator.impl.IrisDataSetIterator;
import org.deeplearning4j.gradientcheck.GradientCheckUtil;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.ConvolutionMode;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.distribution.NormalDistribution;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.conf.layers.convolutional.Cropping2D;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.NoOp;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import java.util.Arrays;
import static org.deeplearning4j.nn.conf.ConvolutionMode.Same;
import static org.deeplearning4j.nn.conf.ConvolutionMode.Truncate;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Created by nyghtowl on 9/1/15.
 */
@NativeTag
@DisplayName("Cnn Gradient Check Test")
@Tag(TagNames.LARGE_RESOURCES)
@Tag(TagNames.LONG_TEST)
class CNNGradientCheckTest extends BaseDL4JTest {

    private static final boolean PRINT_RESULTS = true;

    private static final boolean RETURN_ON_FIRST_FAILURE = false;

    private static final double DEFAULT_EPS = 1e-6;

    private static final double DEFAULT_MAX_REL_ERROR = 1e-3;

    private static final double DEFAULT_MIN_ABS_ERROR = 1e-8;

    static {
        Nd4j.setDataType(DataType.DOUBLE);
    }

    @Override
    public long getTimeoutMilliseconds() {
        return 180000L;
    }

    @Test
    @DisplayName("Test Gradient CNNMLN")

    void testGradientCNNMLN() {
        // Parameterized test, testing combinations of:
        // (a) activation function
        // (b) Whether to test at random initialization, or after some learning (i.e., 'characteristic mode of operation')
        // (c) Loss function (with specified output activations)
        Activation[] activFns = { Activation.SIGMOID, Activation.TANH };
        // If true: run some backprop steps first
        boolean[] characteristic = { false, true };
        LossFunctions.LossFunction[] lossFunctions = { LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD, LossFunctions.LossFunction.MSE };
        // i.e., lossFunctions[i] used with outputActivations[i] here
        Activation[] outputActivations = { Activation.SOFTMAX, Activation.TANH };
        DataSet ds = new IrisDataSetIterator(150, 150).next();
        ds.normalizeZeroMeanZeroUnitVariance();
        INDArray input = ds.getFeatures();
        INDArray labels = ds.getLabels();
        for (Activation afn : activFns) {
            for (boolean doLearningFirst : characteristic) {
                for (int i = 0; i < lossFunctions.length; i++) {
                    LossFunctions.LossFunction lf = lossFunctions[i];
                    Activation outputActivation = outputActivations[i];
                    MultiLayerConfiguration.Builder builder = new NeuralNetConfiguration.Builder().dataType(DataType.DOUBLE).optimizationAlgo(OptimizationAlgorithm.CONJUGATE_GRADIENT).updater(new NoOp()).weightInit(WeightInit.XAVIER).seed(12345L).list().layer(0, new ConvolutionLayer.Builder(1, 1).nOut(6).activation(afn).cudnnAllowFallback(false).build()).layer(1, new OutputLayer.Builder(lf).activation(outputActivation).nOut(3).build()).setInputType(InputType.convolutionalFlat(1, 4, 1));
                    MultiLayerConfiguration conf = builder.build();
                    MultiLayerNetwork mln = new MultiLayerNetwork(conf);
                    mln.init();
                    String name = new Object() {
                    }.getClass().getEnclosingMethod().getName();
                    if (doLearningFirst) {
                        // Run a number of iterations of learning
                        mln.setInput(ds.getFeatures());
                        mln.setLabels(ds.getLabels());
                        mln.computeGradientAndScore();
                        double scoreBefore = mln.score();
                        for (int j = 0; j < 10; j++) mln.fit(ds);
                        mln.computeGradientAndScore();
                        double scoreAfter = mln.score();
                        // Can't test in 'characteristic mode of operation' if not learning
                        String msg = name + " - score did not (sufficiently) decrease during learning - activationFn=" + afn + ", lossFn=" + lf + ", outputActivation=" + outputActivation + ", doLearningFirst= " + doLearningFirst + " (before=" + scoreBefore + ", scoreAfter=" + scoreAfter + ")";
                        assertTrue(scoreAfter < 0.8 * scoreBefore, msg);
                    }
                    if (PRINT_RESULTS) {
                        System.out.println(name + " - activationFn=" + afn + ", lossFn=" + lf + ", outputActivation=" + outputActivation + ", doLearningFirst=" + doLearningFirst);
                    }
                    boolean gradOK = GradientCheckUtil.checkGradients(mln, DEFAULT_EPS, DEFAULT_MAX_REL_ERROR, DEFAULT_MIN_ABS_ERROR, PRINT_RESULTS, RETURN_ON_FIRST_FAILURE, input, labels);
                    assertTrue(gradOK);
                    TestUtils.testModelSerialization(mln);
                }
            }
        }
    }

    @Test
    @DisplayName("Test Gradient CNNL 1 L 2 MLN")
    void testGradientCNNL1L2MLN() {
        // Parameterized test, testing combinations of:
        // (a) activation function
        // (b) Whether to test at random initialization, or after some learning (i.e., 'characteristic mode of operation')
        // (c) Loss function (with specified output activations)
        DataSet ds = new IrisDataSetIterator(150, 150).next();
        ds.normalizeZeroMeanZeroUnitVariance();
        INDArray input = ds.getFeatures();
        INDArray labels = ds.getLabels();
        // use l2vals[i] with l1vals[i]
        double[] l2vals = { 0.4, 0.0, 0.4, 0.4 };
        double[] l1vals = { 0.0, 0.0, 0.5, 0.0 };
        double[] biasL2 = { 0.0, 0.0, 0.0, 0.2 };
        double[] biasL1 = { 0.0, 0.0, 0.6, 0.0 };
        Activation[] activFns = { Activation.SIGMOID, Activation.TANH, Activation.ELU, Activation.SOFTPLUS };
        // If true: run some backprop steps first
        boolean[] characteristic = { false, true, false, true };
        LossFunctions.LossFunction[] lossFunctions = { LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD, LossFunctions.LossFunction.MSE, LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD, LossFunctions.LossFunction.MSE };
        // i.e., lossFunctions[i] used with outputActivations[i] here
        Activation[] outputActivations = { Activation.SOFTMAX, Activation.TANH, Activation.SOFTMAX, Activation.IDENTITY };
        for (int i = 0; i < l2vals.length; i++) {
            Activation afn = activFns[i];
            boolean doLearningFirst = characteristic[i];
            LossFunctions.LossFunction lf = lossFunctions[i];
            Activation outputActivation = outputActivations[i];
            double l2 = l2vals[i];
            double l1 = l1vals[i];
            MultiLayerConfiguration.Builder builder = new NeuralNetConfiguration.Builder().dataType(DataType.DOUBLE).l2(l2).l1(l1).l2Bias(biasL2[i]).l1Bias(biasL1[i]).optimizationAlgo(OptimizationAlgorithm.CONJUGATE_GRADIENT).seed(12345L).list().layer(0, new ConvolutionLayer.Builder(new int[] { 1, 1 }).nIn(1).nOut(6).weightInit(WeightInit.XAVIER).activation(afn).updater(new NoOp()).build()).layer(1, new OutputLayer.Builder(lf).activation(outputActivation).nOut(3).weightInit(WeightInit.XAVIER).updater(new NoOp()).build()).setInputType(InputType.convolutionalFlat(1, 4, 1));
            MultiLayerConfiguration conf = builder.build();
            MultiLayerNetwork mln = new MultiLayerNetwork(conf);
            mln.init();
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            if (doLearningFirst) {
                // Run a number of iterations of learning
                mln.setInput(ds.getFeatures());
                mln.setLabels(ds.getLabels());
                mln.computeGradientAndScore();
                double scoreBefore = mln.score();
                for (int j = 0; j < 10; j++) mln.fit(ds);
                mln.computeGradientAndScore();
                double scoreAfter = mln.score();
                // Can't test in 'characteristic mode of operation' if not learning
                String msg = testName + "- score did not (sufficiently) decrease during learning - activationFn=" + afn + ", lossFn=" + lf + ", outputActivation=" + outputActivation + ", doLearningFirst=" + doLearningFirst + " (before=" + scoreBefore + ", scoreAfter=" + scoreAfter + ")";
                assertTrue(scoreAfter < 0.8 * scoreBefore, msg);
            }
            if (PRINT_RESULTS) {
                System.out.println(testName + "- activationFn=" + afn + ", lossFn=" + lf + ", outputActivation=" + outputActivation + ", doLearningFirst=" + doLearningFirst);
                // for (int j = 0; j < mln.getnLayers(); j++)
                // System.out.println("Layer " + j + " # params: " + mln.getLayer(j).numParams());
            }
            boolean gradOK = GradientCheckUtil.checkGradients(mln, DEFAULT_EPS, DEFAULT_MAX_REL_ERROR, DEFAULT_MIN_ABS_ERROR, PRINT_RESULTS, RETURN_ON_FIRST_FAILURE, input, labels);
            assertTrue(gradOK);
            TestUtils.testModelSerialization(mln);
        }
    }

    @Test
    @DisplayName("Test Cnn With Space To Depth")
    void testCnnWithSpaceToDepth() {
        Nd4j.getRandom().setSeed(12345);
        int nOut = 4;
        int minibatchSize = 2;
        int width = 5;
        int height = 5;
        int inputDepth = 1;
        int[] kernel = { 2, 2 };
        int blocks = 2;
        String[] activations = { "sigmoid" };
        SubsamplingLayer.PoolingType[] poolingTypes = new SubsamplingLayer.PoolingType[] { SubsamplingLayer.PoolingType.MAX, SubsamplingLayer.PoolingType.AVG, SubsamplingLayer.PoolingType.PNORM };
        for (String afn : activations) {
            for (SubsamplingLayer.PoolingType poolingType : poolingTypes) {
                INDArray input = Nd4j.rand(minibatchSize, width * height * inputDepth);
                INDArray labels = Nd4j.zeros(minibatchSize, nOut);
                for (int i = 0; i < minibatchSize; i++) {
                    labels.putScalar(new int[] { i, i % nOut }, 1.0);
                }
                MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().dataType(DataType.DOUBLE).updater(new NoOp()).dist(new NormalDistribution(0, 1)).list().layer(new ConvolutionLayer.Builder(kernel).nIn(inputDepth).hasBias(false).cudnnAllowFallback(false).nOut(1).build()).layer(new SpaceToDepthLayer.Builder(blocks, SpaceToDepthLayer.DataFormat.NCHW).build()).layer(new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(2 * 2 * 4).nOut(nOut).build()).setInputType(InputType.convolutionalFlat(height, width, inputDepth)).build();
                MultiLayerNetwork net = new MultiLayerNetwork(conf);
                net.init();
                String msg = "PoolingType=" + poolingType + ", minibatch=" + minibatchSize + ", activationFn=" + afn;
                if (PRINT_RESULTS) {
                    System.out.println(msg);
                    // for (int j = 0; j < net.getnLayers(); j++)
                    // System.out.println("Layer " + j + " # params: " + net.getLayer(j).numParams());
                }
                boolean gradOK = GradientCheckUtil.checkGradients(net, DEFAULT_EPS, DEFAULT_MAX_REL_ERROR, DEFAULT_MIN_ABS_ERROR, PRINT_RESULTS, RETURN_ON_FIRST_FAILURE, input, labels);
                assertTrue(gradOK, msg);
                TestUtils.testModelSerialization(net);
            }
        }
    }

    @Test
    @DisplayName("Test Cnn With Space To Batch")
    void testCnnWithSpaceToBatch() {
        Nd4j.getRandom().setSeed(12345);
        int nOut = 4;
        int[] minibatchSizes = { 2, 4 };
        int width = 5;
        int height = 5;
        int inputDepth = 1;
        int[] kernel = { 2, 2 };
        int[] blocks = { 1, 1 };
        String[] activations = { "sigmoid", "tanh" };
        SubsamplingLayer.PoolingType[] poolingTypes = new SubsamplingLayer.PoolingType[] { SubsamplingLayer.PoolingType.MAX, SubsamplingLayer.PoolingType.AVG, SubsamplingLayer.PoolingType.PNORM };
        for (String afn : activations) {
            for (SubsamplingLayer.PoolingType poolingType : poolingTypes) {
                for (int minibatchSize : minibatchSizes) {
                    INDArray input = Nd4j.rand(minibatchSize, width * height * inputDepth);
                    INDArray labels = Nd4j.zeros(minibatchSize, nOut);
                    for (int i = 0; i < minibatchSize; i++) {
                        labels.putScalar(new int[] { i, i % nOut }, 1.0);
                    }
                    MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().dataType(DataType.DOUBLE).updater(new NoOp()).dist(new NormalDistribution(0, 1)).list().layer(new ConvolutionLayer.Builder(kernel).nIn(inputDepth).cudnnAllowFallback(false).nOut(3).build()).layer(// trivial space to batch
                    new SpaceToBatchLayer.Builder(blocks).build()).layer(new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(4 * 4 * 3).nOut(nOut).build()).setInputType(InputType.convolutionalFlat(height, width, inputDepth)).build();
                    MultiLayerNetwork net = new MultiLayerNetwork(conf);
                    net.init();
                    String msg = "PoolingType=" + poolingType + ", minibatch=" + minibatchSize + ", activationFn=" + afn;
                    if (PRINT_RESULTS) {
                        System.out.println(msg);
                        // for (int j = 0; j < net.getnLayers(); j++)
                        // System.out.println("Layer " + j + " # params: " + net.getLayer(j).numParams());
                    }
                    boolean gradOK = GradientCheckUtil.checkGradients(net, DEFAULT_EPS, DEFAULT_MAX_REL_ERROR, DEFAULT_MIN_ABS_ERROR, PRINT_RESULTS, RETURN_ON_FIRST_FAILURE, input, labels);
                    assertTrue(gradOK, msg);
                    TestUtils.testModelSerialization(net);
                }
            }
        }
    }

    @Test
    @DisplayName("Test Cnn With Upsampling")
    void testCnnWithUpsampling() {
        Nd4j.getRandom().setSeed(12345);
        int nOut = 4;
        int[] minibatchSizes = { 1, 3 };
        int width = 5;
        int height = 5;
        int inputDepth = 1;
        int[] kernel = { 2, 2 };
        int[] stride = { 1, 1 };
        int[] padding = { 0, 0 };
        int size = 2;
        for (int minibatchSize : minibatchSizes) {
            INDArray input = Nd4j.rand(minibatchSize, width * height * inputDepth);
            INDArray labels = TestUtils.randomOneHot(minibatchSize, nOut);
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().dataType(DataType.DOUBLE).updater(new NoOp()).dist(new NormalDistribution(0, 1)).list().layer(new ConvolutionLayer.Builder(kernel, stride, padding).nIn(inputDepth).nOut(3).build()).layer(// output: 4*2 =8 -> 8x8x3
            new Upsampling2D.Builder().size(size).build()).layer(new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(8 * 8 * 3).nOut(4).build()).setInputType(InputType.convolutionalFlat(height, width, inputDepth)).build();
            MultiLayerNetwork net = new MultiLayerNetwork(conf);
            net.init();
            String msg = "Upsampling - minibatch=" + minibatchSize;
            if (PRINT_RESULTS) {
                System.out.println(msg);
                // for (int j = 0; j < net.getnLayers(); j++)
                // System.out.println("Layer " + j + " # params: " + net.getLayer(j).numParams());
            }
            boolean gradOK = GradientCheckUtil.checkGradients(net, DEFAULT_EPS, DEFAULT_MAX_REL_ERROR, DEFAULT_MIN_ABS_ERROR, PRINT_RESULTS, RETURN_ON_FIRST_FAILURE, input, labels);
            assertTrue(gradOK, msg);
            TestUtils.testModelSerialization(net);
        }
    }

    @Test
    @DisplayName("Test Cnn With Subsampling")
    void testCnnWithSubsampling() {
        Nd4j.getRandom().setSeed(12345);
        int nOut = 4;
        int[] minibatchSizes = { 1, 3 };
        int width = 5;
        int height = 5;
        int inputDepth = 1;
        int[] kernel = { 2, 2 };
        int[] stride = { 1, 1 };
        int[] padding = { 0, 0 };
        int pnorm = 2;
        Activation[] activations = { Activation.SIGMOID, Activation.TANH };
        SubsamplingLayer.PoolingType[] poolingTypes = new SubsamplingLayer.PoolingType[] { SubsamplingLayer.PoolingType.MAX, SubsamplingLayer.PoolingType.AVG, SubsamplingLayer.PoolingType.PNORM };
        for (Activation afn : activations) {
            for (SubsamplingLayer.PoolingType poolingType : poolingTypes) {
                for (int minibatchSize : minibatchSizes) {
                    INDArray input = Nd4j.rand(minibatchSize, width * height * inputDepth);
                    INDArray labels = Nd4j.zeros(minibatchSize, nOut);
                    for (int i = 0; i < minibatchSize; i++) {
                        labels.putScalar(new int[] { i, i % nOut }, 1.0);
                    }
                    MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().updater(new NoOp()).dataType(DataType.DOUBLE).dist(new NormalDistribution(0, 1)).list().layer(0, new ConvolutionLayer.Builder(kernel, stride, padding).nIn(inputDepth).cudnnAllowFallback(false).nOut(3).build()).layer(1, new SubsamplingLayer.Builder(poolingType).cudnnAllowFallback(false).kernelSize(kernel).stride(stride).padding(padding).pnorm(pnorm).build()).layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(3 * 3 * 3).nOut(4).build()).setInputType(InputType.convolutionalFlat(height, width, inputDepth)).build();
                    MultiLayerNetwork net = new MultiLayerNetwork(conf);
                    net.init();
                    String msg = "PoolingType=" + poolingType + ", minibatch=" + minibatchSize + ", activationFn=" + afn;
                    if (PRINT_RESULTS) {
                        System.out.println(msg);
                        // for (int j = 0; j < net.getnLayers(); j++)
                        // System.out.println("Layer " + j + " # params: " + net.getLayer(j).numParams());
                    }
                    boolean gradOK = GradientCheckUtil.checkGradients(net, DEFAULT_EPS, DEFAULT_MAX_REL_ERROR, DEFAULT_MIN_ABS_ERROR, PRINT_RESULTS, RETURN_ON_FIRST_FAILURE, input, labels);
                    assertTrue(gradOK, msg);
                    TestUtils.testModelSerialization(net);
                }
            }
        }
    }

    @Test
    @DisplayName("Test Cnn With Subsampling V 2")
    void testCnnWithSubsamplingV2() {
        Nd4j.getRandom().setSeed(12345);
        int nOut = 4;
        int[] minibatchSizes = { 1, 3 };
        int width = 5;
        int height = 5;
        int inputDepth = 1;
        int[] kernel = { 2, 2 };
        int[] stride = { 1, 1 };
        int[] padding = { 0, 0 };
        int pNorm = 3;
        Activation[] activations = { Activation.SIGMOID, Activation.TANH };
        SubsamplingLayer.PoolingType[] poolingTypes = new SubsamplingLayer.PoolingType[] { SubsamplingLayer.PoolingType.MAX, SubsamplingLayer.PoolingType.AVG, SubsamplingLayer.PoolingType.PNORM };
        for (Activation afn : activations) {
            for (SubsamplingLayer.PoolingType poolingType : poolingTypes) {
                for (int minibatchSize : minibatchSizes) {
                    INDArray input = Nd4j.rand(minibatchSize, width * height * inputDepth);
                    INDArray labels = Nd4j.zeros(minibatchSize, nOut);
                    for (int i = 0; i < minibatchSize; i++) {
                        labels.putScalar(new int[] { i, i % nOut }, 1.0);
                    }
                    MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().updater(new NoOp()).dataType(DataType.DOUBLE).dist(new NormalDistribution(0, 1)).list().layer(0, new ConvolutionLayer.Builder(kernel, stride, padding).nIn(inputDepth).cudnnAllowFallback(false).nOut(3).build()).layer(1, new SubsamplingLayer.Builder(poolingType).kernelSize(kernel).stride(stride).padding(padding).cudnnAllowFallback(false).pnorm(pNorm).build()).layer(2, new ConvolutionLayer.Builder(kernel, stride, padding).cudnnAllowFallback(false).nIn(3).nOut(2).build()).layer(3, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(2 * 2 * 2).nOut(4).build()).setInputType(InputType.convolutionalFlat(height, width, inputDepth)).build();
                    MultiLayerNetwork net = new MultiLayerNetwork(conf);
                    net.init();
                    String msg = "PoolingType=" + poolingType + ", minibatch=" + minibatchSize + ", activationFn=" + afn;
                    System.out.println(msg);
                    boolean gradOK = GradientCheckUtil.checkGradients(net, DEFAULT_EPS, DEFAULT_MAX_REL_ERROR, DEFAULT_MIN_ABS_ERROR, PRINT_RESULTS, RETURN_ON_FIRST_FAILURE, input, labels);
                    assertTrue(gradOK, msg);
                    TestUtils.testModelSerialization(net);
                }
            }
        }
    }

    @Test
    @DisplayName("Test Cnn Multi Layer")
    void testCnnMultiLayer() {
        int nOut = 2;
        int[] minibatchSizes = { 1, 2, 5 };
        int width = 5;
        int height = 5;
        int[] inputDepths = { 1, 2, 4 };
        Activation[] activations = { Activation.SIGMOID, Activation.TANH };
        SubsamplingLayer.PoolingType[] poolingTypes = new SubsamplingLayer.PoolingType[] { SubsamplingLayer.PoolingType.MAX, SubsamplingLayer.PoolingType.AVG };
        Nd4j.getRandom().setSeed(12345);
        for (int inputDepth : inputDepths) {
            for (Activation afn : activations) {
                for (SubsamplingLayer.PoolingType poolingType : poolingTypes) {
                    for (int minibatchSize : minibatchSizes) {
                        INDArray input = Nd4j.rand(minibatchSize, width * height * inputDepth);
                        INDArray labels = Nd4j.zeros(minibatchSize, nOut);
                        for (int i = 0; i < minibatchSize; i++) {
                            labels.putScalar(new int[] { i, i % nOut }, 1.0);
                        }
                        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().seed(12345).updater(new NoOp()).dataType(DataType.DOUBLE).activation(afn).list().layer(0, new ConvolutionLayer.Builder().kernelSize(2, 2).stride(1, 1).cudnnAllowFallback(false).padding(0, 0).nIn(inputDepth).nOut(2).build()).layer(1, new ConvolutionLayer.Builder().nIn(2).nOut(2).kernelSize(2, 2).cudnnAllowFallback(false).stride(1, 1).padding(0, 0).build()).layer(2, new ConvolutionLayer.Builder().nIn(2).nOut(2).kernelSize(2, 2).cudnnAllowFallback(false).stride(1, 1).padding(0, 0).build()).layer(3, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(2 * 2 * 2).nOut(nOut).build()).setInputType(InputType.convolutionalFlat(height, width, inputDepth)).build();
                        assertEquals(ConvolutionMode.Truncate, ((ConvolutionLayer) conf.getConf(0).getLayer()).getConvolutionMode());
                        MultiLayerNetwork net = new MultiLayerNetwork(conf);
                        net.init();
                        // for (int i = 0; i < 4; i++) {
                        // System.out.println("nParams, layer " + i + ": " + net.getLayer(i).numParams());
                        // }
                        String msg = "PoolingType=" + poolingType + ", minibatch=" + minibatchSize + ", activationFn=" + afn;
                        System.out.println(msg);
                        boolean gradOK = GradientCheckUtil.checkGradients(net, DEFAULT_EPS, DEFAULT_MAX_REL_ERROR, DEFAULT_MIN_ABS_ERROR, PRINT_RESULTS, RETURN_ON_FIRST_FAILURE, input, labels);
                        assertTrue(gradOK, msg);
                        TestUtils.testModelSerialization(net);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Test Cnn Same Padding Mode")
    void testCnnSamePaddingMode() {
        int nOut = 2;
        int[] minibatchSizes = { 1, 3, 3, 2, 1, 2 };
        // Same padding mode: insensitive to exact input size...
        int[] heights = new int[] { 4, 5, 6, 5, 4, 4 };
        int[] kernelSizes = new int[] { 2, 3, 2, 3, 2, 3 };
        int[] inputDepths = { 1, 2, 4, 3, 2, 3 };
        int width = 5;
        Nd4j.getRandom().setSeed(12345);
        for (int i = 0; i < minibatchSizes.length; i++) {
            int inputDepth = inputDepths[i];
            int minibatchSize = minibatchSizes[i];
            int height = heights[i];
            int k = kernelSizes[i];
            INDArray input = Nd4j.rand(minibatchSize, width * height * inputDepth);
            INDArray labels = TestUtils.randomOneHot(minibatchSize, nOut);
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().seed(12345).dataType(DataType.DOUBLE).updater(new NoOp()).activation(Activation.TANH).convolutionMode(Same).list().layer(0, new ConvolutionLayer.Builder().name("layer 0").kernelSize(k, k).stride(1, 1).padding(0, 0).nIn(inputDepth).nOut(2).build()).layer(1, new SubsamplingLayer.Builder().poolingType(SubsamplingLayer.PoolingType.MAX).kernelSize(k, k).stride(1, 1).padding(0, 0).build()).layer(2, new ConvolutionLayer.Builder().nIn(2).nOut(2).kernelSize(k, k).stride(1, 1).padding(0, 0).build()).layer(3, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nOut(nOut).build()).setInputType(InputType.convolutionalFlat(height, width, inputDepth)).build();
            MultiLayerNetwork net = new MultiLayerNetwork(conf);
            net.init();
            // for (int j = 0; j < net.getLayers().length; j++) {
            // System.out.println("nParams, layer " + j + ": " + net.getLayer(j).numParams());
            // }
            String msg = "Minibatch=" + minibatchSize + ", inDepth=" + inputDepth + ", height=" + height + ", kernelSize=" + k;
            System.out.println(msg);
            boolean gradOK = GradientCheckUtil.checkGradients(net, DEFAULT_EPS, DEFAULT_MAX_REL_ERROR, DEFAULT_MIN_ABS_ERROR, PRINT_RESULTS, RETURN_ON_FIRST_FAILURE, input, labels);
            assertTrue(gradOK, msg);
            TestUtils.testModelSerialization(net);
        }
    }

    @Test
    @DisplayName("Test Cnn Same Padding Mode Strided")
    void testCnnSamePaddingModeStrided() {
        int nOut = 2;
        int[] minibatchSizes = { 1, 3 };
        int width = 16;
        int height = 16;
        int[] kernelSizes = new int[] { 2, 3 };
        int[] strides = { 1, 2, 3 };
        int[] inputDepths = { 1, 3 };
        Nd4j.getRandom().setSeed(12345);
        for (int inputDepth : inputDepths) {
            for (int minibatchSize : minibatchSizes) {
                for (int stride : strides) {
                    for (int k : kernelSizes) {
                        for (boolean convFirst : new boolean[] { true, false }) {
                            INDArray input = Nd4j.rand(minibatchSize, width * height * inputDepth);
                            INDArray labels = Nd4j.zeros(minibatchSize, nOut);
                            for (int i = 0; i < minibatchSize; i++) {
                                labels.putScalar(new int[] { i, i % nOut }, 1.0);
                            }
                            Layer convLayer = new ConvolutionLayer.Builder().name("layer 0").kernelSize(k, k).stride(stride, stride).padding(0, 0).nIn(inputDepth).nOut(2).build();
                            Layer poolLayer = new SubsamplingLayer.Builder().poolingType(SubsamplingLayer.PoolingType.MAX).kernelSize(k, k).stride(stride, stride).padding(0, 0).build();
                            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().seed(12345).dataType(DataType.DOUBLE).updater(new NoOp()).activation(Activation.TANH).convolutionMode(Same).list().layer(0, convFirst ? convLayer : poolLayer).layer(1, convFirst ? poolLayer : convLayer).layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nOut(nOut).build()).setInputType(InputType.convolutionalFlat(height, width, inputDepth)).build();
                            MultiLayerNetwork net = new MultiLayerNetwork(conf);
                            net.init();
                            // for (int i = 0; i < net.getLayers().length; i++) {
                            // System.out.println("nParams, layer " + i + ": " + net.getLayer(i).numParams());
                            // }
                            String msg = "Minibatch=" + minibatchSize + ", inDepth=" + inputDepth + ", height=" + height + ", kernelSize=" + k + ", stride = " + stride + ", convLayer first = " + convFirst;
                            System.out.println(msg);
                            boolean gradOK = GradientCheckUtil.checkGradients(new GradientCheckUtil.MLNConfig().net(net).input(input).labels(labels).subset(true).maxPerParam(128));
                            assertTrue(gradOK, msg);
                            TestUtils.testModelSerialization(net);
                        }
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Test Cnn Zero Padding Layer")
    void testCnnZeroPaddingLayer() {
        Nd4j.getRandom().setSeed(12345);
        int nOut = 4;
        int width = 6;
        int height = 6;
        int[] kernel = { 2, 2 };
        int[] stride = { 1, 1 };
        int[] padding = { 0, 0 };
        int[] minibatchSizes = { 1, 3, 2 };
        int[] inputDepths = { 1, 3, 2 };
        int[][] zeroPadLayer = new int[][] { { 0, 0, 0, 0 }, { 1, 1, 0, 0 }, { 2, 2, 2, 2 } };
        for (int i = 0; i < minibatchSizes.length; i++) {
            int minibatchSize = minibatchSizes[i];
            int inputDepth = inputDepths[i];
            int[] zeroPad = zeroPadLayer[i];
            INDArray input = Nd4j.rand(DataType.DOUBLE, new int[] { minibatchSize, inputDepth, height, width });
            INDArray labels = TestUtils.randomOneHot(minibatchSize, nOut);
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().updater(new NoOp()).dataType(DataType.DOUBLE).dist(new NormalDistribution(0, 1)).list().layer(0, new ConvolutionLayer.Builder(kernel, stride, padding).nIn(inputDepth).nOut(3).build()).layer(1, new ZeroPaddingLayer.Builder(zeroPad).build()).layer(2, new ConvolutionLayer.Builder(kernel, stride, padding).nIn(3).nOut(3).build()).layer(3, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nOut(4).build()).setInputType(InputType.convolutional(height, width, inputDepth)).build();
            MultiLayerNetwork net = new MultiLayerNetwork(conf);
            net.init();
            // Check zero padding activation shape
            org.deeplearning4j.nn.layers.convolution.ZeroPaddingLayer zpl = (org.deeplearning4j.nn.layers.convolution.ZeroPaddingLayer) net.getLayer(1);
            val expShape = new long[] { minibatchSize, inputDepth, height + zeroPad[0] + zeroPad[1], width + zeroPad[2] + zeroPad[3] };
            INDArray out = zpl.activate(input, false, LayerWorkspaceMgr.noWorkspaces());
            assertArrayEquals(expShape, out.shape());
            String msg = "minibatch=" + minibatchSize + ", channels=" + inputDepth + ", zeroPad = " + Arrays.toString(zeroPad);
            if (PRINT_RESULTS) {
                System.out.println(msg);
                // for (int j = 0; j < net.getnLayers(); j++)
                // System.out.println("Layer " + j + " # params: " + net.getLayer(j).numParams());
            }
            boolean gradOK = GradientCheckUtil.checkGradients(net, DEFAULT_EPS, DEFAULT_MAX_REL_ERROR, DEFAULT_MIN_ABS_ERROR, PRINT_RESULTS, RETURN_ON_FIRST_FAILURE, input, labels);
            assertTrue(gradOK, msg);
            TestUtils.testModelSerialization(net);
        }
    }

    @Test
    @DisplayName("Test Deconvolution 2 D")
    void testDeconvolution2D() {
        int nOut = 2;
        int[] minibatchSizes = new int[] { 1, 3, 3, 1, 3 };
        int[] kernelSizes = new int[] { 1, 1, 1, 3, 3 };
        int[] strides = { 1, 1, 2, 2, 2 };
        int[] dilation = { 1, 2, 1, 2, 2 };
        Activation[] activations = new Activation[] { Activation.SIGMOID, Activation.TANH, Activation.SIGMOID, Activation.SIGMOID, Activation.SIGMOID };
        ConvolutionMode[] cModes = new ConvolutionMode[] { Same, Same, Truncate, Truncate, Truncate };
        int width = 7;
        int height = 7;
        int inputDepth = 3;
        Nd4j.getRandom().setSeed(12345);
        for (int i = 0; i < minibatchSizes.length; i++) {
            int minibatchSize = minibatchSizes[i];
            int k = kernelSizes[i];
            int s = strides[i];
            int d = dilation[i];
            ConvolutionMode cm = cModes[i];
            Activation act = activations[i];
            int w = d * width;
            int h = d * height;
            INDArray input = Nd4j.rand(minibatchSize, w * h * inputDepth);
            INDArray labels = Nd4j.zeros(minibatchSize, nOut);
            for (int j = 0; j < minibatchSize; j++) {
                labels.putScalar(new int[] { j, j % nOut }, 1.0);
            }
            NeuralNetConfiguration.ListBuilder b = new NeuralNetConfiguration.Builder().seed(12345).dataType(DataType.DOUBLE).updater(new NoOp()).activation(act).list().layer(new Deconvolution2D.Builder().name("deconvolution_2D_layer").kernelSize(k, k).stride(s, s).dilation(d, d).convolutionMode(cm).nIn(inputDepth).nOut(nOut).build());
            MultiLayerConfiguration conf = b.layer(new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nOut(nOut).build()).setInputType(InputType.convolutionalFlat(h, w, inputDepth)).build();
            MultiLayerNetwork net = new MultiLayerNetwork(conf);
            net.init();
            // for (int j = 0; j < net.getLayers().length; j++) {
            // System.out.println("nParams, layer " + j + ": " + net.getLayer(j).numParams());
            // }
            String msg = " - mb=" + minibatchSize + ", k=" + k + ", s=" + s + ", d=" + d + ", cm=" + cm;
            System.out.println(msg);
            boolean gradOK = GradientCheckUtil.checkGradients(new GradientCheckUtil.MLNConfig().net(net).input(input).labels(labels).subset(true).maxPerParam(100));
            assertTrue(gradOK, msg);
            TestUtils.testModelSerialization(net);
        }
    }

    @Test
    @DisplayName("Test Depthwise Conv 2 D")
    void testDepthwiseConv2D() {
        int nIn = 3;
        int depthMultiplier = 2;
        int nOut = nIn * depthMultiplier;
        int width = 5;
        int height = 5;
        Nd4j.getRandom().setSeed(12345);
        int[] ks = new int[] { 1, 3, 3, 1, 3 };
        int[] ss = new int[] { 1, 1, 1, 2, 2 };
        ConvolutionMode[] cms = new ConvolutionMode[] { Truncate, Truncate, Truncate, Truncate, Truncate };
        int[] mb = new int[] { 1, 1, 1, 3, 3 };
        for (int t = 0; t < ks.length; t++) {
            int k = ks[t];
            int s = ss[t];
            ConvolutionMode cm = cms[t];
            int minibatchSize = mb[t];
            INDArray input = Nd4j.rand(minibatchSize, width * height * nIn);
            INDArray labels = Nd4j.zeros(minibatchSize, nOut);
            for (int i = 0; i < minibatchSize; i++) {
                labels.putScalar(new int[] { i, i % nOut }, 1.0);
            }
            NeuralNetConfiguration.ListBuilder b = new NeuralNetConfiguration.Builder().seed(12345).dataType(DataType.DOUBLE).updater(new NoOp()).activation(Activation.TANH).convolutionMode(cm).list().layer(new Convolution2D.Builder().kernelSize(1, 1).stride(1, 1).nIn(nIn).nOut(nIn).build()).layer(new DepthwiseConvolution2D.Builder().name("depth-wise conv 2D layer").cudnnAllowFallback(false).kernelSize(k, k).stride(s, s).depthMultiplier(depthMultiplier).nIn(nIn).build());
            MultiLayerConfiguration conf = b.layer(new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nOut(nOut).build()).setInputType(InputType.convolutionalFlat(height, width, nIn)).build();
            MultiLayerNetwork net = new MultiLayerNetwork(conf);
            net.init();
            // for (int i = 0; i < net.getLayers().length; i++) {
            // System.out.println("nParams, layer " + i + ": " + net.getLayer(i).numParams());
            // }
            String msg = " - mb=" + minibatchSize + ", k=" + k + ", nIn=" + nIn + ", depthMul=" + depthMultiplier + ", s=" + s + ", cm=" + cm;
            System.out.println(msg);
            boolean gradOK = GradientCheckUtil.checkGradients(new GradientCheckUtil.MLNConfig().net(net).input(input).labels(labels).subset(true).maxPerParam(256));
            assertTrue(gradOK, msg);
            TestUtils.testModelSerialization(net);
        }
    }

    @Test
    @DisplayName("Test Separable Conv 2 D")
    void testSeparableConv2D() {
        int nOut = 2;
        int[] minibatchSizes = new int[] { 1, 3 };
        int width = 6;
        int height = 6;
        int inputDepth = 3;
        Nd4j.getRandom().setSeed(12345);
        int[] ks = new int[] { 1, 3, 3, 1, 3 };
        int[] ss = new int[] { 1, 1, 1, 2, 2 };
        int[] ds = new int[] { 1, 1, 2, 2, 2 };
        ConvolutionMode[] cms = new ConvolutionMode[] { Truncate, Truncate, Truncate, Truncate, Truncate };
        int[] mb = new int[] { 1, 1, 1, 3, 3 };
        for (int t = 0; t < ks.length; t++) {
            int k = ks[t];
            int s = ss[t];
            int d = ds[t];
            ConvolutionMode cm = cms[t];
            int minibatchSize = mb[t];
            // Use larger input with larger dilation values (to avoid invalid config)
            int w = d * width;
            int h = d * height;
            INDArray input = Nd4j.rand(minibatchSize, w * h * inputDepth);
            INDArray labels = Nd4j.zeros(minibatchSize, nOut);
            for (int i = 0; i < minibatchSize; i++) {
                labels.putScalar(new int[] { i, i % nOut }, 1.0);
            }
            NeuralNetConfiguration.ListBuilder b = new NeuralNetConfiguration.Builder().seed(12345).dataType(DataType.DOUBLE).updater(new NoOp()).activation(Activation.TANH).convolutionMode(cm).list().layer(new SeparableConvolution2D.Builder().name("Separable conv 2D layer").kernelSize(k, k).stride(s, s).dilation(d, d).depthMultiplier(3).nIn(inputDepth).nOut(2).build());
            MultiLayerConfiguration conf = b.layer(new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nOut(nOut).build()).setInputType(InputType.convolutionalFlat(h, w, inputDepth)).build();
            MultiLayerNetwork net = new MultiLayerNetwork(conf);
            net.init();
            // for (int i = 0; i < net.getLayers().length; i++) {
            // System.out.println("nParams, layer " + i + ": " + net.getLayer(i).numParams());
            // }
            String msg = " - mb=" + minibatchSize + ", k=" + k + ", s=" + s + ", d=" + d + ", cm=" + cm;
            System.out.println(msg);
            boolean gradOK = GradientCheckUtil.checkGradients(new GradientCheckUtil.MLNConfig().net(net).input(input).labels(labels).subset(true).maxPerParam(50));
            assertTrue(gradOK, msg);
            TestUtils.testModelSerialization(net);
        }
    }

    @Test
    @DisplayName("Test Cnn Dilated")
    void testCnnDilated() {
        int nOut = 2;
        int minibatchSize = 2;
        int width = 8;
        int height = 8;
        int inputDepth = 2;
        Nd4j.getRandom().setSeed(12345);
        boolean[] sub = new boolean[] { true, true, false, true, false };
        int[] stride = new int[] { 1, 1, 1, 2, 2 };
        int[] kernel = new int[] { 2, 3, 3, 3, 3 };
        int[] ds = new int[] { 2, 2, 3, 3, 2 };
        ConvolutionMode[] cms = new ConvolutionMode[] { Same, Truncate, Truncate, Same, Truncate };
        for (int t = 0; t < sub.length; t++) {
            boolean subsampling = sub[t];
            int s = stride[t];
            int k = kernel[t];
            int d = ds[t];
            ConvolutionMode cm = cms[t];
            // Use larger input with larger dilation values (to avoid invalid config)
            int w = d * width;
            int h = d * height;
            INDArray input = Nd4j.rand(minibatchSize, w * h * inputDepth);
            INDArray labels = Nd4j.zeros(minibatchSize, nOut);
            for (int i = 0; i < minibatchSize; i++) {
                labels.putScalar(new int[] { i, i % nOut }, 1.0);
            }
            NeuralNetConfiguration.ListBuilder b = new NeuralNetConfiguration.Builder().seed(12345).dataType(DataType.DOUBLE).updater(new NoOp()).activation(Activation.TANH).convolutionMode(cm).list().layer(new ConvolutionLayer.Builder().name("layer 0").kernelSize(k, k).stride(s, s).dilation(d, d).nIn(inputDepth).nOut(2).build());
            if (subsampling) {
                b.layer(new SubsamplingLayer.Builder().poolingType(SubsamplingLayer.PoolingType.MAX).kernelSize(k, k).stride(s, s).dilation(d, d).build());
            } else {
                b.layer(new ConvolutionLayer.Builder().nIn(2).nOut(2).kernelSize(k, k).stride(s, s).dilation(d, d).build());
            }
            MultiLayerConfiguration conf = b.layer(new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nOut(nOut).build()).setInputType(InputType.convolutionalFlat(h, w, inputDepth)).build();
            MultiLayerNetwork net = new MultiLayerNetwork(conf);
            net.init();
            // for (int i = 0; i < net.getLayers().length; i++) {
            // System.out.println("nParams, layer " + i + ": " + net.getLayer(i).numParams());
            // }
            String msg = (subsampling ? "subsampling" : "conv") + " - mb=" + minibatchSize + ", k=" + k + ", s=" + s + ", d=" + d + ", cm=" + cm;
            System.out.println(msg);
            boolean gradOK = GradientCheckUtil.checkGradients(net, DEFAULT_EPS, DEFAULT_MAX_REL_ERROR, DEFAULT_MIN_ABS_ERROR, PRINT_RESULTS, RETURN_ON_FIRST_FAILURE, input, labels);
            assertTrue(gradOK, msg);
            TestUtils.testModelSerialization(net);
        }
    }

    @Test
    @DisplayName("Test Cropping 2 D Layer")
    void testCropping2DLayer() {
        Nd4j.getRandom().setSeed(12345);
        int nOut = 2;
        int[] minibatchSizes = { 1, 3 };
        int width = 12;
        int height = 11;
        int[] inputDepths = { 1, 3 };
        int[] kernel = { 2, 2 };
        int[] stride = { 1, 1 };
        int[] padding = { 0, 0 };
        int[][] cropTestCases = new int[][] { { 0, 0, 0, 0 }, { 1, 1, 0, 0 }, { 2, 2, 2, 2 }, { 1, 2, 3, 4 } };
        for (int inputDepth : inputDepths) {
            for (int minibatchSize : minibatchSizes) {
                INDArray input = Nd4j.rand(new int[] { minibatchSize, inputDepth, height, width });
                INDArray labels = Nd4j.zeros(minibatchSize, nOut);
                for (int i = 0; i < minibatchSize; i++) {
                    labels.putScalar(new int[] { i, i % nOut }, 1.0);
                }
                for (int[] crop : cropTestCases) {
                    MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().dataType(DataType.DOUBLE).updater(new NoOp()).convolutionMode(ConvolutionMode.Same).weightInit(new NormalDistribution(0, 1)).list().layer(new ConvolutionLayer.Builder(kernel, stride, padding).nIn(inputDepth).nOut(2).build()).layer(new Cropping2D(crop)).layer(new ConvolutionLayer.Builder(kernel, stride, padding).nIn(2).nOut(2).build()).layer(new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.AVG).kernelSize(3, 3).stride(3, 3).build()).layer(3, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nOut(nOut).build()).setInputType(InputType.convolutional(height, width, inputDepth)).build();
                    MultiLayerNetwork net = new MultiLayerNetwork(conf);
                    net.init();
                    // Check cropping activation shape
                    org.deeplearning4j.nn.layers.convolution.Cropping2DLayer cl = (org.deeplearning4j.nn.layers.convolution.Cropping2DLayer) net.getLayer(1);
                    val expShape = new long[] { minibatchSize, inputDepth, height - crop[0] - crop[1], width - crop[2] - crop[3] };
                    INDArray out = cl.activate(input, false, LayerWorkspaceMgr.noWorkspaces());
                    assertArrayEquals(expShape, out.shape());
                    String msg = "minibatch=" + minibatchSize + ", channels=" + inputDepth + ", zeroPad = " + Arrays.toString(crop);
                    if (PRINT_RESULTS) {
                        System.out.println(msg);
                    }
                    boolean gradOK = GradientCheckUtil.checkGradients(new GradientCheckUtil.MLNConfig().net(net).input(input).labels(labels).subset(true).maxPerParam(160));
                    assertTrue(gradOK, msg);
                    TestUtils.testModelSerialization(net);
                }
            }
        }
    }
}
