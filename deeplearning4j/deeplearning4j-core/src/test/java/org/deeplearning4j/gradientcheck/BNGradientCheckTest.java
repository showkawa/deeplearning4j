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

import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.TestUtils;
import org.deeplearning4j.datasets.iterator.impl.IrisDataSetIterator;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.distribution.NormalDistribution;
import org.deeplearning4j.nn.conf.distribution.UniformDistribution;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.executioner.OpExecutioner;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerMinMaxScaler;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.NoOp;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.profiler.OpProfiler;
import org.nd4j.linalg.profiler.ProfilerConfig;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 */
@DisplayName("Bn Gradient Check Test")
@NativeTag
@Tag(TagNames.NDARRAY_ETL)
@Tag(TagNames.TRAINING)
@Tag(TagNames.DL4J_OLD_API)
class BNGradientCheckTest extends BaseDL4JTest {

    static {
        Nd4j.setDataType(DataType.DOUBLE);
    }

    @Override
    public long getTimeoutMilliseconds() {
        return 90000L;
    }

    @Test
    @DisplayName("Test Gradient 2 d Simple")
    void testGradient2dSimple() {
        DataNormalization scaler = new NormalizerMinMaxScaler();
        DataSetIterator iter = new IrisDataSetIterator(150, 150);
        scaler.fit(iter);
        iter.setPreProcessor(scaler);
        DataSet ds = iter.next();
        INDArray input = ds.getFeatures();
        INDArray labels = ds.getLabels();
        for (boolean useLogStd : new boolean[] { true, false }) {
            MultiLayerConfiguration.Builder builder = new NeuralNetConfiguration.Builder().updater(new NoOp()).dataType(DataType.DOUBLE).seed(12345L).dist(new NormalDistribution(0, 1)).list().layer(0, new DenseLayer.Builder().nIn(4).nOut(3).activation(Activation.IDENTITY).build()).layer(1, new BatchNormalization.Builder().useLogStd(useLogStd).nOut(3).build()).layer(2, new ActivationLayer.Builder().activation(Activation.TANH).build()).layer(3, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(3).nOut(3).build());
            MultiLayerNetwork mln = new MultiLayerNetwork(builder.build());
            mln.init();
            // for (int j = 0; j < mln.getnLayers(); j++)
            // System.out.println("Layer " + j + " # params: " + mln.getLayer(j).numParams());
            // Mean and variance vars are not gradient checkable; mean/variance "gradient" is used to implement running mean/variance calc
            // i.e., runningMean = decay * runningMean + (1-decay) * batchMean
            // However, numerical gradient will be 0 as forward pass doesn't depend on this "parameter"
            Set<String> excludeParams = new HashSet<>(Arrays.asList("1_mean", "1_var", "1_log10stdev"));
            boolean gradOK = GradientCheckUtil.checkGradients(new GradientCheckUtil.MLNConfig().net(mln).input(input).labels(labels).excludeParams(excludeParams));
            assertTrue(gradOK);
            TestUtils.testModelSerialization(mln);
        }
    }

    @Test
    @DisplayName("Test Gradient Cnn Simple")
    void testGradientCnnSimple() {
        Nd4j.getRandom().setSeed(12345);
        int minibatch = 10;
        int depth = 1;
        int hw = 4;
        int nOut = 4;
        INDArray input = Nd4j.rand(new int[] { minibatch, depth, hw, hw });
        INDArray labels = Nd4j.zeros(minibatch, nOut);
        Random r = new Random(12345);
        for (int i = 0; i < minibatch; i++) {
            labels.putScalar(i, r.nextInt(nOut), 1.0);
        }
        for (boolean useLogStd : new boolean[] { true, false }) {
            MultiLayerConfiguration.Builder builder = new NeuralNetConfiguration.Builder().dataType(DataType.DOUBLE).updater(new NoOp()).seed(12345L).dist(new NormalDistribution(0, 2)).list().layer(0, new ConvolutionLayer.Builder().kernelSize(2, 2).stride(1, 1).nIn(depth).nOut(2).activation(Activation.IDENTITY).build()).layer(1, new BatchNormalization.Builder().useLogStd(useLogStd).build()).layer(2, new ActivationLayer.Builder().activation(Activation.TANH).build()).layer(3, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nOut(nOut).build()).setInputType(InputType.convolutional(hw, hw, depth));
            MultiLayerNetwork mln = new MultiLayerNetwork(builder.build());
            mln.init();
            // for (int j = 0; j < mln.getnLayers(); j++)
            // System.out.println("Layer " + j + " # params: " + mln.getLayer(j).numParams());
            // Mean and variance vars are not gradient checkable; mean/variance "gradient" is used to implement running mean/variance calc
            // i.e., runningMean = decay * runningMean + (1-decay) * batchMean
            // However, numerical gradient will be 0 as forward pass doesn't depend on this "parameter"
            Set<String> excludeParams = new HashSet<>(Arrays.asList("1_mean", "1_var", "1_log10stdev"));
            boolean gradOK = GradientCheckUtil.checkGradients(new GradientCheckUtil.MLNConfig().net(mln).input(input).labels(labels).excludeParams(excludeParams));
            assertTrue(gradOK);
            TestUtils.testModelSerialization(mln);
        }
    }

