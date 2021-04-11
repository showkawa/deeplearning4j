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

package org.deeplearning4j.nn.layers.convolution;

import lombok.val;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.conf.CNN2DFormat;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.layers.AbstractLayer;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.common.primitives.Pair;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.deeplearning4j.nn.workspace.ArrayType;

public class ZeroPaddingLayer extends AbstractLayer<org.deeplearning4j.nn.conf.layers.ZeroPaddingLayer> {

    public ZeroPaddingLayer(NeuralNetConfiguration conf, DataType dataType) {
        super(conf, dataType);
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
    public Type type() {
        return Type.CONVOLUTIONAL;
    }

    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(true);
        val inShape = input.shape();

        boolean nchw = layerConf().getDataFormat() == CNN2DFormat.NCHW;
        int hIdx = nchw ? 2 : 1;
        int wIdx = nchw ? 3 : 2;

        INDArray epsNext;
        int[] padding = layerConf().getPadding();
        if(layerConf().getDataFormat() == CNN2DFormat.NCHW){
            epsNext = epsilon.get(NDArrayIndex.all(), NDArrayIndex.all(),
                    NDArrayIndex.interval(padding[0], padding[0] + inShape[hIdx]),
                    NDArrayIndex.interval(padding[2], padding[2] + inShape[wIdx]));
        } else {
            //NHWC
            epsNext = epsilon.get(NDArrayIndex.all(),
                    NDArrayIndex.interval(padding[0], padding[0] + inShape[hIdx]),
                    NDArrayIndex.interval(padding[2], padding[2] + inShape[wIdx]),
                    NDArrayIndex.all());
        }

        epsNext = workspaceMgr.leverageTo(ArrayType.ACTIVATION_GRAD, epsNext);
        return new Pair<>((Gradient) new DefaultGradient(), epsNext);
    }


    @Override
    public INDArray activate(boolean training, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(false);
        boolean nchw = layerConf().getDataFormat() == CNN2DFormat.NCHW;
        int hIdx = nchw ? 2 : 1;
        int wIdx = nchw ? 3 : 2;

        int[] padding = layerConf().getPadding();
        val inShape = input.shape();
        val outH = inShape[hIdx] + padding[0] + padding[1];
        val outW = inShape[wIdx] + padding[2] + padding[3];
        val outShape = nchw ? new long[] {inShape[0], inShape[1], outH, outW} : new long[] {inShape[0], outH, outW, inShape[3]};

        INDArray out = workspaceMgr.create(ArrayType.ACTIVATIONS, input.dataType(), outShape, 'c');

        if(nchw) {
            out.put(new INDArrayIndex[]{NDArrayIndex.all(), NDArrayIndex.all(),
                    NDArrayIndex.interval(padding[0], padding[0] + inShape[hIdx]),
                    NDArrayIndex.interval(padding[2], padding[2] + inShape[wIdx])}, input);
        } else {
            out.put(new INDArrayIndex[]{NDArrayIndex.all(),
                    NDArrayIndex.interval(padding[0], padding[0] + inShape[hIdx]),
                    NDArrayIndex.interval(padding[2], padding[2] + inShape[wIdx]),
                    NDArrayIndex.all()}, input);
        }

        return out;
    }

    @Override
    public Layer clone() {
        return new ZeroPaddingLayer(conf.clone(), dataType);
    }

    @Override
    public double calcRegularizationScore(boolean backpropParamsOnly){
        return 0;
    }
}
