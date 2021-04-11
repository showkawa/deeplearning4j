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
import org.deeplearning4j.nn.conf.BackpropType;
import org.deeplearning4j.nn.conf.ConvolutionMode;
import org.deeplearning4j.nn.conf.graph.LayerVertex;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.conf.layers.variational.VariationalAutoencoder;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInitXavier;
import org.deeplearning4j.regressiontest.customlayer100a.CustomLayer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.activations.impl.*;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.learning.config.RmsProp;
import org.nd4j.linalg.learning.regularization.WeightDecay;
import org.nd4j.common.resources.Resources;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
@Disabled
@NativeTag
@Tag(TagNames.DL4J_OLD_API)
public class RegressionTest100b3 extends BaseDL4JTest {

    @Override
    public long getTimeoutMilliseconds() {
        return 180000L;  //Most tests should be fast, but slow download may cause timeout on slow connections
    }

    @Override
    public DataType getDataType(){
        return DataType.FLOAT;
    }

    @Test
    public void testCustomLayer() throws Exception {

        for( int i=1; i<2; i++ ) {
            String dtype = (i == 0 ? "float" : "double");
            DataType dt = (i == 0 ? DataType.FLOAT : DataType.DOUBLE);

            File f = Resources.asFile("regression_testing/100b3/CustomLayerExample_100b3_" + dtype + ".bin");
            MultiLayerNetwork.load(f, true);

            MultiLayerNetwork net = MultiLayerNetwork.load(f, true);
//            net = net.clone();

            DenseLayer l0 = (DenseLayer) net.getLayer(0).conf().getLayer();
            assertEquals(new ActivationTanH(), l0.getActivationFn());
            assertEquals(new WeightDecay(0.03, false), TestUtils.getWeightDecayReg(l0));
            assertEquals(new RmsProp(0.95), l0.getIUpdater());

            CustomLayer l1 = (CustomLayer) net.getLayer(1).conf().getLayer();
            assertEquals(new ActivationTanH(), l1.getActivationFn());
            assertEquals(new ActivationSigmoid(), l1.getSecondActivationFunction());
            assertEquals(new RmsProp(0.95), l1.getIUpdater());


            INDArray outExp;
            File f2 = Resources.asFile("regression_testing/100b3/CustomLayerExample_Output_100b3_" + dtype + ".bin");
            try (DataInputStream dis = new DataInputStream(new FileInputStream(f2))) {
                outExp = Nd4j.read(dis);
            }

            INDArray in;
            File f3 = Resources.asFile("regression_testing/100b3/CustomLayerExample_Input_100b3_" + dtype + ".bin");
            try (DataInputStream dis = new DataInputStream(new FileInputStream(f3))) {
                in = Nd4j.read(dis);
            }

            assertEquals(dt, in.dataType());
            assertEquals(dt, outExp.dataType());
            assertEquals(dt, net.params().dataType());
            assertEquals(dt, net.getFlattenedGradients().dataType());
            assertEquals(dt, net.getUpdater().getStateViewArray().dataType());

            //System.out.println(Arrays.toString(net.params().data().asFloat()));

            INDArray outAct = net.output(in);
            assertEquals(dt, outAct.dataType());

            List<INDArray> activations = net.feedForward(in);

            assertEquals(dt, net.getLayerWiseConfigurations().getDataType());
            assertEquals(dt, net.params().dataType());
            assertEquals(outExp, outAct, dtype);
        }
    }


