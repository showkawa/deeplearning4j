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
package org.deeplearning4j.nn.conf.preprocessor;

import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.*;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Nesterovs;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 */
@DisplayName("Cnn Processor Test")
@NativeTag
@Tag(TagNames.DL4J_OLD_API)
class CNNProcessorTest extends BaseDL4JTest {

    private static int rows = 28;

    private static int cols = 28;

    private static INDArray in2D = Nd4j.create(DataType.FLOAT, 1, 784);

    private static INDArray in3D = Nd4j.create(DataType.FLOAT, 20, 784, 7);

    private static INDArray in4D = Nd4j.create(DataType.FLOAT, 20, 1, 28, 28);

    @Test
    @DisplayName("Test Feed Forward To Cnn Pre Processor")
    void testFeedForwardToCnnPreProcessor() {
        FeedForwardToCnnPreProcessor convProcessor = new FeedForwardToCnnPreProcessor(rows, cols, 1);
        INDArray check2to4 = convProcessor.preProcess(in2D, -1, LayerWorkspaceMgr.noWorkspaces());
        int val2to4 = check2to4.shape().length;
        assertTrue(val2to4 == 4);
        assertEquals(Nd4j.create(DataType.FLOAT, 1, 1, 28, 28), check2to4);
        INDArray check4to4 = convProcessor.preProcess(in4D, -1, LayerWorkspaceMgr.noWorkspaces());
        int val4to4 = check4to4.shape().length;
        assertTrue(val4to4 == 4);
        assertEquals(Nd4j.create(DataType.FLOAT, 20, 1, 28, 28), check4to4);
    }

