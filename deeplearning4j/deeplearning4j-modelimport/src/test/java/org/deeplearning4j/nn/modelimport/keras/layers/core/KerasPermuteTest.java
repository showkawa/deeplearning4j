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
package org.deeplearning4j.nn.modelimport.keras.layers.core;

import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.nn.modelimport.keras.config.Keras1LayerConfiguration;
import org.deeplearning4j.nn.modelimport.keras.config.Keras2LayerConfiguration;
import org.deeplearning4j.nn.modelimport.keras.config.KerasLayerConfiguration;
import org.deeplearning4j.nn.modelimport.keras.exceptions.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.exceptions.UnsupportedKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.preprocessors.PermutePreprocessor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;

/**
 * @author Max Pumperla
 */
@DisplayName("Keras Permute Test")
@Tag(TagNames.FILE_IO)
@Tag(TagNames.KERAS)
@NativeTag
class KerasPermuteTest extends BaseDL4JTest {

    private Integer keras1 = 1;

    private Integer keras2 = 2;

    private Keras1LayerConfiguration conf1 = new Keras1LayerConfiguration();

    private Keras2LayerConfiguration conf2 = new Keras2LayerConfiguration();

    @Test
    @DisplayName("Test Permute Layer")
    void testPermuteLayer() throws Exception {
        buildPermuteLayer(conf1, keras1);
        buildPermuteLayer(conf2, keras2);
    }

    private void buildPermuteLayer(KerasLayerConfiguration conf, Integer kerasVersion) throws Exception {
        int[] permuteIndices = new int[] { 2, 1 };
        List<Integer> permuteList = new ArrayList<>();
        permuteList.add(permuteIndices[0]);
        permuteList.add(permuteIndices[1]);
        PermutePreprocessor preProcessor = getPermutePreProcessor(conf, kerasVersion, permuteList);
        assertEquals(preProcessor.getPermutationIndices()[0], permuteIndices[0]);
        assertEquals(preProcessor.getPermutationIndices()[1], permuteIndices[1]);
    }

    private PermutePreprocessor getPermutePreProcessor(KerasLayerConfiguration conf, Integer kerasVersion, List<Integer> permuteList) throws InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {
        Map<String, Object> layerConfig = new HashMap<>();
        layerConfig.put(conf.getLAYER_FIELD_CLASS_NAME(), conf.getLAYER_CLASS_NAME_RESHAPE());
        Map<String, Object> config = new HashMap<>();
        config.put("dims", permuteList);
        config.put(conf.getLAYER_FIELD_NAME(), "permute");
        layerConfig.put(conf.getLAYER_FIELD_CONFIG(), config);
        layerConfig.put(conf.getLAYER_FIELD_KERAS_VERSION(), kerasVersion);
        InputType inputType = InputType.InputTypeFeedForward.recurrent(20, 10);
        return (PermutePreprocessor) new KerasPermute(layerConfig).getInputPreprocessor(inputType);
    }
}
