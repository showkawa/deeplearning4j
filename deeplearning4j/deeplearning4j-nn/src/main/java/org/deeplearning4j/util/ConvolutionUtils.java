/*******************************************************************************
 * Copyright (c) 2015-2018 Skymind, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

package org.deeplearning4j.util;


import lombok.NonNull;
import lombok.val;
import org.deeplearning4j.exception.DL4JInvalidConfigException;
import org.deeplearning4j.exception.DL4JInvalidInputException;
import org.deeplearning4j.nn.conf.CNN2DFormat;
import org.deeplearning4j.nn.conf.ConvolutionMode;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.conf.layers.convolutional.Cropping2D;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.nd4j.common.base.Preconditions;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.api.ops.impl.broadcast.BroadcastCopyOp;
import org.nd4j.linalg.api.ops.impl.layers.convolution.MaxPooling2D;
import org.nd4j.linalg.api.ops.impl.layers.convolution.config.PaddingMode;
import org.nd4j.linalg.api.ops.impl.layers.convolution.config.Pooling2DConfig;
import org.nd4j.linalg.api.ops.impl.transforms.custom.Assign;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.linalg.exception.ND4JArraySizeException;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Arrays;

/**
 * Convolutional shape utilities
 *
 * @author Adam Gibson
 */
public class ConvolutionUtils {

    public static final String NCHW_NHWC_ERROR_MSG = "Note: Convolution layers can be configured for either NCHW (channels first)" +
            " or NHWC (channels last) format for input images and activations.\n" +
            "Layers can be configured using .dataFormat(CNN2DFormat.NCHW/NHWC) when constructing the layer, or for the entire net using" +
            " .setInputType(InputType.convolutional(height, width, depth, CNN2DForman.NCHW/NHWC)).\n" +
            "ImageRecordReader and NativeImageLoader can also be configured to load image data in either NCHW or NHWC format which must match the network";


    private static final int[] ONES = new int[]{1, 1};


    private ConvolutionUtils() {
    }

    /**
     * Use {@link #getOutputSize(INDArray, int[], int[], int[], ConvolutionMode, int[], CNN2DFormat)}
     */
    @Deprecated
    public static int[] getOutputSize(INDArray inputData, int[] kernel, int[] strides, int[] padding,
                                      ConvolutionMode convolutionMode) {
        return getOutputSize(inputData, kernel, strides, padding, convolutionMode, ONES);
    }

    /**
     * Get the output size of a deconvolution operation for given input data. In deconvolution, we compute the inverse
     * of the shape computation of a convolution.
     *
     * @param inputData       Input data
     * @param kernel          Kernel size (height/width)
     * @param strides         Strides (height/width)
     * @param padding         Padding (height/width)
     * @param convolutionMode Convolution mode (Same, Strict, Truncate)
     * @param dilation        Kernel dilation (height/width)
     * @return Output size: int[2] with output height/width
     */
    public static int[] getDeconvolutionOutputSize(INDArray inputData, int[] kernel, int[] strides, int[] padding,
                                                   ConvolutionMode convolutionMode, int[] dilation, CNN2DFormat format) {
        boolean nchw = format == CNN2DFormat.NCHW;
        int hDim = nchw ? 2 : 1;
        int wDim = nchw ? 3 : 2;

        if (inputData.size(hDim) > Integer.MAX_VALUE || inputData.size(wDim) > Integer.MAX_VALUE)
            throw new ND4JArraySizeException();
        int hIn = (int) inputData.size(hDim);
        int wIn = (int) inputData.size(wDim);
        int[] eKernel = effectiveKernelSize(kernel, dilation);

        if (convolutionMode == ConvolutionMode.Same) {
            int hOut = strides[0] * hIn;
            int wOut = strides[1] * wIn;
            return new int[]{hOut, wOut};
        }

        int hOut = strides[0] * (hIn - 1) + eKernel[0] - 2 * padding[0];
        int wOut = strides[1] * (wIn - 1) + eKernel[1] - 2 * padding[1];

        return new int[]{hOut, wOut};
    }

    /**
     * Get the output size of a deconvolution operation for given input data. In deconvolution, we compute the inverse
     * of the shape computation of a convolution.
     *
     * @param inputData       Input data
     * @param kernel          Kernel size (height/width)
     * @param strides         Strides (height/width)
     * @param padding         Padding (height/width)
     * @param convolutionMode Convolution mode (Same, Strict, Truncate)
     * @param dilation        Kernel dilation (height/width)
     * @return Output size: int[2] with output height/width
     */
    public static long[] getDeconvolution3DOutputSize(INDArray inputData, int[] kernel, int[] strides, int[] padding, int[] dilation,
                                                   ConvolutionMode convolutionMode, Convolution3D.DataFormat dataFormat) {

        long hIn, wIn, dIn;
        if(dataFormat == Convolution3D.DataFormat.NCDHW){
            hIn = inputData.size(2);
            wIn = inputData.size(3);
            dIn = inputData.size(4);
        } else {
            hIn = inputData.size(1);
            wIn = inputData.size(2);
            dIn = inputData.size(3);
        }


        int[] eKernel = effectiveKernelSize(kernel, dilation);

        if (convolutionMode == ConvolutionMode.Same) {
            long hOut = strides[0] * hIn;
            long wOut = strides[1] * wIn;
            long dOut = strides[2] * dIn;
            return new long[]{hOut, wOut, dOut};
        }

        long hOut = strides[0] * (hIn - 1) + eKernel[0] - 2 * padding[0];
        long wOut = strides[1] * (wIn - 1) + eKernel[1] - 2 * padding[1];
        long dOut = strides[2] * (dIn - 1) + eKernel[2] - 2 * padding[2];

        return new long[]{hOut, wOut, dOut};
    }