    @Test
    public void testLSTM() throws Exception {

        File f = Resources.asFile("regression_testing/100b3/GravesLSTMCharModelingExample_100b3.bin");
        MultiLayerNetwork net = MultiLayerNetwork.load(f, true);

        LSTM l0 = (LSTM) net.getLayer(0).conf().getLayer();
        assertEquals(new ActivationTanH(), l0.getActivationFn());
        assertEquals(200, l0.getNOut());
        assertEquals(new WeightInitXavier(), l0.getWeightInitFn());
        assertEquals(new WeightDecay(0.0001, false), TestUtils.getWeightDecayReg(l0));
        assertEquals(new Adam(0.005), l0.getIUpdater());

        LSTM l1 = (LSTM) net.getLayer(1).conf().getLayer();
        assertEquals(new ActivationTanH(), l1.getActivationFn());
        assertEquals(200, l1.getNOut());
        assertEquals(new WeightInitXavier(), l1.getWeightInitFn());
        assertEquals(new WeightDecay(0.0001, false), TestUtils.getWeightDecayReg(l1));
        assertEquals(new Adam(0.005), l1.getIUpdater());

        RnnOutputLayer l2 = (RnnOutputLayer) net.getLayer(2).conf().getLayer();
        assertEquals(new ActivationSoftmax(), l2.getActivationFn());
        assertEquals(77, l2.getNOut());
        assertEquals(new WeightInitXavier(), l2.getWeightInitFn());
        assertEquals(new WeightDecay(0.0001, false), TestUtils.getWeightDecayReg(l0));
        assertEquals(new Adam(0.005), l0.getIUpdater());

        assertEquals(BackpropType.TruncatedBPTT, net.getLayerWiseConfigurations().getBackpropType());
        assertEquals(50, net.getLayerWiseConfigurations().getTbpttBackLength());
        assertEquals(50, net.getLayerWiseConfigurations().getTbpttFwdLength());

        INDArray outExp;
        File f2 = Resources.asFile("regression_testing/100b3/GravesLSTMCharModelingExample_Output_100b3.bin");
        try(DataInputStream dis = new DataInputStream(new FileInputStream(f2))){
            outExp = Nd4j.read(dis);
        }

        INDArray in;
        File f3 = Resources.asFile("regression_testing/100b3/GravesLSTMCharModelingExample_Input_100b3.bin");
        try(DataInputStream dis = new DataInputStream(new FileInputStream(f3))){
            in = Nd4j.read(dis);
        }

        INDArray outAct = net.output(in);

        assertEquals(outExp, outAct);
    }

    @Test
    public void testVae() throws Exception {

        File f = Resources.asFile("regression_testing/100b3/VaeMNISTAnomaly_100b3.bin");
        MultiLayerNetwork net = MultiLayerNetwork.load(f, true);

        VariationalAutoencoder l0 = (VariationalAutoencoder) net.getLayer(0).conf().getLayer();
        assertEquals(new ActivationLReLU(), l0.getActivationFn());
        assertEquals(32, l0.getNOut());
        assertArrayEquals(new int[]{256, 256}, l0.getEncoderLayerSizes());
        assertArrayEquals(new int[]{256, 256}, l0.getDecoderLayerSizes());
                assertEquals(new WeightInitXavier(), l0.getWeightInitFn());
        assertEquals(new WeightDecay(1e-4, false), TestUtils.getWeightDecayReg(l0));
        assertEquals(new Adam(1e-3), l0.getIUpdater());

        INDArray outExp;
        File f2 = Resources.asFile("regression_testing/100b3/VaeMNISTAnomaly_Output_100b3.bin");
        try(DataInputStream dis = new DataInputStream(new FileInputStream(f2))){
            outExp = Nd4j.read(dis);
        }

        INDArray in;
        File f3 = Resources.asFile("regression_testing/100b3/VaeMNISTAnomaly_Input_100b3.bin");
        try(DataInputStream dis = new DataInputStream(new FileInputStream(f3))){
            in = Nd4j.read(dis);
        }

        INDArray outAct = net.output(in);

        assertEquals(outExp, outAct);
    }


    @Test
    public void testYoloHouseNumber() throws Exception {

        File f = Resources.asFile("regression_testing/100b3/HouseNumberDetection_100b3.bin");
        ComputationGraph net = ComputationGraph.load(f, true);

        int nBoxes = 5;
        int nClasses = 10;

        ConvolutionLayer cl = (ConvolutionLayer)((LayerVertex)net.getConfiguration().getVertices().get("convolution2d_9")).getLayerConf().getLayer();
        assertEquals(nBoxes * (5 + nClasses), cl.getNOut());
        assertEquals(new ActivationIdentity(), cl.getActivationFn());
        assertEquals(ConvolutionMode.Same, cl.getConvolutionMode());
        assertEquals(new WeightInitXavier(), cl.getWeightInitFn());
        assertArrayEquals(new int[]{1,1}, cl.getKernelSize());
        assertArrayEquals(new int[]{1,1}, cl.getKernelSize());

        INDArray outExp;
        File f2 = Resources.asFile("regression_testing/100b3/HouseNumberDetection_Output_100b3.bin");
        try(DataInputStream dis = new DataInputStream(new FileInputStream(f2))){
            outExp = Nd4j.read(dis);
        }

        INDArray in;
        File f3 = Resources.asFile("regression_testing/100b3/HouseNumberDetection_Input_100b3.bin");
        try(DataInputStream dis = new DataInputStream(new FileInputStream(f3))){
            in = Nd4j.read(dis);
        }

        INDArray outAct = net.outputSingle(in);

        boolean eq = outExp.equalsWithEps(outAct.castTo(outExp.dataType()), 1e-3);
        assertTrue(eq);
    }
}
