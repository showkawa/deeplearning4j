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

package org.deeplearning4j.nn.layers.normalization;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.deeplearning4j.common.config.DL4JClassLoading;
import org.deeplearning4j.nn.conf.CNN2DFormat;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.layers.BaseLayer;
import org.deeplearning4j.nn.layers.LayerHelper;
import org.deeplearning4j.nn.layers.mkldnn.MKLDNNBatchNormHelper;
import org.deeplearning4j.nn.params.BatchNormalizationParamInitializer;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.broadcast.BroadcastAddOp;
import org.nd4j.linalg.api.ops.impl.broadcast.BroadcastDivOp;
import org.nd4j.linalg.api.ops.impl.broadcast.BroadcastMulOp;
import org.nd4j.linalg.api.ops.impl.broadcast.BroadcastSubOp;
import org.nd4j.linalg.api.ops.impl.transforms.pairwise.arithmetic.DivOp;
import org.nd4j.linalg.api.ops.impl.transforms.pairwise.arithmetic.SubOp;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.linalg.exception.ND4JOpProfilerException;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.common.primitives.Pair;
import org.nd4j.common.util.OneTimeLogger;

import java.util.*;

/**
 * Batch normalization layer.<br>
 * Rerences:<br>
 *  <a href="https://arxiv.org/pdf/1502.03167v3.pdf">https://arxiv.org/pdf/1502.03167v3.pdf</a><br>
 *  <a href="https://arxiv.org/pdf/1410.7455v8.pdf">https://arxiv.org/pdf/1410.7455v8.pdf</a><br>
 *  <a href="https://kratzert.github.io/2016/02/12/understanding-the-gradient-flow-through-the-batch-normalization-layer.html">
 *      https://kratzert.github.io/2016/02/12/understanding-the-gradient-flow-through-the-batch-normalization-layer.html</a>
 *
 * Batch normalization should be applied between the output of a layer (with identity activation) and the activation function.
 **/
@Slf4j
public class BatchNormalization extends BaseLayer<org.deeplearning4j.nn.conf.layers.BatchNormalization> {
    protected static final double ONE_ON_2LOGE_10 = 1.0 / (2 * Math.log(10.0));

    BatchNormalizationHelper helper = null;
    protected int helperCountFail = 0;
    protected int index = 0;
    protected List<TrainingListener> listeners = new ArrayList<>();
    protected INDArray std;
    protected INDArray xMu;
    protected INDArray xHat;

    public BatchNormalization(NeuralNetConfiguration conf, DataType dataType) {
        super(conf, dataType);
        initializeHelper();
    }

    void initializeHelper() {
        String backend = Nd4j.getExecutioner().getEnvironmentInformation().getProperty("backend");

        if ("CUDA".equalsIgnoreCase(backend)) {
            helper = DL4JClassLoading.createNewInstance(
                    "org.deeplearning4j.cuda.normalization.CudnnBatchNormalizationHelper",
                    BatchNormalizationHelper.class,
                    dataType);
            log.debug("CudnnBatchNormalizationHelper successfully initialized");
        } else if ("CPU".equalsIgnoreCase(backend)){
            helper = new MKLDNNBatchNormHelper(dataType);
            log.trace("Created MKLDNNBatchNormHelper, layer {}", layerConf().getLayerName());
        }

        if (helper != null && !helper.checkSupported(layerConf().getEps(), layerConf().isLockGammaBeta())) {
            log.debug("Removed helper {} as not supported with epsilon {}, lockGammaBeta={}", helper.getClass(), layerConf().getEps(), layerConf().isLockGammaBeta());
            helper = null;
        }
    }

    @Override
    public Type type() {
        return Type.NORMALIZATION;
    }

    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(true);
        INDArray nextEpsilon;
        val shape = getShape(epsilon);
        val batchSize = epsilon.size(0); // number examples in batch
        org.deeplearning4j.nn.conf.layers.BatchNormalization layerConf = layerConf();

        CNN2DFormat format = layerConf().getCnn2DFormat();
        boolean nchw = format == CNN2DFormat.NCHW;
        int chIdx = epsilon.rank() == 2 || nchw ? 1 : 3;

        INDArray input = this.input.castTo(dataType);   //No-op if correct type

