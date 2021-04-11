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

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.val;
import org.deeplearning4j.nn.api.MaskState;
import org.deeplearning4j.nn.conf.InputPreProcessor;
import org.deeplearning4j.nn.conf.RNNFormat;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.util.TimeSeriesUtils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.common.primitives.Pair;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.nd4j.shade.jackson.annotation.JsonProperty;
import java.util.Arrays;

@Data
@NoArgsConstructor
public class FeedForwardToRnnPreProcessor implements InputPreProcessor {
    private RNNFormat rnnDataFormat = RNNFormat.NCW;

    public FeedForwardToRnnPreProcessor(@JsonProperty("rnnDataFormat") RNNFormat rnnDataFormat){
        if(rnnDataFormat != null)
            this.rnnDataFormat = rnnDataFormat;
    }
    @Override
    public INDArray preProcess(INDArray input, int miniBatchSize, LayerWorkspaceMgr workspaceMgr) {
        //Need to reshape FF activations (2d) activations to 3d (for input into RNN layer)
        if (input.rank() != 2)
            throw new IllegalArgumentException(
                            "Invalid input: expect NDArray with rank 2 (i.e., activations for FF layer)");
        if (input.ordering() != 'f' || !Shape.hasDefaultStridesForShape(input))
            input = workspaceMgr.dup(ArrayType.ACTIVATIONS, input, 'f');

        val shape = input.shape();
        INDArray reshaped = input.reshape('f', miniBatchSize, shape[0] / miniBatchSize, shape[1]);
        if (rnnDataFormat == RNNFormat.NCW){
            reshaped = reshaped.permute(0, 2, 1);
        }
        return workspaceMgr.leverageTo(ArrayType.ACTIVATIONS, reshaped);
    }

    @Override
    public INDArray backprop(INDArray output, int miniBatchSize, LayerWorkspaceMgr workspaceMgr) {
        //Need to reshape RNN epsilons (3d) to 2d (for use in FF layer backprop calculations)
        if (output.rank() != 3)
            throw new IllegalArgumentException(
                            "Invalid input: expect NDArray with rank 3 (i.e., epsilons from RNN layer)");
        if (output.ordering() != 'f' || !Shape.hasDefaultStridesForShape(output))
            output = workspaceMgr.dup(ArrayType.ACTIVATION_GRAD, output, 'f');
        if (rnnDataFormat == RNNFormat.NWC){
            output = output.permute(0, 2, 1);
        }
        val shape = output.shape();

        INDArray ret;
        if (shape[0] == 1) {
            ret = output.tensorAlongDimension(0, 1, 2).permutei(1, 0); //Edge case: miniBatchSize==1
        } else if (shape[2] == 1) {
            return output.tensorAlongDimension(0, 1, 0); //Edge case: timeSeriesLength=1
        } else {
            INDArray permuted = output.permute(0, 2, 1); //Permute, so we get correct order after reshaping
            ret = permuted.reshape('f', shape[0] * shape[2], shape[1]);
        }
        return workspaceMgr.leverageTo(ArrayType.ACTIVATION_GRAD, ret);
    }

    @Override
    public FeedForwardToRnnPreProcessor clone() {
        return new FeedForwardToRnnPreProcessor(rnnDataFormat);
    }

    @Override
    public InputType getOutputType(InputType inputType) {
        if (inputType == null || (inputType.getType() != InputType.Type.FF
                        && inputType.getType() != InputType.Type.CNNFlat)) {
            throw new IllegalStateException("Invalid input: expected input of type FeedForward, got " + inputType);
        }

        if (inputType.getType() == InputType.Type.FF) {
            InputType.InputTypeFeedForward ff = (InputType.InputTypeFeedForward) inputType;
            return InputType.recurrent(ff.getSize(), rnnDataFormat);
        } else {
            InputType.InputTypeConvolutionalFlat cf = (InputType.InputTypeConvolutionalFlat) inputType;
            return InputType.recurrent(cf.getFlattenedSize(), rnnDataFormat);
        }
    }

    @Override
    public Pair<INDArray, MaskState> feedForwardMaskArray(INDArray maskArray, MaskState currentMaskState,
                    int minibatchSize) {
        //Assume mask array is 1d - a mask array that has been reshaped from [minibatch,timeSeriesLength] to [minibatch*timeSeriesLength, 1]
        if (maskArray == null) {
            return new Pair<>(maskArray, currentMaskState);
        } else if (maskArray.isVector()) {
            //Need to reshape mask array from [minibatch*timeSeriesLength, 1] to [minibatch,timeSeriesLength]
            return new Pair<>(TimeSeriesUtils.reshapeVectorToTimeSeriesMask(maskArray, minibatchSize),
                            currentMaskState);
        } else {
            throw new IllegalArgumentException("Received mask array with shape " + Arrays.toString(maskArray.shape())
                            + "; expected vector.");
        }
    }
}