    /**
     * @deprecated Use {@link #getOutputSize(INDArray, int[], int[], int[], ConvolutionMode, int[], CNN2DFormat)}
     */
    @Deprecated
    public static int[] getOutputSize(INDArray inputData, int[] kernel, int[] strides, int[] padding,
                                      ConvolutionMode convolutionMode, int[] dilation) {
        return getOutputSize(inputData, kernel, strides, padding, convolutionMode, dilation, CNN2DFormat.NCHW);
    }

    /**
     * Returns true if a layer has a
     * {@link CNN2DFormat} property.
     * This is currently in use for:
     * {@link ConvolutionLayer},
     * {@link SubsamplingLayer},
     * {@link Upsampling2D},
     * {@link SpaceToBatchLayer},
     * {@link SpaceToDepthLayer},
     * {@link ZeroPaddingLayer},
     * {@link SeparableConvolution2D},
     * {@link Cropping2D},
     * {@link DepthwiseConvolution2D}
     * @param layer the layer to check
     * @return true if the layer is one of the above types, false otherwise
     */
    public static boolean layerHasConvolutionLayout(Layer layer) {
        return layer instanceof ConvolutionLayer ||
                layer instanceof SubsamplingLayer ||
                layer instanceof SpaceToBatchLayer ||
                layer instanceof Upsampling2D ||
                layer instanceof SpaceToDepthLayer ||
                layer instanceof ZeroPaddingLayer ||
                layer instanceof SeparableConvolution2D ||
                layer instanceof Deconvolution2D ||
                layer instanceof Cropping2D ||
                layer instanceof DepthwiseConvolution2D;
    }

    /**
     * Get the format for a given layer.
     * {@link #layerHasConvolutionLayout(Layer)}
     * should return true on the given {@link Layer}
     * type or an {@link IllegalArgumentException}
     * will be thrown
     * @param layer the input layer
     * @return the {@link CNN2DFormat} for the given
     * layer
     */
    public static CNN2DFormat getFormatForLayer(Layer layer) {
       if(layer instanceof Convolution1DLayer) {
           Convolution1DLayer convolution1DLayer = (Convolution1DLayer) layer;
           return convolution1DLayer.getCnn2dDataFormat();
       } else if(layer instanceof ConvolutionLayer) {
            ConvolutionLayer convolutionLayer = (ConvolutionLayer) layer;
            return convolutionLayer.getCnn2dDataFormat();
        } else if(layer instanceof SubsamplingLayer) {
            SubsamplingLayer subsamplingLayer = (SubsamplingLayer) layer;
            return subsamplingLayer.getCnn2dDataFormat();
        } else if(layer instanceof SpaceToBatchLayer) {
            SpaceToBatchLayer spaceToBatchLayer = (SpaceToBatchLayer) layer;
            return spaceToBatchLayer.getFormat();
        } else if(layer instanceof Upsampling2D) {
            Upsampling2D upsampling2D = (Upsampling2D) layer;
            return upsampling2D.getFormat();
        } else if(layer instanceof SpaceToDepthLayer) {
            SpaceToDepthLayer spaceToDepthLayer = (SpaceToDepthLayer) layer;
            return spaceToDepthLayer.getDataFormat();
        } else if(layer instanceof ZeroPaddingLayer) {
            ZeroPaddingLayer zeroPaddingLayer = (ZeroPaddingLayer) layer;
            return zeroPaddingLayer.getDataFormat();
        } else if(layer instanceof SeparableConvolution2D) {
           SeparableConvolution2D separableConvolution2D = (SeparableConvolution2D) layer;
           return separableConvolution2D.getCnn2dDataFormat();
       } else if(layer instanceof Deconvolution2D) {
           Deconvolution2D deconvolution2D = (Deconvolution2D) layer;
           return deconvolution2D.getCnn2dDataFormat();
       } else if(layer instanceof DepthwiseConvolution2D) {
           DepthwiseConvolution2D depthwiseConvolution2D = (DepthwiseConvolution2D) layer;
           return depthwiseConvolution2D.getCnn2dDataFormat();
       } else if(layer instanceof Cropping2D) {
           Cropping2D cropping2D = (Cropping2D) layer;
           return cropping2D.getDataFormat();
       }
        else throw new IllegalArgumentException("Illegal type given " + layer.getClass().getName());
    }


    /**
     * Convert {@link ConvolutionMode}
     * to {@link PaddingMode}
     * {@link ConvolutionMode#Same} : {@link PaddingMode#SAME}
     * {@link ConvolutionMode#Strict}, {@link ConvolutionMode#Truncate} : {@link PaddingMode#VALID}
     * {@link ConvolutionMode#Causal} : {@link PaddingMode#VALID}
     * @param convolutionMode the input {@link ConvolutionMode}
     * @return the equivalent {@link PaddingMode}
     */
    public static PaddingMode paddingModeForConvolutionMode(ConvolutionMode convolutionMode) {
        switch(convolutionMode) {
            case Same:
                return PaddingMode.SAME;
            case Causal:
               return PaddingMode.CAUSAL;
            case Strict:
            case Truncate:
                return PaddingMode.VALID;
            default:
                throw new IllegalArgumentException("Invalid input convolution mode: " + convolutionMode);
        }
    }

