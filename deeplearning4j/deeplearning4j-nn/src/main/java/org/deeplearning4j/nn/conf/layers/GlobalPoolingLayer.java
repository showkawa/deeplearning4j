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
import org.deeplearning4j.nn.conf.RNNFormat;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.memory.LayerMemoryReport;
import org.deeplearning4j.nn.conf.memory.MemoryReport;
import org.deeplearning4j.nn.conf.preprocessor.FeedForwardToCnnPreProcessor;
import org.deeplearning4j.nn.conf.preprocessor.FeedForwardToRnnPreProcessor;
import org.deeplearning4j.nn.params.EmptyParamInitializer;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.deeplearning4j.util.ValidationUtils;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.Collection;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class GlobalPoolingLayer extends NoParamLayer {

    private PoolingType poolingType;
    private int[] poolingDimensions;
    private int pnorm;
    private boolean collapseDimensions = true;

    private GlobalPoolingLayer(Builder builder) {
        super(builder);
        this.poolingType = builder.poolingType;
        this.poolingDimensions = builder.poolingDimensions;
        this.collapseDimensions = builder.collapseDimensions;
        this.pnorm = builder.pnorm;
        this.layerName = builder.layerName;
    }

    public GlobalPoolingLayer() {
        this(PoolingType.MAX);
    }

    public GlobalPoolingLayer(PoolingType poolingType) {
        this(new Builder().poolingType(poolingType));
    }


    @Override
    public org.deeplearning4j.nn.api.Layer instantiate(NeuralNetConfiguration conf,
                                                       Collection<TrainingListener> trainingListeners, int layerIndex, INDArray layerParamsView,
                                                       boolean initializeParams, DataType networkDataType) {
        org.deeplearning4j.nn.layers.pooling.GlobalPoolingLayer ret =
                new org.deeplearning4j.nn.layers.pooling.GlobalPoolingLayer(conf, networkDataType);
        ret.setListeners(trainingListeners);
        ret.setIndex(layerIndex);
        ret.setParamsViewArray(layerParamsView);
        Map<String, INDArray> paramTable = initializer().init(conf, layerParamsView, initializeParams);
        ret.setParamTable(paramTable);
        ret.setConf(conf);
        return ret;
    }

    @Override
    public ParamInitializer initializer() {
        return EmptyParamInitializer.getInstance();
    }

    @Override
    public InputType getOutputType(int layerIndex, InputType inputType) {

        switch (inputType.getType()) {
            case FF:
                throw new UnsupportedOperationException(
                        "Global max pooling cannot be applied to feed-forward input type. Got input type = "
                                + inputType);
            case RNN:
                InputType.InputTypeRecurrent recurrent = (InputType.InputTypeRecurrent) inputType;
                //Return 3d activations, with shape [minibatch, timeStepSize, 1]
                return recurrent;
            case CNN:
                InputType.InputTypeConvolutional conv = (InputType.InputTypeConvolutional) inputType;
                if (collapseDimensions) {
                    return InputType.feedForward(conv.getChannels());
                } else {
                    return InputType.convolutional(1, 1, conv.getChannels(), conv.getFormat());
                }
            case CNN3D:
                InputType.InputTypeConvolutional3D conv3d = (InputType.InputTypeConvolutional3D) inputType;
                if (collapseDimensions) {
                    return InputType.feedForward(conv3d.getChannels());
                } else {
                    return InputType.convolutional3D(1, 1, 1, conv3d.getChannels());
                }
            case CNNFlat:
                InputType.InputTypeConvolutionalFlat convFlat = (InputType.InputTypeConvolutionalFlat) inputType;
                if (collapseDimensions) {
                    return InputType.feedForward(convFlat.getDepth());
                } else {
                    return InputType.convolutional(1, 1, convFlat.getDepth());
                }
            default:
                throw new UnsupportedOperationException("Unknown or not supported input type: " + inputType);
        }
    }

    @Override
    public void setNIn(InputType inputType, boolean override) {
        if(inputType.getType() == InputType.Type.CNN){
            InputType.InputTypeConvolutional c = (InputType.InputTypeConvolutional) inputType;
            if(c.getFormat() == CNN2DFormat.NCHW){
                poolingDimensions = new int[]{2,3};
            } else {
                poolingDimensions = new int[]{1,2};
            }
        }
    }

    @Override
    public InputPreProcessor getPreProcessorForInputType(InputType inputType) {
        switch (inputType.getType()) {
            /**
             * Global pooling won't appear in the context of feed forward, but global pooling itself
             * typically feeds forward a feed forward type. This converts that back to rnn.
             */
            case FF:
                InputType.InputTypeFeedForward feedForward = (InputType.InputTypeFeedForward)  inputType;
                if(feedForward.getTimeDistributedFormat() != null && feedForward.getTimeDistributedFormat() instanceof RNNFormat) {
                    RNNFormat rnnFormat = (RNNFormat) feedForward.getTimeDistributedFormat();
                    return new FeedForwardToRnnPreProcessor(rnnFormat);
                } else if(feedForward.getTimeDistributedFormat() != null && feedForward.getTimeDistributedFormat() instanceof CNN2DFormat) {
                    CNN2DFormat cnn2DFormat = (CNN2DFormat) feedForward.getTimeDistributedFormat();
                    switch(cnn2DFormat) {
                        case NCHW:
                            return new FeedForwardToRnnPreProcessor(RNNFormat.NCW);
                        case NHWC:
                            return new FeedForwardToRnnPreProcessor(RNNFormat.NWC);
                    }

                } else {
                    return new FeedForwardToRnnPreProcessor();
                }

            case RNN:
            case CNN:
            case CNN3D:
                //No preprocessor required
                return null;
            case CNNFlat:
                InputType.InputTypeConvolutionalFlat cFlat = (InputType.InputTypeConvolutionalFlat) inputType;
                return new FeedForwardToCnnPreProcessor(cFlat.getHeight(), cFlat.getWidth(), cFlat.getDepth());
        }

        return null;
    }

    @Override
    public boolean isPretrainParam(String paramName) {
        throw new UnsupportedOperationException("Global pooling layer does not contain parameters");
    }

    @Override
    public LayerMemoryReport getMemoryReport(InputType inputType) {
        InputType outputType = getOutputType(-1, inputType);

        long fwdTrainInferenceWorkingPerEx = 0;
        //Here: we'll assume we are doing 'full array' global pooling.
        //For max/avg/sum pooling, no working memory (GlobalPoolingLayer.activateHelperFullArray
        //But for pnorm, we have working memory
        if (poolingType == PoolingType.PNORM) {
            //Dup the input array once before
            fwdTrainInferenceWorkingPerEx = inputType.arrayElementsPerExample();
        }

        return new LayerMemoryReport.Builder(layerName, GlobalPoolingLayer.class, inputType, outputType)
                .standardMemory(0, 0) //No params
                //Train + Inference: no additional working memory (except pnorm) - the reduction is the output activations
                .workingMemory(0, fwdTrainInferenceWorkingPerEx, 0, fwdTrainInferenceWorkingPerEx)
                .cacheMemory(MemoryReport.CACHE_MODE_ALL_ZEROS, MemoryReport.CACHE_MODE_ALL_ZEROS) //No caching
                .build();
    }

    @Getter
    @Setter
    public static class Builder extends Layer.Builder<Builder> {

        /**
         * Pooling type for global pooling
         */
        private PoolingType poolingType = PoolingType.MAX;

        /**
         * Pooling dimensions. Note: most of the time, this doesn't need to be set, and the defaults can be used.
         * Default for RNN data: pooling dimension 2 (time). Default for CNN data: pooling dimensions 2,3 (height and
         * width) Default for CNN3D data: pooling dimensions 2,3,4 (depth, height and width)
         *
         */
        private int[] poolingDimensions;

        /**
         * P-norm constant. Only used if using {@link PoolingType#PNORM} for the pooling type
         *
         */
        private int pnorm = 2;

        /**
         * Whether to collapse dimensions when pooling or not. Usually you *do* want to do this. Default: true. If
         * true:<br> - 3d (time series) input with shape [miniBatchSize, vectorSize, timeSeriesLength] -> 2d output
         * [miniBatchSize, vectorSize]<br> - 4d (CNN) input with shape [miniBatchSize, channels, height, width] -> 2d
         * output [miniBatchSize, channels]<br> - 5d (CNN3D) input with shape [miniBatchSize, channels, depth, height,
         * width] -> 2d output [miniBatchSize, channels]<br>
         *
         *
         * If false:<br> - 3d (time series) input with shape [miniBatchSize, vectorSize, timeSeriesLength] -> 3d output
         * [miniBatchSize, vectorSize, 1]<br> - 4d (CNN) input with shape [miniBatchSize, channels, height, width] -> 2d
         * output [miniBatchSize, channels, 1, 1]<br> - 5d (CNN3D) input with shape [miniBatchSize, channels, depth,
         * height, width] -> 2d output [miniBatchSize, channels, 1, 1, 1]<br>
         *
         */
        private boolean collapseDimensions = true;

        public Builder() {

        }

        public Builder(PoolingType poolingType) {
            this.setPoolingType(poolingType);
        }

        /**
         * Pooling dimensions. Note: most of the time, this doesn't need to be set, and the defaults can be used.
         * Default for RNN data: pooling dimension 2 (time). Default for CNN data: pooling dimensions 2,3 (height and
         * width) Default for CNN3D data: pooling dimensions 2,3,4 (depth, height and width)
         *
         * @param poolingDimensions Pooling dimensions to use
         */
        public Builder poolingDimensions(int... poolingDimensions) {
            this.setPoolingDimensions(poolingDimensions);
            return this;
        }

        /**
         * @param poolingType Pooling type for global pooling
         */
        public Builder poolingType(PoolingType poolingType) {
            this.setPoolingType(poolingType);
            return this;
        }

        /**
         * Whether to collapse dimensions when pooling or not. Usually you *do* want to do this. Default: true. If
         * true:<br> - 3d (time series) input with shape [miniBatchSize, vectorSize, timeSeriesLength] -> 2d output
         * [miniBatchSize, vectorSize]<br> - 4d (CNN) input with shape [miniBatchSize, channels, height, width] -> 2d
         * output [miniBatchSize, channels]<br> - 5d (CNN3D) input with shape [miniBatchSize, channels, depth, height,
         * width] -> 2d output [miniBatchSize, channels]<br>
         *
         *
         * If false:<br> - 3d (time series) input with shape [miniBatchSize, vectorSize, timeSeriesLength] -> 3d output
         * [miniBatchSize, vectorSize, 1]<br> - 4d (CNN) input with shape [miniBatchSize, channels, height, width] -> 2d
         * output [miniBatchSize, channels, 1, 1]<br> - 5d (CNN3D) input with shape [miniBatchSize, channels, depth,
         * height, width] -> 2d output [miniBatchSize, channels, 1, 1, 1]<br>
         *
         * @param collapseDimensions Whether to collapse the dimensions or not
         */
        public Builder collapseDimensions(boolean collapseDimensions) {
            this.setCollapseDimensions(collapseDimensions);
            return this;
        }

        /**
         * P-norm constant. Only used if using {@link PoolingType#PNORM} for the pooling type
         *
         * @param pnorm P-norm constant
         */
        public Builder pnorm(int pnorm) {
            if (pnorm <= 0) {
                throw new IllegalArgumentException("Invalid input: p-norm value must be greater than 0. Got: " + pnorm);
            }
            this.setPnorm(pnorm);
            return this;
        }

        public void setPnorm(int pnorm){
            ValidationUtils.validateNonNegative(pnorm, "pnorm");
            this.pnorm = pnorm;
        }

        @SuppressWarnings("unchecked")
        public GlobalPoolingLayer build() {
            return new GlobalPoolingLayer(this);
        }
    }
}
