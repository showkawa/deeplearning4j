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
package org.deeplearning4j.nn.modelimport.keras.layers.convolution;

import org.deeplearning4j.nn.conf.layers.convolutional.Cropping3D;
import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.nn.modelimport.keras.config.Keras1LayerConfiguration;
import org.deeplearning4j.nn.modelimport.keras.config.Keras2LayerConfiguration;
import org.deeplearning4j.nn.modelimport.keras.config.KerasLayerConfiguration;
import org.deeplearning4j.nn.modelimport.keras.layers.convolutional.KerasCropping3D;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;

/**
 * @author Max Pumperla
 */
@DisplayName("Keras Cropping 3 D Test")
@Tag(TagNames.FILE_IO)
@Tag(TagNames.KERAS)
@NativeTag
class KerasCropping3DTest extends BaseDL4JTest {

    private final String LAYER_NAME = "cropping_3D_layer";

    private final int[] CROPPING = new int[] { 2, 3, 5 };

    private Keras1LayerConfiguration conf1 = new Keras1LayerConfiguration();

    private Keras2LayerConfiguration conf2 = new Keras2LayerConfiguration();

    @Test
    @DisplayName("Test Cropping 3 D Layer")
    void testCropping3DLayer() throws Exception {
        Integer keras1 = 1;
        buildCropping3DLayer(conf1, keras1);
        Integer keras2 = 2;
        buildCropping3DLayer(conf2, keras2);
        buildCroppingSingleDim3DLayer(conf1, keras1);
        buildCroppingSingleDim3DLayer(conf2, keras2);
    }

    private void buildCropping3DLayer(KerasLayerConfiguration conf, Integer kerasVersion) throws Exception {
        Map<String, Object> layerConfig = new HashMap<>();
        layerConfig.put(conf.getLAYER_FIELD_CLASS_NAME(), conf.getLAYER_CLASS_NAME_CROPPING_3D());
        Map<String, Object> config = new HashMap<>();
        config.put(conf.getLAYER_FIELD_NAME(), LAYER_NAME);
        ArrayList padding = new ArrayList<Integer>() {

            {
                for (int i : CROPPING) add(i);
            }
        };
        config.put(conf.getLAYER_FIELD_CROPPING(), padding);
        layerConfig.put(conf.getLAYER_FIELD_CONFIG(), config);
        layerConfig.put(conf.getLAYER_FIELD_KERAS_VERSION(), kerasVersion);
        Cropping3D layer = new KerasCropping3D(layerConfig).getCropping3DLayer();
        assertEquals(LAYER_NAME, layer.getLayerName());
        assertEquals(CROPPING[0], layer.getCropping()[0]);
        assertEquals(CROPPING[0], layer.getCropping()[1]);
        assertEquals(CROPPING[1], layer.getCropping()[2]);
        assertEquals(CROPPING[1], layer.getCropping()[3]);
        assertEquals(CROPPING[2], layer.getCropping()[4]);
        assertEquals(CROPPING[2], layer.getCropping()[5]);
    }

    private void buildCroppingSingleDim3DLayer(KerasLayerConfiguration conf, Integer kerasVersion) throws Exception {
        Map<String, Object> layerConfig = new HashMap<>();
        layerConfig.put(conf.getLAYER_FIELD_CLASS_NAME(), conf.getLAYER_CLASS_NAME_CROPPING_3D());
        Map<String, Object> config = new HashMap<>();
        config.put(conf.getLAYER_FIELD_NAME(), LAYER_NAME);
        config.put(conf.getLAYER_FIELD_CROPPING(), CROPPING[0]);
        layerConfig.put(conf.getLAYER_FIELD_CONFIG(), config);
        layerConfig.put(conf.getLAYER_FIELD_KERAS_VERSION(), kerasVersion);
        Cropping3D layer = new KerasCropping3D(layerConfig).getCropping3DLayer();
        assertEquals(LAYER_NAME, layer.getLayerName());
        assertEquals(CROPPING[0], layer.getCropping()[0]);
        assertEquals(CROPPING[0], layer.getCropping()[1]);
    }
}
