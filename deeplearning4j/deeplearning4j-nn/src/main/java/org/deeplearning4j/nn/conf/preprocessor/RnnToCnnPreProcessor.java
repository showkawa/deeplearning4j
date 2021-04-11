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

package org.deeplearning4j.nn.conf.preprocessor;

import lombok.*;
import org.deeplearning4j.nn.api.MaskState;
import org.deeplearning4j.nn.conf.InputPreProcessor;
import org.deeplearning4j.nn.conf.RNNFormat;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.util.TimeSeriesUtils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.common.primitives.Pair;
import org.nd4j.common.util.ArrayUtil;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.nd4j.shade.jackson.annotation.JsonProperty;

import java.util.Arrays;

@Data
@EqualsAndHashCode(exclude = {"product"})
public class RnnToCnnPreProcessor implements InputPreProcessor {

    private int inputHeight;
    private int inputWidth;
    private int numChannels;
    private RNNFormat rnnDataFormat = RNNFormat.NCW;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private int product;

    public RnnToCnnPreProcessor(@JsonProperty("inputHeight") int inputHeight,
                                @JsonProperty("inputWidth") int inputWidth,
                                @JsonProperty("numChannels") int numChannels,
                                @JsonProperty("rnnDataFormat") RNNFormat rnnDataFormat) {
        this.inputHeight = inputHeight;
        this.inputWidth = inputWidth;
        this.numChannels = numChannels;
        this.product = inputHeight * inputWidth * numChannels;
        this.rnnDataFormat = rnnDataFormat;
    }

    public RnnToCnnPreProcessor(int inputHeight,
                                int inputWidth,
                                int numChannels){
        this(inputHeight, inputWidth, numChannels, RNNFormat.NCW);
    }

    @Override
    public INDArray preProcess(INDArray input, int miniBatchSize, LayerWorkspaceMgr workspaceMgr) {
        if (input.ordering() != 'f' || !Shape.hasDefaultStridesForShape(input))
            input = input.dup('f');
        //Input: 3d activations (RNN)
        //Output: 4d activations (CNN)
        if (rnnDataFormat == RNNFormat.NWC){
            input = input.permute(0, 2, 1);
        }
        val shape = input.shape();
        INDArray in2d;
        if (shape[0] == 1) {
            //Edge case: miniBatchSize = 1
            in2d = input.tensorAlongDimension(0, 1, 2).permutei(1, 0);
        } else if (shape[2] == 1) {
            //Edge case: time series length = 1
            in2d = input.tensorAlongDimension(0, 1, 0);
        } else {
            INDArray permuted = input.permute(0, 2, 1); //Permute, so we get correct order after reshaping
            in2d = permuted.reshape('f', shape[0] * shape[2], shape[1]);
        }

        return workspaceMgr.dup(ArrayType.ACTIVATIONS, in2d, 'c')
                .reshape('c', shape[0] * shape[2], numChannels, inputHeight, inputWidth);
    }

    @Override
    public INDArray backprop(INDArray output, int miniBatchSize, LayerWorkspaceMgr workspaceMgr) {
        //Input: 4d epsilons (CNN)
        //Output: 3d epsilons (RNN)
        if (output.ordering() != 'c' || !Shape.hasDefaultStridesForShape(output))
            output = output.dup('c');
        val shape = output.shape();
        //First: reshape 4d to 2d
        INDArray twod = output.reshape('c', output.size(0), ArrayUtil.prod(output.shape()) / output.size(0));
        //Second: reshape 2d to 3d
        INDArray reshaped = workspaceMgr.dup(ArrayType.ACTIVATION_GRAD, twod, 'f').reshape('f', miniBatchSize, shape[0] / miniBatchSize, product);
        if (rnnDataFormat == RNNFormat.NCW) {
            reshaped = reshaped.permute(0, 2, 1);
        }
        return reshaped;
    }

    @Override
    public RnnToCnnPreProcessor clone() {
        return new RnnToCnnPreProcessor(inputHeight, inputWidth, numChannels, rnnDataFormat);
    }

    @Override
    public InputType getOutputType(InputType inputType) {
        if (inputType == null || inputType.getType() != InputType.Type.RNN) {
            throw new IllegalStateException("Invalid input type: Expected input of type RNN, got " + inputType);
        }

        InputType.InputTypeRecurrent c = (InputType.InputTypeRecurrent) inputType;
        int expSize = inputHeight * inputWidth * numChannels;
        if (c.getSize() != expSize) {
            throw new IllegalStateException("Invalid input: expected RNN input of size " + expSize + " = (d="
                            + numChannels + " * w=" + inputWidth + " * h=" + inputHeight + "), got " + inputType);
        }

        return InputType.convolutional(inputHeight, inputWidth, numChannels);
    }

    @Override
    public Pair<INDArray, MaskState> feedForwardMaskArray(INDArray maskArray, MaskState currentMaskState,
                    int minibatchSize) {
        //Assume mask array is 2d for time series (1 value per time step)
        if (maskArray == null) {
            return new Pair<>(maskArray, currentMaskState);
        } else if (maskArray.rank() == 2) {
            //Need to reshape mask array from [minibatch,timeSeriesLength] to 4d minibatch format: [minibatch*timeSeriesLength, 1, 1, 1]
            return new Pair<>(TimeSeriesUtils.reshapeTimeSeriesMaskToCnn4dMask(maskArray,
                    LayerWorkspaceMgr.noWorkspacesImmutable(), ArrayType.INPUT), currentMaskState);
        } else {
            throw new IllegalArgumentException("Received mask array of rank " + maskArray.rank()
                            + "; expected rank 2 mask array. Mask array shape: " + Arrays.toString(maskArray.shape()));
        }
    }
}