    /**
     * Get the output size (height/width) for the given input data and CNN configuration
     *
     * @param inputData       Input data
     * @param kernel          Kernel size (height/width)
     * @param strides         Strides (height/width)
     * @param padding         Padding (height/width)
     * @param convolutionMode Convolution mode (Same, Strict, Truncate)
     * @param dilation        Kernel dilation (height/width)
     * @param format          Format for input activations
     * @return Output size: int[2] with output height/width
     */
    public static int[] getOutputSize(INDArray inputData, int[] kernel, int[] strides, int[] padding,
                                      ConvolutionMode convolutionMode, int[] dilation, CNN2DFormat format) {
        int hDim = 2;
        int wDim = 3;

        if(format == CNN2DFormat.NHWC) {
            hDim = 1;
            wDim = 2;
        }

        if (inputData.size(hDim) > Integer.MAX_VALUE || inputData.size(wDim) > Integer.MAX_VALUE)
            throw new ND4JArraySizeException();
        int inH = (int) inputData.size(hDim);
        int inW = (int) inputData.size(wDim);

        //Determine the effective kernel size, accounting for dilation
        //http://deeplearning.net/software/theano/tutorial/conv_arithmetic.html#dilated-convolutions
        int[] eKernel = effectiveKernelSize(kernel, dilation);
        boolean atrous = (eKernel == kernel);

        int[] inShape = new int[]{inH, inW};
        validateShapes(inputData, eKernel, strides, padding, convolutionMode, dilation, inShape, atrous);

        if (convolutionMode == ConvolutionMode.Same || convolutionMode == ConvolutionMode.Causal) {

            int outH = (int) Math.ceil(inH / ((double) strides[0]));
            int outW = (int) Math.ceil(inW / ((double) strides[1]));

            return new int[]{outH, outW};
        }

        int hOut = (inH - eKernel[0] + 2 * padding[0]) / strides[0] + 1;
        int wOut = (inW - eKernel[1] + 2 * padding[1]) / strides[1] + 1;

        return new int[]{hOut, wOut};
    }

    public static void validateShapes(INDArray inputData, int[] eKernel, int[] strides, int[] padding,
                                      ConvolutionMode convolutionMode, int[] dilation, int[] inShape,
                                      boolean atrous) {

        int inH = inShape[0];
        int inW = inShape[1];

        boolean t = (convolutionMode == ConvolutionMode.Truncate);

        if (t && (eKernel[0] <= 0 || eKernel[0] > inH + 2 * padding[0])) {
            StringBuilder sb = new StringBuilder();
            sb.append("Invalid input data or configuration: ");
            if (atrous) sb.append("effective ");
            sb.append("kernel height and input height must satisfy 0 < ");
            if (atrous) sb.append("effective ");
            sb.append("kernel height <= input height + 2 * padding height. \nGot ");
            if (atrous) sb.append("effective ");
            sb.append("kernel height = ").append(eKernel[0]).append(", input height = ").append(inH)
                    .append(" and padding height = ").append(padding[0]).append(" which do not satisfy 0 < ")
                    .append(eKernel[0]).append(" <= ").append(inH + 2 * padding[0])
                    .append(getCommonErrorMsg(inputData, eKernel, strides, padding, dilation));

            throw new DL4JInvalidInputException(sb.toString());
        }

        if (t && (eKernel[1] <= 0 || eKernel[1] > inW + 2 * padding[1])) {
            StringBuilder sb = new StringBuilder();
            sb.append("Invalid input data or configuration: ");
            if (atrous) sb.append("effective ");
            sb.append("kernel width and input width must satisfy  0 < kernel width <= input width + 2 * padding width. ");
            sb.append("\nGot ");
            if (atrous) sb.append("effective ");
            sb.append("kernel width = ").append(eKernel[1]).append(", input width = ").append(inW)
                    .append(" and padding width = ").append(padding[1]).append(" which do not satisfy 0 < ")
                    .append(eKernel[1]).append(" <= ").append(inW + 2 * padding[1])
                    .append("\nInput size: [numExamples,inputDepth,inputHeight,inputWidth]=")
                    .append(Arrays.toString(inputData.shape()))
                    .append(getCommonErrorMsg(inputData, eKernel, strides, padding, dilation));

            throw new DL4JInvalidInputException(sb.toString());
        }

        if (eKernel.length == 3 && t && (eKernel[2] <= 0 || eKernel[2] > inShape[2] + 2 * padding[2])) {
            int inD = inShape[2];
            StringBuilder sb = new StringBuilder();
            sb.append("Invalid input data or configuration: ");
            if (atrous) sb.append("effective ");
            sb.append("kernel channels and input channels must satisfy 0 < ");
            if (atrous) sb.append("effective ");
            sb.append("kernel channels <= input channels + 2 * padding channels. \nGot ");
            if (atrous) sb.append("effective ");
            sb.append("kernel channels = ").append(eKernel[2]).append(", input channels = ").append(inD)
                    .append(" and padding height = ").append(padding[2]).append(" which do not satisfy 0 < ")
                    .append(eKernel[2]).append(" <= ").append(inD + 2 * padding[2])
                    .append(getCommonErrorMsg(inputData, eKernel, strides, padding, dilation));

            throw new DL4JInvalidInputException(sb.toString());
        }

        if (convolutionMode == ConvolutionMode.Strict) {
            if ((inH - eKernel[0] + 2 * padding[0]) % strides[0] != 0) {
                double d = (inH - eKernel[0] + 2 * padding[0]) / ((double) strides[0]) + 1.0;
                String str = String.format("%.2f", d);
                int truncated = (int) d;
                int sameSize = (int) Math.ceil(inH / ((double) strides[0]));

                StringBuilder sb = new StringBuilder();
                sb.append("Invalid input data or configuration: Combination of kernel size, stride and padding are not valid for given input height, using ConvolutionMode.Strict\n")
                        .append("ConvolutionMode.Strict requires: output height = (input height - kernelSize + 2*padding)/stride + 1 to be an integer. Got: (")
                        .append(inH).append(" - ").append(eKernel[0]).append(" + 2*").append(padding[0]).append(")/").append(strides[0]).append(" + 1 = ")
                        .append(str).append("\n").append("See \"Constraints on strides\" at http://cs231n.github.io/convolutional-networks/ and ConvolutionType enumeration Javadoc.\n")
                        .append("To truncate/crop the input, such that output height = floor(").append(str).append(") = ")
                        .append(truncated).append(", use ConvolutionType.Truncate.\n")
                        .append("Alternatively use ConvolutionType.Same, which will use padding to give an output height of ceil(")
                        .append(inH).append("/").append(strides[0]).append(")=").append(sameSize).append(getCommonErrorMsg(inputData, eKernel, strides, padding, dilation));

                throw new DL4JInvalidConfigException(sb.toString());
            }

            if ((inW - eKernel[1] + 2 * padding[1]) % strides[1] != 0) {
                double d = (inW - eKernel[1] + 2 * padding[1]) / ((double) strides[1]) + 1.0;
                String str = String.format("%.2f", d);
                int truncated = (int) d;
                int sameSize = (int) Math.ceil(inW / ((double) strides[1]));
                StringBuilder sb = new StringBuilder();
                sb.append("Invalid input data or configuration: Combination of kernel size, stride and padding are not valid for given input width, using ConvolutionMode.Strict\n")
                        .append("ConvolutionMode.Strict requires: output width = (input - kernelSize + 2*padding)/stride + 1 to be an integer. Got: (")
                        .append(inW).append(" - ").append(eKernel[1]).append(" + 2*").append(padding[1])
                        .append(")/").append(strides[1]).append(" + 1 = ").append(str).append("\n")
                        .append("See \"Constraints on strides\" at http://cs231n.github.io/convolutional-networks/ and ConvolutionType enumeration Javadoc.\n")
                        .append("To truncate/crop the input, such that output width = floor(").append(str).append(") = ")
                        .append(truncated).append(", use ConvolutionType.Truncate.\n")
                        .append("Alternatively use ConvolutionType.Same, which will use padding to give an output width of ceil(")
                        .append(inW).append("/").append(strides[1]).append(")=").append(sameSize)
                        .append(getCommonErrorMsg(inputData, eKernel, strides, padding, dilation));
                throw new DL4JInvalidConfigException(
                        sb.toString());
            }

            if (eKernel.length == 3 && (inShape[2] - eKernel[2] + 2 * padding[2]) % strides[2] != 0) {
                int inD = inShape[2];
                double d = (inD - eKernel[2] + 2 * padding[2]) / ((double) strides[2]) + 1.0;
                String str = String.format("%.2f", d);
                int truncated = (int) d;
                int sameSize = (int) Math.ceil(inD / ((double) strides[2]));
                StringBuilder sb = new StringBuilder();
                sb.append("Invalid input data or configuration: Combination of kernel size, stride and padding are not valid for given input width, using ConvolutionMode.Strict\n")
                        .append("ConvolutionMode.Strict requires: output channels = (input - kernelSize + 2*padding)/stride + 1 to be an integer. Got: (")
                        .append(inD).append(" - ").append(eKernel[2]).append(" + 2*").append(padding[2])
                        .append(")/").append(strides[1]).append(" + 1 = ").append(str).append("\n")
                        .append("See \"Constraints on strides\" at http://cs231n.github.io/convolutional-networks/ and ConvolutionType enumeration Javadoc.\n")
                        .append("To truncate/crop the input, such that output width = floor(").append(str).append(") = ")
                        .append(truncated).append(", use ConvolutionType.Truncate.\n")
                        .append("Alternatively use ConvolutionType.Same, which will use padding to give an output width of ceil(")
                        .append(inW).append("/").append(strides[2]).append(")=").append(sameSize)
                        .append(getCommonErrorMsg(inputData, eKernel, strides, padding, dilation));
                throw new DL4JInvalidConfigException(
                        sb.toString());
            }
        }

    }

