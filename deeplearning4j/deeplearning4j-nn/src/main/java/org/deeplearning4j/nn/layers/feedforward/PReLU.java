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

package org.deeplearning4j.nn.layers.feedforward;

import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.layers.BaseLayer;
import org.deeplearning4j.nn.params.PReLUParamInitializer;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.nd4j.linalg.activations.IActivation;
import org.nd4j.linalg.activations.impl.ActivationPReLU;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.common.primitives.Pair;

public class PReLU extends BaseLayer<org.deeplearning4j.nn.conf.layers.PReLULayer> {

    long[] axes = layerConf().getSharedAxes();


    public PReLU(NeuralNetConfiguration conf, DataType dataType) {
        super(conf, dataType);
    }

    @Override
    public Type type() {
        return Type.FEED_FORWARD;
    }

    @Override
    public INDArray activate(boolean training, LayerWorkspaceMgr mgr) {
        assertInputSet(false);
        applyDropOutIfNecessary(training, mgr);

        INDArray in;
        if (training) {
            in = mgr.dup(ArrayType.ACTIVATIONS, input, input.ordering());
        } else {
            in = mgr.leverageTo(ArrayType.ACTIVATIONS, input);
        }

        INDArray alpha = getParam(PReLUParamInitializer.WEIGHT_KEY);

        return new ActivationPReLU(alpha, axes).getActivation(in, training);
    }

    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(true);
        INDArray layerInput = workspaceMgr.dup(ArrayType.ACTIVATION_GRAD, input, input.ordering());

        INDArray alpha = getParam(PReLUParamInitializer.WEIGHT_KEY);
        IActivation prelu = new ActivationPReLU(alpha, axes);

        Pair<INDArray, INDArray> deltas = prelu.backprop(layerInput, epsilon);
        INDArray delta = deltas.getFirst();
        INDArray weightGrad = deltas.getSecond();
        INDArray weightGradView = gradientViews.get(PReLUParamInitializer.WEIGHT_KEY);
        weightGradView.assign(weightGrad);


        delta = workspaceMgr.leverageTo(ArrayType.ACTIVATION_GRAD, delta);  //Usually a no-op (except for perhaps identity)
        delta = backpropDropOutIfPresent(delta);
        Gradient ret = new DefaultGradient();
        ret.setGradientFor(PReLUParamInitializer.WEIGHT_KEY, weightGradView, 'c');

        return new Pair<>(ret, delta);
    }


    @Override
    public boolean isPretrainLayer() {
        return false;
    }

}