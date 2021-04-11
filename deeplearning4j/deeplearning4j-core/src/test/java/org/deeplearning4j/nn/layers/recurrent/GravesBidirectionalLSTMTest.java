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
package org.deeplearning4j.nn.layers.recurrent;

import lombok.val;
import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.CacheMode;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.RNNFormat;
import org.deeplearning4j.nn.conf.distribution.UniformDistribution;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.params.GravesBidirectionalLSTMParamInitializer;
import org.deeplearning4j.nn.params.GravesLSTMParamInitializer;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.nd4j.common.primitives.Pair;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.BaseNd4jTestWithBackends;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.activations.impl.ActivationSigmoid;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.linalg.learning.config.AdaGrad;
import org.nd4j.linalg.learning.config.NoOp;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Graves Bidirectional LSTM Test")
@NativeTag
@Tag(TagNames.DL4J_OLD_API)
class GravesBidirectionalLSTMTest extends BaseDL4JTest {

    private double score = 0.0;



    public static Stream<Arguments> params() {
        List<Arguments> args = new ArrayList<>();
        for(Nd4jBackend nd4jBackend : BaseNd4jTestWithBackends.BACKENDS) {
            for(RNNFormat rnnFormat : RNNFormat.values()) {
                args.add(Arguments.of(rnnFormat,nd4jBackend));
            }
        }
        return args.stream();
    }

    @DisplayName("Test Bidirectional LSTM Graves Forward Basic")
    @MethodSource("org.deeplearning4j.nn.layers.recurrent.GravesBidirectionalLSTMTest#params")
    @ParameterizedTest
    void testBidirectionalLSTMGravesForwardBasic(RNNFormat rnnDataFormat,Nd4jBackend backend) {
        // Very basic test of forward prop. of LSTM layer with a time series.
        // Essentially make sure it doesn't throw any exceptions, and provides output in the correct shape.
        int nIn = 13;
        int nHiddenUnits = 17;
        final NeuralNetConfiguration conf = new NeuralNetConfiguration.Builder().layer(new org.deeplearning4j.nn.conf.layers.GravesBidirectionalLSTM.Builder().nIn(nIn).nOut(nHiddenUnits).dataFormat(rnnDataFormat).activation(Activation.TANH).build()).build();
        val numParams = conf.getLayer().initializer().numParams(conf);
        INDArray params = Nd4j.create(1, numParams);
        final GravesBidirectionalLSTM layer = (GravesBidirectionalLSTM) conf.getLayer().instantiate(conf, null, 0, params, true, params.dataType());
        // Data: has shape [miniBatchSize,nIn,timeSeriesLength];
        // Output/activations has shape [miniBatchsize,nHiddenUnits,timeSeriesLength];
        if (rnnDataFormat == RNNFormat.NCW) {
            final INDArray dataSingleExampleTimeLength1 = Nd4j.ones(1, nIn, 1);
            final INDArray activations1 = layer.activate(dataSingleExampleTimeLength1, false, LayerWorkspaceMgr.noWorkspaces());
            assertArrayEquals(activations1.shape(), new long[] { 1, nHiddenUnits, 1 });
            final INDArray dataMultiExampleLength1 = Nd4j.ones(10, nIn, 1);
            final INDArray activations2 = layer.activate(dataMultiExampleLength1, false, LayerWorkspaceMgr.noWorkspaces());
            assertArrayEquals(activations2.shape(), new long[] { 10, nHiddenUnits, 1 });
            final INDArray dataSingleExampleLength12 = Nd4j.ones(1, nIn, 12);
            final INDArray activations3 = layer.activate(dataSingleExampleLength12, false, LayerWorkspaceMgr.noWorkspaces());
            assertArrayEquals(activations3.shape(), new long[] { 1, nHiddenUnits, 12 });
            final INDArray dataMultiExampleLength15 = Nd4j.ones(10, nIn, 15);
            final INDArray activations4 = layer.activate(dataMultiExampleLength15, false, LayerWorkspaceMgr.noWorkspaces());
            assertArrayEquals(activations4.shape(), new long[] { 10, nHiddenUnits, 15 });
        } else {
            final INDArray dataSingleExampleTimeLength1 = Nd4j.ones(1, 1, nIn);
            final INDArray activations1 = layer.activate(dataSingleExampleTimeLength1, false, LayerWorkspaceMgr.noWorkspaces());
            assertArrayEquals(activations1.shape(), new long[] { 1, 1, nHiddenUnits });
            final INDArray dataMultiExampleLength1 = Nd4j.ones(10, 1, nIn);
            final INDArray activations2 = layer.activate(dataMultiExampleLength1, false, LayerWorkspaceMgr.noWorkspaces());
            assertArrayEquals(activations2.shape(), new long[] { 10, 1, nHiddenUnits });
            final INDArray dataSingleExampleLength12 = Nd4j.ones(1, 12, nIn);
            final INDArray activations3 = layer.activate(dataSingleExampleLength12, false, LayerWorkspaceMgr.noWorkspaces());
            assertArrayEquals(activations3.shape(), new long[] { 1, 12, nHiddenUnits });
            final INDArray dataMultiExampleLength15 = Nd4j.ones(10, 15, nIn);
            final INDArray activations4 = layer.activate(dataMultiExampleLength15, false, LayerWorkspaceMgr.noWorkspaces());
            assertArrayEquals(activations4.shape(), new long[] { 10, 15, nHiddenUnits });
        }
    }

