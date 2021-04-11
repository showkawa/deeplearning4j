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
import org.deeplearning4j.nn.modelimport.keras.preprocessors.ReshapePreprocessor;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * @author Max Pumperla
 */
@DisplayName("Keras Reshape Test")
@Tag(TagNames.FILE_IO)
@Tag(TagNames.KERAS)
@NativeTag
class KerasReshapeTest extends BaseDL4JTest {

    private Integer keras1 = 1;

    private Integer keras2 = 2;

    private Keras1LayerConfiguration conf1 = new Keras1LayerConfiguration();

    private Keras2LayerConfiguration conf2 = new Keras2LayerConfiguration();

    @Test
    @DisplayName("Test Reshape Layer")
    void testReshapeLayer() throws Exception {
        buildReshapeLayer(conf1, keras1);
        buildReshapeLayer(conf2, keras2);
    }

    @Test
    @DisplayName("Test Reshape Dynamic Minibatch")
    void testReshapeDynamicMinibatch() throws Exception {
        testDynamicMinibatches(conf1, keras1);
        testDynamicMinibatches(conf2, keras2);
    }

    private void buildReshapeLayer(KerasLayerConfiguration conf, Integer kerasVersion) throws Exception {
        int[] targetShape = new int[] { 10, 5 };
        List<Integer> targetShapeList = new ArrayList<>();
        targetShapeList.add(targetShape[0]);
        targetShapeList.add(targetShape[1]);
        ReshapePreprocessor preProcessor = getReshapePreProcessor(conf, kerasVersion, targetShapeList);
        assertEquals(preProcessor.getTargetShape()[0], targetShape[0]);
        assertEquals(preProcessor.getTargetShape()[1], targetShape[1]);
    }

    private ReshapePreprocessor getReshapePreProcessor(KerasLayerConfiguration conf, Integer kerasVersion, List<Integer> targetShapeList) throws InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {
        Map<String, Object> layerConfig = new HashMap<>();
        layerConfig.put(conf.getLAYER_FIELD_CLASS_NAME(), conf.getLAYER_CLASS_NAME_RESHAPE());
        Map<String, Object> config = new HashMap<>();
        String LAYER_FIELD_TARGET_SHAPE = "target_shape";
        config.put(LAYER_FIELD_TARGET_SHAPE, targetShapeList);
        String layerName = "reshape";
        config.put(conf.getLAYER_FIELD_NAME(), layerName);
        layerConfig.put(conf.getLAYER_FIELD_CONFIG(), config);
        layerConfig.put(conf.getLAYER_FIELD_KERAS_VERSION(), kerasVersion);
        InputType inputType = InputType.InputTypeFeedForward.feedForward(20);
        return (ReshapePreprocessor) new KerasReshape(layerConfig).getInputPreprocessor(inputType);
    }

    private void testDynamicMinibatches(KerasLayerConfiguration conf, Integer kerasVersion) throws InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {
        List<Integer> targetShape = Collections.singletonList(20);
        ReshapePreprocessor preprocessor = getReshapePreProcessor(conf, kerasVersion, targetShape);
        INDArray r1 = preprocessor.preProcess(Nd4j.zeros(10, 20), 10, LayerWorkspaceMgr.noWorkspaces());
        INDArray r2 = preprocessor.preProcess(Nd4j.zeros(5, 20), 5, LayerWorkspaceMgr.noWorkspaces());
        Assertions.assertArrayEquals(r2.shape(), new long[] { 5, 20 });
        Assertions.assertArrayEquals(r1.shape(), new long[] { 10, 20 });
    }
}
