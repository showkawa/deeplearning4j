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

package org.deeplearning4j.nn.layers.convolution;


import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.common.config.DL4JClassLoading;
import org.deeplearning4j.exception.DL4JInvalidInputException;
import org.deeplearning4j.nn.api.MaskState;
import org.deeplearning4j.nn.conf.CNN2DFormat;
import org.deeplearning4j.nn.conf.CacheMode;
import org.deeplearning4j.nn.conf.ConvolutionMode;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.layers.BaseLayer;
import org.deeplearning4j.nn.layers.LayerHelper;
import org.deeplearning4j.nn.layers.mkldnn.MKLDNNConvHelper;
import org.deeplearning4j.nn.params.ConvolutionParamInitializer;
import org.deeplearning4j.util.ConvolutionUtils;
import org.nd4j.common.base.Preconditions;
import org.nd4j.linalg.activations.IActivation;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.memory.MemoryWorkspace;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.linalg.convolution.Convolution;
import org.nd4j.linalg.exception.ND4JArraySizeException;
import org.nd4j.linalg.exception.ND4JOpProfilerException;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.common.primitives.Pair;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.nd4j.common.util.OneTimeLogger;

import java.util.Arrays;


/**
 * Convolution layer
 *
 * @author Adam Gibson (original impl), Alex Black (current version)
 */
@Slf4j
public class ConvolutionLayer extends BaseLayer<org.deeplearning4j.nn.conf.layers.ConvolutionLayer> {

    protected INDArray i2d;
    protected ConvolutionHelper helper = null;
    protected int helperCountFail = 0;
    protected ConvolutionMode convolutionMode;
    protected transient INDArray dummyBias;     //Used only when: hasBias == false AND helpers are used
    protected transient INDArray dummyBiasGrad; //As above

    public ConvolutionLayer(NeuralNetConfiguration conf, DataType dataType) {
        super(conf, dataType);
        initializeHelper();
        convolutionMode = ((org.deeplearning4j.nn.conf.layers.ConvolutionLayer) conf().getLayer()).getConvolutionMode();
    }

    void initializeHelper() {
        String backend = Nd4j.getExecutioner().getEnvironmentInformation().getProperty("backend");
        if("CUDA".equalsIgnoreCase(backend)) {
            helper = DL4JClassLoading.createNewInstance(
                    "org.deeplearning4j.cuda.convolution.CudnnConvolutionHelper",
                    ConvolutionHelper.class,
                    dataType);
            log.debug("CudnnConvolutionHelper successfully initialized");
            if (!helper.checkSupported()) {
                helper = null;
            }
        } else if("CPU".equalsIgnoreCase(backend)){
            helper = new MKLDNNConvHelper(dataType);
            log.trace("Created MKLDNNConvHelper, layer {}", layerConf().getLayerName());
        }

        if (helper != null && !helper.checkSupported()) {
            log.debug("Removed helper {} as not supported", helper.getClass());
            helper = null;
        }
    }

    @Override
    public Type type() {
        return Type.CONVOLUTIONAL;
    }

    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(true);
        INDArray weights = getParamWithNoise(ConvolutionParamInitializer.WEIGHT_KEY, true, workspaceMgr);
        INDArray bias = getParamWithNoise(ConvolutionParamInitializer.BIAS_KEY, true, workspaceMgr);

        INDArray input = this.input.castTo(dataType);       //No op if correct type
        if(epsilon.dataType() != dataType)
            epsilon = epsilon.castTo(dataType);

        INDArray origInput = input;
        INDArray origEps = epsilon;
        if(layerConf().getCnn2dDataFormat() != CNN2DFormat.NCHW) {
            input = input.permute(0,3,1,2); //NHWC to NCHW
            epsilon = epsilon.permute(0,3,1,2); //NHWC to NCHW
        }


        long miniBatch = input.size(0);
        int inH = (int) input.size(2);
        int inW = (int) input.size(3);

        long outDepth = weights.size(0);
        long inDepth = weights.size(1);
        int kH = (int) weights.size(2);
        int kW = (int) weights.size(3);