        INDArray globalMean = params.get(BatchNormalizationParamInitializer.GLOBAL_MEAN);
        INDArray globalVar = params.get(BatchNormalizationParamInitializer.GLOBAL_VAR);             //One of log10std will be null depending on config
        INDArray globalLog10Std = params.get(BatchNormalizationParamInitializer.GLOBAL_LOG_STD);
        INDArray gamma = null;
        INDArray beta = null;
        INDArray dGammaView;
        INDArray dBetaView;
        INDArray dGlobalMeanView = gradientViews.get(BatchNormalizationParamInitializer.GLOBAL_MEAN);
        INDArray dGlobalVarView = gradientViews.get(BatchNormalizationParamInitializer.GLOBAL_VAR);
        INDArray dGlobalLog10StdView = gradientViews.get(BatchNormalizationParamInitializer.GLOBAL_LOG_STD);
        if (layerConf.isLockGammaBeta()) {
            val tempShape = new long[] {1, shape[chIdx]};
            dGammaView = Nd4j.createUninitialized(dataType, tempShape, 'c');
            dBetaView = Nd4j.createUninitialized(dataType, tempShape, 'c');
        } else {
            gamma = getParam(BatchNormalizationParamInitializer.GAMMA);
            beta = getParam(BatchNormalizationParamInitializer.BETA);
            dGammaView = gradientViews.get(BatchNormalizationParamInitializer.GAMMA);
            dBetaView = gradientViews.get(BatchNormalizationParamInitializer.BETA);
        }

        Gradient retGradient = new DefaultGradient();


        if (helper != null && (helperCountFail == 0 || !layerConf().isCudnnAllowFallback())){
            //Note that cudnn does not support dense (2d) batch norm case as of v5.1
            if (layerConf.isLockGammaBeta()) {
                gamma = Nd4j.createUninitialized(dataType, 1, shape[chIdx]).assign(layerConf.getGamma());
            }

            INDArray in;
            INDArray eps;
            if(input.rank() == 2){
                long[] shapeTemp = nchw ? new long[]{input.size(0), input.size(1), 1, 1} : new long[]{input.size(0), 1, 1, input.size(1)};
                in = input.reshape(input.ordering(), shapeTemp);
                eps = epsilon.reshape(epsilon.ordering(), shapeTemp);
            } else {
                in = input;
                eps = epsilon;
            }

            Pair<Gradient,INDArray> ret = null;
            try {
                ret = helper.backpropGradient(in, eps, shape, gamma, beta, dGammaView, dBetaView,
                        layerConf.getEps(), format, workspaceMgr);
            } catch (ND4JOpProfilerException e){
                throw e;    //NaN panic etc for debugging
            } catch (Throwable t){
                if(t.getMessage() != null && t.getMessage().contains("Failed to allocate")){
                    //This is a memory exception - don't fallback to built-in implementation
                    throw t;
                }

                if(layerConf().isCudnnAllowFallback()){
                    helperCountFail++;
                    log.warn("CuDNN BatchNormalization backprop execution failed - falling back on built-in implementation",t);
                } else {
                    throw new RuntimeException("Error during BatchNormalization CuDNN helper backprop - isCudnnAllowFallback() is set to false", t);
                }
            }
            if (ret != null) {
                ret.getFirst().setGradientFor(BatchNormalizationParamInitializer.GLOBAL_MEAN, dGlobalMeanView);
                if(layerConf().isUseLogStd()){
                    ret.getFirst().setGradientFor(BatchNormalizationParamInitializer.GLOBAL_LOG_STD, dGlobalLog10StdView);
                } else {
                    ret.getFirst().setGradientFor(BatchNormalizationParamInitializer.GLOBAL_VAR, dGlobalVarView);
                }

                if(input.rank() == 2){
                    INDArray e = ret.getSecond();
                    ret.setSecond(e.reshape(e.ordering(), e.size(0), e.size(1)));
                }

                /*
                Handling of global mean and variance:
                Normally the design for batch norm is to:
                    globalMean = decay * globalMean + (1-decay) * minibatchMean
                    globalVar  = decay * globalVar  + (1-decay) * minibatchVar
                However, because of distributed training (gradient sharing), we don't want to do this...
                Instead: We'll use the mathematically equivalent but "distributed safe" approach of:
                mean[t+1] = mean[t] - updateMean
                updateMean = mean[t] - mean[t+1] = (1-d) * (mean[t] - minibatchMean)
                And use the same idea for global variance estimate.

                Note also that we have 2 supported parameterizations here:
                1. global variance estimate (only option until after 1.0.0-beta3)
                2. global log10(std) estimate
                These make zero difference for local training (other than perhaps when using FP16), but the latter is more
                numerically stable and is scaled better for distributed training
                 */
                INDArray batchMean = helper.getMeanCache(dataType);
                INDArray batchVar = helper.getVarCache(dataType);

                Nd4j.getExecutioner().exec(new SubOp(globalMean, batchMean, dGlobalMeanView));   //deltaGlobalMean = globalMean[t] - batchMean
                dGlobalMeanView.muli(1-layerConf().getDecay());

                if(layerConf().isUseLogStd()){
                    //Use log10(std) parameterization. This is more numerically stable for FP16 and better for distributed training
                    //First: we have log10(var[i]) from last iteration, hence can calculate var[i] and stdev[i]
                    //Need to calculate log10{std[i]) - log10(std[i+1]) as the "update"
                    //Note, var[i+1] = d*var[i] + (1-d)*batchVar
                    INDArray vari = Nd4j.createUninitialized(dataType, globalLog10Std.shape()).assign(10.0);
                    Transforms.pow(vari, globalLog10Std, false);     //variance = (10^log10(s))^2
                    vari.muli(vari);

                    double decay = layerConf().getDecay();
                    INDArray varip1 = vari.mul(decay).addi(batchVar.mul(1-decay));
                    Nd4j.getExecutioner().exec(new DivOp(vari, varip1, dGlobalLog10StdView));
                    Transforms.log(dGlobalLog10StdView, false);
                    dGlobalLog10StdView.muli(ONE_ON_2LOGE_10);
                } else {
                    //Use variance estimate parameterization. This was only option up to and including 1.0.0-beta3
                    Nd4j.getExecutioner().exec(new SubOp(globalVar, batchVar, dGlobalVarView));      //deltaGlobalVar = globalVar[t] - batchVar
                    dGlobalVarView.muli(1 - layerConf().getDecay());
                }

                return ret;
            }
        }