    @DisplayName("Test Bidirectional LSTM Graves Backward Basic")
    @MethodSource("org.deeplearning4j.nn.layers.recurrent.GravesBidirectionalLSTMTest#params")
    @ParameterizedTest
    void testBidirectionalLSTMGravesBackwardBasic(RNNFormat rnnDataFormat,Nd4jBackend backend) {
        // Very basic test of backprop for mini-batch + time series
        // Essentially make sure it doesn't throw any exceptions, and provides output in the correct shape.
        testGravesBackwardBasicHelper(rnnDataFormat,13, 3, 17, 10, 7);
        // Edge case: miniBatchSize = 1
        testGravesBackwardBasicHelper(rnnDataFormat,13, 3, 17, 1, 7);
        // Edge case: timeSeriesLength = 1
        testGravesBackwardBasicHelper(rnnDataFormat,13, 3, 17, 10, 1);
        // Edge case: both miniBatchSize = 1 and timeSeriesLength = 1
        testGravesBackwardBasicHelper(rnnDataFormat,13, 3, 17, 1, 1);
    }

    private void testGravesBackwardBasicHelper(RNNFormat rnnDataFormat,int nIn, int nOut, int lstmNHiddenUnits, int miniBatchSize, int timeSeriesLength) {
        INDArray inputData = (rnnDataFormat == RNNFormat.NCW) ? Nd4j.ones(miniBatchSize, nIn, timeSeriesLength) : Nd4j.ones(miniBatchSize, timeSeriesLength, nIn);
        NeuralNetConfiguration conf = new NeuralNetConfiguration.Builder().layer(new org.deeplearning4j.nn.conf.layers.GravesBidirectionalLSTM.Builder().nIn(nIn).nOut(lstmNHiddenUnits).dataFormat(rnnDataFormat).dist(new UniformDistribution(0, 1)).activation(Activation.TANH).build()).build();
        long numParams = conf.getLayer().initializer().numParams(conf);
        INDArray params = Nd4j.create(1, numParams);
        GravesBidirectionalLSTM lstm = (GravesBidirectionalLSTM) conf.getLayer().instantiate(conf, null, 0, params, true, params.dataType());
        lstm.setBackpropGradientsViewArray(Nd4j.create(1, conf.getLayer().initializer().numParams(conf)));
        // Set input, do a forward pass:
        lstm.activate(inputData, false, LayerWorkspaceMgr.noWorkspaces());
        assertNotNull(lstm.input());
        INDArray epsilon = (rnnDataFormat == RNNFormat.NCW) ? Nd4j.ones(miniBatchSize, lstmNHiddenUnits, timeSeriesLength) : Nd4j.ones(miniBatchSize, timeSeriesLength, lstmNHiddenUnits);
        Pair<Gradient, INDArray> out = lstm.backpropGradient(epsilon, LayerWorkspaceMgr.noWorkspaces());
        Gradient outGradient = out.getFirst();
        INDArray nextEpsilon = out.getSecond();
        INDArray biasGradientF = outGradient.getGradientFor(GravesBidirectionalLSTMParamInitializer.BIAS_KEY_FORWARDS);
        INDArray inWeightGradientF = outGradient.getGradientFor(GravesBidirectionalLSTMParamInitializer.INPUT_WEIGHT_KEY_FORWARDS);
        INDArray recurrentWeightGradientF = outGradient.getGradientFor(GravesBidirectionalLSTMParamInitializer.RECURRENT_WEIGHT_KEY_FORWARDS);
        assertNotNull(biasGradientF);
        assertNotNull(inWeightGradientF);
        assertNotNull(recurrentWeightGradientF);
        INDArray biasGradientB = outGradient.getGradientFor(GravesBidirectionalLSTMParamInitializer.BIAS_KEY_BACKWARDS);
        INDArray inWeightGradientB = outGradient.getGradientFor(GravesBidirectionalLSTMParamInitializer.INPUT_WEIGHT_KEY_BACKWARDS);
        INDArray recurrentWeightGradientB = outGradient.getGradientFor(GravesBidirectionalLSTMParamInitializer.RECURRENT_WEIGHT_KEY_BACKWARDS);
        assertNotNull(biasGradientB);
        assertNotNull(inWeightGradientB);
        assertNotNull(recurrentWeightGradientB);
        assertArrayEquals(biasGradientF.shape(), new long[] { 1, 4 * lstmNHiddenUnits });
        assertArrayEquals(inWeightGradientF.shape(), new long[] { nIn, 4 * lstmNHiddenUnits });
        assertArrayEquals(recurrentWeightGradientF.shape(), new long[] { lstmNHiddenUnits, 4 * lstmNHiddenUnits + 3 });
        assertArrayEquals(biasGradientB.shape(), new long[] { 1, 4 * lstmNHiddenUnits });
        assertArrayEquals(inWeightGradientB.shape(), new long[] { nIn, 4 * lstmNHiddenUnits });
        assertArrayEquals(recurrentWeightGradientB.shape(), new long[] { lstmNHiddenUnits, 4 * lstmNHiddenUnits + 3 });
        assertNotNull(nextEpsilon);
        if (rnnDataFormat == RNNFormat.NCW) {
            assertArrayEquals(nextEpsilon.shape(), new long[] { miniBatchSize, nIn, timeSeriesLength });
        } else {
            assertArrayEquals(nextEpsilon.shape(), new long[] { miniBatchSize, timeSeriesLength, nIn });
        }
        // Check update:
        for (String s : outGradient.gradientForVariable().keySet()) {
            lstm.update(outGradient.getGradientFor(s), s);
        }
    }

