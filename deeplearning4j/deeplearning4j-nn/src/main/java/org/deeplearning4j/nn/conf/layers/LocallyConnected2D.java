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
import org.deeplearning4j.nn.conf.CNN2DFormat;
import org.deeplearning4j.nn.conf.ConvolutionMode;
import org.deeplearning4j.nn.conf.InputPreProcessor;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.samediff.SameDiffLayer;
import org.deeplearning4j.nn.conf.layers.samediff.SDLayerParams;
import org.deeplearning4j.nn.conf.layers.samediff.SameDiffLayerUtils;
import org.deeplearning4j.nn.params.ConvolutionParamInitializer;
import org.deeplearning4j.nn.weights.WeightInitUtil;
import org.deeplearning4j.util.ConvolutionUtils;
import org.deeplearning4j.util.ValidationUtils;
import org.nd4j.autodiff.samediff.SDIndex;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.enums.PadMode;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.memory.MemoryWorkspace;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.shade.jackson.annotation.JsonIgnoreProperties;

import java.util.*;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({"paramShapes"})
public class LocallyConnected2D extends SameDiffLayer {

    private static final List<String> WEIGHT_KEYS = Collections.singletonList(ConvolutionParamInitializer.WEIGHT_KEY);
    private static final List<String> BIAS_KEYS = Collections.singletonList(ConvolutionParamInitializer.BIAS_KEY);
    private static final List<String> PARAM_KEYS =
                    Arrays.asList(ConvolutionParamInitializer.BIAS_KEY, ConvolutionParamInitializer.WEIGHT_KEY);

    private long nIn;
    private long nOut;
    private Activation activation;
    private int[] kernel;
    private int[] stride;
    private int[] padding;
    private int[] paddingBr;
    private ConvolutionMode cm;
    private int[] dilation;
    private boolean hasBias;
    private int[] inputSize;
    private int[] outputSize;
    private int featureDim;
    protected CNN2DFormat format = CNN2DFormat.NCHW;

    protected LocallyConnected2D(Builder builder) {
        super(builder);
        this.nIn = builder.nIn;
        this.nOut = builder.nOut;
        this.activation = builder.activation;
        this.kernel = builder.kernel;
        this.stride = builder.stride;
        this.padding = builder.padding;
        this.cm = builder.cm;
        this.dilation = builder.dilation;
        this.hasBias = builder.hasBias;
        this.inputSize = builder.inputSize;
        this.featureDim = kernel[0] * kernel[1] * (int) nIn;
        this.format = builder.format;
    }

    private LocallyConnected2D() {
        //No arg constructor for Jackson/JSON serialization
    }

    public void computeOutputSize() {
        int nIn = (int) getNIn();

        if (inputSize == null) {
            throw new IllegalArgumentException("Input size has to be specified for locally connected layers.");
        }

        boolean nchw = format == CNN2DFormat.NCHW;

        int[] inputShape = nchw ? new int[] {1, nIn, inputSize[0], inputSize[1]} : new int[] {1, inputSize[0], inputSize[1], nIn};
        INDArray dummyInputForShapeInference = Nd4j.ones(inputShape);

        if (cm == ConvolutionMode.Same) {
            this.outputSize = ConvolutionUtils.getOutputSize(dummyInputForShapeInference, kernel, stride, null, cm,
                            dilation, format);
            this.padding = ConvolutionUtils.getSameModeTopLeftPadding(outputSize, inputSize, kernel, stride, dilation);
            this.paddingBr = ConvolutionUtils.getSameModeBottomRightPadding(outputSize, inputSize, kernel, stride, dilation);
        } else {
            this.outputSize = ConvolutionUtils.getOutputSize(dummyInputForShapeInference, kernel, stride, padding, cm,
                            dilation, format);
        }
    }

    @Override
    public InputType getOutputType(int layerIndex, InputType inputType) {
        if (inputType == null || inputType.getType() != InputType.Type.CNN) {
            throw new IllegalArgumentException("Provided input type for locally connected 2D layers has to be "
                            + "of CNN type, got: " + inputType);
        }
        // dynamically compute input size from input type
        InputType.InputTypeConvolutional cnnType = (InputType.InputTypeConvolutional) inputType;
        this.inputSize = new int[] {(int) cnnType.getHeight(), (int) cnnType.getWidth()};
        computeOutputSize();

        return InputTypeUtil.getOutputTypeCnnLayers(inputType, kernel, stride, padding, new int[] {1, 1}, cm, nOut,
                        layerIndex, getLayerName(), format, LocallyConnected2D.class);
    }

    @Override
    public void setNIn(InputType inputType, boolean override) {
        if (nIn <= 0 || override) {
            InputType.InputTypeConvolutional c = (InputType.InputTypeConvolutional) inputType;
            this.nIn = c.getChannels();
            this.featureDim = kernel[0] * kernel[1] * (int) nIn;
        }
        this.format = ((InputType.InputTypeConvolutional)inputType).getFormat();
    }