        INDArray batchMean;
        INDArray batchVar;
        if (epsilon.rank() == 2) {
            if(xHat == null && helper != null){
                INDArray mean = helper.getMeanCache(dataType);
                std = Transforms.sqrt(helper.getVarCache(dataType).addi(layerConf().getEps()));
                xMu =  Nd4j.createUninitialized(dataType, input.shape(), input.ordering());
                xMu = Nd4j.getExecutioner().exec(new BroadcastSubOp(input, mean, xMu, 1));
                xHat =  Nd4j.createUninitialized(dataType, input.shape(), input.ordering());
                xHat = Nd4j.getExecutioner().exec(new BroadcastDivOp(xMu, std,xHat, 1));
            }

            //TODO: handle fixed beta/gamma case...
            INDArray dBeta = epsilon.sum(true, 0); //dL/dBeta = sum_examples dL/dOut
            INDArray dGamma = epsilon.mul(xHat).sum(true, 0); //dL/dGamma = sum_examples dL/dOut .* xHat
            INDArray dxhat;
            if (layerConf.isLockGammaBeta()) {
                dxhat = epsilon.mul(layerConf.getGamma());
            } else {
                //Standard case
                dxhat = epsilon.mulRowVector(gamma); //dL/dxHat = dL/dOut . gamma        Shape: [minibatchSize, nOut]
            }


            //dL/dVariance
            INDArray dLdVar = dxhat.mul(xMu).sum(true, 0).muli(-0.5).muli(Transforms.pow(std, -3.0, true)); //Shape: [1, miniBatch]

            //dL/dmu
            INDArray dxmu1 = dxhat.sum(true, 0).divi(std).negi();
            INDArray dxmu2 = xMu.sum(true, 0).muli(-2.0 / batchSize).muli(dLdVar);

            INDArray dLdmu = dxmu1.addi(dxmu2); //Shape: [1, nOut]

            //Note the array reuse here: dxhat, xMu, dLdVar, dLdmu - all are invalid after this line (but aren't used later anyway)
            INDArray dLdx = dxhat.diviRowVector(std).addi(xMu.muliRowVector(dLdVar.muli(2.0 / batchSize)))
                            .addiRowVector(dLdmu.muli(1.0 / batchSize));

            //TODO rework this to avoid the assign here
            dGammaView.assign(dGamma);
            dBetaView.assign(dBeta);

            retGradient.setGradientFor(BatchNormalizationParamInitializer.GAMMA, dGammaView);
            retGradient.setGradientFor(BatchNormalizationParamInitializer.BETA, dBetaView);

            nextEpsilon = dLdx;

            batchMean = input.mean(0);
            batchVar = input.var(false, 0);
        } else if (epsilon.rank() == 4) {
            int[] nonChDims = nchw ? new int[]{0, 2, 3} : new int[]{0, 1, 2};
            int hIdx = nchw ? 2 : 1;
            int wIdx = nchw ? 3 : 2;

            if(xHat == null && helper != null){
                INDArray mean = helper.getMeanCache(dataType);
                std = Transforms.sqrt(helper.getVarCache(dataType).addi(layerConf().getEps()));
                xMu =  Nd4j.createUninitialized(dataType, input.shape(), input.ordering());
                xMu = Nd4j.getExecutioner().exec(new BroadcastSubOp(input, mean, xMu, chIdx));
                xHat =  Nd4j.createUninitialized(dataType, input.shape(), input.ordering());
                xHat = Nd4j.getExecutioner().exec(new BroadcastDivOp(xMu, std,xHat, chIdx));
            }

            INDArray dBeta = epsilon.sum(nonChDims);
            INDArray dGamma = epsilon.mul(xHat).sum(nonChDims);
            INDArray dxhat;
            if (layerConf.isLockGammaBeta()) {
                dxhat = epsilon.mul(layerConf.getGamma());
            } else {
                //Standard case
                dxhat = Nd4j.getExecutioner().exec(new BroadcastMulOp(epsilon, gamma,
                                Nd4j.createUninitialized(epsilon.dataType(), epsilon.shape(), epsilon.ordering()), chIdx));
            }

            //dL/dVariance
            INDArray dLdVar = dxhat.mul(xMu).sum(nonChDims).muli(-0.5).muli(Transforms.pow(std, -3.0, true));

            //dL/dmu
            val effectiveBatchSize = input.size(0) * input.size(hIdx) * input.size(wIdx);
            INDArray dxmu1 = dxhat.sum(nonChDims).divi(std).negi();
            INDArray dxmu2 = xMu.sum(nonChDims).muli(-2.0 / effectiveBatchSize).muli(dLdVar);
            INDArray dLdmu = dxmu1.addi(dxmu2);

            INDArray dLdx = Nd4j.getExecutioner().exec(new BroadcastDivOp(dxhat, std, dxhat, chIdx))
                            .addi(Nd4j.getExecutioner().exec(new BroadcastMulOp(xMu, dLdVar.muli(2.0 / effectiveBatchSize), xMu, chIdx)));
            Nd4j.getExecutioner()
                            .execAndReturn(new BroadcastAddOp(dLdx, dLdmu.muli(1.0 / effectiveBatchSize), dLdx, chIdx));

            //TODO rework this to avoid the assign here
            dGammaView.assign(dGamma);
            dBetaView.assign(dBeta);

            retGradient.setGradientFor(BatchNormalizationParamInitializer.GAMMA, dGammaView);
            retGradient.setGradientFor(BatchNormalizationParamInitializer.BETA, dBetaView);

            nextEpsilon = dLdx;
            batchMean = input.mean(nonChDims);
            batchVar = input.var(false, nonChDims);
        } else {
            // TODO setup BatchNorm for RNN https://arxiv.org/pdf/1510.01378v1.pdf
            throw new IllegalStateException( "The layer prior to BatchNorm in the configuration is not currently supported. " + layerId());
        }


