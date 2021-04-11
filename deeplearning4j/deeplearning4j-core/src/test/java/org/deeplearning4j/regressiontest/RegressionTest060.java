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

package org.deeplearning4j.regressiontest;

import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.TestUtils;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.ConvolutionMode;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.distribution.NormalDistribution;
import org.deeplearning4j.nn.conf.dropout.Dropout;
import org.deeplearning4j.nn.conf.graph.LayerVertex;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.conf.preprocessor.CnnToFeedForwardPreProcessor;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInitDistribution;
import org.deeplearning4j.nn.weights.WeightInitRelu;
import org.deeplearning4j.nn.weights.WeightInitXavier;
import org.deeplearning4j.util.ModelSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.activations.impl.ActivationLReLU;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Nesterovs;
import org.nd4j.linalg.learning.config.RmsProp;
import org.nd4j.linalg.learning.regularization.WeightDecay;
import org.nd4j.linalg.lossfunctions.impl.LossMCXENT;
import org.nd4j.linalg.lossfunctions.impl.LossMSE;
import org.nd4j.linalg.lossfunctions.impl.LossNegativeLogLikelihood;
import org.nd4j.common.resources.Resources;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
@NativeTag
@Tag(TagNames.DL4J_OLD_API)
public class RegressionTest060 extends BaseDL4JTest {

    @Override
    public DataType getDataType(){
        return DataType.FLOAT;
    }

    @Override
    public long getTimeoutMilliseconds() {
        return 180000L;  //Most tests should be fast, but slow download may cause timeout on slow connections
    }

    @Test
    public void regressionTestMLP1() throws Exception {

        File f = Resources.asFile("regression_testing/060/060_ModelSerializer_Regression_MLP_1.zip");

        MultiLayerNetwork net = ModelSerializer.restoreMultiLayerNetwork(f, true);

        MultiLayerConfiguration conf = net.getLayerWiseConfigurations();
        assertEquals(2, conf.getConfs().size());

        DenseLayer l0 = (DenseLayer) conf.getConf(0).getLayer();
        assertEquals("relu", l0.getActivationFn().toString());
        assertEquals(3, l0.getNIn());
        assertEquals(4, l0.getNOut());
        assertEquals(new WeightInitXavier(), l0.getWeightInitFn());
        assertEquals(new Nesterovs(0.15, 0.9), l0.getIUpdater());
        assertEquals(0.15, ((Nesterovs)l0.getIUpdater()).getLearningRate(), 1e-6);

        OutputLayer l1 = (OutputLayer) conf.getConf(1).getLayer();
        assertEquals("softmax", l1.getActivationFn().toString());
        assertTrue(l1.getLossFn() instanceof LossMCXENT);
        assertEquals(4, l1.getNIn());
        assertEquals(5, l1.getNOut());
        assertEquals(new WeightInitXavier(), l1.getWeightInitFn());
        assertEquals(new Nesterovs(0.15, 0.9), l1.getIUpdater());
        assertEquals(0.9, ((Nesterovs)l1.getIUpdater()).getMomentum(), 1e-6);
        assertEquals(0.15, ((Nesterovs)l1.getIUpdater()).getLearningRate(), 1e-6);

        int numParams = (int)net.numParams();
        assertEquals(Nd4j.linspace(1, numParams, numParams, Nd4j.dataType()).reshape(1,numParams), net.params());
        int updaterSize = (int) new Nesterovs().stateSize(numParams);
        assertEquals(Nd4j.linspace(1, updaterSize, updaterSize, Nd4j.dataType()).reshape(1,numParams), net.getUpdater().getStateViewArray());
    }