    @Test
    @DisplayName("Test Feed Forward To Cnn Pre Processor 2")
    void testFeedForwardToCnnPreProcessor2() {
        int[] nRows = { 1, 5, 20 };
        int[] nCols = { 1, 5, 20 };
        int[] nDepth = { 1, 3 };
        int[] nMiniBatchSize = { 1, 5 };
        for (int rows : nRows) {
            for (int cols : nCols) {
                for (int d : nDepth) {
                    FeedForwardToCnnPreProcessor convProcessor = new FeedForwardToCnnPreProcessor(rows, cols, d);
                    for (int miniBatch : nMiniBatchSize) {
                        long[] ffShape = new long[] { miniBatch, rows * cols * d };
                        INDArray rand = Nd4j.rand(ffShape);
                        INDArray ffInput_c = Nd4j.create(DataType.FLOAT, ffShape, 'c');
                        INDArray ffInput_f = Nd4j.create(DataType.FLOAT, ffShape, 'f');
                        ffInput_c.assign(rand);
                        ffInput_f.assign(rand);
                        assertEquals(ffInput_c, ffInput_f);
                        // Test forward pass:
                        INDArray convAct_c = convProcessor.preProcess(ffInput_c, -1, LayerWorkspaceMgr.noWorkspaces());
                        INDArray convAct_f = convProcessor.preProcess(ffInput_f, -1, LayerWorkspaceMgr.noWorkspaces());
                        long[] convShape = { miniBatch, d, rows, cols };
                        assertArrayEquals(convShape, convAct_c.shape());
                        assertArrayEquals(convShape, convAct_f.shape());
                        assertEquals(convAct_c, convAct_f);
                        // Check values:
                        // CNN reshaping (for each example) takes a 1d vector and converts it to 3d
                        // (4d total, for minibatch data)
                        // 1d vector is assumed to be rows from channels 0 concatenated, followed by channels 1, etc
                        for (int ex = 0; ex < miniBatch; ex++) {
                            for (int r = 0; r < rows; r++) {
                                for (int c = 0; c < cols; c++) {
                                    for (int depth = 0; depth < d; depth++) {
                                        // pos in vector
                                        int origPosition = depth * (rows * cols) + r * cols + c;
                                        double vecValue = ffInput_c.getDouble(ex, origPosition);
                                        double convValue = convAct_c.getDouble(ex, depth, r, c);
                                        assertEquals(vecValue, convValue, 0.0);
                                    }
                                }
                            }
                        }
                        // Test backward pass:
                        // Idea is that backward pass should do opposite to forward pass
                        INDArray epsilon4_c = Nd4j.create(DataType.FLOAT, convShape, 'c');
                        INDArray epsilon4_f = Nd4j.create(DataType.FLOAT, convShape, 'f');
                        epsilon4_c.assign(convAct_c);
                        epsilon4_f.assign(convAct_f);
                        INDArray epsilon2_c = convProcessor.backprop(epsilon4_c, -1, LayerWorkspaceMgr.noWorkspaces());
                        INDArray epsilon2_f = convProcessor.backprop(epsilon4_f, -1, LayerWorkspaceMgr.noWorkspaces());
                        assertEquals(ffInput_c, epsilon2_c);
                        assertEquals(ffInput_c, epsilon2_f);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Test Feed Forward To Cnn Pre Processor Backprop")
    void testFeedForwardToCnnPreProcessorBackprop() {
        FeedForwardToCnnPreProcessor convProcessor = new FeedForwardToCnnPreProcessor(rows, cols, 1);
        convProcessor.preProcess(in2D, -1, LayerWorkspaceMgr.noWorkspaces());
        INDArray check2to2 = convProcessor.backprop(in2D, -1, LayerWorkspaceMgr.noWorkspaces());
        int val2to2 = check2to2.shape().length;
        assertTrue(val2to2 == 2);
        assertEquals(Nd4j.create(DataType.FLOAT, 1, 784), check2to2);
    }

    @Test
    @DisplayName("Test Cnn To Feed Forward Processor")
    void testCnnToFeedForwardProcessor() {
        CnnToFeedForwardPreProcessor convProcessor = new CnnToFeedForwardPreProcessor(rows, cols, 1);
        INDArray check2to4 = convProcessor.backprop(in2D, -1, LayerWorkspaceMgr.noWorkspaces());
        int val2to4 = check2to4.shape().length;
        assertTrue(val2to4 == 4);
        assertEquals(Nd4j.create(DataType.FLOAT, 1, 1, 28, 28), check2to4);
        INDArray check4to4 = convProcessor.backprop(in4D, -1, LayerWorkspaceMgr.noWorkspaces());
        int val4to4 = check4to4.shape().length;
        assertTrue(val4to4 == 4);
        assertEquals(Nd4j.create(DataType.FLOAT, 20, 1, 28, 28), check4to4);
    }

    @Test
    @DisplayName("Test Cnn To Feed Forward Pre Processor Backprop")
    void testCnnToFeedForwardPreProcessorBackprop() {
        CnnToFeedForwardPreProcessor convProcessor = new CnnToFeedForwardPreProcessor(rows, cols, 1);
        convProcessor.preProcess(in4D, -1, LayerWorkspaceMgr.noWorkspaces());
        INDArray check2to2 = convProcessor.preProcess(in2D, -1, LayerWorkspaceMgr.noWorkspaces());
        int val2to2 = check2to2.shape().length;
        assertTrue(val2to2 == 2);
        assertEquals(Nd4j.create(DataType.FLOAT, 1, 784), check2to2);
        INDArray check4to2 = convProcessor.preProcess(in4D, -1, LayerWorkspaceMgr.noWorkspaces());
        int val4to2 = check4to2.shape().length;
        assertTrue(val4to2 == 2);
        assertEquals(Nd4j.create(DataType.FLOAT, 20, 784), check4to2);
    }

    @Test
    @DisplayName("Test Cnn To Feed Forward Pre Processor 2")
    void testCnnToFeedForwardPreProcessor2() {
        int[] nRows = { 1, 5, 20 };
        int[] nCols = { 1, 5, 20 };
        int[] nDepth = { 1, 3 };
        int[] nMiniBatchSize = { 1, 5 };
        for (int rows : nRows) {
            for (int cols : nCols) {
                for (int d : nDepth) {
                    CnnToFeedForwardPreProcessor convProcessor = new CnnToFeedForwardPreProcessor(rows, cols, d);
                    for (int miniBatch : nMiniBatchSize) {
                        long[] convActShape = new long[] { miniBatch, d, rows, cols };
                        INDArray rand = Nd4j.rand(convActShape);
                        INDArray convInput_c = Nd4j.create(DataType.FLOAT, convActShape, 'c');
                        INDArray convInput_f = Nd4j.create(DataType.FLOAT, convActShape, 'f');
                        convInput_c.assign(rand);
                        convInput_f.assign(rand);
                        assertEquals(convInput_c, convInput_f);
                        // Test forward pass:
                        INDArray ffAct_c = convProcessor.preProcess(convInput_c, -1, LayerWorkspaceMgr.noWorkspaces());
                        INDArray ffAct_f = convProcessor.preProcess(convInput_f, -1, LayerWorkspaceMgr.noWorkspaces());
                        long[] ffActShape = { miniBatch, d * rows * cols };
                        assertArrayEquals(ffActShape, ffAct_c.shape());
                        assertArrayEquals(ffActShape, ffAct_f.shape());
                        assertEquals(ffAct_c, ffAct_f);
                        // Check values:
                        // CNN reshaping (for each example) takes a 1d vector and converts it to 3d
                        // (4d total, for minibatch data)
                        // 1d vector is assumed to be rows from channels 0 concatenated, followed by channels 1, etc
                        for (int ex = 0; ex < miniBatch; ex++) {
                            for (int r = 0; r < rows; r++) {
                                for (int c = 0; c < cols; c++) {
                                    for (int depth = 0; depth < d; depth++) {
                                        // pos in vector after reshape
                                        int vectorPosition = depth * (rows * cols) + r * cols + c;
                                        double vecValue = ffAct_c.getDouble(ex, vectorPosition);
                                        double convValue = convInput_c.getDouble(ex, depth, r, c);
                                        assertEquals(convValue, vecValue, 0.0);
                                    }
                                }
                            }
                        }
                        // Test backward pass:
                        // Idea is that backward pass should do opposite to forward pass
                        INDArray epsilon2_c = Nd4j.create(DataType.FLOAT, ffActShape, 'c');
                        INDArray epsilon2_f = Nd4j.create(DataType.FLOAT, ffActShape, 'f');
                        epsilon2_c.assign(ffAct_c);
                        epsilon2_f.assign(ffAct_c);
                        INDArray epsilon4_c = convProcessor.backprop(epsilon2_c, -1, LayerWorkspaceMgr.noWorkspaces());
                        INDArray epsilon4_f = convProcessor.backprop(epsilon2_f, -1, LayerWorkspaceMgr.noWorkspaces());
                        assertEquals(convInput_c, epsilon4_c);
                        assertEquals(convInput_c, epsilon4_f);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Test Invalid Input Shape")
    void testInvalidInputShape() {
        NeuralNetConfiguration.Builder builder = new NeuralNetConfiguration.Builder().seed(123).miniBatch(true).cacheMode(CacheMode.DEVICE).updater(new Nesterovs(0.9)).gradientNormalization(GradientNormalization.RenormalizeL2PerLayer).optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT);
        int[] kernelArray = new int[] { 3, 3 };
        int[] strideArray = new int[] { 1, 1 };
        int[] zeroPaddingArray = new int[] { 0, 0 };
        int processWidth = 4;
        // Building the DL4J network
        NeuralNetConfiguration.ListBuilder listBuilder = builder.list();
        listBuilder = listBuilder.layer(0, new ConvolutionLayer.Builder(kernelArray, strideArray, zeroPaddingArray).name("cnn1").convolutionMode(ConvolutionMode.Strict).nIn(// 2 input channels
        2).nOut(processWidth).weightInit(WeightInit.XAVIER_UNIFORM).activation(Activation.RELU).biasInit(1e-2).build());
        listBuilder = listBuilder.layer(1, new ConvolutionLayer.Builder(kernelArray, strideArray, zeroPaddingArray).name("cnn2").convolutionMode(ConvolutionMode.Strict).nOut(processWidth).weightInit(WeightInit.XAVIER_UNIFORM).activation(Activation.RELU).biasInit(1e-2).build());
        listBuilder = listBuilder.layer(2, new ConvolutionLayer.Builder(kernelArray, strideArray, zeroPaddingArray).name("cnn3").convolutionMode(ConvolutionMode.Strict).nOut(processWidth).weightInit(WeightInit.XAVIER_UNIFORM).activation(Activation.RELU).build());
        listBuilder = listBuilder.layer(3, new ConvolutionLayer.Builder(kernelArray, strideArray, zeroPaddingArray).name("cnn4").convolutionMode(ConvolutionMode.Strict).nOut(processWidth).weightInit(WeightInit.XAVIER_UNIFORM).activation(Activation.RELU).build());
        listBuilder = listBuilder.layer(4, new OutputLayer.Builder(LossFunctions.LossFunction.MSE).name("output").nOut(1).activation(Activation.TANH).build());
        MultiLayerConfiguration conf = listBuilder.setInputType(InputType.convolutional(20, 10, 2)).build();
        // For some reason, this model works
        MultiLayerNetwork niceModel = new MultiLayerNetwork(conf);
        niceModel.init();
        // Valid
        niceModel.output(Nd4j.create(DataType.FLOAT, 1, 2, 20, 10));
        try {
            niceModel.output(Nd4j.create(DataType.FLOAT, 1, 2, 10, 20));
            fail("Expected exception");
        } catch (IllegalStateException e) {
            // OK
        }
    }
}
