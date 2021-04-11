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

package org.deeplearning4j.nn.conf.layers;

import lombok.*;
import org.deeplearning4j.nn.api.ParamInitializer;
import org.deeplearning4j.nn.conf.CNN2DFormat;
import org.deeplearning4j.nn.conf.InputPreProcessor;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.memory.LayerMemoryReport;
import org.deeplearning4j.nn.conf.memory.MemoryReport;
import org.deeplearning4j.nn.params.EmptyParamInitializer;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.deeplearning4j.util.ValidationUtils;
import org.nd4j.common.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.Collection;
import java.util.Map;

@Data
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class SpaceToBatchLayer extends NoParamLayer {

    // TODO: throw error when block and padding dims don't match

    protected int[] blocks;
    protected int[][] padding;
    protected CNN2DFormat format = CNN2DFormat.NCHW;


    protected SpaceToBatchLayer(Builder builder) {
        super(builder);
        this.blocks = builder.blocks;
        this.padding = builder.padding;
        this.format = builder.format;
    }

    @Override
    public SpaceToBatchLayer clone() {
        return (SpaceToBatchLayer) super.clone();
    }

    @Override
    public org.deeplearning4j.nn.api.Layer instantiate(NeuralNetConfiguration conf,
                                                       Collection<TrainingListener> trainingListeners, int layerIndex, INDArray layerParamsView,
                                                       boolean initializeParams, DataType networkDataType) {
        org.deeplearning4j.nn.layers.convolution.SpaceToBatch ret =
                        new org.deeplearning4j.nn.layers.convolution.SpaceToBatch(conf, networkDataType);
        ret.setListeners(trainingListeners);
        ret.setIndex(layerIndex);
        ret.setParamsViewArray(layerParamsView);
        Map<String, INDArray> paramTable = initializer().init(conf, layerParamsView, initializeParams);
        ret.setParamTable(paramTable);
        ret.setConf(conf);
        return ret;
    }

    @Override
    public LayerMemoryReport getMemoryReport(InputType inputType) {
        InputType.InputTypeConvolutional c = (InputType.InputTypeConvolutional) inputType;
        InputType.InputTypeConvolutional outputType = (InputType.InputTypeConvolutional) getOutputType(-1, inputType);

        return new LayerMemoryReport.Builder(layerName, SpaceToBatchLayer.class, inputType, outputType)
                        .standardMemory(0, 0) //No params
                        .cacheMemory(MemoryReport.CACHE_MODE_ALL_ZEROS, MemoryReport.CACHE_MODE_ALL_ZEROS) //No caching
                        .build();
    }

    @Override
    public InputType getOutputType(int layerIndex, InputType inputType) {
        if (inputType == null || inputType.getType() != InputType.Type.CNN) {
            throw new IllegalStateException("Invalid input for Subsampling layer (layer name=\"" + getLayerName()
                            + "\"): Expected CNN input, got " + inputType);
        }
        InputType.InputTypeConvolutional i = (InputType.InputTypeConvolutional) inputType;
        return InputType.convolutional((i.getHeight() + padding[0][0] + padding[0][1]) / blocks[0],
                        (i.getWidth() + padding[1][0] + padding[1][1]) / blocks[1], i.getChannels(), i.getFormat());
    }

    @Override
    public ParamInitializer initializer() {
        return EmptyParamInitializer.getInstance();
    }


    @Override
    public void setNIn(InputType inputType, boolean override) {
        Preconditions.checkState(inputType.getType() == InputType.Type.CNN, "Only CNN input types can be used with SpaceToBatchLayer, got %s", inputType);
        this.format = ((InputType.InputTypeConvolutional)inputType).getFormat();
    }

    @Override
    public InputPreProcessor getPreProcessorForInputType(InputType inputType) {
        if (inputType == null) {
            throw new IllegalStateException("Invalid input for space to batch layer (layer name=\"" + getLayerName()
                            + "\"): input is null");
        }
        return InputTypeUtil.getPreProcessorForInputTypeCnnLayers(inputType, getLayerName());
    }

    @Override
    public boolean isPretrainParam(String paramName) {
        throw new UnsupportedOperationException("SpaceToBatchLayer does not contain parameters");
    }


    @NoArgsConstructor
    @Getter
    @Setter
    public static class Builder<T extends Builder<T>> extends Layer.Builder<T> {

        /**
         * Block size for SpaceToBatch layer. Should be a length 2 array for the height and width
         * dimensions
         */
        @Setter(AccessLevel.NONE)
        protected int[] blocks;

        /**
         * A 2d array, with format [[padTop, padBottom], [padLeft, padRight]]
         */
        protected int[][] padding;

        protected CNN2DFormat format = CNN2DFormat.NCHW;

        /**
         * @param blocks Block size for SpaceToBatch layer. Should be a length 2 array for the height and width
         * dimensions
         */
        public void setBlocks(int... blocks) {
            this.blocks = ValidationUtils.validate2NonNegative(blocks, false, "blocks");
        }

        /**
         * @param padding Padding - should be a 2d array, with format [[padTop, padBottom], [padLeft, padRight]]
         */
        public void setPadding(int[][] padding) {
            this.padding = ValidationUtils.validate2x2NonNegative(padding, "padding");
        }


        /**
         * @param blocks Block size for SpaceToBatch layer. Should be a length 2 array for the height and width
         * dimensions
         */
        public Builder(int[] blocks) {
            this.setBlocks(blocks);
            this.setPadding(new int[][] {{0, 0}, {0, 0}});
        }

        /**
         * @param blocks Block size for SpaceToBatch layer. Should be a length 2 array for the height and width
         * dimensions
         * @param padding Padding - should be a 2d array, with format [[padTop, padBottom], [padLeft, padRight]]
         */
        public Builder(int[] blocks, int[][] padding) {
            this.setBlocks(blocks);
            this.setPadding(padding);
        }

        /**
         * Set the data format for the CNN activations - NCHW (channels first) or NHWC (channels last).
         * See {@link CNN2DFormat} for more details.<br>
         * Default: NCHW
         * @param format Format for activations (in and out)
         */
        public T dataFormat(CNN2DFormat format){
            this.format = format;
            return (T)this;
        }

        /**
         * @param blocks Block size for SpaceToBatch layer. Should be a length 2 array for the height and width
         * dimensions
         */
        public T blocks(int... blocks) {
            this.setBlocks(blocks);
            return (T) this;
        }

        /**
         * @param padding Padding - should be a 2d array, with format [[padTop, padBottom], [padLeft, padRight]]
         */
        public T padding(int[][] padding) {
            this.setPadding(padding);
            return (T) this;
        }

        @Override
        public T name(String layerName) {
            this.setLayerName(layerName);
            return (T) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public SpaceToBatchLayer build() {
            if(padding == null)
                setPadding(new int[][] {{0, 0}, {0, 0}});
            return new SpaceToBatchLayer(this);
        }
    }

}