    @Test
    public void regressionTestMLP2() throws Exception {

        File f = Resources.asFile("regression_testing/060/060_ModelSerializer_Regression_MLP_2.zip");

        MultiLayerNetwork net = ModelSerializer.restoreMultiLayerNetwork(f, true);

        MultiLayerConfiguration conf = net.getLayerWiseConfigurations();
        assertEquals(2, conf.getConfs().size());

        DenseLayer l0 = (DenseLayer) conf.getConf(0).getLayer();
        assertTrue(l0.getActivationFn() instanceof ActivationLReLU);
        assertEquals(3, l0.getNIn());
        assertEquals(4, l0.getNOut());
        assertEquals(new WeightInitDistribution(new NormalDistribution(0.1, 1.2)), l0.getWeightInitFn());
        assertEquals(new RmsProp(0.15, 0.96, RmsProp.DEFAULT_RMSPROP_EPSILON), l0.getIUpdater());
        assertEquals(0.15, ((RmsProp)l0.getIUpdater()).getLearningRate(), 1e-6);
        assertEquals(new Dropout(0.6), l0.getIDropout());
        assertEquals(0.1, TestUtils.getL1(l0), 1e-6);
        assertEquals(new WeightDecay(0.2, false), TestUtils.getWeightDecayReg(l0));
        assertEquals(GradientNormalization.ClipElementWiseAbsoluteValue, l0.getGradientNormalization());
        assertEquals(1.5, l0.getGradientNormalizationThreshold(), 1e-5);

        OutputLayer l1 = (OutputLayer) conf.getConf(1).getLayer();
        assertEquals("identity", l1.getActivationFn().toString());
        assertTrue(l1.getLossFn() instanceof LossMSE);
        assertEquals(4, l1.getNIn());
        assertEquals(5, l1.getNOut());
        assertEquals(new WeightInitDistribution(new NormalDistribution(0.1, 1.2)), l0.getWeightInitFn());
        assertEquals(new RmsProp(0.15, 0.96, RmsProp.DEFAULT_RMSPROP_EPSILON), l1.getIUpdater());
        assertEquals(0.15, ((RmsProp)l1.getIUpdater()).getLearningRate(), 1e-6);
        assertEquals(new Dropout(0.6), l1.getIDropout());
        assertEquals(0.1, TestUtils.getL1(l1), 1e-6);
        assertEquals(new WeightDecay(0.2,false), TestUtils.getWeightDecayReg(l1));
        assertEquals(GradientNormalization.ClipElementWiseAbsoluteValue, l1.getGradientNormalization());
        assertEquals(1.5, l1.getGradientNormalizationThreshold(), 1e-5);

        int numParams = (int)net.numParams();
        assertEquals(Nd4j.linspace(1, numParams, numParams, Nd4j.dataType()).reshape(1,numParams), net.params());
        int updaterSize = (int) new RmsProp().stateSize(numParams);
        assertEquals(Nd4j.linspace(1, updaterSize, updaterSize, Nd4j.dataType()).reshape(1,numParams), net.getUpdater().getStateViewArray());
    }

    @Test
    public void regressionTestCNN1() throws Exception {

        File f = Resources.asFile("regression_testing/060/060_ModelSerializer_Regression_CNN_1.zip");

        MultiLayerNetwork net = ModelSerializer.restoreMultiLayerNetwork(f, true);

        MultiLayerConfiguration conf = net.getLayerWiseConfigurations();
        assertEquals(3, conf.getConfs().size());

        ConvolutionLayer l0 = (ConvolutionLayer) conf.getConf(0).getLayer();
        assertEquals("tanh", l0.getActivationFn().toString());
        assertEquals(3, l0.getNIn());
        assertEquals(3, l0.getNOut());
        assertEquals(new WeightInitRelu(), l0.getWeightInitFn());
        assertEquals(new RmsProp(0.15, 0.96, RmsProp.DEFAULT_RMSPROP_EPSILON), l0.getIUpdater());
        assertEquals(0.15, ((RmsProp)l0.getIUpdater()).getLearningRate(), 1e-6);
        assertArrayEquals(new int[] {2, 2}, l0.getKernelSize());
        assertArrayEquals(new int[] {1, 1}, l0.getStride());
        assertArrayEquals(new int[] {0, 0}, l0.getPadding());
        assertEquals(ConvolutionMode.Truncate, l0.getConvolutionMode()); //Pre-0.7.0: no ConvolutionMode. Want to default to truncate here if not set

        SubsamplingLayer l1 = (SubsamplingLayer) conf.getConf(1).getLayer();
        assertArrayEquals(new int[] {2, 2}, l1.getKernelSize());
        assertArrayEquals(new int[] {1, 1}, l1.getStride());
        assertArrayEquals(new int[] {0, 0}, l1.getPadding());
        assertEquals(PoolingType.MAX, l1.getPoolingType());
        assertEquals(ConvolutionMode.Truncate, l1.getConvolutionMode()); //Pre-0.7.0: no ConvolutionMode. Want to default to truncate here if not set

        OutputLayer l2 = (OutputLayer) conf.getConf(2).getLayer();
        assertEquals("sigmoid", l2.getActivationFn().toString());
        assertTrue(l2.getLossFn() instanceof LossNegativeLogLikelihood); //TODO
        assertEquals(26 * 26 * 3, l2.getNIn());
        assertEquals(5, l2.getNOut());
        assertEquals(new WeightInitRelu(), l0.getWeightInitFn());
        assertEquals(new RmsProp(0.15, 0.96, RmsProp.DEFAULT_RMSPROP_EPSILON), l0.getIUpdater());
        assertEquals(0.15, ((RmsProp)l0.getIUpdater()).getLearningRate(), 1e-6);

        assertTrue(conf.getInputPreProcess(2) instanceof CnnToFeedForwardPreProcessor);

        int numParams = (int)net.numParams();
        assertEquals(Nd4j.linspace(1, numParams, numParams, Nd4j.dataType()).reshape(1,numParams), net.params());
        int updaterSize = (int) new RmsProp().stateSize(numParams);
        assertEquals(Nd4j.linspace(1, updaterSize, updaterSize, Nd4j.dataType()).reshape(1,numParams), net.getUpdater().getStateViewArray());
    }