    public static int[] effectiveKernelSize(int[] kernel, int[] dilation) {
        //Determine the effective kernel size, accounting for dilation
        //http://deeplearning.net/software/theano/tutorial/conv_arithmetic.html#dilated-convolutions
        if (kernel.length == 2) {
            if (dilation[0] == 1 && dilation[1] == 1) {
                return kernel;
            } else {
                return new int[]{
                        kernel[0] + (kernel[0] - 1) * (dilation[0] - 1),
                        kernel[1] + (kernel[1] - 1) * (dilation[1] - 1)};
            }
        } else if (kernel.length == 3) {
            if (dilation[0] == 1 && dilation[1] == 1 && dilation[2] == 1) {
                return kernel;
            } else {
                return new int[]{
                        kernel[0] + (kernel[0] - 1) * (dilation[0] - 1),
                        kernel[1] + (kernel[1] - 1) * (dilation[1] - 1),
                        kernel[2] + (kernel[2] - 1) * (dilation[2] - 1)
                };
            }
        } else {
            throw new IllegalArgumentException("Kernel size has to be either two or three, got: " + kernel.length);
        }
    }

    private static String getCommonErrorMsg(INDArray inputData, int[] kernel, int[] strides, int[] padding, int[] dilation) {
        String s = "\nInput size: [numExamples,inputDepth,inputHeight,inputWidth]=" + Arrays.toString(inputData.shape())
                + ", inputKernel=" + Arrays.toString(kernel);
        if (dilation[0] != 1 || dilation[1] != 1) {
            int[] effectiveKernel = effectiveKernelSize(kernel, dilation);
            s += ", effectiveKernelGivenDilation=" + Arrays.toString(effectiveKernel);
        }
        return s + ", strides=" + Arrays.toString(strides) + ", padding="
                + Arrays.toString(padding) + ", dilation=" + Arrays.toString(dilation);
    }

