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
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.conf.layers.recurrent.Bidirectional;
import org.deeplearning4j.nn.conf.layers.recurrent.LastTimeStep;
import org.deeplearning4j.nn.conf.layers.recurrent.SimpleRnn;
import org.deeplearning4j.nn.conf.layers.recurrent.TimeDistributed;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.NoOp;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag(TagNames.NDARRAY_ETL)
@Tag(TagNames.TRAINING)
@Tag(TagNames.DL4J_OLD_API)
@NativeTag
public class RnnGradientChecks extends BaseDL4JTest {

    private static final boolean PRINT_RESULTS = true;

    static {
        Nd4j.setDataType(DataType.DOUBLE);
    }

    @Override
    public long getTimeoutMilliseconds() {
        return 90000L;
    }

    @Test
    @Disabled("AB 2019/06/24 - Ignored to get to all passing baseline to prevent regressions via CI - see issue #7912")
    public void testBidirectionalWrapper() {

        int nIn = 3;
        int nOut = 5;
        int tsLength = 4;

        Bidirectional.Mode[] modes = new Bidirectional.Mode[]{Bidirectional.Mode.CONCAT, Bidirectional.Mode.ADD,
                Bidirectional.Mode.AVERAGE, Bidirectional.Mode.MUL};

        Random r = new Random(12345);
        for (int mb : new int[]{1, 3}) {
            for (boolean inputMask : new boolean[]{false, true}) {
                for (boolean simple : new boolean[]{false, true}) {
                    for(boolean hasLayerNorm: new boolean[]{true, false}) {
                        if(!simple && hasLayerNorm)
                            continue;

                        INDArray in = Nd4j.rand(new int[]{mb, nIn, tsLength});
                        INDArray labels = Nd4j.create(mb, nOut, tsLength);
                        for (int i = 0; i < mb; i++) {
                            for (int j = 0; j < tsLength; j++) {
                                labels.putScalar(i, r.nextInt(nOut), j, 1.0);
                            }
                        }
                        String maskType = (inputMask ? "inputMask" : "none");

                        INDArray inMask = null;
                        if (inputMask) {
                            inMask = Nd4j.ones(mb, tsLength);
                            for (int i = 0; i < mb; i++) {
                                int firstMaskedStep = tsLength - 1 - i;
                                if (firstMaskedStep == 0) {
                                    firstMaskedStep = tsLength;
                                }
                                for (int j = firstMaskedStep; j < tsLength; j++) {
                                    inMask.putScalar(i, j, 1.0);
                                }
                            }
                        }

                        for (Bidirectional.Mode m : modes) {
                            //Skip 3 of 4 test cases: from 64 to 16, which still should be good coverage
                            //Note RNG seed - deterministic run-to-run
                            if(r.nextInt(4) != 0)
                                continue;

                            String name = "mb=" + mb + ", maskType=" + maskType + ", mode=" + m + ", hasLayerNorm=" + hasLayerNorm + ", rnnType="
                                    + (simple ? "SimpleRnn" : "LSTM");

                            System.out.println("Starting test: " + name);

                            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                                    .dataType(DataType.DOUBLE)
                                    .updater(new NoOp())
                                    .weightInit(WeightInit.XAVIER)
                                    .list()
                                    .layer(new LSTM.Builder().nIn(nIn).nOut(3).build())
                                    .layer(new Bidirectional(m,
                                            (simple ?
                                                    new SimpleRnn.Builder().nIn(3).nOut(3).hasLayerNorm(hasLayerNorm).build() :
                                                    new LSTM.Builder().nIn(3).nOut(3).build())))
                                    .layer(new RnnOutputLayer.Builder().nOut(nOut).activation(Activation.SOFTMAX).build())
                                    .build();


                            MultiLayerNetwork net = new MultiLayerNetwork(conf);
                            net.init();


                            boolean gradOK = GradientCheckUtil.checkGradients(new GradientCheckUtil.MLNConfig().net(net).input(in)
                                    .labels(labels).inputMask(inMask));
                            assertTrue(gradOK);


                            TestUtils.testModelSerialization(net);
                        }
                    }
                }
            }
        }
    }

