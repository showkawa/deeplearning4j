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

package org.deeplearning4j.nn.layers;


import org.deeplearning4j.exception.DL4JInvalidInputException;
import org.deeplearning4j.nn.conf.CacheMode;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.RNNFormat;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.memory.MemoryWorkspace;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.common.primitives.Pair;

import java.util.Arrays;

public class RepeatVector extends AbstractLayer<org.deeplearning4j.nn.conf.layers.misc.RepeatVector> {

    public RepeatVector(NeuralNetConfiguration conf, DataType dataType) {
        super(conf, dataType);
    }

    @Override
    public double calcRegularizationScore(boolean backpropParamsOnly){
        return 0;
    }

    @Override
    public Type type() {
        return Type.UPSAMPLING;
    }


    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(true);

        if(epsilon.dataType() != dataType){
            epsilon = epsilon.castTo(dataType);
        }

        INDArray outEpsilon;
        try(MemoryWorkspace ws = workspaceMgr.notifyScopeBorrowed(ArrayType.ACTIVATION_GRAD)){
            if (layerConf().getDataFormat() == RNNFormat.NCW) {
                outEpsilon = epsilon.sum(2);
            }else{
                outEpsilon = epsilon.sum(1);
            }
        }

        Gradient gradient = new DefaultGradient();
        return new Pair<>(gradient, outEpsilon);
    }

    protected int getN(){
        return layerConf().getN();
    }

    protected INDArray preOutput(boolean training, boolean forBackprop, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(false);
        applyDropOutIfNecessary(training, workspaceMgr);

        if (input.rank() != 2) {
            throw new DL4JInvalidInputException("Got rank " + input.rank()
                    + " array as input to RepeatVector with shape " + Arrays.toString(input.shape())
                    + ". Expected rank 2 array with shape [minibatchSize, size]. "
                    + layerId());
        }

        if (preOutput != null && forBackprop) {
            return preOutput;
        }

        long miniBatch = input.size(0);
        long size = input.size(1);
        if (getDataFormat() == RNNFormat.NCW) {
            INDArray output = input.reshape(miniBatch, size, 1).castTo(dataType);
            try (MemoryWorkspace ws = workspaceMgr.notifyScopeBorrowed(ArrayType.ACTIVATIONS)) {
                return output.repeat(2, (long) getN());
            }
        }
        else{
            INDArray output = input.reshape(miniBatch, 1, size).castTo(dataType);
            try (MemoryWorkspace ws = workspaceMgr.notifyScopeBorrowed(ArrayType.ACTIVATIONS)) {
                return output.repeat(1, (long) getN());
            }
        }
    }

    public RNNFormat getDataFormat(){
        return layerConf().getDataFormat();
    }

    @Override
    public INDArray activate(boolean training, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(false);

        if (cacheMode == null)
            cacheMode = CacheMode.NONE;

        INDArray z = preOutput(training, false, workspaceMgr);
        if (training && cacheMode != CacheMode.NONE && workspaceMgr.hasConfiguration(ArrayType.FF_CACHE)
                && workspaceMgr.isWorkspaceOpen(ArrayType.FF_CACHE)) {
            try (MemoryWorkspace wsB = workspaceMgr.notifyScopeBorrowed(ArrayType.FF_CACHE)) {
                preOutput = z.unsafeDuplication();
            }
        }
        return z;
    }

    @Override
    public boolean isPretrainLayer() {
        return false;
    }

    @Override
    public void clearNoiseWeightParams() {
        //No op
    }

    @Override
    public Gradient gradient() {
        throw new UnsupportedOperationException("Not supported - no parameters");
    }

    @Override
    public void fit() {

    }

    @Override
    public long numParams() {
        return 0;
    }

    @Override
    public void fit(INDArray input, LayerWorkspaceMgr workspaceMgr) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public double score() {
        return 0;
    }

    @Override
    public void update(INDArray gradient, String paramType) {

    }

    @Override
    public INDArray params() {
        return null;
    }

    @Override
    public INDArray getParam(String param) {
        return params();
    }

    @Override
    public void setParams(INDArray params) {

    }

}