    /**
     * Get top and left padding for same mode only.
     *
     * @param outSize  Output size (length 2 array, height dimension first)
     * @param inSize   Input size (length 2 array, height dimension first)
     * @param kernel   Kernel size (length 2 array, height dimension first)
     * @param strides  Strides  (length 2 array, height dimension first)
     * @param dilation Dilation (length 2 array, height dimension first)
     * @return Top left padding (length 2 array, height dimension first)
     */
    public static int[] getSameModeTopLeftPadding(int[] outSize, int[] inSize, int[] kernel, int[] strides, int[] dilation) {
        int[] eKernel = effectiveKernelSize(kernel, dilation);
        int[] outPad = new int[kernel.length];
        boolean allGt0 = true;

        for( int i = 0; i < kernel.length; i++) {
            outPad[i] = ((outSize[i] - 1) * strides[i] + eKernel[i] - inSize[i]) / 2; //Note that padBottom is 1 bigger than this if bracketed term is not divisible by 2
            allGt0 &= outPad[i] >= 0;
        }

        Preconditions.checkState(allGt0, "Invalid padding values calculated: %s - layer configuration is invalid? Input size %s, output size %s, kernel %s, strides %s, dilation %s",
                outPad, inSize, outSize, kernel, strides, dilation);

        return outPad;
    }

    /**
     * Get bottom and right padding for same mode only.
     *
     * @param outSize  Output size (length 2 array, height dimension first)
     * @param inSize   Input size (length 2 array, height dimension first)
     * @param kernel   Kernel size (length 2 array, height dimension first)
     * @param strides  Strides  (length 2 array, height dimension first)
     * @param dilation Dilation (length 2 array, height dimension first)
     * @return Bottom right padding (length 2 array, height dimension first)
     */
    public static int[] getSameModeBottomRightPadding(int[] outSize, int[] inSize, int[] kernel, int[] strides, int[] dilation) {
        int[] eKernel = effectiveKernelSize(kernel, dilation);
        int[] outPad = new int[2];
        outPad[0] = ((outSize[0] - 1) * strides[0] + eKernel[0] - inSize[0] + 1) / 2; //Note that padTop is 1 smaller than this if bracketed term is not divisible by 2
        outPad[1] = ((outSize[1] - 1) * strides[1] + eKernel[1] - inSize[1] + 1) / 2; //As above
        Preconditions.checkState(outPad[0] >= 0 && outPad[1] >= 0, "Invalid padding values calculated: %s - layer configuration is invalid? Input size %s, output size %s, kernel %s, strides %s, dilation %s",
                outPad, inSize, outSize, kernel, strides, dilation);
        return outPad;
    }

    /**
     * Get the height and width
     * from the configuration
     *
     * @param conf the configuration to get height and width from
     * @return the configuration to get height and width from
     */
    public static int[] getHeightAndWidth(NeuralNetConfiguration conf) {
        return getHeightAndWidth(
                ((org.deeplearning4j.nn.conf.layers.ConvolutionLayer) conf.getLayer()).getKernelSize());
    }


    /**
     * @param conf the configuration to get
     *             the number of kernels from
     * @return the number of kernels/filters to apply
     */
    public static long numFeatureMap(NeuralNetConfiguration conf) {
        return ((org.deeplearning4j.nn.conf.layers.ConvolutionLayer) conf.getLayer()).getNOut();
    }

    /**
     * Get the height and width
     * for an image
     *
     * @param shape the shape of the image
     * @return the height and width for the image
     */
    public static int[] getHeightAndWidth(int[] shape) {
        if (shape.length < 2)
            throw new IllegalArgumentException("No width and height able to be found: array must be at least length 2");
        return new int[]{shape[shape.length - 1], shape[shape.length - 2]};
    }

    /**
     * Returns the number of
     * feature maps for a given shape (must be at least 3 dimensions
     *
     * @param shape the shape to get the
     *              number of feature maps for
     * @return the number of feature maps
     * for a particular shape
     */
    public static int numChannels(int[] shape) {
        if (shape.length < 4)
            return 1;
        return shape[1];
    }


    /**
     * Check that the convolution mode is consistent with the padding specification
     */
    public static void validateConvolutionModePadding(ConvolutionMode mode, int[] padding) {
        if (mode == ConvolutionMode.Same) {
            boolean nullPadding = true;
            for (int i : padding) {
                if (i != 0) nullPadding = false;
            }
            if (!nullPadding)
                throw new IllegalArgumentException("Padding cannot be used when using the `same' convolution mode");
        }
    }

    /**
     * Perform validation on the CNN layer kernel/stride/padding. Expect 2d int[], with values > 0 for kernel size and
     * stride, and values >= 0 for padding.
     *
     * @param kernelSize Kernel size array to check
     * @param stride     Stride array to check
     * @param padding    Padding array to check
     */
    public static void validateCnnKernelStridePadding(int[] kernelSize, int[] stride, int[] padding) {
        if (kernelSize == null || kernelSize.length != 2) {
            throw new IllegalStateException("Invalid kernel size: expected int[] of length 2, got "
                    + (kernelSize == null ? null : Arrays.toString(kernelSize)));
        }

        if (stride == null || stride.length != 2) {
            throw new IllegalStateException("Invalid stride configuration: expected int[] of length 2, got "
                    + (stride == null ? null : Arrays.toString(stride)));
        }

        if (padding == null || padding.length != 2) {
            throw new IllegalStateException("Invalid padding configuration: expected int[] of length 2, got "
                    + (padding == null ? null : Arrays.toString(padding)));
        }

        if (kernelSize[0] <= 0 || kernelSize[1] <= 0) {
            throw new IllegalStateException(
                    "Invalid kernel size: values must be positive (> 0) for all dimensions. Got: "
                            + Arrays.toString(kernelSize));
        }

        if (stride[0] <= 0 || stride[1] <= 0) {
            throw new IllegalStateException(
                    "Invalid stride configuration: values must be positive (> 0) for all dimensions. Got: "
                            + Arrays.toString(stride));
        }

        if (padding[0] < 0 || padding[1] < 0) {
            throw new IllegalStateException(
                    "Invalid padding configuration: values must be >= 0 for all dimensions. Got: "
                            + Arrays.toString(padding));
        }
    }