    @Test
    @Disabled("AB 2019/06/24 - Ignored to get to all passing baseline to prevent regressions via CI - see issue #7912")
    public void testSimpleRnn() {
        int nOut = 5;

        double[] l1s = new double[]{0.0, 0.4};
        double[] l2s = new double[]{0.0, 0.6};

        Random r = new Random(12345);
        for (int mb : new int[]{1, 3}) {
            for (int tsLength : new int[]{1, 4}) {
                for (int nIn : new int[]{3, 1}) {
                    for (int layerSize : new int[]{4, 1}) {
                        for (boolean inputMask : new boolean[]{false, true}) {
                            for (boolean hasLayerNorm : new boolean[]{true, false}) {
                                for (int l = 0; l < l1s.length; l++) {
                                    //Only run 1 of 5 (on average - note RNG seed for deterministic testing) - 25 of 128 test cases (to minimize test time)
                                    if(r.nextInt(5) != 0)
                                        continue;

                                    INDArray in = Nd4j.rand(new int[]{mb, nIn, tsLength});
                                    INDArray labels = Nd4j.create(mb, nOut, tsLength);
                                    for (int i = 0; i < mb; i++) {
                                        for (int j = 0; j < tsLength; j++) {
                                            labels.putScalar(i, r.nextInt(nOut), j, 1.0);
                                        }
                                    }
                                    String maskType = (inputMask ? "inputMask" : "none");

                                    INDArray inMask = null;
                                    if (inputMask) {
                                        inMask = Nd4j.ones(mb, tsLength);
                                        for (int i = 0; i < mb; i++) {
                                            int firstMaskedStep = tsLength - 1 - i;
                                            if (firstMaskedStep == 0) {
                                                firstMaskedStep = tsLength;
                                            }
                                            for (int j = firstMaskedStep; j < tsLength; j++) {
                                                inMask.putScalar(i, j, 0.0);
                                            }
                                        }
                                    }

                                    String name = "testSimpleRnn() - mb=" + mb + ", tsLength = " + tsLength + ", maskType=" +
                                            maskType + ", l1=" + l1s[l] + ", l2=" + l2s[l] + ", hasLayerNorm=" + hasLayerNorm;

                                    System.out.println("Starting test: " + name);

                                    MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                                            .dataType(DataType.DOUBLE)
                                            .updater(new NoOp())
                                            .weightInit(WeightInit.XAVIER)
                                            .activation(Activation.TANH)
                                            .l1(l1s[l])
                                            .l2(l2s[l])
                                            .list()
                                            .layer(new SimpleRnn.Builder().nIn(nIn).nOut(layerSize).hasLayerNorm(hasLayerNorm).build())
                                            .layer(new SimpleRnn.Builder().nIn(layerSize).nOut(layerSize).hasLayerNorm(hasLayerNorm).build())
                                            .layer(new RnnOutputLayer.Builder().nIn(layerSize).nOut(nOut)
                                                    .activation(Activation.SOFTMAX).lossFunction(LossFunctions.LossFunction.MCXENT)
                                                    .build())
                                            .build();

                                    MultiLayerNetwork net = new MultiLayerNetwork(conf);
                                    net.init();


                                    boolean gradOK = GradientCheckUtil.checkGradients(new GradientCheckUtil.MLNConfig().net(net).input(in)
                                            .labels(labels).inputMask(inMask));
                                    assertTrue(gradOK);
                                    TestUtils.testModelSerialization(net);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    @Disabled("AB 2019/06/24 - Ignored to get to all passing baseline to prevent regressions via CI - see issue #7912")
    public void testLastTimeStepLayer(){
        int nIn = 3;
        int nOut = 5;
        int tsLength = 4;
        int layerSize = 8;

        Random r = new Random(12345);
        for (int mb : new int[]{1, 3}) {
            for (boolean inputMask : new boolean[]{false, true}) {
                for (boolean simple : new boolean[]{false, true}) {
                    for (boolean hasLayerNorm : new boolean[]{true, false}) {
                        if(!simple && hasLayerNorm)
                            continue;


                        INDArray in = Nd4j.rand(new int[]{mb, nIn, tsLength});
                        INDArray labels = Nd4j.create(mb, nOut);
                        for (int i = 0; i < mb; i++) {
                            labels.putScalar(i, r.nextInt(nOut), 1.0);
                        }
                        String maskType = (inputMask ? "inputMask" : "none");

                        INDArray inMask = null;
                        if (inputMask) {
                            inMask = Nd4j.ones(mb, tsLength);
                            for (int i = 0; i < mb; i++) {
                                int firstMaskedStep = tsLength - 1 - i;
                                if (firstMaskedStep == 0) {
                                    firstMaskedStep = tsLength;
                                }
                                for (int j = firstMaskedStep; j < tsLength; j++) {
                                    inMask.putScalar(i, j, 0.0);
                                }
                            }
                        }

                        String name = "testLastTimeStepLayer() - mb=" + mb + ", tsLength = " + tsLength + ", maskType=" + maskType
                                + ", hasLayerNorm=" + hasLayerNorm + ", rnnType=" + (simple ? "SimpleRnn" : "LSTM");
                        if (PRINT_RESULTS) {
                            System.out.println("Starting test: " + name);
                        }

                        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                                .dataType(DataType.DOUBLE)
                                .activation(Activation.TANH)
                                .updater(new NoOp())
                                .weightInit(WeightInit.XAVIER)
                                .list()
                                .layer(simple ? new SimpleRnn.Builder().nOut(layerSize).hasLayerNorm(hasLayerNorm).build() :
                                        new LSTM.Builder().nOut(layerSize).build())
                                .layer(new LastTimeStep(simple ? new SimpleRnn.Builder().nOut(layerSize).hasLayerNorm(hasLayerNorm).build() :
                                        new LSTM.Builder().nOut(layerSize).build()))
                                .layer(new OutputLayer.Builder().nOut(nOut).activation(Activation.SOFTMAX)
                                        .lossFunction(LossFunctions.LossFunction.MCXENT).build())
                                .setInputType(InputType.recurrent(nIn))
                                .build();

                        MultiLayerNetwork net = new MultiLayerNetwork(conf);
                        net.init();

                        boolean gradOK = GradientCheckUtil.checkGradients(new GradientCheckUtil.MLNConfig().net(net).input(in)
                                .labels(labels).inputMask(inMask).subset(true).maxPerParam(16));
                        assertTrue(gradOK, name);
                        TestUtils.testModelSerialization(net);
                    }
                }
            }
        }
    }




    @Test
    public void testTimeDistributedDense() {
        int nIn = 3;
        int nOut = 5;
        int tsLength = 4;
        int layerSize = 8;

        Random r = new Random(12345);
        for (int mb : new int[]{1, 3}) {
            for (boolean inputMask : new boolean[]{false, true}) {


                INDArray in = Nd4j.rand(new int[]{mb, nIn, tsLength});
                INDArray labels = TestUtils.randomOneHotTimeSeries(mb, nOut, tsLength);
                String maskType = (inputMask ? "inputMask" : "none");

                INDArray inMask = null;
                if (inputMask) {
                    inMask = Nd4j.ones(mb, tsLength);
                    for (int i = 0; i < mb; i++) {
                        int firstMaskedStep = tsLength - 1 - i;
                        if (firstMaskedStep == 0) {
                            firstMaskedStep = tsLength;
                        }
                        for (int j = firstMaskedStep; j < tsLength; j++) {
                            inMask.putScalar(i, j, 0.0);
                        }
                    }
                }

                String name = "testLastTimeStepLayer() - mb=" + mb + ", tsLength = " + tsLength + ", maskType=" + maskType;
                if (PRINT_RESULTS) {
                    System.out.println("Starting test: " + name);
                }

                MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                        .dataType(DataType.DOUBLE)
                        .activation(Activation.TANH)
                        .updater(new NoOp())
                        .weightInit(WeightInit.XAVIER)
                        .list()
                        .layer(new LSTM.Builder().nOut(layerSize).build())
                        .layer(new TimeDistributed(new DenseLayer.Builder().nOut(layerSize).activation(Activation.SOFTMAX).build()))
                        .layer(new RnnOutputLayer.Builder().nOut(nOut).activation(Activation.SOFTMAX)
                                .lossFunction(LossFunctions.LossFunction.MCXENT).build())
                        .setInputType(InputType.recurrent(nIn))
                        .build();

                MultiLayerNetwork net = new MultiLayerNetwork(conf);
                net.init();

                boolean gradOK = GradientCheckUtil.checkGradients(new GradientCheckUtil.MLNConfig().net(net).input(in)
                        .labels(labels).inputMask(inMask).subset(true).maxPerParam(16));
                assertTrue(gradOK, name);
                TestUtils.testModelSerialization(net);
            }
        }
    }
}
