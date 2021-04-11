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

import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.TestUtils;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.RNNFormat;
import org.deeplearning4j.nn.conf.dropout.TestDropout;
import org.deeplearning4j.nn.conf.layers.GravesLSTM;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.Layer;
import org.deeplearning4j.nn.conf.layers.RnnLossLayer;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.conf.layers.recurrent.SimpleRnn;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.enums.RnnDataFormat;
import org.nd4j.linalg.BaseNd4jTestWithBackends;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.linalg.learning.config.NoOp;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.common.primitives.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
@NativeTag
@Tag(TagNames.DL4J_OLD_API)
public class TestRnnLayers extends BaseDL4JTest {


    public static Stream<Arguments> params() {
        List<Arguments> args = new ArrayList<>();
        for(Nd4jBackend nd4jBackend : BaseNd4jTestWithBackends.BACKENDS) {
            for(RNNFormat rnnFormat : RNNFormat.values()) {
                args.add(Arguments.of(rnnFormat,nd4jBackend));
            }
        }
        return args.stream();
    }

    @ParameterizedTest
    @MethodSource("org.deeplearning4j.nn.layers.recurrent.TestRnnLayers#params")
    public void testTimeStepIs3Dimensional(RNNFormat rnnDataFormat,Nd4jBackend backend) {

        int nIn = 12;
        int nOut = 3;

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .updater(new NoOp())
                .weightInit(WeightInit.XAVIER)
                .list()
                .layer(new SimpleRnn.Builder().nIn(nIn).nOut(3).dataFormat(rnnDataFormat).build())
                .layer(new LSTM.Builder().nIn(3).nOut(5).dataFormat(rnnDataFormat).build())
                .layer(new RnnOutputLayer.Builder().nOut(nOut).activation(Activation.SOFTMAX).build())
                .build();


        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        org.deeplearning4j.nn.layers.recurrent.SimpleRnn simpleRnn =
                (org.deeplearning4j.nn.layers.recurrent.SimpleRnn) net.getLayer(0);

        INDArray rnnInput3d = (rnnDataFormat==RNNFormat.NCW)?Nd4j.create(10,12, 1):Nd4j.create(10, 1, 12);
        INDArray simpleOut = simpleRnn.rnnTimeStep(rnnInput3d, LayerWorkspaceMgr.noWorkspaces());
        assertTrue(Arrays.equals(simpleOut.shape(), (rnnDataFormat==RNNFormat.NCW)?new long[] {10, 3, 1}:new long[]{10, 1, 3}));

        INDArray rnnInput2d = Nd4j.create(10, 12);
        try {
            simpleRnn.rnnTimeStep(rnnInput2d, LayerWorkspaceMgr.noWorkspaces());
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().equals("3D input expected to RNN layer expected, got 2"));
        }

        org.deeplearning4j.nn.layers.recurrent.LSTM lstm =
                (org.deeplearning4j.nn.layers.recurrent.LSTM) net.getLayer(1);

        INDArray lstmInput3d = (rnnDataFormat==RNNFormat.NCW)?Nd4j.create(10, 3, 1):Nd4j.create(10, 1, 3);
        INDArray lstmOut = lstm.rnnTimeStep(lstmInput3d, LayerWorkspaceMgr.noWorkspaces());
        assertTrue(Arrays.equals(lstmOut.shape(), (rnnDataFormat==RNNFormat.NCW)?new long[] {10, 5, 1}:new long[]{10, 1, 5}));