    @DisplayName("Test Graves Bidirectional LSTM Forward Pass Helper")
    @ParameterizedTest
    @MethodSource("org.deeplearning4j.nn.layers.recurrent.GravesBidirectionalLSTMTest#params")
    void testGravesBidirectionalLSTMForwardPassHelper(RNNFormat rnnDataFormat,Nd4jBackend backend) throws Exception {
        // GravesBidirectionalLSTM.activateHelper() has different behaviour (due to optimizations) when forBackprop==true vs false
        // But should otherwise provide identical activations
        Nd4j.getRandom().setSeed(12345);
        final int nIn = 10;
        final int layerSize = 15;
        final int miniBatchSize = 4;
        final int timeSeriesLength = 7;
        final NeuralNetConfiguration conf = new NeuralNetConfiguration.Builder().layer(new org.deeplearning4j.nn.conf.layers.GravesBidirectionalLSTM.Builder().nIn(nIn).nOut(layerSize).dist(new UniformDistribution(0, 1)).activation(Activation.TANH).build()).build();
        long numParams = conf.getLayer().initializer().numParams(conf);
        INDArray params = Nd4j.create(1, numParams);
        final GravesBidirectionalLSTM lstm = (GravesBidirectionalLSTM) conf.getLayer().instantiate(conf, null, 0, params, true, params.dataType());
        final INDArray input = Nd4j.rand(new int[] { miniBatchSize, nIn, timeSeriesLength });
        lstm.setInput(input, LayerWorkspaceMgr.noWorkspaces());
        final INDArray fwdPassFalse = LSTMHelpers.activateHelper(lstm, lstm.conf(), new ActivationSigmoid(), lstm.input(), lstm.getParam(GravesBidirectionalLSTMParamInitializer.RECURRENT_WEIGHT_KEY_FORWARDS), lstm.getParam(GravesBidirectionalLSTMParamInitializer.INPUT_WEIGHT_KEY_FORWARDS), lstm.getParam(GravesBidirectionalLSTMParamInitializer.BIAS_KEY_FORWARDS), false, null, null, false, true, GravesBidirectionalLSTMParamInitializer.INPUT_WEIGHT_KEY_FORWARDS, null, true, null, CacheMode.NONE, LayerWorkspaceMgr.noWorkspaces(), true).fwdPassOutput;
        final INDArray[] fwdPassTrue = LSTMHelpers.activateHelper(lstm, lstm.conf(), new ActivationSigmoid(), lstm.input(), lstm.getParam(GravesBidirectionalLSTMParamInitializer.RECURRENT_WEIGHT_KEY_FORWARDS), lstm.getParam(GravesBidirectionalLSTMParamInitializer.INPUT_WEIGHT_KEY_FORWARDS), lstm.getParam(GravesBidirectionalLSTMParamInitializer.BIAS_KEY_FORWARDS), false, null, null, true, true, GravesBidirectionalLSTMParamInitializer.INPUT_WEIGHT_KEY_FORWARDS, null, true, null, CacheMode.NONE, LayerWorkspaceMgr.noWorkspaces(), true).fwdPassOutputAsArrays;
        // I have no idea what the heck this does --Ben
        for (int i = 0; i < timeSeriesLength; i++) {
            final INDArray sliceFalse = fwdPassFalse.tensorAlongDimension(i, 1, 0);
            final INDArray sliceTrue = fwdPassTrue[i];
            assertTrue(sliceFalse.equals(sliceTrue));
        }
    }

