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

package org.deeplearning4j.nn.layers.convolution.subsampling;

import org.deeplearning4j.exception.DL4JInvalidInputException;
import org.deeplearning4j.nn.api.MaskState;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.util.ConvolutionUtils;
import org.nd4j.common.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Broadcast;
import org.nd4j.common.primitives.Pair;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;

import java.util.Arrays;

public class Subsampling1DLayer extends SubsamplingLayer {
    public Subsampling1DLayer(NeuralNetConfiguration conf, DataType dataType) {
        super(conf, dataType);
    }

    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon, LayerWorkspaceMgr workspaceMgr) {
        if (epsilon.rank() != 3)
            throw new DL4JInvalidInputException("Got rank " + epsilon.rank()
                            + " array as epsilon for Subsampling1DLayer backprop with shape "
                            + Arrays.toString(epsilon.shape())
                            + ". Expected rank 3 array with shape [minibatchSize, features, length]. " + layerId());
        if(maskArray != null){
            INDArray maskOut = feedForwardMaskArray(maskArray, MaskState.Active, (int)epsilon.size(0)).getFirst();
            Preconditions.checkState(epsilon.size(0) == maskOut.size(0) && epsilon.size(2) == maskOut.size(1),
                    "Activation gradients dimensions (0,2) and mask dimensions (0,1) don't match: Activation gradients %s, Mask %s",
                    epsilon.shape(), maskOut.shape());
            Broadcast.mul(epsilon, maskOut, epsilon, 0, 2);
        }

        // add singleton fourth dimension to input and next layer's epsilon
        INDArray origInput = input;
        input = input.castTo(dataType).reshape(input.size(0), input.size(1), input.size(2), 1);
        epsilon = epsilon.reshape(epsilon.size(0), epsilon.size(1), epsilon.size(2), 1);

        // call 2D SubsamplingLayer's backpropGradient method
        Pair<Gradient, INDArray> gradientEpsNext = super.backpropGradient(epsilon, workspaceMgr);
        INDArray epsNext = gradientEpsNext.getSecond();

        // remove singleton fourth dimension from input and current epsilon
        input = origInput;
        epsNext = epsNext.reshape(epsNext.size(0), epsNext.size(1), epsNext.size(2));

        return new Pair<>(gradientEpsNext.getFirst(), epsNext);
    }

    @Override
    public INDArray activate(boolean training, LayerWorkspaceMgr workspaceMgr) {
        if (input.rank() != 3)
            throw new DL4JInvalidInputException("Got rank " + input.rank()
                            + " array as input to Subsampling1DLayer with shape " + Arrays.toString(input.shape())
                            + ". Expected rank 3 array with shape [minibatchSize, features, length]. " + layerId());

        // add singleton fourth dimension to input
        INDArray origInput = input;
        input = input.castTo(dataType).reshape(input.size(0), input.size(1), input.size(2), 1);

        // call 2D SubsamplingLayer's activate method
        INDArray acts = super.activate(training, workspaceMgr);

        // remove singleton fourth dimension from input and output activations
        input = origInput;
        acts = acts.reshape(acts.size(0), acts.size(1), acts.size(2));

        if(maskArray != null){
            INDArray maskOut = feedForwardMaskArray(maskArray, MaskState.Active, (int)acts.size(0)).getFirst();
            Preconditions.checkState(acts.size(0) == maskOut.size(0) && acts.size(2) == maskOut.size(1),
                    "Activations dimensions (0,2) and mask dimensions (0,1) don't match: Activations %s, Mask %s",
                    acts.shape(), maskOut.shape());
            Broadcast.mul(acts, maskOut, acts, 0, 2);
        }

        return acts;
    }

    @Override
    public Pair<INDArray, MaskState> feedForwardMaskArray(INDArray maskArray, MaskState currentMaskState,
                                                          int minibatchSize) {
        INDArray reduced = ConvolutionUtils.cnn1dMaskReduction(maskArray, layerConf().getKernelSize()[0],
                layerConf().getStride()[0], layerConf().getPadding()[0], layerConf().getDilation()[0],
                layerConf().getConvolutionMode());
        return new Pair<>(reduced, currentMaskState);
    }
}