    @Test
    public void regressionTestLSTM1() throws Exception {

        File f = Resources.asFile("regression_testing/060/060_ModelSerializer_Regression_LSTM_1.zip");

        MultiLayerNetwork net = ModelSerializer.restoreMultiLayerNetwork(f, true);

        MultiLayerConfiguration conf = net.getLayerWiseConfigurations();
        assertEquals(3, conf.getConfs().size());

        GravesLSTM l0 = (GravesLSTM) conf.getConf(0).getLayer();
        assertEquals("tanh", l0.getActivationFn().toString());
        assertEquals(3, l0.getNIn());
        assertEquals(4, l0.getNOut());
        assertEquals(GradientNormalization.ClipElementWiseAbsoluteValue, l0.getGradientNormalization());
        assertEquals(1.5, l0.getGradientNormalizationThreshold(), 1e-5);

        GravesBidirectionalLSTM l1 = (GravesBidirectionalLSTM) conf.getConf(1).getLayer();
        assertEquals("softsign", l1.getActivationFn().toString());
        assertEquals(4, l1.getNIn());
        assertEquals(4, l1.getNOut());
        assertEquals(GradientNormalization.ClipElementWiseAbsoluteValue, l1.getGradientNormalization());
        assertEquals(1.5, l1.getGradientNormalizationThreshold(), 1e-5);

        RnnOutputLayer l2 = (RnnOutputLayer) conf.getConf(2).getLayer();
        assertEquals(4, l2.getNIn());
        assertEquals(5, l2.getNOut());
        assertEquals("softmax", l2.getActivationFn().toString());
        assertTrue(l2.getLossFn() instanceof LossMCXENT);
    }

    @Test
    public void regressionTestCGLSTM1() throws Exception {

        File f = Resources.asFile("regression_testing/060/060_ModelSerializer_Regression_CG_LSTM_1.zip");

        ComputationGraph net = ModelSerializer.restoreComputationGraph(f, true);

        ComputationGraphConfiguration conf = net.getConfiguration();
        assertEquals(3, conf.getVertices().size());

        GravesLSTM l0 = (GravesLSTM) ((LayerVertex) conf.getVertices().get("0")).getLayerConf().getLayer();
        assertEquals("tanh", l0.getActivationFn().toString());
        assertEquals(3, l0.getNIn());
        assertEquals(4, l0.getNOut());
        assertEquals(GradientNormalization.ClipElementWiseAbsoluteValue, l0.getGradientNormalization());
        assertEquals(1.5, l0.getGradientNormalizationThreshold(), 1e-5);

        GravesBidirectionalLSTM l1 =
                        (GravesBidirectionalLSTM) ((LayerVertex) conf.getVertices().get("1")).getLayerConf().getLayer();
        assertEquals("softsign", l1.getActivationFn().toString());
        assertEquals(4, l1.getNIn());
        assertEquals(4, l1.getNOut());
        assertEquals(GradientNormalization.ClipElementWiseAbsoluteValue, l1.getGradientNormalization());
        assertEquals(1.5, l1.getGradientNormalizationThreshold(), 1e-5);

        RnnOutputLayer l2 = (RnnOutputLayer) ((LayerVertex) conf.getVertices().get("2")).getLayerConf().getLayer();
        assertEquals(4, l2.getNIn());
        assertEquals(5, l2.getNOut());
        assertEquals("softmax", l2.getActivationFn().toString());
        assertTrue(l2.getLossFn() instanceof LossMCXENT);
    }
}
