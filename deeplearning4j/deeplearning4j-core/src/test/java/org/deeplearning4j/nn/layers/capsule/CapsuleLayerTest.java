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
package org.deeplearning4j.nn.layers.capsule;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.CapsuleLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("Capsule Layer Test")
@NativeTag
@Tag(TagNames.DL4J_OLD_API)
class CapsuleLayerTest extends BaseDL4JTest {

    @Override
    public DataType getDataType() {
        return DataType.FLOAT;
    }

    @Test
    @DisplayName("Test Output Type")
    void testOutputType() {
        CapsuleLayer layer = new CapsuleLayer.Builder(10, 16, 5).build();
        InputType in1 = InputType.recurrent(5, 8);
        assertEquals(InputType.recurrent(10, 16), layer.getOutputType(0, in1));
    }

    @Test
    @DisplayName("Test Input Type")
    void testInputType() {
        CapsuleLayer layer = new CapsuleLayer.Builder(10, 16, 5).build();
        InputType in1 = InputType.recurrent(5, 8);
        layer.setNIn(in1, true);
        assertEquals(5, layer.getInputCapsules());
        assertEquals(8, layer.getInputCapsuleDimensions());
    }

    @Test
    @DisplayName("Test Config")
    void testConfig() {
        CapsuleLayer layer1 = new CapsuleLayer.Builder(10, 16, 5).build();
        assertEquals(10, layer1.getCapsules());
        assertEquals(16, layer1.getCapsuleDimensions());
        assertEquals(5, layer1.getRoutings());
        assertFalse(layer1.isHasBias());
        CapsuleLayer layer2 = new CapsuleLayer.Builder(10, 16, 5).hasBias(true).build();
        assertTrue(layer2.isHasBias());
    }

    @Test
    @DisplayName("Test Layer")
    void testLayer() {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().seed(123).list().layer(new CapsuleLayer.Builder(10, 16, 3).build()).setInputType(InputType.recurrent(10, 8)).build();
        MultiLayerNetwork model = new MultiLayerNetwork(conf);
        model.init();
        INDArray emptyFeatures = Nd4j.zeros(64, 10, 8);
        long[] shape = model.output(emptyFeatures).shape();
        assertArrayEquals(new long[] { 64, 10, 16 }, shape);
    }
}