        int[] dilation = layerConf().getDilation();
        int[] kernel = layerConf().getKernelSize();
        int[] strides = layerConf().getStride();
        int[] pad;
        int[] outSize;
        if (convolutionMode == ConvolutionMode.Same) {
            outSize = ConvolutionUtils.getOutputSize(input, kernel, strides, null, convolutionMode, dilation, CNN2DFormat.NCHW); //Also performs validation
            pad = ConvolutionUtils.getSameModeTopLeftPadding(outSize, new int[] {inH, inW}, kernel, strides, dilation);
        } else {
            pad = layerConf().getPadding();
            outSize = ConvolutionUtils.getOutputSize(input, kernel, strides, pad, convolutionMode, dilation, CNN2DFormat.NCHW); //Also performs validation
        }

        int outH = outSize[0];
        int outW = outSize[1];


        INDArray biasGradView = gradientViews.get(ConvolutionParamInitializer.BIAS_KEY);
        INDArray weightGradView = gradientViews.get(ConvolutionParamInitializer.WEIGHT_KEY); //4d, c order. Shape: [outDepth,inDepth,kH,kW]
        INDArray weightGradView2df = Shape
                .newShapeNoCopy(weightGradView, new long[]{outDepth, inDepth * kH * kW}, false).transpose();



        INDArray delta;
        IActivation afn = layerConf().getActivationFn();

        Pair<INDArray, INDArray> p = preOutput4d(true, true, workspaceMgr);
        INDArray z = p.getFirst();
        CNN2DFormat f = layerConf().getCnn2dDataFormat();
        if(f != CNN2DFormat.NCHW){
            z = z.permute(0,3,1,2); //NHWC to NCHW
        }
        delta = afn.backprop(z, epsilon).getFirst(); //TODO handle activation function params

        if (helper != null && (helperCountFail == 0 || !layerConf().isCudnnAllowFallback())) {
            INDArray helperDelta = delta;
            if(layerConf().getCnn2dDataFormat() == CNN2DFormat.NHWC)
                helperDelta = delta.permute(0,2,3,1); //NCHW to NHWC

            if(!hasBias() && !(helper instanceof MKLDNNConvHelper)){
                //MKL-DNN supports no bias, CuDNN doesn't
                if(dummyBiasGrad == null){
                    try (MemoryWorkspace wsO = Nd4j.getMemoryManager().scopeOutOfWorkspaces()) {
                        dummyBiasGrad = Nd4j.create(1, layerConf().getNOut());
                    }
                }
                biasGradView = dummyBiasGrad;
            }

            Pair<Gradient, INDArray> ret = null;
            try {
                ret = helper.backpropGradient(origInput, weights, bias, helperDelta, kernel, strides,
                        pad, biasGradView, weightGradView, afn,
                        layerConf().getCudnnAlgoMode(), layerConf().getCudnnBwdFilterAlgo(), layerConf().getCudnnBwdDataAlgo(),
                        convolutionMode, dilation, layerConf().getCnn2dDataFormat(), workspaceMgr);
            } catch (ND4JOpProfilerException e){
                throw e;    //NaN panic etc for debugging
            } catch (Exception e){
                if(e.getMessage().contains("Failed to allocate")){
                    //This is a memory exception - don't fallback to built-in implementation
                    throw e;
                }

                if(layerConf().isCudnnAllowFallback()){
                    helperCountFail++;
                    if(helper instanceof MKLDNNConvHelper){
                        log.warn("MKL-DNN execution failed - falling back on built-in implementation",e);
                    } else {
                        log.warn("CuDNN execution failed - falling back on built-in implementation",e);
                    }
                } else {
                    throw new RuntimeException("Error during ConvolutionLayer MKL/CuDNN helper backprop - isCudnnAllowFallback() is set to false", e);
                }
            }

            if (ret != null) {
                //Backprop dropout, if present
                INDArray gradPostDropout = ret.getRight();
                gradPostDropout = backpropDropOutIfPresent(gradPostDropout);
                ret.setSecond(gradPostDropout);
                return ret;
            }
        }

        delta = delta.permute(1, 0, 2, 3); //To shape: [outDepth,miniBatch,outH,outW]

        //Note: due to the permute in preOut, and the fact that we essentially do a preOut.muli(epsilon), this reshape
        // should be zero-copy; only possible exception being sometimes with the "identity" activation case
        INDArray delta2d = delta.reshape('c', new long[] {outDepth, miniBatch * outH * outW}); //Shape.newShapeNoCopy(delta,new int[]{outDepth,miniBatch*outH*outW},false);