    public static INDArray reshape4dTo2d(INDArray in, LayerWorkspaceMgr workspaceMgr, ArrayType type) {
        return reshape4dTo2d(in, CNN2DFormat.NCHW, workspaceMgr, type);
    }

    public static INDArray reshape4dTo2d(INDArray in, CNN2DFormat format, LayerWorkspaceMgr workspaceMgr, ArrayType type){
        if (in.rank() != 4)
            throw new IllegalArgumentException("Invalid input: expect NDArray with rank 4, got rank " + in.rank()
                    + " with shape " + Arrays.toString(in.shape()));
        val shape = in.shape();

        if(format == CNN2DFormat.NCHW){
            //Reshape: from [n,c,h,w] to [n*h*w,c]
            INDArray out = in.permute(0, 2, 3, 1);
            if (out.ordering() != 'c' || !Shape.strideDescendingCAscendingF(out))
                out = workspaceMgr.dup(type, out, 'c');
            return workspaceMgr.leverageTo(type, out.reshape('c', shape[0] * shape[2] * shape[3], shape[1]));
        } else {
            //Reshape: from [n,h,w,c] to [n*h*w,c]
            if (in.ordering() != 'c' || !Shape.strideDescendingCAscendingF(in))
                in = workspaceMgr.dup(type, in, 'c');
            return workspaceMgr.leverageTo(type, in.reshape('c', shape[0] * shape[1] * shape[2], shape[3]));
        }
    }

    public static INDArray reshape5dTo2d(@NonNull Convolution3D.DataFormat format, INDArray in, LayerWorkspaceMgr workspaceMgr, ArrayType type){
        Preconditions.checkState(in.rank() == 5, "Invalid input: expect NDArray with rank 5, got rank %ndRank with shape %ndShape", in, in);
        //Reshape: from either [n,c,d,h,w] to [n*d*h*w,c] (NCDHW format)
        // or reshape from [n,d,h,w,c] to [n*d*h*w,c] (NDHWC format)
        if(format != Convolution3D.DataFormat.NDHWC){
            in = in.permute(0, 2, 3, 4, 1);
        }

        if(in.ordering() != 'c' || !Shape.hasDefaultStridesForShape(in))
            in = workspaceMgr.dup(type, in, 'c');
        return workspaceMgr.leverageTo(type, in.reshape('c', in.size(0)*in.size(1)*in.size(2)*in.size(3), in.size(4)));
    }

    public static INDArray reshapeCnn3dMask(@NonNull Convolution3D.DataFormat format, INDArray mask, INDArray label, LayerWorkspaceMgr workspaceMgr, ArrayType type){
        if(mask == null)
            return null;
        Preconditions.checkState(mask.rank() == 5, "Expected rank 5 mask for Cnn3DLossLayer in a shape broadcastable to labels shape:" +
                " got mask shape %ndShape with label shape %ndShape", mask, label);

        if(mask.equalShapes(label) ||
                (format == Convolution3D.DataFormat.NDHWC && mask.size(0) == label.size(0) && mask.size(1) == label.size(1) && mask.size(2) == label.size(2) && mask.size(3) == label.size(3)) ||
                (format == Convolution3D.DataFormat.NDHWC && mask.size(0) == label.size(0) && mask.size(2) == label.size(2) && mask.size(3) == label.size(3) && mask.size(4) == label.size(4))) {
            //Already OK shape for reshaping
            return reshape5dTo2d(format, mask, workspaceMgr, type);
        } else {
            //Need to broadcast first
            long[] lShape = label.shape().clone();
            int channelIdx = format == Convolution3D.DataFormat.NCDHW ? 1 : 4;
            lShape[channelIdx] = mask.size(channelIdx);     //Keep existing channel size

            INDArray bMask = workspaceMgr.createUninitialized(type, mask.dataType(), lShape, 'c');
            Nd4j.exec(new Assign(new INDArray[]{bMask, mask}, new INDArray[]{bMask}));
            return reshape5dTo2d(format, bMask, workspaceMgr, type);
        }
    }

    public static INDArray reshape2dTo4d(INDArray in2d, long[] toShape, CNN2DFormat format, LayerWorkspaceMgr workspaceMgr, ArrayType type){
        if(in2d.rank() != 2)
            throw new IllegalArgumentException("Invalid input: expect NDArray with rank 2");
        if (toShape.length != 4)
            throw new IllegalArgumentException("Invalid input: expect toShape with 4 elements: got " + Arrays.toString(toShape));

        if (in2d.ordering() != 'c' || !Shape.hasDefaultStridesForShape(in2d))
            in2d = workspaceMgr.dup(type, in2d, 'c');

        if(format == CNN2DFormat.NCHW) {
            //Reshape: from [n*h*w,c] to [n,h,w,c] to [n,c,h,w]
            INDArray out = in2d.reshape('c', toShape[0], toShape[2], toShape[3], toShape[1]);
            return workspaceMgr.leverageTo(type, out.permute(0, 3, 1, 2));
        } else {
            //Reshape: from [n*h*w,c] to [n,h,w,c]
            return workspaceMgr.leverageTo(type, in2d.reshape('c', toShape));
        }
    }