    static private void reverseColumnsInPlace(final INDArray x) {
        final long N = x.size(1);
        final INDArray x2 = x.dup();
        for (int t = 0; t < N; t++) {
            final long b = N - t - 1;
            // clone?
            x.putColumn(t, x2.getColumn(b));
        }
    }

    @DisplayName("Test Get Set Params")
    @MethodSource("org.deeplearning4j.nn.layers.recurrent.GravesBidirectionalLSTMTest#params")
    @ParameterizedTest
    void testGetSetParmas(RNNFormat rnnDataFormat,Nd4jBackend backend) {
        final int nIn = 2;
        final int layerSize = 3;
        final int miniBatchSize = 2;
        final int timeSeriesLength = 10;
        Nd4j.getRandom().setSeed(12345);
        final NeuralNetConfiguration confBidirectional = new NeuralNetConfiguration.Builder().layer(new org.deeplearning4j.nn.conf.layers.GravesBidirectionalLSTM.Builder().nIn(nIn).nOut(layerSize).dataFormat(rnnDataFormat).dist(new UniformDistribution(-0.1, 0.1)).activation(Activation.TANH).build()).build();
        long numParams = confBidirectional.getLayer().initializer().numParams(confBidirectional);
        INDArray params = Nd4j.create(1, numParams);
        final GravesBidirectionalLSTM bidirectionalLSTM = (GravesBidirectionalLSTM) confBidirectional.getLayer().instantiate(confBidirectional, null, 0, params, true, params.dataType());
        final INDArray sig = (rnnDataFormat == RNNFormat.NCW) ? Nd4j.rand(new int[] { miniBatchSize, nIn, timeSeriesLength }) : Nd4j.rand(new int[] { miniBatchSize, timeSeriesLength, nIn });
        final INDArray act1 = bidirectionalLSTM.activate(sig, false, LayerWorkspaceMgr.noWorkspaces());
        params = bidirectionalLSTM.params();
        bidirectionalLSTM.setParams(params);
        final INDArray act2 = bidirectionalLSTM.activate(sig, false, LayerWorkspaceMgr.noWorkspaces());
        assertArrayEquals(act2.data().asDouble(), act1.data().asDouble(), 1e-8);
    }