        //Do im2col, but with order [miniB,outH,outW,depthIn,kH,kW]; but need to input [miniBatch,channels,kH,kW,outH,outW] given the current im2col implementation
        //To get this: create an array of the order we want, permute it to the order required by im2col implementation, and then do im2col on that
        //to get old order from required order: permute(0,3,4,5,1,2)
        INDArray im2col2d = p.getSecond(); //Re-use im2col2d array from forward pass if available; recalculate if not
        if (im2col2d == null) {
            INDArray col = Nd4j.createUninitialized(dataType, new long[] {miniBatch, outH, outW, inDepth, kH, kW}, 'c');
            INDArray col2 = col.permute(0, 3, 4, 5, 1, 2);
            Convolution.im2col(input, kH, kW, strides[0], strides[1], pad[0], pad[1], dilation[0], dilation[1],
                    convolutionMode == ConvolutionMode.Same, col2);
            //Shape im2col to 2d. Due to the permuting above, this should be a zero-copy reshape
            im2col2d = col.reshape('c', miniBatch * outH * outW, inDepth * kH * kW);
        }

        //Calculate weight gradients, using cc->c mmul.
        //weightGradView2df is f order, but this is because it's transposed from c order
        //Here, we are using the fact that AB = (B^T A^T)^T; output here (post transpose) is in c order, not usual f order
        Nd4j.gemm(im2col2d, delta2d, weightGradView2df, true, true, 1.0, 0.0);

        //Flatten 4d weights to 2d... this again is a zero-copy op (unless weights are not originally in c order for some reason)
        INDArray wPermuted = weights.permute(3, 2, 1, 0); //Start with c order weights, switch order to f order
        INDArray w2d = wPermuted.reshape('f', inDepth * kH * kW, outDepth);

        //Calculate epsilons for layer below, in 2d format (note: this is in 'image patch' format before col2im reduction)
        //Note: cc -> f mmul here, then reshape to 6d in f order
        INDArray epsNext2d = w2d.mmul(delta2d); //TODO can we reuse im2col array instead of allocating new result array?
        INDArray eps6d = Shape.newShapeNoCopy(epsNext2d, new long[] {kW, kH, inDepth, outW, outH, miniBatch}, true);

        //Calculate epsilonNext by doing im2col reduction.
        //Current col2im implementation expects input with order: [miniBatch,channels,kH,kW,outH,outW]
        //currently have [kH,kW,inDepth,outW,outH,miniBatch] -> permute first
        eps6d = eps6d.permute(5, 2, 1, 0, 4, 3);
        INDArray epsNextOrig = workspaceMgr.createUninitialized(ArrayType.ACTIVATION_GRAD, eps6d.dataType(), new long[] {inDepth, miniBatch, inH, inW}, 'c');

        //Note: we are execute col2im in a way that the output array should be used in a stride 1 muli in the layer below... (same strides as zs/activations)
        INDArray epsNext = epsNextOrig.permute(1, 0, 2, 3);
        Convolution.col2im(eps6d, epsNext, strides[0], strides[1], pad[0], pad[1], inH, inW, dilation[0], dilation[1]);

        Gradient retGradient = new DefaultGradient();
        if(layerConf().hasBias()){
            delta2d.sum(biasGradView, 1); //biasGradView is initialized/zeroed first in sum op
            retGradient.setGradientFor(ConvolutionParamInitializer.BIAS_KEY, biasGradView);
        }
        retGradient.setGradientFor(ConvolutionParamInitializer.WEIGHT_KEY, weightGradView, 'c');

        weightNoiseParams.clear();

        epsNext = backpropDropOutIfPresent(epsNext);

        if(layerConf().getCnn2dDataFormat() != CNN2DFormat.NCHW){
            epsNext = epsNext.permute(0,2,3,1); //NCHW to NHWC
        }

