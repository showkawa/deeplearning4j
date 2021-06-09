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

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.ParamInitializer;
import org.deeplearning4j.nn.conf.CNN2DFormat;
import org.deeplearning4j.nn.conf.ConvolutionMode;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.layers.convolution.Deconvolution2DLayer;
import org.deeplearning4j.nn.params.DeconvolutionParamInitializer;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.deeplearning4j.util.ValidationUtils;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.Collection;
import java.util.Map;

@Data
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Deconvolution2D extends ConvolutionLayer {

    /**
     * Deconvolution2D layer nIn in the input layer is the number of channels nOut is the number of filters to be used
     * in the net or in other words the channels The builder specifies the filter/kernel size, the stride and padding
     * The pooling layer takes the kernel size
     */
    protected Deconvolution2D(BaseConvBuilder<?> builder) {
        super(builder);
        initializeConstraints(builder);
        if(builder instanceof Builder){
            this.cnn2dDataFormat = ((Builder) builder).format;
        }
    }

    public boolean hasBias() {
        return hasBias;
    }

    @Override
    public Deconvolution2D clone() {
        Deconvolution2D clone = (Deconvolution2D) super.clone();
        if (clone.kernelSize != null) {
            clone.kernelSize = clone.kernelSize.clone();
        }
        if (clone.stride != null) {
            clone.stride = clone.stride.clone();
        }
        if (clone.padding != null) {
            clone.padding = clone.padding.clone();
        }
        return clone;
    }

    @Override
    public Layer instantiate(NeuralNetConfiguration conf, Collection<TrainingListener> trainingListeners,
                             int layerIndex, INDArray layerParamsView, boolean initializeParams, DataType networkDataType) {
        LayerValidation.assertNInNOutSet("Deconvolution2D", getLayerName(), layerIndex, getNIn(), getNOut());

        Deconvolution2DLayer ret =
                        new Deconvolution2DLayer(conf, networkDataType);
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
        return DeconvolutionParamInitializer.getInstance();
    }

    @Override
    public InputType getOutputType(int layerIndex, InputType inputType) {
        if (inputType == null || inputType.getType() != InputType.Type.CNN) {
            throw new IllegalStateException("Invalid input for Convolution layer (layer name=\"" + getLayerName()
                            + "\"): Expected CNN input, got " + inputType);
        }

        return InputTypeUtil.getOutputTypeDeconvLayer(inputType, kernelSize, stride, padding, dilation, convolutionMode,
                        nOut, layerIndex, getLayerName(), Deconvolution2DLayer.class);
    }

    public static class Builder extends BaseConvBuilder<Builder> {

        public Builder(int[] kernelSize, int[] stride, int[] padding) {
            super(kernelSize, stride, padding);
        }

        public Builder(int[] kernelSize, int[] stride) {
            super(kernelSize, stride);
        }

        public Builder(int... kernelSize) {
            super(kernelSize);
        }

        public Builder() {
            super();
        }

        private CNN2DFormat format = CNN2DFormat.NCHW;

        public Builder dataFormat(CNN2DFormat format){
            this.format = format;
            return this;
        }

        @Override
        protected boolean allowCausal() {
            //Causal convolution - allowed for 1D only
            return false;
        }

        /**
         * Set the convolution mode for the Convolution layer. See {@link ConvolutionMode} for more details
         *
         * @param convolutionMode Convolution mode for layer
         */
        public Builder convolutionMode(ConvolutionMode convolutionMode) {
            return super.convolutionMode(convolutionMode);
        }

        /**
         * Size of the convolution rows/columns
         *
         * @param kernelSize the height and width of the kernel
         */
        public Builder kernelSize(int... kernelSize) {
            this.setKernelSize(kernelSize);
            return this;
        }

        public Builder stride(int... stride) {
            this.setStride(stride);
            return this;
        }

        public Builder padding(int... padding) {
            this.setPadding(padding);
            return this;
        }

        @Override
        public void setKernelSize(int... kernelSize) {
            this.kernelSize = ValidationUtils.validate2NonNegative(kernelSize, false, "kernelSize");
        }

        @Override
        public void setStride(int... stride) {
            this.stride = ValidationUtils.validate2NonNegative(stride, false,"stride");
        }

        @Override
        public void setPadding(int... padding) {
            this.padding = ValidationUtils.validate2NonNegative(padding, false, "padding");
        }

        @Override
        public void setDilation(int... dilation) {
            this.dilation = ValidationUtils.validate2NonNegative(dilation, false,"dilation");
        }

        @Override
        public Deconvolution2D build() {
            return new Deconvolution2D(this);
        }
    }

}
