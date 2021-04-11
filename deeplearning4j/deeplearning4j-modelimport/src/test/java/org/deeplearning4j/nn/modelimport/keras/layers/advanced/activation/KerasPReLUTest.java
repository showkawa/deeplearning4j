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
package org.deeplearning4j.nn.modelimport.keras.layers.advanced.activation;

import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.PReLULayer;
import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.nn.modelimport.keras.config.Keras1LayerConfiguration;
import org.deeplearning4j.nn.modelimport.keras.config.Keras2LayerConfiguration;
import org.deeplearning4j.nn.modelimport.keras.config.KerasLayerConfiguration;
import org.deeplearning4j.nn.modelimport.keras.layers.advanced.activations.KerasPReLU;
import org.deeplearning4j.nn.weights.IWeightInit;
import org.deeplearning4j.nn.weights.WeightInitXavier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;

/**
 * @author Max Pumperla
 */
@DisplayName("Keras P Re LU Test")
@Tag(TagNames.FILE_IO)
@Tag(TagNames.KERAS)
@NativeTag
class KerasPReLUTest extends BaseDL4JTest {

    private Keras1LayerConfiguration conf1 = new Keras1LayerConfiguration();

    private Keras2LayerConfiguration conf2 = new Keras2LayerConfiguration();

    private final String INIT_KERAS = "glorot_normal";

    private final IWeightInit INIT_DL4J = new WeightInitXavier();

    @Test
    @DisplayName("Test P Re LU Layer")
    void testPReLULayer() throws Exception {
        Integer keras1 = 1;
        buildPReLULayer(conf1, keras1);
        Integer keras2 = 2;
        buildPReLULayer(conf2, keras2);
    }

    private void buildPReLULayer(KerasLayerConfiguration conf, Integer kerasVersion) throws Exception {
        Map<String, Object> layerConfig = new HashMap<>();
        layerConfig.put(conf.getLAYER_FIELD_CLASS_NAME(), conf.getLAYER_CLASS_NAME_LEAKY_RELU());
        Map<String, Object> config = new HashMap<>();
        String layerName = "prelu";
        config.put(conf.getLAYER_FIELD_NAME(), layerName);
        layerConfig.put(conf.getLAYER_FIELD_CONFIG(), config);
        layerConfig.put(conf.getLAYER_FIELD_KERAS_VERSION(), kerasVersion);
        if (kerasVersion == 1) {
            config.put("alpha_initializer", INIT_KERAS);
        } else {
            Map<String, Object> init = new HashMap<>();
            init.put("class_name", conf.getINIT_GLOROT_NORMAL());
            config.put("alpha_initializer", init);
        }
        KerasPReLU kerasPReLU = new KerasPReLU(layerConfig);
        kerasPReLU.getOutputType(InputType.convolutional(5, 4, 3));
        PReLULayer layer = kerasPReLU.getPReLULayer();
        assertArrayEquals(layer.getInputShape(), new long[] { 3, 5, 4 });
        assertEquals(INIT_DL4J, layer.getWeightInitFn());
        assertEquals(layerName, layer.getLayerName());
    }
}
