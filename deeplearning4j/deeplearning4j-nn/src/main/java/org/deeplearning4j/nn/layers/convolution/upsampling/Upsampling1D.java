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

package org.deeplearning4j.nn.layers.convolution.upsampling;

import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.exception.DL4JInvalidInputException;
import org.deeplearning4j.nn.conf.CNN2DFormat;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.BaseUpsamplingLayer;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.CustomOp;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.common.primitives.Pair;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;

import java.util.Arrays;


@Slf4j
public class Upsampling1D extends Upsampling2D {


    public Upsampling1D(NeuralNetConfiguration conf, DataType dataType) {
        super(conf, dataType);
    }

    @Override
    protected CNN2DFormat getFormat(){
        return CNN2DFormat.NCHW;
    }

    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(true);

        int[] size = ((BaseUpsamplingLayer) layerConf()).getSize();
        epsilon = epsilon.reshape(epsilon.size(0), epsilon.size(1), epsilon.size(2), 1);
        // we replicate the error term times "size" so that backprop works properly on it
        epsilon = epsilon.repeat(3, size[0]);

        INDArray originalInput = input;
        input = input.castTo(dataType).reshape(input.size(0), input.size(1), input.size(2), 1);

        long miniBatch = input.size(0);
        long inDepth = input.size(1);
        long inH = input.size(2);
        long inW = input.size(3);


        INDArray outEpsilon = workspaceMgr.createUninitialized(ArrayType.ACTIVATION_GRAD, input.dataType(), miniBatch * inDepth * inH * inW);
        INDArray reshapedEpsilon = outEpsilon.reshape('c', miniBatch, inDepth, inH, inW);

        int[] intArgs = new int[] {1}; // 1 is for NCHW

        CustomOp op = DynamicCustomOp.builder("upsampling_bp")
                .addIntegerArguments(intArgs)
                .addInputs(input, epsilon)
                .addOutputs(reshapedEpsilon)
                .callInplace(false)
                .build();
        Nd4j.getExecutioner().exec(op);

        Gradient gradient = new DefaultGradient();

        reshapedEpsilon = reshapedEpsilon.slice(0, 3);
        input = originalInput;

        // Since we aggregate the gradient across "size" slices, we need to normalize afterwards.
        return new Pair<>(gradient, reshapedEpsilon.divi(size[0]));
    }

    @Override
    protected int[] getSize(){
        return ((org.deeplearning4j.nn.conf.layers.Upsampling1D)conf.getLayer()).getSize();
    }

    @Override
    public INDArray activate(boolean training, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(false);
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

        return acts;
    }

    @Override
    protected INDArray preOutput(boolean training, boolean forBackprop, LayerWorkspaceMgr workspaceMgr) {
        INDArray originalInput = input;
        input = input.reshape(input.size(0), input.size(1), input.size(2), 1);

        INDArray preOutput = super.preOutput(training, forBackprop, workspaceMgr);

        input = originalInput;
        preOutput = preOutput.slice(0, 3);

        return preOutput;
    }


}