        /*
        Handling of global mean and variance:
        Normally the design for batch norm is to:
            globalMean = decay * globalMean + (1-decay) * minibatchMean
            globalVar  = decay * globalVar  + (1-decay) * minibatchVar
        However, because of distributed training (gradient sharing), we don't want to do this...
        Instead: We'll use the mathematically equivalent but "distributed safe" approach of:
        mean[t+1] = mean[t] - updateMean
        updateMean = mean[t] - mean[t+1] = (1-d) * (mean[t] - minibatchMean)
        And use the same idea for global variance estimate
         */

        Nd4j.getExecutioner().exec(new SubOp(globalMean, batchMean, dGlobalMeanView));   //deltaGlobalMean = globalMean[t] - batchMean
        dGlobalMeanView.muli(1-layerConf().getDecay());

        if(layerConf().isUseLogStd()){
            //Use log10(std) parameterization. This is more numerically stable for FP16 and better for distributed training
            //First: we have log10(var[i]) from last iteration, hence can calculate var[i] and stdev[i]
            //Need to calculate log10{std[i]) - log10(std[i+1]) as the "update"
            //Note, var[i+1] = d*var[i] + (1-d)*batchVar
            INDArray vari = Nd4j.valueArrayOf(globalLog10Std.shape(), 10.0, globalMean.dataType());
            Transforms.pow(vari, globalLog10Std, false);     //variance = (10^log10(s))^2
            vari.muli(vari);

            double decay = layerConf().getDecay();
            INDArray varip1 = vari.mul(decay).addi(batchVar.mul(1-decay));
            Nd4j.getExecutioner().exec(new DivOp(vari, varip1, dGlobalLog10StdView));
            Transforms.log(dGlobalLog10StdView, false);
            dGlobalLog10StdView.muli(ONE_ON_2LOGE_10);
        } else {
            //Use variance estimate parameterization. This was only option up to and including 1.0.0-beta3
            Nd4j.getExecutioner().exec(new SubOp(globalVar, batchVar, dGlobalVarView));      //deltaGlobalVar = globalVar[t] - batchVar
            dGlobalVarView.muli(1 - layerConf().getDecay());
        }

