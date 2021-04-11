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

import lombok.*;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.ParamInitializer;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.memory.LayerMemoryReport;
import org.deeplearning4j.nn.conf.memory.MemoryReport;
import org.deeplearning4j.nn.params.DefaultParamInitializer;
import org.deeplearning4j.nn.params.EmbeddingLayerParamInitializer;
import org.deeplearning4j.nn.weights.IWeightInit;
import org.deeplearning4j.nn.weights.embeddings.ArrayEmbeddingInitializer;
import org.deeplearning4j.nn.weights.embeddings.EmbeddingInitializer;
import org.deeplearning4j.nn.weights.embeddings.WeightInitEmbedding;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.nd4j.linalg.activations.impl.ActivationIdentity;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.Collection;
import java.util.Map;

@Data
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class EmbeddingLayer extends FeedForwardLayer {

    private boolean hasBias = true; //Default for pre-0.9.2 implementations

    private EmbeddingLayer(Builder builder) {
        super(builder);
        this.hasBias = builder.hasBias;
        initializeConstraints(builder);
    }

    @Override
    public Layer instantiate(NeuralNetConfiguration conf, Collection<TrainingListener> trainingListeners,
                             int layerIndex, INDArray layerParamsView, boolean initializeParams, DataType networkDataType) {
        org.deeplearning4j.nn.layers.feedforward.embedding.EmbeddingLayer ret =
                        new org.deeplearning4j.nn.layers.feedforward.embedding.EmbeddingLayer(conf, networkDataType);
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
        return EmbeddingLayerParamInitializer.getInstance();
    }

    @Override
    public LayerMemoryReport getMemoryReport(InputType inputType) {
        //Basically a dense layer, but no dropout is possible here, and no epsilons
        InputType outputType = getOutputType(-1, inputType);

        val actElementsPerEx = outputType.arrayElementsPerExample();
        val numParams = initializer().numParams(this);
        val updaterStateSize = (int) getIUpdater().stateSize(numParams);

        //Embedding layer does not use caching.
        //Inference: no working memory - just activations (pullRows)
        //Training: preout op, the only in-place ops on epsilon (from layer above) + assign ops

        return new LayerMemoryReport.Builder(layerName, EmbeddingLayer.class, inputType, outputType)
                        .standardMemory(numParams, updaterStateSize).workingMemory(0, 0, 0, actElementsPerEx)
                        .cacheMemory(MemoryReport.CACHE_MODE_ALL_ZEROS, MemoryReport.CACHE_MODE_ALL_ZEROS) //No caching
                        .build();
    }

    public boolean hasBias() {
        return hasBias;
    }

    @Getter
    @Setter
    public static class Builder extends FeedForwardLayer.Builder<Builder> {

        /**
         * If true: include bias parameters in the layer. False (default): no bias.
         *
         */
        private boolean hasBias = false;

        public Builder(){
            //Default to Identity activation - i.e., don't inherit.
            //For example, if user sets ReLU as global default, they very likely don't intend to use it for Embedding layer also
            this.activationFn = new ActivationIdentity();
        }


        /**
         * If true: include bias parameters in the layer. False (default): no bias.
         *
         * @param hasBias If true: include bias parameters in this layer
         */
        public Builder hasBias(boolean hasBias) {
            this.hasBias = hasBias;
            return this;
        }

        @Override
        public Builder weightInit(IWeightInit weightInit) {
            if(weightInit instanceof WeightInitEmbedding){
                long[] shape = ((WeightInitEmbedding) weightInit).shape();
                nIn(shape[0]);
                nOut(shape[1]);
            }
            return super.weightInit(weightInit);
        }

        /**
         * Initialize the embedding layer using the specified EmbeddingInitializer - such as a Word2Vec instance
         *
         * @param embeddingInitializer Source of the embedding layer weights
         */
        public Builder weightInit(EmbeddingInitializer embeddingInitializer){
            return weightInit(new WeightInitEmbedding(embeddingInitializer));
        }

        /**
         * Initialize the embedding layer using values from the specified array. Note that the array should have shape
         * [vocabSize, vectorSize]. After copying values from the array to initialize the network parameters, the input
         * array will be discarded (so that, if necessary, it can be garbage collected)
         *
         * @param vectors Vectors to initialize the embedding layer with
         */
        public Builder weightInit(INDArray vectors){
            return weightInit(new ArrayEmbeddingInitializer(vectors));
        }

        @Override
        @SuppressWarnings("unchecked")
        public EmbeddingLayer build() {
            return new EmbeddingLayer(this);
        }
    }
}
