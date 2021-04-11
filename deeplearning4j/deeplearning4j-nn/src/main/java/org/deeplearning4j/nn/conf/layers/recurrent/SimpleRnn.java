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

package org.deeplearning4j.nn.conf.layers.recurrent;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.ParamInitializer;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.BaseRecurrentLayer;
import org.deeplearning4j.nn.conf.layers.LayerValidation;
import org.deeplearning4j.nn.conf.memory.LayerMemoryReport;
import org.deeplearning4j.nn.params.SimpleRnnParamInitializer;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.Collection;
import java.util.Map;

@Data
public class SimpleRnn extends BaseRecurrentLayer {

    private boolean hasLayerNorm = false;

    protected SimpleRnn(Builder builder) {
        super(builder);
        this.hasLayerNorm = builder.hasLayerNorm;
    }

    private SimpleRnn() {

    }

    @Override
    public Layer instantiate(NeuralNetConfiguration conf, Collection<TrainingListener> trainingListeners,
                             int layerIndex, INDArray layerParamsView, boolean initializeParams, DataType networkDataType) {
        LayerValidation.assertNInNOutSet("SimpleRnn", getLayerName(), layerIndex, getNIn(), getNOut());

        org.deeplearning4j.nn.layers.recurrent.SimpleRnn ret =
                        new org.deeplearning4j.nn.layers.recurrent.SimpleRnn(conf, networkDataType);
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
        return SimpleRnnParamInitializer.getInstance();
    }

    @Override
    public LayerMemoryReport getMemoryReport(InputType inputType) {
        return null;
    }

    public boolean hasLayerNorm(){
        return hasLayerNorm;
    }

    @NoArgsConstructor
    @Getter
    @Setter
    public static class Builder extends BaseRecurrentLayer.Builder<Builder> {


        @Override
        public SimpleRnn build() {
            return new SimpleRnn(this);
        }

        /**
         * If true (default = false): enable layer normalization on this layer
         *
         */
        private boolean hasLayerNorm = false;
        public SimpleRnn.Builder hasLayerNorm(boolean hasLayerNorm){
            this.hasLayerNorm = hasLayerNorm;
            return this;
        }
    }
}