        retGradient.setGradientFor(BatchNormalizationParamInitializer.GLOBAL_MEAN, dGlobalMeanView);
        if(layerConf().isUseLogStd()){
            retGradient.setGradientFor(BatchNormalizationParamInitializer.GLOBAL_LOG_STD, dGlobalLog10StdView);
        } else {
            retGradient.setGradientFor(BatchNormalizationParamInitializer.GLOBAL_VAR, dGlobalVarView);
        }


        //TODO could optimize this
        nextEpsilon = workspaceMgr.leverageTo(ArrayType.ACTIVATION_GRAD, nextEpsilon);

        xHat = null;
        xMu = null;

        return new Pair<>(retGradient, nextEpsilon);
    }

    @Override
    public void fit(INDArray input, LayerWorkspaceMgr workspaceMgr) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public INDArray activate(boolean training, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(false);
        return preOutput(input, training ? TrainingMode.TRAIN : TrainingMode.TEST, workspaceMgr);
    }

    @Override
    public Gradient gradient() {
        return gradient;
    }

    public INDArray preOutput(INDArray x, TrainingMode training, LayerWorkspaceMgr workspaceMgr) {
        int dim = 1;
        if(x.rank() == 4 && layerConf().getCnn2DFormat() == CNN2DFormat.NHWC)
            dim = 3;
        if(x.size(dim) != layerConf().getNOut()){
            throw new IllegalArgumentException("input.size(" + dim + ") does not match expected input size of " + layerConf().getNIn()
                    + " - got input array with shape " + Arrays.toString(x.shape()));
        }
        x = x.castTo(dataType); //No-op if correct type

        INDArray activations;
        // TODO add this directly in layer or get the layer prior...

        org.deeplearning4j.nn.conf.layers.BatchNormalization layerConf = layerConf();
        val shape = getShape(x);

        INDArray gamma = null;
        INDArray beta = null;
        INDArray globalMeanView = getParam(BatchNormalizationParamInitializer.GLOBAL_MEAN);
        INDArray globalVarView = getParam(BatchNormalizationParamInitializer.GLOBAL_VAR);           //Either this or log10std will be null depending on config
        if (layerConf.isLockGammaBeta()) {
            if (helper != null && input.rank() == 4) {
                //TODO: don't create these each iteration, when using cudnn
                val gammaBetaShape = new long[] {1, layerConf().getNOut()};
                gamma = Nd4j.valueArrayOf(gammaBetaShape, layerConf().getGamma(), dataType);
                beta = Nd4j.valueArrayOf(gammaBetaShape, layerConf().getBeta(), dataType);
            }
        } else {
            gamma = getParam(BatchNormalizationParamInitializer.GAMMA);
            beta = getParam(BatchNormalizationParamInitializer.BETA);
        }

        if (helper != null && (helperCountFail == 0 || !layerConf().isCudnnAllowFallback())){

            INDArray in = x;
            if(x.rank() == 2)
                in = x.reshape(x.ordering(), in.size(0), in.size(1), 1, 1);

            //Note that cudnn does not support dense (2d) batch norm case as of v7.1
            double decay = layerConf.getDecay();

            INDArray ret = null;
            try {
                if(globalVarView == null){
                    //May be null when useLogStd is true
                    INDArray log10s = getParam(BatchNormalizationParamInitializer.GLOBAL_LOG_STD);
                    globalVarView = Transforms.pow(Nd4j.valueArrayOf(log10s.shape(), 10.0, dataType), log10s, false);
                    globalVarView.muli(globalVarView);
                }

                ret = helper.preOutput(in, training == TrainingMode.TRAIN, shape, gamma, beta, globalMeanView,
                        globalVarView, decay, layerConf.getEps(), layerConf().getCnn2DFormat(), workspaceMgr);
            } catch (ND4JOpProfilerException e){
                throw e;    //NaN panic etc for debugging
            } catch (Throwable t) {
                if(t.getMessage() != null && t.getMessage().contains("Failed to allocate")){
                    //This is a memory exception - don't fallback to built-in implementation
                    throw t;
                }

                if(layerConf().isCudnnAllowFallback()){
                    helperCountFail++;
                    log.warn("CuDNN BatchNormalization forward pass execution failed - falling back on built-in implementation",t);
                } else {
                    throw new RuntimeException("Error during BatchNormalization CuDNN helper backprop - isCudnnAllowFallback() is set to false", t);
                }
            }
            if (ret != null) {
                if(input.rank() == 2){
                    return ret.reshape(ret.ordering(), ret.size(0), ret.size(1));
                } else {
                    return ret;
                }
            }
        }

        CNN2DFormat format = layerConf().getCnn2DFormat();
        boolean nchw = format == CNN2DFormat.NCHW;
        int chIdx = nchw ? 1 : 3;
        int[] nonChDims = nchw ? new int[]{0, 2, 3} : new int[]{0, 1, 2};
        int hIdx = nchw ? 2 : 1;
        int wIdx = nchw ? 3 : 2;

        // xHat = (x-xmean) / sqrt(var + epsilon)
        //Note that for CNNs, mean and variance are calculated per feature map (i.e., per activation) rather than per activation
        //Pg5 of https://arxiv.org/pdf/1502.03167v3.pdf
        // "For convolutional layers, we additionally want the normalization to obey the convolutional property – so that
        //  different elements of the same feature map, at different locations, are normalized in the same way. To achieve
        //  this, we jointly normalize all the activations in a minibatch, over all locations."
        INDArray mean, var;
        if (training == TrainingMode.TRAIN) {
            switch (x.rank()) {
                case 2:
                    // mean and variance over samples in batch
                    mean = x.mean(0);
                    var = x.var(false, 0);
                    break;
                case 4:
                    // mean and variance over samples AND locations

                    mean = x.mean(nonChDims);
                    var = x.var(false, nonChDims);
                    break;
                default:
                    throw new IllegalStateException("Batch normalization on activations of rank " + x.rank()
                            + " not supported " + layerId());
            }

            std = Transforms.sqrt(workspaceMgr.dup(ArrayType.INPUT, var).addi(layerConf().getEps()), false);
        } else {
            // Global mean and variance estimate - used after training
            mean = getParam(BatchNormalizationParamInitializer.GLOBAL_MEAN);
            if(layerConf().isUseLogStd()){
                //var = (10^(log10(s)))^2
                INDArray log10s = getParam(BatchNormalizationParamInitializer.GLOBAL_LOG_STD);
                var = Transforms.pow(Nd4j.valueArrayOf(log10s.shape(), 10.0, dataType), log10s);
                var.muli(var);
            } else {
                var = getParam(BatchNormalizationParamInitializer.GLOBAL_VAR);
            }
            std = Transforms.sqrt(workspaceMgr.dup(ArrayType.INPUT, var).addi(layerConf().getEps()), false);
        }

        // BN(xk) = gamma*xˆ + β (applying gamma and beta for each activation)
        if (x.rank() == 2) {
            xMu = workspaceMgr.leverageTo(ArrayType.INPUT, x.subRowVector(mean));
            xHat = workspaceMgr.leverageTo(ArrayType.INPUT, xMu.divRowVector(std));


            if (layerConf.isLockGammaBeta()) {
                //Special case: gamma/beta have fixed values for all outputs
                //Use mul/addi(Number) here to avoid allocating temp arrays of all same value
                double g = layerConf.getGamma();
                double b = layerConf.getBeta();
                if (g != 1.0 && b != 0.0) {
                    //Default and most common case: 1.0 and 0.0 for these parameters. No point executing 1 * x + 0 op
                    activations = xHat.mul(g).addi(b);
                } else {
                    activations = xHat;
                }
            } else {
                //Standard case: gamma and beta are learned per parameter
                activations = xHat.mulRowVector(gamma).addiRowVector(beta);
            }
        } else if (x.rank() == 4) {
            if (!Shape.strideDescendingCAscendingF(x))
                x = x.dup(); //TODO: temp Workaround for broadcast bug. To be removed when fixed
            xMu = workspaceMgr.createUninitialized(ArrayType.INPUT, x.dataType(), x.shape(), x.ordering());
            xMu = Nd4j.getExecutioner().exec(new BroadcastSubOp(x, mean,xMu, chIdx));
            xHat =  workspaceMgr.createUninitialized(ArrayType.INPUT, x.dataType(), x.shape(), x.ordering());
            xHat = Nd4j.getExecutioner().exec(new BroadcastDivOp(xMu, std,xHat, chIdx));

            if (layerConf.isLockGammaBeta()) {
                //Special case: gamma/beta have fixed values for all outputs
                //Use mul/addi(Number) here to avoid allocating temp arrays of all same value
                double g = layerConf.getGamma();
                double b = layerConf.getBeta();
                if (g != 1.0 && b != 0.0) {
                    //Default and most common case: 1.0 and 0.0 for these parameters. No point executing 1 * x + 0 op
                    activations = xHat.mul(g).addi(b);
                } else {
                    activations = xHat;
                }
            } else {
                //Standard case: gamma and beta are learned per parameter
                activations = workspaceMgr.createUninitialized(ArrayType.ACTIVATIONS, x.dataType(), x.shape(), x.ordering());
                activations = Nd4j.getExecutioner().exec(new BroadcastMulOp(xHat, gamma, activations, chIdx));
                activations = Nd4j.getExecutioner().exec(new BroadcastAddOp(activations, beta, activations, chIdx));
            }
        } else {
            // TODO setup BatchNorm for RNN https://arxiv.org/pdf/1510.01378v1.pdf
            throw new IllegalStateException(
                            "The layer prior to BatchNorm in the configuration is not currently supported. "
                                            + layerId());
        }

        /*
        A note regarding running mean and variance updating:
        Normally these are updated like globalMean = decay * globalMean + (1-decay) * minibatchMean
        However, because of distributed training (gradient sharing), we don't want to do this...
        Instead: We'll use the mathematically equivalent but "distributed safe" approach of:
        mean[t+1] = mean[t] - updateMean
        updateMean = mean[t] - mean[t+1] = (1-d) * (mean[t] - minibatchMean)
        And use the same idea for global variance estimate
         */

        activations = workspaceMgr.leverageTo(ArrayType.ACTIVATIONS, activations);   //Most of the time this should be a no-op
        return activations;
    }

    @Override
    public Collection<TrainingListener> getListeners() {
        return listeners;
    }

    @Override
    public void setListeners(TrainingListener... listeners) {
        this.listeners = new ArrayList<>(Arrays.asList(listeners));
    }

    @Override
    public void setIndex(int index) {
        this.index = index;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public boolean isPretrainLayer() {
        return false;
    }

    @Override
    public LayerHelper getHelper() {
        return helper;
    }

    public long[] getShape(INDArray x) {
        if (x.rank() == 2 )
            return new long[] {1, x.size(1)};
        if(x.rank() == 4){
            int chIdx = layerConf().getCnn2DFormat() == CNN2DFormat.NCHW ? 1 : 3;
            return new long[]{1, x.size(chIdx)};
        }
        if (x.rank() == 3) {
            val wDim = x.size(1);
            val hdim = x.size(2);
            if (x.size(0) > 1 && wDim * hdim == x.length())
                throw new IllegalArgumentException("Illegal input for batch size " + layerId());
            return new long[] {1, wDim * hdim};
        } else
            throw new IllegalStateException("Unable to process input of rank " + x.rank() + " " + layerId());
    }

    @Override
    public boolean updaterDivideByMinibatch(String paramName) {
        //Majority of params's gradients should be... Exception: batch norm mean/variance estimate
        if(BatchNormalizationParamInitializer.GLOBAL_MEAN.equals(paramName) || BatchNormalizationParamInitializer.GLOBAL_VAR.equals(paramName)
                || BatchNormalizationParamInitializer.GLOBAL_LOG_STD.equals(paramName)){
            return false;
        }
        return true;
    }

}