    @Test
    @DisplayName("Test Gradient BN With CN Nand Subsampling")
    void testGradientBNWithCNNandSubsampling() {
        // Parameterized test, testing combinations of:
        // (a) activation function
        // (b) Whether to test at random initialization, or after some learning (i.e., 'characteristic mode of operation')
        // (c) Loss function (with specified output activations)
        // (d) l1 and l2 values
        Activation[] activFns = { Activation.SIGMOID, Activation.TANH, Activation.IDENTITY };
        // If true: run some backprop steps first
        boolean[] characteristic = { true };
        LossFunctions.LossFunction[] lossFunctions = { LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD, LossFunctions.LossFunction.MSE };
        // i.e., lossFunctions[i] used with outputActivations[i] here
        Activation[] outputActivations = { Activation.SOFTMAX, Activation.TANH };
        double[] l2vals = { 0.0, 0.1, 0.1 };
        // i.e., use l2vals[j] with l1vals[j]
        double[] l1vals = { 0.0, 0.0, 0.2 };
        Nd4j.getRandom().setSeed(12345);
        int minibatch = 4;
        int depth = 2;
        int hw = 5;
        int nOut = 2;
        INDArray input = Nd4j.rand(new int[] { minibatch, depth, hw, hw }).muli(5).subi(2.5);
        INDArray labels = TestUtils.randomOneHot(minibatch, nOut);
        DataSet ds = new DataSet(input, labels);
        Random rng = new Random(12345);
        for (boolean useLogStd : new boolean[] { true, false }) {
            for (Activation afn : activFns) {
                for (boolean doLearningFirst : characteristic) {
                    for (int i = 0; i < lossFunctions.length; i++) {
                        for (int j = 0; j < l2vals.length; j++) {
                            // Skip 2 of every 3 tests: from 24 cases to 8, still with decent coverage
                            if (rng.nextInt(3) != 0)
                                continue;
                            LossFunctions.LossFunction lf = lossFunctions[i];
                            Activation outputActivation = outputActivations[i];
                            MultiLayerConfiguration.Builder builder = new NeuralNetConfiguration.Builder().seed(12345).dataType(DataType.DOUBLE).l2(l2vals[j]).optimizationAlgo(OptimizationAlgorithm.LINE_GRADIENT_DESCENT).updater(new NoOp()).dist(new UniformDistribution(-2, 2)).seed(12345L).list().layer(0, new ConvolutionLayer.Builder(2, 2).stride(1, 1).nOut(3).activation(afn).build()).layer(1, new BatchNormalization.Builder().useLogStd(useLogStd).build()).layer(2, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX).kernelSize(2, 2).stride(1, 1).build()).layer(3, new BatchNormalization()).layer(4, new ActivationLayer.Builder().activation(afn).build()).layer(5, new OutputLayer.Builder(lf).activation(outputActivation).nOut(nOut).build()).setInputType(InputType.convolutional(hw, hw, depth));
                            MultiLayerConfiguration conf = builder.build();
                            MultiLayerNetwork mln = new MultiLayerNetwork(conf);
                            mln.init();
                            String name = new Object() {
                            }.getClass().getEnclosingMethod().getName();
                            // System.out.println("Num params: " + mln.numParams());
                            if (doLearningFirst) {
                                // Run a number of iterations of learning
                                mln.setInput(ds.getFeatures());
                                mln.setLabels(ds.getLabels());
                                mln.computeGradientAndScore();
                                double scoreBefore = mln.score();
                                for (int k = 0; k < 20; k++) mln.fit(ds);
                                mln.computeGradientAndScore();
                                double scoreAfter = mln.score();
                                // Can't test in 'characteristic mode of operation' if not learning
                                String msg = name + " - score did not (sufficiently) decrease during learning - activationFn=" + afn + ", lossFn=" + lf + ", outputActivation=" + outputActivation + ", doLearningFirst= " + doLearningFirst + " (before=" + scoreBefore + ", scoreAfter=" + scoreAfter + ")";
                                assertTrue(scoreAfter < 0.9 * scoreBefore,msg);
                            }
                            System.out.println(name + " - activationFn=" + afn + ", lossFn=" + lf + ", outputActivation=" + outputActivation + ", doLearningFirst=" + doLearningFirst + ", l1=" + l1vals[j] + ", l2=" + l2vals[j]);
                            // for (int k = 0; k < mln.getnLayers(); k++)
                            // System.out.println("Layer " + k + " # params: " + mln.getLayer(k).numParams());
                            // Mean and variance vars are not gradient checkable; mean/variance "gradient" is used to implement running mean/variance calc
                            // i.e., runningMean = decay * runningMean + (1-decay) * batchMean
                            // However, numerical gradient will be 0 as forward pass doesn't depend on this "parameter"
                            Set<String> excludeParams = new HashSet<>(Arrays.asList("1_mean", "1_var", "3_mean", "3_var", "1_log10stdev", "3_log10stdev"));
                            boolean gradOK = GradientCheckUtil.checkGradients(new GradientCheckUtil.MLNConfig().net(mln).input(input).labels(labels).excludeParams(excludeParams).subset(true).maxPerParam(// Most params are in output layer, only these should be skipped with this threshold
                            25));
                            assertTrue(gradOK);
                            TestUtils.testModelSerialization(mln);
                        }
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Test Gradient Dense")
    void testGradientDense() {
        // Parameterized test, testing combinations of:
        // (a) activation function
        // (b) Whether to test at random initialization, or after some learning (i.e., 'characteristic mode of operation')
        // (c) Loss function (with specified output activations)
        // (d) l1 and l2 values
        Activation[] activFns = { Activation.TANH, Activation.IDENTITY };
        // If true: run some backprop steps first
        boolean[] characteristic = { true };
        LossFunctions.LossFunction[] lossFunctions = { LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD, LossFunctions.LossFunction.MSE };
        // i.e., lossFunctions[i] used with outputActivations[i] here
        Activation[] outputActivations = { Activation.SOFTMAX, Activation.TANH };
        double[] l2vals = { 0.0, 0.1 };
        // i.e., use l2vals[j] with l1vals[j]
        double[] l1vals = { 0.0, 0.2 };
        Nd4j.getRandom().setSeed(12345);
        int minibatch = 10;
        int nIn = 5;
        int nOut = 3;
        INDArray input = Nd4j.rand(new int[] { minibatch, nIn });
        INDArray labels = Nd4j.zeros(minibatch, nOut);
        Random r = new Random(12345);
        for (int i = 0; i < minibatch; i++) {
            labels.putScalar(i, r.nextInt(nOut), 1.0);
        }
        DataSet ds = new DataSet(input, labels);
        for (boolean useLogStd : new boolean[] { true, false }) {
            for (Activation afn : activFns) {
                for (boolean doLearningFirst : characteristic) {
                    for (int i = 0; i < lossFunctions.length; i++) {
                        for (int j = 0; j < l2vals.length; j++) {
                            LossFunctions.LossFunction lf = lossFunctions[i];
                            Activation outputActivation = outputActivations[i];
                            MultiLayerConfiguration.Builder builder = new NeuralNetConfiguration.Builder().dataType(DataType.DOUBLE).l2(l2vals[j]).optimizationAlgo(OptimizationAlgorithm.CONJUGATE_GRADIENT).updater(new NoOp()).dist(new UniformDistribution(-2, 2)).seed(12345L).list().layer(0, new DenseLayer.Builder().nIn(nIn).nOut(4).activation(afn).build()).layer(1, new BatchNormalization.Builder().useLogStd(useLogStd).build()).layer(2, new DenseLayer.Builder().nIn(4).nOut(4).build()).layer(3, new BatchNormalization.Builder().useLogStd(useLogStd).build()).layer(4, new OutputLayer.Builder(lf).activation(outputActivation).nOut(nOut).build());
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
                                for (int k = 0; k < 10; k++) mln.fit(ds);
                                mln.computeGradientAndScore();
                                double scoreAfter = mln.score();
                                // Can't test in 'characteristic mode of operation' if not learning
                                String msg = name + " - score did not (sufficiently) decrease during learning - activationFn=" + afn + ", lossFn=" + lf + ", outputActivation=" + outputActivation + ", doLearningFirst= " + doLearningFirst + " (before=" + scoreBefore + ", scoreAfter=" + scoreAfter + ")";
                                assertTrue(scoreAfter < 0.8 * scoreBefore,msg);
                            }
                            System.out.println(name + " - activationFn=" + afn + ", lossFn=" + lf + ", outputActivation=" + outputActivation + ", doLearningFirst=" + doLearningFirst + ", l1=" + l1vals[j] + ", l2=" + l2vals[j]);
                            // for (int k = 0; k < mln.getnLayers(); k++)
                            // System.out.println("Layer " + k + " # params: " + mln.getLayer(k).numParams());
                            // Mean and variance vars are not gradient checkable; mean/variance "gradient" is used to implement running mean/variance calc
                            // i.e., runningMean = decay * runningMean + (1-decay) * batchMean
                            // However, numerical gradient will be 0 as forward pass doesn't depend on this "parameter"
                            Set<String> excludeParams = new HashSet<>(Arrays.asList("1_mean", "1_var", "3_mean", "3_var", "1_log10stdev", "3_log10stdev"));
                            boolean gradOK = GradientCheckUtil.checkGradients(new GradientCheckUtil.MLNConfig().net(mln).input(input).labels(labels).excludeParams(excludeParams));
                            assertTrue(gradOK);
                            TestUtils.testModelSerialization(mln);
                        }
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Test Gradient 2 d Fixed Gamma Beta")
    void testGradient2dFixedGammaBeta() {
        DataNormalization scaler = new NormalizerMinMaxScaler();
        DataSetIterator iter = new IrisDataSetIterator(150, 150);
        scaler.fit(iter);
        iter.setPreProcessor(scaler);
        DataSet ds = iter.next();
        INDArray input = ds.getFeatures();
        INDArray labels = ds.getLabels();
        for (boolean useLogStd : new boolean[] { true, false }) {
            MultiLayerConfiguration.Builder builder = new NeuralNetConfiguration.Builder().updater(new NoOp()).dataType(DataType.DOUBLE).seed(12345L).dist(new NormalDistribution(0, 1)).list().layer(0, new DenseLayer.Builder().nIn(4).nOut(3).activation(Activation.IDENTITY).build()).layer(1, new BatchNormalization.Builder().useLogStd(useLogStd).lockGammaBeta(true).gamma(2.0).beta(0.5).nOut(3).build()).layer(2, new ActivationLayer.Builder().activation(Activation.TANH).build()).layer(3, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(3).nOut(3).build());
            MultiLayerNetwork mln = new MultiLayerNetwork(builder.build());
            mln.init();
            // for (int j = 0; j < mln.getnLayers(); j++)
            // System.out.println("Layer " + j + " # params: " + mln.getLayer(j).numParams());
            // Mean and variance vars are not gradient checkable; mean/variance "gradient" is used to implement running mean/variance calc
            // i.e., runningMean = decay * runningMean + (1-decay) * batchMean
            // However, numerical gradient will be 0 as forward pass doesn't depend on this "parameter"
            Set<String> excludeParams = new HashSet<>(Arrays.asList("1_mean", "1_var", "1_log10stdev"));
            boolean gradOK = GradientCheckUtil.checkGradients(new GradientCheckUtil.MLNConfig().net(mln).input(input).labels(labels).excludeParams(excludeParams));
            assertTrue(gradOK);
            TestUtils.testModelSerialization(mln);
        }
    }

    @Test
    @DisplayName("Test Gradient Cnn Fixed Gamma Beta")
    void testGradientCnnFixedGammaBeta() {
        Nd4j.getRandom().setSeed(12345);
        int minibatch = 10;
        int depth = 1;
        int hw = 4;
        int nOut = 4;
        INDArray input = Nd4j.rand(new int[] { minibatch, depth, hw, hw });
        INDArray labels = Nd4j.zeros(minibatch, nOut);
        Random r = new Random(12345);
        for (int i = 0; i < minibatch; i++) {
            labels.putScalar(i, r.nextInt(nOut), 1.0);
        }
        for (boolean useLogStd : new boolean[] { true, false }) {
            MultiLayerConfiguration.Builder builder = new NeuralNetConfiguration.Builder().updater(new NoOp()).dataType(DataType.DOUBLE).seed(12345L).dist(new NormalDistribution(0, 2)).list().layer(0, new ConvolutionLayer.Builder().kernelSize(2, 2).stride(1, 1).nIn(depth).nOut(2).activation(Activation.IDENTITY).build()).layer(1, new BatchNormalization.Builder().useLogStd(useLogStd).lockGammaBeta(true).gamma(2.0).beta(0.5).build()).layer(2, new ActivationLayer.Builder().activation(Activation.TANH).build()).layer(3, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nOut(nOut).build()).setInputType(InputType.convolutional(hw, hw, depth));
            MultiLayerNetwork mln = new MultiLayerNetwork(builder.build());
            mln.init();
            // for (int j = 0; j < mln.getnLayers(); j++)
            // System.out.println("Layer " + j + " # params: " + mln.getLayer(j).numParams());
            // Mean and variance vars are not gradient checkable; mean/variance "gradient" is used to implement running mean/variance calc
            // i.e., runningMean = decay * runningMean + (1-decay) * batchMean
            // However, numerical gradient will be 0 as forward pass doesn't depend on this "parameter"
            Set<String> excludeParams = new HashSet<>(Arrays.asList("1_mean", "1_var", "1_log10stdev"));
            boolean gradOK = GradientCheckUtil.checkGradients(new GradientCheckUtil.MLNConfig().net(mln).input(input).labels(labels).excludeParams(excludeParams));
            assertTrue(gradOK);
            TestUtils.testModelSerialization(mln);
        }
    }

    @Test
    @DisplayName("Test Batch Norm Comp Graph Simple")
    void testBatchNormCompGraphSimple() {
        int numClasses = 2;
        int height = 3;
        int width = 3;
        int channels = 1;
        long seed = 123;
        int minibatchSize = 3;
        for (boolean useLogStd : new boolean[] { true, false }) {
            ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder().seed(seed).updater(new NoOp()).dataType(DataType.DOUBLE).weightInit(WeightInit.XAVIER).graphBuilder().addInputs("in").setInputTypes(InputType.convolutional(height, width, channels)).addLayer("bn", new BatchNormalization.Builder().useLogStd(useLogStd).build(), "in").addLayer("out", new OutputLayer.Builder().lossFunction(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nOut(numClasses).build(), "bn").setOutputs("out").build();
            ComputationGraph net = new ComputationGraph(conf);
            net.init();
            Random r = new Random(12345);
            // Order: examples, channels, height, width
            INDArray input = Nd4j.rand(new int[] { minibatchSize, channels, height, width });
            INDArray labels = Nd4j.zeros(minibatchSize, numClasses);
            for (int i = 0; i < minibatchSize; i++) {
                labels.putScalar(new int[] { i, r.nextInt(numClasses) }, 1.0);
            }
            // Mean and variance vars are not gradient checkable; mean/variance "gradient" is used to implement running mean/variance calc
            // i.e., runningMean = decay * runningMean + (1-decay) * batchMean
            // However, numerical gradient will be 0 as forward pass doesn't depend on this "parameter"
            Set<String> excludeParams = new HashSet<>(Arrays.asList("bn_mean", "bn_var"));
            boolean gradOK = GradientCheckUtil.checkGradients(new GradientCheckUtil.GraphConfig().net(net).inputs(new INDArray[] { input }).labels(new INDArray[] { labels }).excludeParams(excludeParams));
            assertTrue(gradOK);
            TestUtils.testModelSerialization(net);
        }
    }

    @Test
    @DisplayName("Test Gradient BN With CN Nand Subsampling Comp Graph")
    void testGradientBNWithCNNandSubsamplingCompGraph() {
        // Parameterized test, testing combinations of:
        // (a) activation function
        // (b) Whether to test at random initialization, or after some learning (i.e., 'characteristic mode of operation')
        // (c) Loss function (with specified output activations)
        // (d) l1 and l2 values
        Activation[] activFns = { Activation.TANH, Activation.IDENTITY };
        boolean doLearningFirst = true;
        LossFunctions.LossFunction[] lossFunctions = { LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD };
        // i.e., lossFunctions[i] used with outputActivations[i] here
        Activation[] outputActivations = { Activation.SOFTMAX };
        double[] l2vals = { 0.0, 0.1 };
        // i.e., use l2vals[j] with l1vals[j]
        double[] l1vals = { 0.0, 0.2 };
        Nd4j.getRandom().setSeed(12345);
        int minibatch = 10;
        int depth = 2;
        int hw = 5;
        int nOut = 3;
        INDArray input = Nd4j.rand(new int[] { minibatch, depth, hw, hw });
        INDArray labels = Nd4j.zeros(minibatch, nOut);
        Random r = new Random(12345);
        for (int i = 0; i < minibatch; i++) {
            labels.putScalar(i, r.nextInt(nOut), 1.0);
        }
        DataSet ds = new DataSet(input, labels);
        for (boolean useLogStd : new boolean[] { true, false }) {
            for (Activation afn : activFns) {
                for (int i = 0; i < lossFunctions.length; i++) {
                    for (int j = 0; j < l2vals.length; j++) {
                        LossFunctions.LossFunction lf = lossFunctions[i];
                        Activation outputActivation = outputActivations[i];
                        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder().seed(12345).dataType(DataType.DOUBLE).optimizationAlgo(OptimizationAlgorithm.LINE_GRADIENT_DESCENT).updater(new NoOp()).dist(new UniformDistribution(-2, 2)).seed(12345L).graphBuilder().addInputs("in").addLayer("0", new ConvolutionLayer.Builder(2, 2).stride(1, 1).nOut(3).activation(afn).build(), "in").addLayer("1", new BatchNormalization.Builder().useLogStd(useLogStd).build(), "0").addLayer("2", new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX).kernelSize(2, 2).stride(1, 1).build(), "1").addLayer("3", new BatchNormalization.Builder().useLogStd(useLogStd).build(), "2").addLayer("4", new ActivationLayer.Builder().activation(afn).build(), "3").addLayer("5", new OutputLayer.Builder(lf).activation(outputActivation).nOut(nOut).build(), "4").setOutputs("5").setInputTypes(InputType.convolutional(hw, hw, depth)).build();
                        ComputationGraph net = new ComputationGraph(conf);
                        net.init();
                        String name = new Object() {
                        }.getClass().getEnclosingMethod().getName();
                        if (doLearningFirst) {
                            // Run a number of iterations of learning
                            net.setInput(0, ds.getFeatures());
                            net.setLabels(ds.getLabels());
                            net.computeGradientAndScore();
                            double scoreBefore = net.score();
                            for (int k = 0; k < 20; k++) net.fit(ds);
                            net.computeGradientAndScore();
                            double scoreAfter = net.score();
                            // Can't test in 'characteristic mode of operation' if not learning
                            String msg = name + " - score did not (sufficiently) decrease during learning - activationFn=" + afn + ", lossFn=" + lf + ", outputActivation=" + outputActivation + ", doLearningFirst= " + doLearningFirst + " (before=" + scoreBefore + ", scoreAfter=" + scoreAfter + ")";
                            assertTrue(scoreAfter < 0.9 * scoreBefore,msg);
                        }
                        System.out.println(name + " - activationFn=" + afn + ", lossFn=" + lf + ", outputActivation=" + outputActivation + ", doLearningFirst=" + doLearningFirst + ", l1=" + l1vals[j] + ", l2=" + l2vals[j]);
                        // for (int k = 0; k < net.getNumLayers(); k++)
                        // System.out.println("Layer " + k + " # params: " + net.getLayer(k).numParams());
                        // Mean and variance vars are not gradient checkable; mean/variance "gradient" is used to implement running mean/variance calc
                        // i.e., runningMean = decay * runningMean + (1-decay) * batchMean
                        // However, numerical gradient will be 0 as forward pass doesn't depend on this "parameter"
                        Set<String> excludeParams = new HashSet<>(Arrays.asList("1_mean", "1_var", "3_mean", "3_var", "1_log10stdev", "3_log10stdev"));
                        boolean gradOK = GradientCheckUtil.checkGradients(new GradientCheckUtil.GraphConfig().net(net).inputs(new INDArray[] { input }).labels(new INDArray[] { labels }).excludeParams(excludeParams));
                        assertTrue(gradOK);
                        TestUtils.testModelSerialization(net);
                    }
                }
            }
        }
    }
}
