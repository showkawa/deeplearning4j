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
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.distribution.UniformDistribution;
import org.deeplearning4j.nn.conf.layers.GravesLSTM;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.recurrent.SimpleRnn;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.api.ndarray.INDArray;

import static org.junit.jupiter.api.Assertions.assertTrue;
@NativeTag
@Tag(TagNames.DL4J_OLD_API)
public class TestRecurrentWeightInit extends BaseDL4JTest {

    @Test
    public void testRWInit() {

        for (boolean rwInit : new boolean[]{false, true}) {
            for (int i = 0; i < 3; i++) {

                NeuralNetConfiguration.ListBuilder b = new NeuralNetConfiguration.Builder()
                        .weightInit(new UniformDistribution(0, 1))
                        .list();

                if(rwInit) {
                    switch (i) {
                        case 0:
                            b.layer(new LSTM.Builder().nIn(10).nOut(10)
                                    .weightInitRecurrent(new UniformDistribution(2, 3))
                                    .build());
                            break;
                        case 1:
                            b.layer(new GravesLSTM.Builder().nIn(10).nOut(10)
                                    .weightInitRecurrent(new UniformDistribution(2, 3))
                                    .build());
                            break;
                        case 2:
                            b.layer(new SimpleRnn.Builder().nIn(10).nOut(10)
                                    .weightInitRecurrent(new UniformDistribution(2, 3)).build());
                            break;
                        default:
                            throw new RuntimeException();
                    }
                } else {
                    switch (i) {
                        case 0:
                            b.layer(new LSTM.Builder().nIn(10).nOut(10).build());
                            break;
                        case 1:
                            b.layer(new GravesLSTM.Builder().nIn(10).nOut(10).build());
                            break;
                        case 2:
                            b.layer(new SimpleRnn.Builder().nIn(10).nOut(10).build());
                            break;
                        default:
                            throw new RuntimeException();
                    }
                }

                MultiLayerNetwork net = new MultiLayerNetwork(b.build());
                net.init();

                INDArray rw = net.getParam("0_RW");
                double min = rw.minNumber().doubleValue();
                double max = rw.maxNumber().doubleValue();
                if(rwInit){
                    assertTrue(min >= 2.0, String.valueOf(min));
                    assertTrue(max <= 3.0, String.valueOf(max));
                } else {
                    assertTrue(min >= 0.0, String.valueOf(min));
                    assertTrue(max <= 1.0, String.valueOf(max));
                }
            }
        }
    }

}