        return new Pair<>(retGradient, epsNext);
    }

    /**
     * preOutput4d: Used so that ConvolutionLayer subclasses (such as Convolution1DLayer) can maintain their standard
     * non-4d preOutput method, while overriding this to return 4d activations (for use in backprop) without modifying
     * the public API
     */
    protected Pair<INDArray, INDArray> preOutput4d(boolean training, boolean forBackprop, LayerWorkspaceMgr workspaceMgr) {
        return preOutput(training, forBackprop, workspaceMgr);
    }

    protected void validateInputRank() {
        //Input validation: expect rank 4 matrix
        if (input.rank() != 4) {
            String layerName = conf.getLayer().getLayerName();
            if (layerName == null)
                layerName = "(not named)";
            throw new DL4JInvalidInputException("Got rank " + input.rank()
                    + " array as input to ConvolutionLayer (layer name = " + layerName + ", layer index = "
                    + index + ") with shape " + Arrays.toString(input.shape()) + ". "
                    + "Expected rank 4 array with shape [minibatchSize, layerInputDepth, inputHeight, inputWidth]."
                    + (input.rank() == 2
                    ? " (Wrong input type (see InputType.convolutionalFlat()) or wrong data type?)"
                    : "")
                    + " " + layerId());
        }
    }

    protected void validateInputDepth(long inDepth) {
        CNN2DFormat format = layerConf().getCnn2dDataFormat();
        int dim = format == CNN2DFormat.NHWC ? 3 : 1;
        if (input.size(dim) != inDepth) {
            String layerName = conf.getLayer().getLayerName();
            if (layerName == null)
                layerName = "(not named)";

            String s = "Cannot do forward pass in Convolution layer (layer name = " + layerName
                    + ", layer index = " + index + "): input array channels does not match CNN layer configuration"
                    + " (data format = " + format + ", data input channels = " + input.size(dim) + ", " + layerConf().getCnn2dDataFormat().dimensionNames()
                    + "=" + Arrays.toString(input.shape()) + "; expected" + " input channels = " + inDepth + ") "
                    + layerId();

            int dimIfWrongFormat = format == CNN2DFormat.NHWC ? 1 : 3;
            if(input.size(dimIfWrongFormat) == inDepth){
                //User might have passed NCHW data to a NHWC net, or vice versa?
                s += "\n" + ConvolutionUtils.NCHW_NHWC_ERROR_MSG;
            }


            throw new DL4JInvalidInputException(s);
        }
    }

    /**
     * PreOutput method that also returns the im2col2d array (if being called for backprop), as this can be re-used
     * instead of being calculated again.
     *
     * @param training    Train or test time (impacts dropout)
     * @param forBackprop If true: return the im2col2d array for re-use during backprop. False: return null for second
     *                    pair entry. Note that it may still be null in the case of CuDNN and the like.
     * @return            Pair of arrays: preOutput (activations) and optionally the im2col2d array
     */
    protected Pair<INDArray, INDArray> preOutput(boolean training, boolean forBackprop, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(false);
        INDArray bias = getParamWithNoise(ConvolutionParamInitializer.BIAS_KEY, training, workspaceMgr);
        INDArray weights = getParamWithNoise(ConvolutionParamInitializer.WEIGHT_KEY, training, workspaceMgr);

        validateInputRank();

        INDArray input = this.input.castTo(dataType);
        INDArray inputOrig = input;
        if(layerConf().getCnn2dDataFormat() == CNN2DFormat.NHWC) {
            input = input.permute(0,3,1,2).dup(); //NHWC to NCHW
        }

        long miniBatch = input.size(0);
        long outDepth = weights.size(0);
        long inDepth = weights.size(1);
        validateInputDepth(inDepth);

        long kH = weights.size(2);
        long kW = weights.size(3);


        int[] dilation = layerConf().getDilation();
        int[] kernel = layerConf().getKernelSize();
        int[] strides = layerConf().getStride();



        int[] pad;
        int[] outSize;
        if (convolutionMode == ConvolutionMode.Same) {
            outSize = ConvolutionUtils.getOutputSize(
                    input,
                    kernel,
                    strides,
                    null,
                    convolutionMode,
                    dilation,
                    CNN2DFormat.NCHW); //Note: hardcoded to NCHW due to permute earlier in this method

            if (input.size(2) > Integer.MAX_VALUE ||  input.size(3) > Integer.MAX_VALUE)
                throw new ND4JArraySizeException();
            int[] inWidthHeight;
            //  if(layerConf().getCnn2dDataFormat() == CNN2DFormat.NCHW)
            //TODO: Switch hardcoded state later. For now, convolution is implemented as
            //switch to NCHW then permute back for NWHC
            inWidthHeight =  new int[] {(int) input.size(2), (int) input.size(3)};

       /*     else if(layerConf().getCnn2dDataFormat() == CNN2DFormat.NHWC) {
                inWidthHeight =  new int[] {(int) input.size(1), (int) input.size(2)};
            }
            else
                 throw new IllegalStateException("No data format configured!");*/
            pad = ConvolutionUtils.getSameModeTopLeftPadding(
                    outSize,
                    inWidthHeight,
                    kernel,
                    strides,
                    dilation);
        } else {
            pad = layerConf().getPadding();
            outSize = ConvolutionUtils.getOutputSize(
                    input,
                    kernel,
                    strides,
                    pad,
                    convolutionMode,
                    dilation,
                    CNN2DFormat.NCHW); //Note: hardcoded to NCHW due to permute earlier in this method
        }

        int outH = outSize[0];
        int outW = outSize[1];


        if (helper != null && (helperCountFail == 0 || !layerConf().isCudnnAllowFallback())) {
            if (preOutput != null && forBackprop) {
                return new Pair<>(preOutput, null);
            }

            //For no-bias convolutional layers: use an empty (all 0s) value for biases
            if(!hasBias()){
                if(dummyBias == null){
                    try (MemoryWorkspace wsO = Nd4j.getMemoryManager().scopeOutOfWorkspaces()) {
                        dummyBias = Nd4j.create(1, layerConf().getNOut());
                    }
                }
                bias = dummyBias;
            }

            INDArray ret = null;
            try {
                ret = helper.preOutput(inputOrig, weights, bias, kernel, strides, pad, layerConf().getCudnnAlgoMode(),
                        layerConf().getCudnnFwdAlgo(), convolutionMode, dilation, layerConf().getCnn2dDataFormat(), workspaceMgr);
            } catch (ND4JOpProfilerException e){
                throw e;    //NaN panic etc for debugging
            } catch (Exception e){
                if(e.getMessage() != null && e.getMessage().contains("Failed to allocate")){
                    //This is a memory exception - don't fallback to built-in implementation
                    throw e;
                }

                if(layerConf().isCudnnAllowFallback()) {
                    helperCountFail++;
                    if(helper instanceof MKLDNNConvHelper) {
                        log.warn("MKL-DNN execution failed - falling back on built-in implementation",e);
                    } else {
                        log.warn("CuDNN execution failed - falling back on built-in implementation",e);
                    }
                } else {
                    throw new RuntimeException("Error during ConvolutionLayer MKL/CuDNN helper forward pass - isCudnnAllowFallback() is set to false", e);
                }
            }
            if (ret != null) {
                return new Pair<>(ret, null);
            }
        }

        if (preOutput != null && i2d != null && forBackprop) {
            return new Pair<>(preOutput, i2d);
        }

        //im2col in the required order: want [outW,outH,miniBatch,depthIn,kH,kW], but need to input [miniBatch,channels,kH,kW,outH,outW] given the current im2col implementation
        //To get this: create an array of the order we want, permute it to the order required by im2col implementation, and then do im2col on that
        //to get old order from required order: permute(0,3,4,5,1,2)
        //Post reshaping: rows are such that minibatch varies slowest, outW fastest as we step through the rows post-reshape
        INDArray col = Nd4j.createUninitialized(weights.dataType(), new long[] {miniBatch, outH, outW, inDepth, kH, kW}, 'c');
        int[] permute =  new int[]{0, 3, 4, 5, 1, 2};
        INDArray col2 = col.permute(permute);
        INDArray im2ColIn = input.castTo(col2.dataType());      //No op if already (for example) float
        if (kH > Integer.MAX_VALUE || kW > Integer.MAX_VALUE)
            throw new ND4JArraySizeException();
        Convolution.im2col(
                im2ColIn,
                (int)kH,
                (int)kW,
                strides[0], strides[1],
                pad[0], pad[1],
                dilation[0], dilation[1],
                convolutionMode == ConvolutionMode.Same,
                col2);


        INDArray im2col2d = Shape.newShapeNoCopy(col, new long[] {miniBatch * outH * outW, inDepth * kH * kW}, false);

        //Current order of weights: [depthOut,depthIn,kH,kW], c order
        //Permute to give [kW,kH,depthIn,depthOut], f order
        //Reshape to give [kW*kH*depthIn, depthOut]. This should always be zero-copy reshape, unless weights aren't in c order for some reason
        INDArray permutedW = weights.permute(3, 2, 1, 0);
        INDArray reshapedW = permutedW.reshape('f', kW * kH * inDepth, outDepth);

        //Do the MMUL; c and f orders in, f order out. output shape: [miniBatch*outH*outW,depthOut]
        INDArray z = workspaceMgr.createUninitialized(ArrayType.ACTIVATIONS, weights.dataType(), new long[]{im2col2d.size(0), reshapedW.size(1)}, 'f');
        im2col2d.mmuli(reshapedW, z);

        //Add biases, before reshaping. Note that biases are [1,depthOut] and currently z is [miniBatch*outH*outW,depthOut] -> addiRowVector
        if(layerConf().hasBias()){
            z.addiRowVector(bias);
        }

        //Now, reshape to [outW,outH,miniBatch,outDepth], and permute to have correct output order: [miniBatch,outDepth,outH,outW];
        z = Shape.newShapeNoCopy(z, new long[] {outW, outH, miniBatch, outDepth}, true);
        z = z.permute(2, 3, 1, 0);

        if (training && cacheMode != CacheMode.NONE && workspaceMgr.hasConfiguration(ArrayType.FF_CACHE) && workspaceMgr.isWorkspaceOpen(ArrayType.FF_CACHE)) {
            try (MemoryWorkspace wsB = workspaceMgr.notifyScopeBorrowed(ArrayType.FF_CACHE)) {
                i2d = im2col2d.unsafeDuplication();
            }
        }

        if(layerConf().getCnn2dDataFormat() == CNN2DFormat.NHWC) {
            z = z.permute(0,2,3,1); //NCHW to NHWC
            z = workspaceMgr.dup(ArrayType.ACTIVATIONS, z);
        }

        return new Pair<>(z, forBackprop ? im2col2d : null);
    }

    @Override
    public INDArray activate(boolean training, LayerWorkspaceMgr workspaceMgr) {
        if (input == null) {
            throw new IllegalArgumentException("Cannot perform forward pass with null input " + layerId());
        }

        if (cacheMode == null)
            cacheMode = CacheMode.NONE;

        applyDropOutIfNecessary(training, workspaceMgr);

        INDArray z = preOutput(training, false, workspaceMgr).getFirst();

        // we do cache only if cache workspace exists. Skip otherwise
        if (training && cacheMode != CacheMode.NONE && workspaceMgr.hasConfiguration(ArrayType.FF_CACHE) && workspaceMgr.isWorkspaceOpen(ArrayType.FF_CACHE)) {
            try (MemoryWorkspace wsB = workspaceMgr.notifyScopeBorrowed(ArrayType.FF_CACHE)) {
                preOutput = z.unsafeDuplication();
            }
        }

        //String afn = conf.getLayer().getActivationFunction();
        IActivation afn = layerConf().getActivationFn();

        if (helper != null && Shape.strideDescendingCAscendingF(z) && (helperCountFail == 0 || !layerConf().isCudnnAllowFallback())) {
            INDArray ret = null;
            try {
                ret = helper.activate(z, layerConf().getActivationFn(), training);
            } catch (ND4JOpProfilerException e){
                throw e;    //NaN panic etc for debugging
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("Failed to allocate")) {
                    //This is a memory exception - don't fallback to built-in implementation
                    throw e;
                }

                if (layerConf().isCudnnAllowFallback()) {
                    helperCountFail++;
                    if (helper instanceof MKLDNNConvHelper) {
                        log.warn("MKL-DNN execution failed - falling back on built-in implementation", e);
                    } else {
                        log.warn("CuDNN execution failed - falling back on built-in implementation", e);
                    }
                } else {
                    throw new RuntimeException("Error during ConvolutionLayer MKL/CuDNN helper forward pass - isCudnnAllowFallback() is set to false", e);
                }
            }

            if (ret != null) {
                return ret;
            }
        }

        INDArray activation = afn.getActivation(z, training);
        return activation;
    }

    @Override
    public boolean hasBias() {
        return layerConf().hasBias();
    }

    @Override
    public boolean isPretrainLayer() {
        return false;
    }

    @Override
    public LayerHelper getHelper() {
        return helper;
    }

    @Override
    public void fit(INDArray input, LayerWorkspaceMgr workspaceMgr) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void setParams(INDArray params) {
        //Override, as base layer does f order parameter flattening by default
        setParams(params, 'c');
    }

    @Override
    public Pair<INDArray, MaskState> feedForwardMaskArray(INDArray maskArray, MaskState currentMaskState, int minibatchSize) {
        if (maskArray == null) {
            //For same mode (with stride 1): output activations size is always same size as input activations size -> mask array is same size
            return new Pair<>(maskArray, currentMaskState);
        }

        INDArray outMask = ConvolutionUtils.cnn2dMaskReduction(maskArray, layerConf().getKernelSize(), layerConf().getStride(),
                layerConf().getPadding(), layerConf().getDilation(), layerConf().getConvolutionMode());
        return new Pair<>(outMask, currentMaskState);
    }

}
