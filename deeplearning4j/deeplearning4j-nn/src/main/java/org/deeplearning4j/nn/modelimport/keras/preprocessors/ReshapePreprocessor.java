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

package org.deeplearning4j.nn.modelimport.keras.preprocessors;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.deeplearning4j.nn.conf.CNN2DFormat;
import org.deeplearning4j.nn.conf.DataFormat;
import org.deeplearning4j.nn.conf.RNNFormat;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.inputs.InvalidInputTypeException;
import org.deeplearning4j.nn.conf.layers.Convolution3D;
import org.deeplearning4j.nn.conf.preprocessor.BaseInputPreProcessor;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.shade.jackson.annotation.JsonIgnoreProperties;
import org.nd4j.shade.jackson.annotation.JsonProperty;

import java.util.Arrays;

import static org.nd4j.common.util.ArrayUtil.prodLong;

@Data
@Slf4j
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties({"miniBatchSize", "staticTargetShape"})
public class ReshapePreprocessor extends BaseInputPreProcessor {

    private final long[] inputShape;
    private final long[] targetShape;
    private boolean hasMiniBatchDimension;
    private DataFormat format;

    /**
     * @param inputShape            Input shape, with or without leading minibatch dimension, depending on value of hasMiniBatchDimension
     * @param targetShape           Target shape, with or without leading minibatch dimension, depending on value of hasMiniBatchDimension
     * @param hasMiniBatchDimension If true: shapes should be of the form [minibatch, x, y, ...]; if false: shapes should be of form [x, y, ...]
     */
    public ReshapePreprocessor(long[] inputShape, long[] targetShape, boolean hasMiniBatchDimension) {
        this(inputShape, targetShape, hasMiniBatchDimension, null);
    }

    /**
     * @param inputShape            Input shape, with or without leading minibatch dimension, depending on value of hasMiniBatchDimension
     * @param targetShape           Target shape, with or without leading minibatch dimension, depending on value of hasMiniBatchDimension
     * @param hasMiniBatchDimension If true: shapes should be of the form [minibatch, x, y, ...]; if false: shapes should be of form [x, y, ...]
     * @param dataFormat            May be null. If non-null:
     */
    public ReshapePreprocessor(@JsonProperty("inputShape") long[] inputShape, @JsonProperty("targetShape") long[] targetShape,
                               @JsonProperty("hasMiniBatchDimension") boolean hasMiniBatchDimension,
                               @JsonProperty("dataFormat") DataFormat dataFormat) {
        this.inputShape = inputShape;
        this.targetShape = targetShape;
        this.hasMiniBatchDimension = hasMiniBatchDimension;
        this.format = dataFormat;
    }

    private long[] getShape(long[] originalShape, long minibatch) {
        long[] newShape = (hasMiniBatchDimension ? originalShape : prependMiniBatchSize(originalShape, minibatch));
        if (newShape[0] != minibatch) {
            newShape = newShape.clone();
            newShape[0] = minibatch;
        }
        return newShape;
    }

    private static long[] prependMiniBatchSize(long[] shape, long miniBatchSize) {
        int shapeLength = shape.length;
        val miniBatchShape = new long[shapeLength + 1];
        miniBatchShape[0] = miniBatchSize;
        for (int i = 1; i < miniBatchShape.length; i++) {
            miniBatchShape[i] = shape[i - 1];
        }
        return miniBatchShape;
    }

    @Override
    public INDArray preProcess(INDArray input, int miniBatchSize, LayerWorkspaceMgr workspaceMgr) {
        // the target shape read from a keras config does not have mini-batch size included. We prepend it here dynamically.
        long[] targetShape = getShape(this.targetShape, miniBatchSize);

        if (prodLong(input.shape()) == prodLong((targetShape))) {
            if (input.ordering() != 'c' || !Shape.hasDefaultStridesForShape(input)) {
                input = workspaceMgr.dup(ArrayType.ACTIVATIONS, input, 'c');
            }
            return workspaceMgr.leverageTo(ArrayType.ACTIVATIONS, input.reshape(targetShape));
        } else {
            throw new IllegalStateException("Input shape " + Arrays.toString(input.shape())
                    + " and target shape" + Arrays.toString(targetShape) + " do not match");
        }
    }