    public static INDArray reshape2dTo5d(Convolution3D.DataFormat format, INDArray in2d, long n, long d, long h, long w, long ch, LayerWorkspaceMgr workspaceMgr, ArrayType type){
        if(in2d.rank() != 2)
            throw new IllegalArgumentException("Invalid input: expect NDArray with rank 2");

        //Reshape: from [n*d*h*w,c] to [n,d,h,w,c]; if NCDHW format permute to [n,c,d,h,w]
        if(in2d.ordering() != 'c' || !Shape.hasDefaultStridesForShape(in2d))
            in2d = workspaceMgr.dup(type, in2d, 'c');

        INDArray ndhwc = in2d.reshape('c', n, d, h, w, ch);
        if(format == Convolution3D.DataFormat.NDHWC){
            return workspaceMgr.leverageTo(type, ndhwc);
        } else {
            return workspaceMgr.leverageTo(type, ndhwc.permute(0, 4, 1, 2, 3));
        }
    }

    /**
     * @deprecated Use {@link #reshapeMaskIfRequired(INDArray, INDArray, CNN2DFormat, LayerWorkspaceMgr, ArrayType)}
     */
    @Deprecated
    public static INDArray reshapeMaskIfRequired(INDArray mask, INDArray output, LayerWorkspaceMgr workspaceMgr, ArrayType type) {
        return reshapeMaskIfRequired(mask, output, null, workspaceMgr, type);
    }

    public static INDArray reshapeMaskIfRequired(INDArray mask, INDArray output, CNN2DFormat format, LayerWorkspaceMgr workspaceMgr, ArrayType type){
        if (mask == null)
            return null;
        if (mask.rank() == 2) {
            return adapt2dMask(mask, output, format, workspaceMgr, type);
        } else if (mask.rank() == 3) {
            return reshape3dMask(mask, workspaceMgr, type);
        } else {
            return reshape4dTo2d(mask, workspaceMgr, type);
        }
    }

    public static INDArray adapt2dMask(INDArray mask, INDArray output, @NonNull CNN2DFormat format, LayerWorkspaceMgr workspaceMgr, ArrayType type){

        if(format == CNN2DFormat.NCHW){
            //Input in [n,c,h,w] which is reshaped to [n*h*w,c], mask is [n,1]
            //So: We'll broadcast to [n,1,h,w] then reshape to [n*h*w,1] required for the current DL4J loss functions...

            //Use workaround for: https://github.com/deeplearning4j/nd4j/issues/2066

            val s = output.shape();
            INDArray bMask = workspaceMgr.create(type, mask.dataType(), new long[]{s[0], 1, s[2], s[3]}, 'c');
            Nd4j.getExecutioner().exec(new BroadcastCopyOp(bMask, mask, bMask, 0, 1));

            INDArray bMaskPermute = bMask.permute(0, 2, 3, 1).dup('c');  //Not sure if dup is strictly necessary...

            return workspaceMgr.leverageTo(type, bMaskPermute.reshape('c', s[0] * s[2] * s[3], 1));
        } else {
            //Input in [n,h,w,c] which is reshaped to [n*h*w,c], mask is [n,1]
            //So: We'll broadcast to [n,h,w,1] then reshape to [n*h*w,1] required for the current DL4J loss functions...
            val s = output.shape();
            INDArray bMask = workspaceMgr.create(type, mask.dataType(), new long[]{s[0], s[2], s[3], 1}, 'c');
            Nd4j.getExecutioner().exec(new BroadcastCopyOp(bMask, mask, bMask, 0, 3));

            return workspaceMgr.leverageTo(type, bMask.reshape('c', s[0] * s[2] * s[3], 1));
        }
    }

    public static INDArray reshape3dMask(INDArray mask, LayerWorkspaceMgr workspaceMgr, ArrayType type){
        //Assume mask has shape [n,h,w] and will be broadcast along dimension
        if(mask.ordering() != 'c' || !Shape.hasDefaultStridesForShape(mask))
            mask = workspaceMgr.dup(type, mask, 'c');

        return mask.reshape('c', mask.length(), 1);
    }

    public static INDArray reshape4dMask(INDArray mask, LayerWorkspaceMgr workspaceMgr, ArrayType arrayType) {
        return reshape4dTo2d(mask, workspaceMgr, arrayType);
    }

    /**
     * Get heigh/width/channels as length 3 int[] from the InputType
     *
     * @param inputType Input type to get
     * @return Length
     */
    public static int[] getHWDFromInputType(InputType inputType) {
        int inH;
        int inW;
        int inDepth;

        if (inputType instanceof InputType.InputTypeConvolutional) {
            InputType.InputTypeConvolutional conv = (InputType.InputTypeConvolutional) inputType;
            if (conv.getHeight() > Integer.MAX_VALUE || conv.getWidth() > Integer.MAX_VALUE ||
                conv.getChannels() > Integer.MAX_VALUE){
                throw new ND4JArraySizeException();
            }
            inH = (int) conv.getHeight();
            inW = (int) conv.getWidth();
            inDepth = (int) conv.getChannels();
        } else if (inputType instanceof InputType.InputTypeConvolutionalFlat) {
            InputType.InputTypeConvolutionalFlat conv = (InputType.InputTypeConvolutionalFlat) inputType;
            if (conv.getHeight() > Integer.MAX_VALUE || conv.getWidth() > Integer.MAX_VALUE ||
                conv.getDepth() > Integer.MAX_VALUE) {
                throw new ND4JArraySizeException();
            }
            inH = (int) conv.getHeight();
            inW = (int) conv.getWidth();
            inDepth = (int) conv.getDepth();
        } else {
            throw new IllegalStateException(
                    "Invalid input type: expected InputTypeConvolutional or InputTypeConvolutionalFlat."
                            + " Got: " + inputType);
        }
        return new int[]{inH, inW, inDepth};
    }

