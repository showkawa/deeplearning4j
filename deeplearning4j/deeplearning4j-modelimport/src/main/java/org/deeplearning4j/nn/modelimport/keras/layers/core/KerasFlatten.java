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

package org.deeplearning4j.nn.modelimport.keras.layers.core;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.deeplearning4j.nn.conf.CNN2DFormat;
import org.deeplearning4j.nn.conf.InputPreProcessor;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.inputs.InputType.InputTypeConvolutional;
import org.deeplearning4j.nn.conf.layers.Convolution3D;
import org.deeplearning4j.nn.conf.preprocessor.Cnn3DToFeedForwardPreProcessor;
import org.deeplearning4j.nn.conf.preprocessor.CnnToFeedForwardPreProcessor;
import org.deeplearning4j.nn.modelimport.keras.KerasLayer;
import org.deeplearning4j.nn.modelimport.keras.exceptions.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.exceptions.UnsupportedKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.preprocessors.KerasFlattenRnnPreprocessor;
import org.deeplearning4j.nn.modelimport.keras.preprocessors.ReshapePreprocessor;

import java.util.Map;

@Slf4j
public class KerasFlatten extends KerasLayer {

    /**
     * Constructor from parsed Keras layer configuration dictionary.
     *
     * @param layerConfig dictionary containing Keras layer configuration
     * @throws InvalidKerasConfigurationException     Invalid Keras config
     * @throws UnsupportedKerasConfigurationException Unsupported Keras config
     */
    public KerasFlatten(Map<String, Object> layerConfig)
            throws InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {
        this(layerConfig, true);
    }

    /**
     * Constructor from parsed Keras layer configuration dictionary.
     *
     * @param layerConfig           dictionary containing Keras layer configuration
     * @param enforceTrainingConfig whether to enforce training-related configuration options
     * @throws InvalidKerasConfigurationException     Invalid Keras config
     * @throws UnsupportedKerasConfigurationException Unsupported Keras config
     */
    public KerasFlatten(Map<String, Object> layerConfig, boolean enforceTrainingConfig)
            throws InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {
        super(layerConfig, enforceTrainingConfig);
    }

    /**
     * Whether this Keras layer maps to a DL4J InputPreProcessor.
     *
     * @return true
     */
    @Override
    public boolean isInputPreProcessor() {
        return true;
    }

    /**
     * Gets appropriate DL4J InputPreProcessor for given InputTypes.
     *
     * @param inputType Array of InputTypes
     * @return DL4J InputPreProcessor
     * @throws InvalidKerasConfigurationException Invalid Keras config
     * @see org.deeplearning4j.nn.conf.InputPreProcessor
     */
    @Override
    public InputPreProcessor getInputPreprocessor(InputType... inputType) throws InvalidKerasConfigurationException {
        if (inputType.length > 1)
            throw new InvalidKerasConfigurationException(
                    "Keras Flatten layer accepts only one input (received " + inputType.length + ")");
        /**
         * TODO: On layer name dropout_2 as input the flatten layer seems to be outputting 20 instead of 80.
         * Likely due to needing to multiply the final outputs totaled to 80, but only getting to 20.
         */
        InputPreProcessor preprocessor = null;
        if (inputType[0] instanceof InputTypeConvolutional) {
            InputTypeConvolutional it = (InputTypeConvolutional) inputType[0];
            switch (this.getDimOrder()) {
                case NONE:
                case THEANO:
                    preprocessor = new CnnToFeedForwardPreProcessor(it.getHeight(), it.getWidth(), it.getChannels(), CNN2DFormat.NCHW);
                    break;
                case TENSORFLOW:
                    preprocessor = new CnnToFeedForwardPreProcessor(it.getHeight(), it.getWidth(), it.getChannels(), CNN2DFormat.NHWC);
                    break;
                default:
                    throw new InvalidKerasConfigurationException("Unknown Keras backend " + this.getDimOrder());
            }
        } else if (inputType[0] instanceof InputType.InputTypeRecurrent) {
            InputType.InputTypeRecurrent it = (InputType.InputTypeRecurrent) inputType[0];
            preprocessor = new KerasFlattenRnnPreprocessor(it.getSize(), it.getTimeSeriesLength());
        } else if (inputType[0] instanceof InputType.InputTypeFeedForward) {
            // NOTE: The output of an embedding layer in DL4J is of feed-forward type. Only if an FF to RNN input
            // preprocessor is set or we explicitly provide 3D input data to start with, will the its output be set
            // to RNN type. Otherwise we add this trivial preprocessor (since there's nothing to flatten).
            InputType.InputTypeFeedForward it = (InputType.InputTypeFeedForward) inputType[0];
            val inputShape = new long[]{it.getSize()};
            preprocessor = new ReshapePreprocessor(inputShape, inputShape, false, null);
        } else if(inputType[0] instanceof InputType.InputTypeConvolutional3D) {
            InputType.InputTypeConvolutional3D it = (InputType.InputTypeConvolutional3D) inputType[0];
            switch (this.getDimOrder()) {
                case NONE:
                case THEANO:
                    preprocessor = new Cnn3DToFeedForwardPreProcessor(it.getDepth(),it.getHeight(),it.getWidth(),
                            it.getChannels(),it.getDataFormat() == Convolution3D.DataFormat.NCDHW);
                    break;
                case TENSORFLOW:
                    preprocessor = new Cnn3DToFeedForwardPreProcessor(it.getDepth(),it.getHeight(),it.getWidth(),
                            it.getChannels(),it.getDataFormat() != Convolution3D.DataFormat.NCDHW);
                    break;
                default:
                    throw new InvalidKerasConfigurationException("Unknown Keras backend " + this.getDimOrder());
            }
        }
        return preprocessor;
    }

    /**
     * Get layer output type.
     *
     * @param inputType Array of InputTypes
     * @return output type as InputType
     * @throws InvalidKerasConfigurationException Invalid Keras config
     */
    @Override
    public InputType getOutputType(InputType... inputType) throws InvalidKerasConfigurationException {
        if (inputType.length > 1)
            throw new InvalidKerasConfigurationException(
                    "Keras Flatten layer accepts only one input (received " + inputType.length + ")");
        InputPreProcessor preprocessor = getInputPreprocessor(inputType);
        if (preprocessor != null) {
            return preprocessor.getOutputType(inputType[0]);
        }
        return inputType[0];
    }
}