    @Override
    public INDArray backprop(INDArray output, int miniBatchSize, LayerWorkspaceMgr workspaceMgr) {
        long[] targetShape = getShape(this.targetShape, miniBatchSize);
        long[] inputShape = getShape(this.inputShape, miniBatchSize);

        if (!Arrays.equals(targetShape, output.shape())) {
            throw new IllegalStateException("Unexpected output shape" + Arrays.toString(output.shape())
                    + " (expected to be " + Arrays.toString(targetShape) + ")");
        }
        if (prodLong(output.shape()) == prodLong((targetShape))) {
            if (output.ordering() != 'c' || !Shape.hasDefaultStridesForShape(output)) {
                output = workspaceMgr.dup(ArrayType.ACTIVATIONS, output, 'c');
            }
            return workspaceMgr.leverageTo(ArrayType.ACTIVATION_GRAD, output.reshape(inputShape));
        } else {
            throw new IllegalStateException("Output shape" + Arrays.toString(output.shape())
                    + " and input shape" + Arrays.toString(targetShape) + " do not match");
        }
    }

    @Override
    public InputType getOutputType(InputType inputType) throws InvalidInputTypeException {
        long[] shape = getShape(this.targetShape, 0);
        InputType ret;
        switch (shape.length) {
            case 2:
                ret = InputType.feedForward(shape[1]);
                break;
            case 3:
                RNNFormat format = RNNFormat.NWC;
                if(this.format != null && this.format instanceof RNNFormat)
                    format = (RNNFormat) this.format;

                ret = InputType.recurrent(shape[2], shape[1], format);
                break;
            case 4:
                if (inputShape.length == 1 || inputType.getType() == InputType.Type.RNN) {
                    //note here the default is tensorflow initialization for keras.
                    //being channels first has side effects when working with other models
                    ret = InputType.convolutional(shape[1], shape[2], shape[3],CNN2DFormat.NHWC);
                } else {

                    CNN2DFormat cnnFormat = CNN2DFormat.NCHW;
                    if (this.format != null && this.format instanceof CNN2DFormat)
                        cnnFormat = (CNN2DFormat) this.format;

                    if (cnnFormat == CNN2DFormat.NCHW) {
                        ret = InputType.convolutional(shape[2], shape[3], shape[1], cnnFormat);
                    } else {
                        ret = InputType.convolutional(shape[1], shape[2], shape[3], cnnFormat);
                    }
                }
                break;
            case 5:
                if (inputShape.length == 1 || inputType.getType() == InputType.Type.RNN) {
                    //note here the default is tensorflow initialization for keras.
                    //being channels first has side effects when working with other models
                    Convolution3D.DataFormat dataFormat = (Convolution3D.DataFormat) this.format;
                    if(dataFormat == Convolution3D.DataFormat.NCDHW) {
                        ret =  InputType.convolutional3D(dataFormat,shape[2],shape[3],shape[4],shape[0]);
                        //default value
                    } else if(dataFormat == Convolution3D.DataFormat.NDHWC || dataFormat == null) {
                        ret =  InputType.convolutional3D(dataFormat,shape[1],shape[2],shape[3],shape[0]);
                    } else {
                        throw new IllegalArgumentException("Illegal format found " + dataFormat);
                    }
                } else {

                    CNN2DFormat cnnFormat = CNN2DFormat.NCHW;
                    if (this.format != null && this.format instanceof CNN2DFormat)
                        cnnFormat = (CNN2DFormat) this.format;

                    if (cnnFormat == CNN2DFormat.NCHW) {
                        ret = InputType.convolutional(shape[2], shape[3], shape[1], cnnFormat);
                    } else {
                        ret = InputType.convolutional(shape[1], shape[2], shape[3], cnnFormat);
                    }
                }
                break;
            default:
                throw new UnsupportedOperationException(
                        "Cannot infer input type for reshape array " + Arrays.toString(shape));
        }
        return ret;
    }
}