        INDArray lstmInput2d = Nd4j.create(10, 3);
        try {
            lstm.rnnTimeStep(lstmInput2d, LayerWorkspaceMgr.noWorkspaces());
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().equals("3D input expected to RNN layer expected, got 2"));
        }


    }

    @ParameterizedTest
    @MethodSource("org.deeplearning4j.nn.layers.recurrent.TestRnnLayers#params")
    public void testDropoutRecurrentLayers(RNNFormat rnnDataFormat,Nd4jBackend backend) {
        Nd4j.getRandom().setSeed(12345);

        String[] layerTypes = new String[]{"graves", "lstm", "simple"};

        for(String s : layerTypes){

            Layer layer;
            Layer layerD;
            Layer layerD2;
            TestDropout.CustomDropout cd = new TestDropout.CustomDropout();
            switch (s){
                case "graves":
                    layer = new GravesLSTM.Builder().activation(Activation.TANH).nIn(10).nOut(10).dataFormat(rnnDataFormat).build();
                    layerD = new GravesLSTM.Builder().dropOut(0.5).activation(Activation.TANH).nIn(10).nOut(10).dataFormat(rnnDataFormat).build();
                    layerD2 = new GravesLSTM.Builder().dropOut(cd).activation(Activation.TANH).nIn(10).nOut(10).dataFormat(rnnDataFormat).build();
                    break;
                case "lstm":
                    layer = new LSTM.Builder().activation(Activation.TANH).nIn(10).nOut(10).dataFormat(rnnDataFormat).build();
                    layerD = new LSTM.Builder().dropOut(0.5).activation(Activation.TANH).nIn(10).nOut(10).dataFormat(rnnDataFormat).build();
                    layerD2 = new LSTM.Builder().dropOut(cd).activation(Activation.TANH).nIn(10).nOut(10).dataFormat(rnnDataFormat).build();
                    break;
                case "simple":
                    layer = new SimpleRnn.Builder().activation(Activation.TANH).nIn(10).nOut(10).dataFormat(rnnDataFormat).build();
                    layerD = new SimpleRnn.Builder().dropOut(0.5).activation(Activation.TANH).nIn(10).nOut(10).dataFormat(rnnDataFormat).build();
                    layerD2 = new SimpleRnn.Builder().dropOut(cd).activation(Activation.TANH).nIn(10).nOut(10).dataFormat(rnnDataFormat).build();
                    break;
                default:
                    throw new RuntimeException(s);
            }

            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .seed(12345)
                    .list()
                    .layer(layer)
                    .layer(new RnnOutputLayer.Builder().activation(Activation.TANH).lossFunction(LossFunctions.LossFunction.MSE).nIn(10).nOut(10).dataFormat(rnnDataFormat).build())
                    .build();

            MultiLayerConfiguration confD = new NeuralNetConfiguration.Builder()
                    .seed(12345)
                    .list()
                    .layer(layerD)
                    .layer(new RnnOutputLayer.Builder().activation(Activation.TANH).lossFunction(LossFunctions.LossFunction.MSE).nIn(10).nOut(10).dataFormat(rnnDataFormat).build())
                    .build();

            MultiLayerConfiguration confD2 = new NeuralNetConfiguration.Builder()
                    .seed(12345)
                    .list()
                    .layer(layerD2)
                    .layer(new RnnOutputLayer.Builder().activation(Activation.TANH).lossFunction(LossFunctions.LossFunction.MSE).nIn(10).nOut(10).dataFormat(rnnDataFormat).build())
                    .build();

            MultiLayerNetwork net = new MultiLayerNetwork(conf);
            net.init();

            MultiLayerNetwork netD = new MultiLayerNetwork(confD);
            netD.init();

            MultiLayerNetwork netD2 = new MultiLayerNetwork(confD2);
            netD2.init();

            assertEquals(net.params(), netD.params(), s);
            assertEquals(net.params(), netD2.params(), s);

            INDArray f = Nd4j.rand(DataType.FLOAT, new int[]{3, 10, 10});

            //Output: test mode -> no dropout
            INDArray out1 = net.output(f);
            INDArray out1D = netD.output(f);
            INDArray out1D2 = netD2.output(f);
            assertEquals(out1, out1D, s);
            assertEquals(out1, out1D2, s);


            INDArray out2 = net.output(f, true);
            INDArray out2D = netD.output(f, true);
            assertNotEquals(out2, out2D, s);

            INDArray l = TestUtils.randomOneHotTimeSeries(3, 10, 10, 12345);
            net.fit(f.dup(), l);
            netD.fit(f.dup(), l);
            assertNotEquals(net.params(), netD.params(), s);

            netD2.fit(f.dup(), l);
            netD2.fit(f.dup(), l);
            netD2.fit(f.dup(), l);


            List<Pair<Integer,Integer>> expected = Arrays.asList(
                    new Pair<>(0, 0),
                    new Pair<>(1, 0),
                    new Pair<>(2, 0));

            assertEquals(expected, cd.getAllCalls(), s);
        }
    }

    @ParameterizedTest
    @MethodSource("org.deeplearning4j.nn.layers.recurrent.TestRnnLayers#params")
    public void testMismatchedInputLabelLength(RNNFormat rnnDataFormat,Nd4jBackend backend){

        for( int i = 0; i < 2; i++) {

            NeuralNetConfiguration.ListBuilder lb = new NeuralNetConfiguration.Builder()

                    .list()
                    .layer(new SimpleRnn.Builder().nIn(5).nOut(5).dataFormat(rnnDataFormat).build());

            switch (i){
                case 0:
                    lb.layer(new RnnOutputLayer.Builder().activation(Activation.SOFTMAX).lossFunction(LossFunctions.LossFunction.MCXENT).nIn(5).nOut(5).dataFormat(rnnDataFormat).build());
                    break;
                case 1:
                    lb.layer(new RnnLossLayer.Builder().activation(Activation.SOFTMAX).lossFunction(LossFunctions.LossFunction.MCXENT).dataFormat(rnnDataFormat).build());
                    break;
                default:
                    throw new RuntimeException();
            }

            MultiLayerConfiguration conf = lb.build();
            MultiLayerNetwork net = new MultiLayerNetwork(conf);
            net.init();

            INDArray in = Nd4j.rand(DataType.FLOAT, 3, 5, 5);
            INDArray l = TestUtils.randomOneHotTimeSeries(rnnDataFormat, 3, 5, 10, new Random(12345));
            try{
                net.fit(in,l);
            } catch (Throwable t){
                String msg = t.getMessage();
                if(msg == null)
                    t.printStackTrace();
                System.out.println(i);
                assertTrue(msg != null && msg.contains("sequence length") && msg.contains("input") && msg.contains("label"), msg);
            }

        }


    }
}