    @DisplayName("Test Simple Forwards And Backwards Activation")
    @MethodSource("org.deeplearning4j.nn.layers.recurrent.GravesBidirectionalLSTMTest#params")
    @ParameterizedTest
    void testSimpleForwardsAndBackwardsActivation(RNNFormat rnnDataFormat,Nd4jBackend backend) {
        final int nIn = 2;
        final int layerSize = 3;
        final int miniBatchSize = 1;
        final int timeSeriesLength = 5;
        Nd4j.getRandom().setSeed(12345);
        final NeuralNetConfiguration confBidirectional = new NeuralNetConfiguration.Builder().layer(new org.deeplearning4j.nn.conf.layers.GravesBidirectionalLSTM.Builder().nIn(nIn).nOut(layerSize).dataFormat(rnnDataFormat).dist(new UniformDistribution(-0.1, 0.1)).activation(Activation.TANH).updater(new NoOp()).build()).build();
        final NeuralNetConfiguration confForwards = new NeuralNetConfiguration.Builder().layer(new org.deeplearning4j.nn.conf.layers.GravesLSTM.Builder().nIn(nIn).nOut(layerSize).dataFormat(rnnDataFormat).weightInit(WeightInit.ZERO).activation(Activation.TANH).build()).build();
        long numParams = confForwards.getLayer().initializer().numParams(confForwards);
        INDArray params = Nd4j.create(1, numParams);
        long numParamsBD = confBidirectional.getLayer().initializer().numParams(confBidirectional);
        INDArray paramsBD = Nd4j.create(1, numParamsBD);
        final GravesBidirectionalLSTM bidirectionalLSTM = (GravesBidirectionalLSTM) confBidirectional.getLayer().instantiate(confBidirectional, null, 0, paramsBD, true, params.dataType());
        final GravesLSTM forwardsLSTM = (GravesLSTM) confForwards.getLayer().instantiate(confForwards, null, 0, params, true, params.dataType());
        bidirectionalLSTM.setBackpropGradientsViewArray(Nd4j.create(1, confBidirectional.getLayer().initializer().numParams(confBidirectional)));
        forwardsLSTM.setBackpropGradientsViewArray(Nd4j.create(1, confForwards.getLayer().initializer().numParams(confForwards)));
        final INDArray sig = (rnnDataFormat == RNNFormat.NCW) ? Nd4j.rand(new int[] { miniBatchSize, nIn, timeSeriesLength }) : Nd4j.rand(new int[] { miniBatchSize, timeSeriesLength, nIn });
        final INDArray sigb = sig.dup();
        if (rnnDataFormat == RNNFormat.NCW) {
            reverseColumnsInPlace(sigb.slice(0));
        } else {
            reverseColumnsInPlace(sigb.slice(0).permute(1, 0));
        }
        final INDArray recurrentWeightsF = bidirectionalLSTM.getParam(GravesBidirectionalLSTMParamInitializer.RECURRENT_WEIGHT_KEY_FORWARDS);
        final INDArray inputWeightsF = bidirectionalLSTM.getParam(GravesBidirectionalLSTMParamInitializer.INPUT_WEIGHT_KEY_FORWARDS);
        final INDArray biasWeightsF = bidirectionalLSTM.getParam(GravesBidirectionalLSTMParamInitializer.BIAS_KEY_FORWARDS);
        final INDArray recurrentWeightsF2 = forwardsLSTM.getParam(GravesLSTMParamInitializer.RECURRENT_WEIGHT_KEY);
        final INDArray inputWeightsF2 = forwardsLSTM.getParam(GravesLSTMParamInitializer.INPUT_WEIGHT_KEY);
        final INDArray biasWeightsF2 = forwardsLSTM.getParam(GravesLSTMParamInitializer.BIAS_KEY);
        // assert that the forwards part of the bidirectional layer is equal to that of the regular LSTM
        assertArrayEquals(recurrentWeightsF2.shape(), recurrentWeightsF.shape());
        assertArrayEquals(inputWeightsF2.shape(), inputWeightsF.shape());
        assertArrayEquals(biasWeightsF2.shape(), biasWeightsF.shape());
        forwardsLSTM.setParam(GravesLSTMParamInitializer.RECURRENT_WEIGHT_KEY, recurrentWeightsF);
        forwardsLSTM.setParam(GravesLSTMParamInitializer.INPUT_WEIGHT_KEY, inputWeightsF);
        forwardsLSTM.setParam(GravesLSTMParamInitializer.BIAS_KEY, biasWeightsF);
        // copy forwards weights to make the forwards activations do the same thing
        final INDArray recurrentWeightsB = bidirectionalLSTM.getParam(GravesBidirectionalLSTMParamInitializer.RECURRENT_WEIGHT_KEY_BACKWARDS);
        final INDArray inputWeightsB = bidirectionalLSTM.getParam(GravesBidirectionalLSTMParamInitializer.INPUT_WEIGHT_KEY_BACKWARDS);
        final INDArray biasWeightsB = bidirectionalLSTM.getParam(GravesBidirectionalLSTMParamInitializer.BIAS_KEY_BACKWARDS);
        // assert that the forwards and backwards are the same shapes
        assertArrayEquals(recurrentWeightsF.shape(), recurrentWeightsB.shape());
        assertArrayEquals(inputWeightsF.shape(), inputWeightsB.shape());
        assertArrayEquals(biasWeightsF.shape(), biasWeightsB.shape());
        // zero out backwards layer
        bidirectionalLSTM.setParam(GravesBidirectionalLSTMParamInitializer.RECURRENT_WEIGHT_KEY_BACKWARDS, Nd4j.zeros(recurrentWeightsB.shape()));
        bidirectionalLSTM.setParam(GravesBidirectionalLSTMParamInitializer.INPUT_WEIGHT_KEY_BACKWARDS, Nd4j.zeros(inputWeightsB.shape()));
        bidirectionalLSTM.setParam(GravesBidirectionalLSTMParamInitializer.BIAS_KEY_BACKWARDS, Nd4j.zeros(biasWeightsB.shape()));
        forwardsLSTM.setInput(sig, LayerWorkspaceMgr.noWorkspaces());
        // compare activations
        final INDArray activation1 = forwardsLSTM.activate(sig, false, LayerWorkspaceMgr.noWorkspaces()).slice(0);
        final INDArray activation2 = bidirectionalLSTM.activate(sig, false, LayerWorkspaceMgr.noWorkspaces()).slice(0);
        assertArrayEquals(activation1.data().asFloat(), activation2.data().asFloat(), 1e-5f);
        final INDArray randSig = (rnnDataFormat == RNNFormat.NCW) ? Nd4j.rand(new int[] { 1, layerSize, timeSeriesLength }) : Nd4j.rand(new int[] { 1, timeSeriesLength, layerSize });
        INDArray randSigBackwards = randSig.dup();
        if (rnnDataFormat == RNNFormat.NCW) {
            reverseColumnsInPlace(randSigBackwards.slice(0));
        } else {
            reverseColumnsInPlace(randSigBackwards.slice(0).permute(1, 0));
        }
        final Pair<Gradient, INDArray> backprop1 = forwardsLSTM.backpropGradient(randSig, LayerWorkspaceMgr.noWorkspaces());
        final Pair<Gradient, INDArray> backprop2 = bidirectionalLSTM.backpropGradient(randSig, LayerWorkspaceMgr.noWorkspaces());
        // compare gradients
        assertArrayEquals(backprop1.getFirst().getGradientFor(GravesLSTMParamInitializer.RECURRENT_WEIGHT_KEY).dup().data().asFloat(), backprop2.getFirst().getGradientFor(GravesBidirectionalLSTMParamInitializer.RECURRENT_WEIGHT_KEY_FORWARDS).dup().data().asFloat(), 1e-5f);
        assertArrayEquals(backprop1.getFirst().getGradientFor(GravesLSTMParamInitializer.INPUT_WEIGHT_KEY).dup().data().asFloat(), backprop2.getFirst().getGradientFor(GravesBidirectionalLSTMParamInitializer.INPUT_WEIGHT_KEY_FORWARDS).dup().data().asFloat(), 1e-5f);
        assertArrayEquals(backprop1.getFirst().getGradientFor(GravesLSTMParamInitializer.BIAS_KEY).dup().data().asFloat(), backprop2.getFirst().getGradientFor(GravesBidirectionalLSTMParamInitializer.BIAS_KEY_FORWARDS).dup().data().asFloat(), 1e-5f);
        // copy forwards to backwards
        bidirectionalLSTM.setParam(GravesBidirectionalLSTMParamInitializer.RECURRENT_WEIGHT_KEY_BACKWARDS, bidirectionalLSTM.getParam(GravesBidirectionalLSTMParamInitializer.RECURRENT_WEIGHT_KEY_FORWARDS));
        bidirectionalLSTM.setParam(GravesBidirectionalLSTMParamInitializer.INPUT_WEIGHT_KEY_BACKWARDS, bidirectionalLSTM.getParam(GravesBidirectionalLSTMParamInitializer.INPUT_WEIGHT_KEY_FORWARDS));
        bidirectionalLSTM.setParam(GravesBidirectionalLSTMParamInitializer.BIAS_KEY_BACKWARDS, bidirectionalLSTM.getParam(GravesBidirectionalLSTMParamInitializer.BIAS_KEY_FORWARDS));
        // zero out forwards layer
        bidirectionalLSTM.setParam(GravesBidirectionalLSTMParamInitializer.RECURRENT_WEIGHT_KEY_FORWARDS, Nd4j.zeros(recurrentWeightsB.shape()));
        bidirectionalLSTM.setParam(GravesBidirectionalLSTMParamInitializer.INPUT_WEIGHT_KEY_FORWARDS, Nd4j.zeros(inputWeightsB.shape()));
        bidirectionalLSTM.setParam(GravesBidirectionalLSTMParamInitializer.BIAS_KEY_FORWARDS, Nd4j.zeros(biasWeightsB.shape()));
        // run on reversed signal
        final INDArray activation3 = bidirectionalLSTM.activate(sigb, false, LayerWorkspaceMgr.noWorkspaces()).slice(0);
        final INDArray activation3Reverse = activation3.dup();
        if (rnnDataFormat == RNNFormat.NCW) {
            reverseColumnsInPlace(activation3Reverse);
        } else {
            reverseColumnsInPlace(activation3Reverse.permute(1, 0));
        }
        assertArrayEquals(activation3Reverse.shape(), activation1.shape());
        assertEquals(activation3Reverse, activation1);
        // test backprop now
        final INDArray refBackGradientReccurrent = backprop1.getFirst().getGradientFor(GravesLSTMParamInitializer.RECURRENT_WEIGHT_KEY);
        final INDArray refBackGradientInput = backprop1.getFirst().getGradientFor(GravesLSTMParamInitializer.INPUT_WEIGHT_KEY);
        final INDArray refBackGradientBias = backprop1.getFirst().getGradientFor(GravesLSTMParamInitializer.BIAS_KEY);
        // reverse weights only with backwards signal should yield same result as forwards weights with forwards signal
        final Pair<Gradient, INDArray> backprop3 = bidirectionalLSTM.backpropGradient(randSigBackwards, LayerWorkspaceMgr.noWorkspaces());
        final INDArray backGradientRecurrent = backprop3.getFirst().getGradientFor(GravesBidirectionalLSTMParamInitializer.RECURRENT_WEIGHT_KEY_BACKWARDS);
        final INDArray backGradientInput = backprop3.getFirst().getGradientFor(GravesBidirectionalLSTMParamInitializer.INPUT_WEIGHT_KEY_BACKWARDS);
        final INDArray backGradientBias = backprop3.getFirst().getGradientFor(GravesBidirectionalLSTMParamInitializer.BIAS_KEY_BACKWARDS);
        assertArrayEquals(refBackGradientBias.dup().data().asDouble(), backGradientBias.dup().data().asDouble(), 1e-6);
        assertArrayEquals(refBackGradientInput.dup().data().asDouble(), backGradientInput.dup().data().asDouble(), 1e-6);
        assertArrayEquals(refBackGradientReccurrent.dup().data().asDouble(), backGradientRecurrent.dup().data().asDouble(), 1e-6);
        final INDArray refEpsilon = backprop1.getSecond().dup();
        final INDArray backEpsilon = backprop3.getSecond().dup();
        if (rnnDataFormat == RNNFormat.NCW) {
            reverseColumnsInPlace(refEpsilon.slice(0));
        } else {
            reverseColumnsInPlace(refEpsilon.slice(0).permute(1, 0));
        }
        assertArrayEquals(backEpsilon.dup().data().asDouble(), refEpsilon.dup().data().asDouble(), 1e-6);
    }

