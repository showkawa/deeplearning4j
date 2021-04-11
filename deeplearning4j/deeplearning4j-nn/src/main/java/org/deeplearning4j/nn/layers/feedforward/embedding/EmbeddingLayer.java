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

package org.deeplearning4j.nn.layers.feedforward.embedding;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.exception.ND4JArraySizeException;
import org.nd4j.common.primitives.Pair;
import org.deeplearning4j.exception.DL4JInvalidInputException;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.layers.BaseLayer;
import org.deeplearning4j.nn.params.DefaultParamInitializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.deeplearning4j.nn.workspace.ArrayType;

@Slf4j
public class EmbeddingLayer extends BaseLayer<org.deeplearning4j.nn.conf.layers.EmbeddingLayer> {
    private static final int[] DIM_1 = new int[]{1};

    public EmbeddingLayer(NeuralNetConfiguration conf, DataType dataType) {
        super(conf, dataType);
    }

    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(true);
        //If this layer is layer L, then epsilon is (w^(L+1)*(d^(L+1))^T) (or equivalent)
        INDArray z = preOutput(true, workspaceMgr);
        INDArray delta = layerConf().getActivationFn().backprop(z, epsilon).getFirst(); //TODO handle activation function params

        if (maskArray != null) {
            delta.muliColumnVector(maskArray.castTo(dataType));
        }

        INDArray weightGradients = gradientViews.get(DefaultParamInitializer.WEIGHT_KEY);
        weightGradients.assign(0);

        long[] indexes = new long[(int) input.length()];
        for (int i = 0; i < indexes.length; i++) {
            indexes[i] = input.getInt(i, 0);
        }

        INDArray indices = Nd4j.createFromArray(indexes);
        Nd4j.scatterUpdate(org.nd4j.linalg.api.ops.impl.scatter.ScatterUpdate.UpdateOp.ADD, weightGradients, indices, delta, DIM_1);


        Gradient ret = new DefaultGradient();
        ret.gradientForVariable().put(DefaultParamInitializer.WEIGHT_KEY, weightGradients);

        if(hasBias()) {
            INDArray biasGradientsView = gradientViews.get(DefaultParamInitializer.BIAS_KEY);
            delta.sum(biasGradientsView, 0); //biasGradientView is initialized/zeroed first in sum op
            ret.gradientForVariable().put(DefaultParamInitializer.BIAS_KEY, biasGradientsView);
        }

        return new Pair<>(ret, null); //Don't bother returning epsilons: no layer below this one...
    }

    @Override
    protected INDArray preOutput(boolean training, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(false);
        if (input.columns() != 1) {
            if(input.isRowVector()) {
                input = input.reshape(input.length(),1);
            }
            else
                //Assume shape is [numExamples,1], and each entry is an integer index
                throw new DL4JInvalidInputException(
                        "Cannot do forward pass for embedding layer with input more than one column. "
                                + "Expected input shape: [numExamples,1] with each entry being an integer index "
                                + layerId());
        }

        val nIn = layerConf().getNIn();

        if (input.length() > Integer.MAX_VALUE)
            throw new ND4JArraySizeException();
        int[] indexes = new int[(int) input.length()];
        for (int i = 0; i < indexes.length; i++) {
            indexes[i] = input.getInt(i, 0);

            if (indexes[i] < 0 || indexes[i] >= nIn) {
                throw new DL4JInvalidInputException("Invalid index for embedding layer: got index " + indexes[i]
                        + " for entry " + i + " in minibatch; indexes must be between 0 and nIn-1 inclusive (0 to "
                        + (nIn  -1) + ")");
            }
        }

        INDArray weights = getParam(DefaultParamInitializer.WEIGHT_KEY);
        INDArray bias = getParam(DefaultParamInitializer.BIAS_KEY);

        INDArray destination = workspaceMgr.createUninitialized(ArrayType.ACTIVATIONS, weights.dataType(), input.size(0), weights.size(1));
        INDArray rows = Nd4j.pullRows(weights, destination, 1, indexes);
        if(hasBias()){
            rows.addiRowVector(bias);
        }

        return rows;
    }

    @Override
    public INDArray activate(boolean training, LayerWorkspaceMgr workspaceMgr) {
        INDArray rows = preOutput(training, workspaceMgr);

        INDArray ret = layerConf().getActivationFn().getActivation(rows, training);
        if (maskArray != null) {
            ret.muliColumnVector(maskArray.castTo(dataType));
        }
        return ret;
    }

    @Override
    public boolean hasBias() {
        return layerConf().hasBias();
    }

    @Override
    public boolean isPretrainLayer() {
        return false;
    }

    @Override
    protected void applyDropOutIfNecessary(boolean training, LayerWorkspaceMgr workspaceMgr) {
        throw new UnsupportedOperationException("Dropout not supported with EmbeddingLayer " + layerId());
    }

}