    @Override
    public InputPreProcessor getPreProcessorForInputType(InputType inputType) {
        return InputTypeUtil.getPreProcessorForInputTypeCnnLayers(inputType, getLayerName());
    }

    @Override
    public void defineParameters(SDLayerParams params) {
        params.clear();
        val weightsShape = new long[] {outputSize[0] * outputSize[1], featureDim, nOut};
        params.addWeightParam(ConvolutionParamInitializer.WEIGHT_KEY, weightsShape);
        if (hasBias) {
            val biasShape = new long[] {nOut};
            params.addBiasParam(ConvolutionParamInitializer.BIAS_KEY, biasShape);
        }
    }

    @Override
    public void initializeParameters(Map<String, INDArray> params) {
        try (MemoryWorkspace ws = Nd4j.getWorkspaceManager().scopeOutOfWorkspaces()) {
            for (Map.Entry<String, INDArray> e : params.entrySet()) {
                if (ConvolutionParamInitializer.BIAS_KEY.equals(e.getKey())) {
                    e.getValue().assign(0);
                } else {
                    double fanIn = nIn * kernel[0] * kernel[1];
                    double fanOut = nOut * kernel[0] * kernel[1] / ((double) stride[0] * stride[1]);
                    WeightInitUtil.initWeights(fanIn, fanOut, e.getValue().shape(), weightInit, null, 'c',
                                    e.getValue());
                }
            }
        }
    }

    @Override
    public SDVariable defineLayer(SameDiff sameDiff, SDVariable layerInput, Map<String, SDVariable> paramTable, SDVariable mask) {

        SDVariable w = paramTable.get(ConvolutionParamInitializer.WEIGHT_KEY);

        long[] inputShape = layerInput.getShape();
        long miniBatch = inputShape[0];
        int outH = outputSize[0];
        int outW = outputSize[1];
        int sH = stride[0];
        int sW = stride[1];
        int kH = kernel[0];
        int kW = kernel[1];

        boolean nchw = format == CNN2DFormat.NCHW;
        if(!nchw)
            layerInput = layerInput.permute(0,3,1,2);       //NHWC to NCHW

        if(padding[0] > 0 || padding[1] > 0 || (cm == ConvolutionMode.Same && (paddingBr[0] > 0 || paddingBr[1] > 0))){
            //Note: for same mode, bottom/right padding can be 1 more than top/left padding
            //NCHW format
            if(cm == ConvolutionMode.Same){
                layerInput = sameDiff.nn().pad(layerInput,
                        sameDiff.constant(Nd4j.createFromArray(new int[][]{{0,0},{0,0},{padding[0], paddingBr[0]}, {padding[1], paddingBr[1]}})), PadMode.CONSTANT, 0.0);
            } else {
                layerInput = sameDiff.nn().pad(layerInput,
                        sameDiff.constant(Nd4j.createFromArray(new int[][]{{0,0},{0,0},{padding[0], padding[0]}, {padding[1], padding[1]}})), PadMode.CONSTANT, 0.0);
            }
        }

        SDVariable[] inputArray = new SDVariable[outH * outW];
        for (int y = 0; y < outH; y++) {
            for (int x = 0; x < outW; x++) {
                SDVariable slice = layerInput.get(SDIndex.all(), // miniBatch
                                SDIndex.all(), // nIn
                                SDIndex.interval(y * sH, y * sH + kH), // kernel height
                                SDIndex.interval(x * sW, x * sW + kW) // kernel width
                );
                inputArray[x * outH + y] = sameDiff.reshape(slice, 1, miniBatch, featureDim);
            }
        }
        SDVariable concatOutput = sameDiff.concat(0, inputArray); // (outH * outW, miniBatch, featureDim)

        SDVariable mmulResult = sameDiff.mmul(concatOutput, w); // (outH * outW, miniBatch, nOut)

        SDVariable reshapeResult = sameDiff.reshape(mmulResult, outH, outW, miniBatch, nOut);

        SDVariable permutedResult = nchw ? reshapeResult.permute(2, 3, 0, 1) : reshapeResult.permute(2, 0, 1, 3); // (mb, nOut, outH, outW) or (mb, outH, outW, nOut)

        if (hasBias) {
            SDVariable b = paramTable.get(ConvolutionParamInitializer.BIAS_KEY);
            SDVariable biasAddedResult = sameDiff.nn().biasAdd(permutedResult, b, nchw);
            return activation.asSameDiff("out", sameDiff, biasAddedResult);
        } else {
            return activation.asSameDiff("out", sameDiff, permutedResult);
        }
    }

    @Override
    public void applyGlobalConfigToLayer(NeuralNetConfiguration.Builder globalConfig) {
        if (activation == null) {
            activation = SameDiffLayerUtils.fromIActivation(globalConfig.getActivationFn());
        }
        if (cm == null) {
            cm = globalConfig.getConvolutionMode();
        }
    }