    @MethodSource("org.deeplearning4j.nn.layers.recurrent.GravesBidirectionalLSTMTest#params")
    @DisplayName("Test Serialization")
    @ParameterizedTest
    void testSerialization(RNNFormat rnnDataFormat,Nd4jBackend backend) {
        final MultiLayerConfiguration conf1 = new NeuralNetConfiguration.Builder().optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).updater(new AdaGrad(0.1)).l2(0.001).seed(12345).list().layer(0, new org.deeplearning4j.nn.conf.layers.GravesBidirectionalLSTM.Builder().activation(Activation.TANH).nIn(2).nOut(2).dist(new UniformDistribution(-0.05, 0.05)).build()).layer(1, new org.deeplearning4j.nn.conf.layers.GravesBidirectionalLSTM.Builder().activation(Activation.TANH).nIn(2).nOut(2).dist(new UniformDistribution(-0.05, 0.05)).build()).layer(2, new org.deeplearning4j.nn.conf.layers.RnnOutputLayer.Builder().activation(Activation.SOFTMAX).lossFunction(LossFunctions.LossFunction.MCXENT).nIn(2).nOut(2).build()).build();
        final String json1 = conf1.toJson();
        final MultiLayerConfiguration conf2 = MultiLayerConfiguration.fromJson(json1);
        final String json2 = conf1.toJson();
        assertEquals(json1, json2);
    }

    @DisplayName("Test Gate Activation Fns Sanity Check")
    @MethodSource("org.deeplearning4j.nn.layers.recurrent.GravesBidirectionalLSTMTest#params")
    @ParameterizedTest
    void testGateActivationFnsSanityCheck(RNNFormat rnnDataFormat,Nd4jBackend backend) {
        for (String gateAfn : new String[] { "sigmoid", "hardsigmoid" }) {
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).seed(12345).list().layer(0, new org.deeplearning4j.nn.conf.layers.GravesBidirectionalLSTM.Builder().gateActivationFunction(gateAfn).activation(Activation.TANH).nIn(2).nOut(2).dataFormat(rnnDataFormat).build()).layer(1, new org.deeplearning4j.nn.conf.layers.RnnOutputLayer.Builder().lossFunction(LossFunctions.LossFunction.MSE).nIn(2).nOut(2).dataFormat(rnnDataFormat).activation(Activation.TANH).build()).build();
            MultiLayerNetwork net = new MultiLayerNetwork(conf);
            net.init();
            assertEquals(gateAfn, ((org.deeplearning4j.nn.conf.layers.GravesBidirectionalLSTM) net.getLayer(0).conf().getLayer()).getGateActivationFn().toString());
            INDArray in = Nd4j.rand(new int[] { 3, 2, 5 });
            INDArray labels = Nd4j.rand(new int[] { 3, 2, 5 });
            if (rnnDataFormat == RNNFormat.NWC) {
                in = in.permute(0, 2, 1);
                labels = labels.permute(0, 2, 1);
            }
            net.fit(in, labels);
        }
    }
}
