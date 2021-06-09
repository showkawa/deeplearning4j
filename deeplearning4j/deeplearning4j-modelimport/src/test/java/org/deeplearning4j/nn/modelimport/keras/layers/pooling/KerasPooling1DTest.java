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
package org.deeplearning4j.nn.modelimport.keras.layers.pooling;

import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.conf.CNN2DFormat;
import org.deeplearning4j.nn.conf.ConvolutionMode;
import org.deeplearning4j.nn.conf.layers.PoolingType;
import org.deeplearning4j.nn.conf.layers.Subsampling1DLayer;
import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.graph.vertex.GraphVertex;
import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.modelimport.keras.config.Keras1LayerConfiguration;
import org.deeplearning4j.nn.modelimport.keras.config.Keras2LayerConfiguration;
import org.deeplearning4j.nn.modelimport.keras.config.KerasLayerConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nd4j.common.resources.Resources;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;

/**
 * @author Max Pumperla
 */
@DisplayName("Keras Pooling 1 D Test")
@Tag(TagNames.FILE_IO)
@Tag(TagNames.KERAS)
@NativeTag
class KerasPooling1DTest extends BaseDL4JTest {

    private final String LAYER_NAME = "test_layer";

    private final int[] KERNEL_SIZE = new int[] { 2 };

    private final int[] STRIDE = new int[] { 4 };

    private final PoolingType POOLING_TYPE = PoolingType.MAX;

    private final String BORDER_MODE_VALID = "valid";

    private final int[] VALID_PADDING = new int[] { 0, 0 };

    private Integer keras1 = 1;

    private Integer keras2 = 2;

    private Keras1LayerConfiguration conf1 = new Keras1LayerConfiguration();

    private Keras2LayerConfiguration conf2 = new Keras2LayerConfiguration();

    @Test
    @DisplayName("Test Pooling 1 D Layer")
    void testPooling1DLayer() throws Exception {
        buildPooling1DLayer(conf1, keras1);
        buildPooling1DLayer(conf2, keras2);
    }

    private void buildPooling1DLayer(KerasLayerConfiguration conf, Integer kerasVersion) throws Exception {
        Map<String, Object> layerConfig = new HashMap<>();
        layerConfig.put(conf.getLAYER_FIELD_CLASS_NAME(), conf.getLAYER_CLASS_NAME_MAX_POOLING_1D());
        Map<String, Object> config = new HashMap<>();
        config.put(conf.getLAYER_FIELD_NAME(), LAYER_NAME);
        if (kerasVersion == 2) {
            ArrayList kernel = new ArrayList<Integer>() {

                {
                    for (int i : KERNEL_SIZE) add(i);
                }
            };
            config.put(conf.getLAYER_FIELD_POOL_1D_SIZE(), kernel);
        } else {
            config.put(conf.getLAYER_FIELD_POOL_1D_SIZE(), KERNEL_SIZE[0]);
        }
        if (kerasVersion == 2) {
            ArrayList stride = new ArrayList<Integer>() {

                {
                    for (int i : STRIDE) add(i);
                }
            };
            config.put(conf.getLAYER_FIELD_POOL_1D_STRIDES(), stride);
        } else {
            config.put(conf.getLAYER_FIELD_POOL_1D_STRIDES(), STRIDE[0]);
        }
        config.put(conf.getLAYER_FIELD_BORDER_MODE(), BORDER_MODE_VALID);
        layerConfig.put(conf.getLAYER_FIELD_CONFIG(), config);
        layerConfig.put(conf.getLAYER_FIELD_KERAS_VERSION(), kerasVersion);
        Subsampling1DLayer layer = new KerasPooling1D(layerConfig).getSubsampling1DLayer();
        assertEquals(LAYER_NAME, layer.getLayerName());
        assertEquals(KERNEL_SIZE[0], layer.getKernelSize()[0]);
        assertEquals(STRIDE[0], layer.getStride()[0]);
        assertEquals(POOLING_TYPE, layer.getPoolingType());
        assertEquals(ConvolutionMode.Truncate, layer.getConvolutionMode());
        assertEquals(VALID_PADDING[0], layer.getPadding()[0]);
    }


    @Test
    public void testPooling1dNWHC() throws  Exception {
        File file = Resources.asFile("modelimport/keras/tfkeras/issue_9349.hdf5");
        ComputationGraph computationGraph = KerasModelImport.importKerasModelAndWeights(file.getAbsolutePath());
        GraphVertex maxpooling1d = computationGraph.getVertex("max_pooling1d");
        assertNotNull(maxpooling1d);
        Layer layer = maxpooling1d.getLayer();
        org.deeplearning4j.nn.layers.convolution.subsampling.Subsampling1DLayer subsampling1DLayer = (org.deeplearning4j.nn.layers.convolution.subsampling.Subsampling1DLayer) layer;
        assertEquals(CNN2DFormat.NHWC,subsampling1DLayer.layerConf().getCnn2dDataFormat());
    }


}
