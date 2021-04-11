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

package org.deeplearning4j.nn.conf.layers;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.ParamInitializer;
import org.deeplearning4j.nn.conf.InputPreProcessor;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.RNNFormat;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.memory.LayerMemoryReport;
import org.deeplearning4j.nn.conf.memory.MemoryReport;
import org.deeplearning4j.nn.params.EmptyParamInitializer;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.lossfunctions.ILossFunction;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.Collection;
import java.util.Map;

@Data
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class RnnLossLayer extends FeedForwardLayer {
    private RNNFormat rnnDataFormat = RNNFormat.NCW;
    protected ILossFunction lossFn;

    private RnnLossLayer(Builder builder) {
        super(builder);
        this.setLossFn(builder.lossFn);
        this.rnnDataFormat = builder.rnnDataFormat;
    }

    @Override
    public Layer instantiate(NeuralNetConfiguration conf, Collection<TrainingListener> trainingListeners,
                             int layerIndex, INDArray layerParamsView, boolean initializeParams, DataType networkDataType) {
        org.deeplearning4j.nn.layers.recurrent.RnnLossLayer ret =
                        new org.deeplearning4j.nn.layers.recurrent.RnnLossLayer(conf, networkDataType);
        ret.setListeners(trainingListeners);
        ret.setIndex(layerIndex);
        ret.setParamsViewArray(layerParamsView);
        Map<String, INDArray> paramTable = initializer().init(conf, layerParamsView, initializeParams);
        ret.setParamTable(paramTable);
        ret.setConf(conf);
        return ret;
    }

    @Override
    public ParamInitializer initializer() {
        return EmptyParamInitializer.getInstance();
    }

    @Override
    public InputType getOutputType(int layerIndex, InputType inputType) {
        if (inputType == null || inputType.getType() != InputType.Type.RNN) {
            throw new IllegalStateException("Invalid input type for RnnLossLayer (layer index = " + layerIndex
                            + ", layer name=\"" + getLayerName() + "\"): Expected RNN input, got " + inputType);
        }
        return inputType;
    }

    @Override
    public InputPreProcessor getPreProcessorForInputType(InputType inputType) {
        return InputTypeUtil.getPreprocessorForInputTypeRnnLayers(inputType, RNNFormat.NCW, getLayerName());
    }

    @Override
    public LayerMemoryReport getMemoryReport(InputType inputType) {
        //During inference and training: dup the input array. But, this counts as *activations* not working memory
        return new LayerMemoryReport.Builder(layerName, LossLayer.class, inputType, inputType).standardMemory(0, 0) //No params
                        .workingMemory(0, 0, 0, 0)
                        .cacheMemory(MemoryReport.CACHE_MODE_ALL_ZEROS, MemoryReport.CACHE_MODE_ALL_ZEROS) //No caching
                        .build();
    }

    @Override
    public void setNIn(InputType inputType, boolean override) {
        //No op
    }


    public static class Builder extends BaseOutputLayer.Builder<Builder> {

        private RNNFormat rnnDataFormat = RNNFormat.NCW;

        public Builder() {
        }

        /**
         * @param lossFunction Loss function for the loss layer
         */
        public Builder(LossFunctions.LossFunction lossFunction) {
            lossFunction(lossFunction);
        }

        /**
         * @param lossFunction Loss function for the loss layer
         */
        public Builder(ILossFunction lossFunction) {
            this.setLossFn(lossFunction);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Builder nIn(int nIn) {
            throw new UnsupportedOperationException("Ths layer has no parameters, thus nIn will always equal nOut.");
        }

        @Override
        @SuppressWarnings("unchecked")
        public Builder nOut(int nOut) {
            throw new UnsupportedOperationException("Ths layer has no parameters, thus nIn will always equal nOut.");
        }

        @Override
        public void setNIn(long nIn){
            throw new UnsupportedOperationException(
                    "This layer has no parameters, thus nIn will always equal nOut.");
        }

        @Override
        public void setNOut(long nOut){
            throw new UnsupportedOperationException(
                    "This layer has no parameters, thus nIn will always equal nOut.");
        }

        /**
         * @param rnnDataFormat Data format expected by the layer. NCW = [miniBatchSize, size, timeSeriesLength],
         * NWC = [miniBatchSize, timeSeriesLength, size]. Defaults to NCW.
         */
        public Builder dataFormat(RNNFormat rnnDataFormat){
            this.rnnDataFormat = rnnDataFormat;
            return this;
        }
        @Override
        @SuppressWarnings("unchecked")
        public RnnLossLayer build() {
            return new RnnLossLayer(this);
        }
    }
}