    @Getter
    @Setter
    public static class Builder extends SameDiffLayer.Builder<Builder> {

        /**
         * Number of inputs to the layer (input size)
         */
        private int nIn;

        /**
         * Number of outputs (output size)
         */
        private int nOut;

        /**
         * Activation function for the layer
         */
        private Activation activation = Activation.TANH;

        /**
         * Kernel size for the layer. Must be 2 values (height/width)
         */
        @Setter(AccessLevel.NONE)
        private int[] kernel = new int[] {2, 2};

        /**
         * Stride for the layer. Must be 2 values (height/width)
         */
        @Setter(AccessLevel.NONE)
        private int[] stride = new int[] {1, 1};

        /**
         * Padding for the layer. Not used if {@link ConvolutionMode#Same} is set. Must be 2 values (height/width)
         */
        @Setter(AccessLevel.NONE)
        private int[] padding = new int[] {0, 0};

        /**
         * Dilation for the layer. Must be 2 values (height/width)
         */
        @Setter(AccessLevel.NONE)
        private int[] dilation = new int[] {1, 1};

        /**
         * Set input filter size (h,w) for this locally connected 2D layer
         *
         */
        @Setter(AccessLevel.NONE)
        private int[] inputSize;

        /**
         * Convolution mode for the layer. See {@link ConvolutionMode} for details
         */
        private ConvolutionMode cm = ConvolutionMode.Same;

        /**
         * If true (default is false) the layer will have a bias
         */
        private boolean hasBias = true;

        protected CNN2DFormat format = CNN2DFormat.NCHW;


        /**
         * @param kernel Kernel size for the layer. Must be 2 values (height/width)
         */
        public void setKernel(int... kernel) {
            this.kernel = ValidationUtils.validate2NonNegative(kernel, false, "kernel");
        }

        /**
         * @param stride Stride for the layer. Must be 2 values (height/width)
         */
        public void setStride(int... stride) {
            this.stride = ValidationUtils.validate2NonNegative(stride, false, "stride");
        }

        /**
         * @param padding Padding for the layer. Not used if {@link ConvolutionMode#Same} is set. Must be 2 values (height/width)
         */
        public void setPadding(int... padding) {
            this.padding = ValidationUtils.validate2NonNegative(padding, false, "padding");
        }

        /**
         * @param dilation Dilation for the layer. Must be 2 values (height/width)
         */
        public void setDilation(int... dilation) {
            this.dilation = ValidationUtils.validate2NonNegative(dilation, false, "dilation");
        }

        /**
         * @param nIn Number of inputs to the layer (input size)
         */
        public Builder nIn(int nIn) {
            this.setNIn(nIn);
            return this;
        }

        /**
         * @param nOut Number of outputs (output size)
         */
        public Builder nOut(int nOut) {
            this.setNOut(nOut);
            return this;
        }

        /**
         * @param activation Activation function for the layer
         */
        public Builder activation(Activation activation) {
            this.setActivation(activation);
            return this;
        }

        /**
         * @param k Kernel size for the layer. Must be 2 values (height/width)
         */
        public Builder kernelSize(int... k) {
            this.setKernel(k);
            return this;
        }

        /**
         * @param s Stride for the layer. Must be 2 values (height/width)
         */
        public Builder stride(int... s) {
            this.setStride(s);
            return this;
        }

        /**
         * @param p Padding for the layer. Not used if {@link ConvolutionMode#Same} is set. Must be 2 values (height/width)
         */
        public Builder padding(int... p) {
            this.setPadding(p);
            return this;
        }

        /**
         * @param cm Convolution mode for the layer. See {@link ConvolutionMode} for details
         */
        public Builder convolutionMode(ConvolutionMode cm) {
            this.setCm(cm);
            return this;
        }

        /**
         * @param d Dilation for the layer. Must be 2 values (height/width)
         */
        public Builder dilation(int... d) {
            this.setDilation(d);
            return this;
        }

        /**
         * Set the data format for the CNN activations - NCHW (channels first) or NHWC (channels last).
         * See {@link CNN2DFormat} for more details.<br>
         * Default: NCHW
         * @param format Format for activations (in and out)
         */
        public Builder dataFormat(CNN2DFormat format){
            this.format = format;
            return this;
        }

        /**
         * @param hasBias If true (default is false) the layer will have a bias
         */
        public Builder hasBias(boolean hasBias) {
            this.setHasBias(hasBias);
            return this;
        }

        /**
         * Set input filter size (h,w) for this locally connected 2D layer
         *
         * @param inputSize pair of height and width of the input filters to this layer
         * @return Builder
         */
        public Builder setInputSize(int... inputSize) {
            this.inputSize = ValidationUtils.validate2(inputSize, false, "inputSize");
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public LocallyConnected2D build() {
            ConvolutionUtils.validateConvolutionModePadding(cm, padding);
            ConvolutionUtils.validateCnnKernelStridePadding(kernel, stride, padding);
            return new LocallyConnected2D(this);
        }
    }
}
