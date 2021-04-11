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
import org.deeplearning4j.nn.conf.layers.recurrent.SimpleRnn;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.BaseNd4jTestWithBackends;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.linalg.learning.config.NoOp;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.nd4j.linalg.indexing.NDArrayIndex.all;
import static org.nd4j.linalg.indexing.NDArrayIndex.interval;
import static org.nd4j.linalg.indexing.NDArrayIndex.point;
@NativeTag
@Tag(TagNames.DL4J_OLD_API)
public class TestSimpleRnn extends BaseDL4JTest {


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
    public void testSimpleRnn(RNNFormat rnnDataFormat, Nd4jBackend backend) {
        Nd4j.getRandom().setSeed(12345);

        int m = 3;
        int nIn = 5;
        int layerSize = 6;
        int tsLength = 7;
        INDArray in;
        if (rnnDataFormat == RNNFormat.NCW){
            in = Nd4j.rand(DataType.FLOAT, m, nIn, tsLength);
        }
        else{
            in = Nd4j.rand(DataType.FLOAT, m, tsLength, nIn);
        }


        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .updater(new NoOp())
                .weightInit(WeightInit.XAVIER)
                .activation(Activation.TANH)
                .list()
                .layer(new SimpleRnn.Builder().nIn(nIn).nOut(layerSize).dataFormat(rnnDataFormat).build())
                .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        INDArray out = net.output(in);

        INDArray w = net.getParam("0_W");
        INDArray rw = net.getParam("0_RW");
        INDArray b = net.getParam("0_b");

        INDArray outLast = null;
        for( int i=0; i<tsLength; i++ ){
            INDArray inCurrent;
            if (rnnDataFormat == RNNFormat.NCW){
                inCurrent = in.get(all(), all(), point(i));
            }
            else{
                inCurrent = in.get(all(), point(i), all());
            }

            INDArray outExpCurrent = inCurrent.mmul(w);
            if(outLast != null){
                outExpCurrent.addi(outLast.mmul(rw));
            }

            outExpCurrent.addiRowVector(b);

            Transforms.tanh(outExpCurrent, false);

            INDArray outActCurrent;
            if (rnnDataFormat == RNNFormat.NCW){
                outActCurrent = out.get(all(), all(), point(i));
            }
            else{
                outActCurrent = out.get(all(), point(i), all());
            }
            assertEquals(outExpCurrent, outActCurrent, String.valueOf(i));

            outLast = outExpCurrent;
        }


        TestUtils.testModelSerialization(net);
    }

    @ParameterizedTest
    @MethodSource("org.deeplearning4j.nn.layers.recurrent.TestRnnLayers#params")
    public void testBiasInit(RNNFormat rnnDataFormat,Nd4jBackend backend) {
        Nd4j.getRandom().setSeed(12345);
        int nIn = 5;
        int layerSize = 6;

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .updater(new NoOp())
                .weightInit(WeightInit.XAVIER)
                .activation(Activation.TANH)
                .list()
                .layer(new SimpleRnn.Builder().nIn(nIn).nOut(layerSize).dataFormat(rnnDataFormat)
                        .biasInit(100)
                        .build())
                .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        INDArray bArr = net.getParam("0_b");
        assertEquals(Nd4j.valueArrayOf(new long[]{1,layerSize}, 100.0f), bArr);
    }
}
