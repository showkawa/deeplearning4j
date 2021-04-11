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
package org.deeplearning4j.nn.layers.ocnn;

import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.datasets.iterator.impl.IrisDataSetIterator;
import org.deeplearning4j.gradientcheck.GradientCheckUtil;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.util.ModelSerializer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.activations.impl.ActivationIdentity;
import org.nd4j.linalg.activations.impl.ActivationReLU;
import org.nd4j.linalg.activations.impl.ActivationSigmoid;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerStandardize;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.learning.config.Nesterovs;
import org.nd4j.linalg.learning.config.NoOp;
import org.nd4j.linalg.schedule.ScheduleType;
import org.nd4j.linalg.schedule.StepSchedule;
import java.io.File;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("Ocnn Output Layer Test")
@NativeTag
@Tag(TagNames.DL4J_OLD_API)
@Tag(TagNames.FILE_IO)
class OCNNOutputLayerTest extends BaseDL4JTest {

    private static final boolean PRINT_RESULTS = true;

    private static final boolean RETURN_ON_FIRST_FAILURE = false;

    private static final double DEFAULT_EPS = 1e-6;

    private static final double DEFAULT_MAX_REL_ERROR = 1e-3;

    private static final double DEFAULT_MIN_ABS_ERROR = 1e-8;

    @TempDir
    public Path testDir;

    static {
        Nd4j.setDataType(DataType.DOUBLE);
    }

    @Test
    @DisplayName("Test Layer")
    void testLayer() {
        DataSetIterator dataSetIterator = getNormalizedIterator();
        boolean doLearningFirst = true;
        MultiLayerNetwork network = getGradientCheckNetwork(2);
        DataSet ds = dataSetIterator.next();
        INDArray arr = ds.getFeatures();
        network.setInput(arr);
        if (doLearningFirst) {
            // Run a number of iterations of learning
            network.setInput(arr);
            network.setListeners(new ScoreIterationListener(1));
            network.computeGradientAndScore();
            double scoreBefore = network.score();
            for (int j = 0; j < 10; j++) network.fit(ds);
            network.computeGradientAndScore();
            double scoreAfter = network.score();
            // Can't test in 'characteristic mode of operation' if not learning
            String msg = "testLayer() - score did not (sufficiently) decrease during learning - activationFn=" + "relu" + ", lossFn=" + "ocnn" + ", " + "sigmoid" + ", doLearningFirst=" + doLearningFirst + " (before=" + scoreBefore + ", scoreAfter=" + scoreAfter + ")";
            // assertTrue(msg, scoreAfter <  scoreBefore);
        }
        if (PRINT_RESULTS) {
            System.out.println("testLayer() - activationFn=" + "relu" + ", lossFn=" + "ocnn" + "sigmoid" + ", doLearningFirst=" + doLearningFirst);
            for (int j = 0; j < network.getnLayers(); j++) System.out.println("Layer " + j + " # params: " + network.getLayer(j).numParams());
        }
        boolean gradOK = GradientCheckUtil.checkGradients(network, DEFAULT_EPS, DEFAULT_MAX_REL_ERROR, DEFAULT_MIN_ABS_ERROR, PRINT_RESULTS, RETURN_ON_FIRST_FAILURE, ds.getFeatures(), ds.getLabels());
        String msg = "testLayer() - activationFn=" + "relu" + ", lossFn=" + "ocnn" + ",=" + "sigmoid" + ", doLearningFirst=" + doLearningFirst;
        assertTrue(gradOK,msg);
    }

    @Test
    @DisplayName("Test Label Probabilities")
    void testLabelProbabilities() throws Exception {
        Nd4j.getRandom().setSeed(42);
        DataSetIterator dataSetIterator = getNormalizedIterator();
        MultiLayerNetwork network = getSingleLayer();
        DataSet next = dataSetIterator.next();
        DataSet filtered = next.filterBy(new int[] { 0, 1 });
        for (int i = 0; i < 10; i++) {
            network.setEpochCount(i);
            network.getLayerWiseConfigurations().setEpochCount(i);
            network.fit(filtered);
        }
        DataSet anomalies = next.filterBy(new int[] { 2 });
        INDArray output = network.output(anomalies.getFeatures());
        INDArray normalOutput = network.output(anomalies.getFeatures(), false);
        assertEquals(output.lt(0.0).castTo(Nd4j.defaultFloatingPointType()).sumNumber().doubleValue(), normalOutput.eq(0.0).castTo(Nd4j.defaultFloatingPointType()).sumNumber().doubleValue(), 1e-1);
        // System.out.println("Labels " + anomalies.getLabels());
        // System.out.println("Anomaly output " + normalOutput);
        // System.out.println(output);
        INDArray normalProbs = network.output(filtered.getFeatures());
        INDArray outputForNormalSamples = network.output(filtered.getFeatures(), false);
        System.out.println("Normal probabilities " + normalProbs);
        System.out.println("Normal raw output " + outputForNormalSamples);
        File tmpFile = new File(testDir.toFile(), "tmp-file-" + UUID.randomUUID().toString());
        ModelSerializer.writeModel(network, tmpFile, true);
        tmpFile.deleteOnExit();
        MultiLayerNetwork multiLayerNetwork = ModelSerializer.restoreMultiLayerNetwork(tmpFile);
        assertEquals(network.params(), multiLayerNetwork.params());
        assertEquals(network.numParams(), multiLayerNetwork.numParams());
    }

    public DataSetIterator getNormalizedIterator() {
        DataSetIterator dataSetIterator = new IrisDataSetIterator(150, 150);
        NormalizerStandardize normalizerStandardize = new NormalizerStandardize();
        normalizerStandardize.fit(dataSetIterator);
        dataSetIterator.reset();
        dataSetIterator.setPreProcessor(normalizerStandardize);
        return dataSetIterator;
    }

    private MultiLayerNetwork getSingleLayer() {
        int numHidden = 2;
        MultiLayerConfiguration configuration = new NeuralNetConfiguration.Builder().seed(12345).weightInit(WeightInit.XAVIER).miniBatch(true).updater(new Adam(0.1)).list(new DenseLayer.Builder().activation(new ActivationReLU()).nIn(4).nOut(2).build(), new org.deeplearning4j.nn.conf.ocnn.OCNNOutputLayer.Builder().nIn(2).activation(new ActivationSigmoid()).initialRValue(0.1).nu(0.1).hiddenLayerSize(numHidden).build()).build();
        MultiLayerNetwork network = new MultiLayerNetwork(configuration);
        network.init();
        network.setListeners(new ScoreIterationListener(1));
        return network;
    }

    public MultiLayerNetwork getGradientCheckNetwork(int numHidden) {
        MultiLayerConfiguration configuration = new NeuralNetConfiguration.Builder().dataType(DataType.DOUBLE).seed(42).updater(new NoOp()).miniBatch(false).list(new DenseLayer.Builder().activation(new ActivationIdentity()).nIn(4).nOut(4).build(), new org.deeplearning4j.nn.conf.ocnn.OCNNOutputLayer.Builder().nIn(4).nu(0.002).activation(new ActivationSigmoid()).hiddenLayerSize(numHidden).build()).build();
        MultiLayerNetwork network = new MultiLayerNetwork(configuration);
        network.init();
        return network;
    }
}