    /**
     * Given a mask array for a 1D CNN layer of shape [minibatch, sequenceLength], reduce the mask according to the 1D CNN layer configuration.
     * Unlike RNN layers, 1D CNN layers may down-sample the data; consequently, we need to down-sample the mask array
     * in the same way, to maintain the correspondence between the masks and the output activations
     *
     * @param in       Input size
     * @param kernel   Kernel size
     * @param stride   Stride
     * @param padding  Padding
     * @param dilation Dilation
     * @param cm       Convolution mode
     * @return Reduced mask
     */
    public static INDArray cnn1dMaskReduction(INDArray in, int kernel, int stride, int padding, int dilation, ConvolutionMode cm){
        Preconditions.checkState(in.rank()==2, "Rank must be 2 for cnn1d mask array - shape ", in.shape());
        if((cm == ConvolutionMode.Same || cm == ConvolutionMode.Causal) && stride == 1 ){
            return in;
        }

        if(!Shape.hasDefaultStridesForShape(in)){
            in = in.dup();
        }

        INDArray reshaped4d = in.reshape(in.size(0), 1, in.size(1), 1);

        int[] outSize;
        int[] pad = null;
        int[] k = new int[]{kernel,1};
        int[] s = new int[]{stride, 1};
        int[] d = new int[]{dilation, 1};
        if (cm == ConvolutionMode.Same || cm == ConvolutionMode.Causal) {
            outSize = ConvolutionUtils.getOutputSize(reshaped4d, k, s, null, cm, d, CNN2DFormat.NCHW); //Also performs validation
        } else {
            pad = new int[]{padding, 0};
            outSize = ConvolutionUtils.getOutputSize(reshaped4d, k, s, pad, cm, d, CNN2DFormat.NCHW); //Also performs validation
        }
        int outH = outSize[0];

        INDArray output = Nd4j.createUninitialized(new int[]{(int)in.size(0), 1, outH, 1}, 'c');

        DynamicCustomOp op = new MaxPooling2D(reshaped4d, output, Pooling2DConfig.builder()
                .kH(k[0]).kW(k[1])
                .sH(s[0]).sW(s[1])
                .pH(pad == null ? 0 : pad[0]).pW(pad == null ? 0 : pad[1])
                .dH(d[0]).dW(d[1])
                .isSameMode(cm == ConvolutionMode.Same || cm == ConvolutionMode.Causal)
                .isNHWC(false)
                .build());

        Nd4j.getExecutioner().exec(op);
        return output.reshape('c', in.size(0), outH);
    }

    /**
     * Reduce a 2d CNN layer mask array (of 0s and 1s) according to the layer configuration. Note that when a CNN layer
     * changes the shape of the activations (for example, stride > 1) the corresponding mask array needs to change shape
     * also (as there is a correspondence between the two). This method performs the forward pass for the mask.
     * @param inMask          Input mask array - rank 4, shape [mb,c,h,1] or [mb,c,w,1] or [mb,c,h,w]
     * @param kernel          Kernel configuration for the layer
     * @param stride          Stride
     * @param padding         Padding
     * @param dilation        Dilation
     * @param convolutionMode Convolution mode
     * @return The mask array corresponding to the network output
     */
    public static INDArray cnn2dMaskReduction(INDArray inMask, int[] kernel, int[] stride, int[] padding, int[] dilation, ConvolutionMode convolutionMode ){
        //Mask array should be broadcastable with CNN activations. Thus should have shape [mb,x,y,z]
        //where:
        // x == 1 OR channels
        // y == 1 OR height
        // z == 1 OR width

        if(inMask.rank() != 4){
            throw new IllegalStateException("Expected rank 4 mask array for 2D CNN layers. Mask arrays for 2D CNN layers " +
                    "must have shape [batchSize,channels,X,Y] where X = (1 or activationsHeight) and Y = (1 or activationsWidth): " +
                    "Got rank " + inMask.rank() + " array with shape " + Arrays.toString(inMask.shape()));
        }

        if(convolutionMode == ConvolutionMode.Same && stride[0] == 1 && stride[1] == 1){
            //Output activations size same as input activations size
            return inMask;
        }

        if(inMask.size(2) == 1 && inMask.size(3) == 1){
            //per-example mask - broadcast along all channels/x/y
            return inMask;
        }

        int[] k;
        int[] s;
        int[] p;
        int[] d;
        if(inMask.size(3) == 1){
            //[mb,x,y,1] case -> pool mask along height
            k = new int[]{kernel[0],1};
            s = new int[]{stride[0], 1};
            p = new int[]{padding[0], 0};
            d = new int[]{dilation[0], 1};
        } else if(inMask.size(2) == 1){
            //[mb,x,1,z] case -> pool mask along width
            k = new int[]{1, kernel[1]};
            s = new int[]{1, stride[1]};
            p = new int[]{0, padding[1]};
            d = new int[]{1, dilation[1]};
        } else {
            //[mb,x,y,z] -> pool mask along height and width
            k = kernel;
            s = stride;
            p = padding;
            d = dilation;
        }

        int[] outSize = ConvolutionUtils.getOutputSize(inMask, k, s, p, convolutionMode, d); //Also performs validation
        boolean allEq = true;
        for( int i=0; i<outSize.length; i++ ){
            if(outSize[i] != inMask.size(i)){
                allEq = false;
                break;
            }
        }
        if(allEq){
            //Same output size -> same mask size
            return inMask;
        }

        long[] outArraySize = new long[]{inMask.size(0), inMask.size(1), outSize[0], outSize[1]};
        INDArray outMask = Nd4j.createUninitialized(inMask.dataType(), outArraySize);

        DynamicCustomOp op = new MaxPooling2D(inMask, outMask, Pooling2DConfig.builder()
                .kH(k[0]).kW(k[1])
                .sH(s[0]).sW(s[1])
                .pH(p[0]).pW(p[1])
                .dH(d[0]).dW(d[1])
                .isSameMode(convolutionMode == ConvolutionMode.Same)
                .isNHWC(false)
                .build());

        Nd4j.exec(op);
        return outMask;
    }
}
