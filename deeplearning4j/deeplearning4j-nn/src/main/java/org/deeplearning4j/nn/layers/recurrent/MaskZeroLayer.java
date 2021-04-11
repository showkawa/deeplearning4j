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

package org.deeplearning4j.nn.layers.recurrent;

import java.util.Arrays;

import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.MaskState;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.layers.wrapper.BaseWrapperLayer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.common.primitives.Pair;

import lombok.NonNull;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;

import static org.deeplearning4j.nn.conf.RNNFormat.NWC;

public class MaskZeroLayer extends BaseWrapperLayer {

    private static final long serialVersionUID = -7369482676002469854L;
    private double maskingValue;

    public MaskZeroLayer(@NonNull Layer underlying, double maskingValue){
        super(underlying);
        this.maskingValue = maskingValue;
    }


    @Override
    public Type type() {
        return Type.RECURRENT;
    }

    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon, LayerWorkspaceMgr workspaceMgr) {
        return underlying.backpropGradient(epsilon, workspaceMgr);
    }


    @Override
    public INDArray activate(boolean training, LayerWorkspaceMgr workspaceMgr) {
        INDArray input = input();
        setMaskFromInput(input);
        return underlying.activate(training, workspaceMgr);
    }

    @Override
    public INDArray activate(INDArray input, boolean training, LayerWorkspaceMgr workspaceMgr) {
        setMaskFromInput(input);
        return underlying.activate(input, training, workspaceMgr);
    }

    private void setMaskFromInput(INDArray input) {
        if (input.rank() != 3) {
            throw new IllegalArgumentException("Expected input of shape [batch_size, timestep_input_size, timestep], " +
                    "got shape "+Arrays.toString(input.shape()) + " instead");
        }
        if ((underlying instanceof BaseRecurrentLayer &&
                ((BaseRecurrentLayer)underlying).getDataFormat() == NWC)){
            input = input.permute(0, 2, 1);
        }
        INDArray mask = input.eq(maskingValue).castTo(input.dataType()).sum(1).neq(input.shape()[1]).castTo(input.dataType());
        underlying.setMaskArray(mask.detach());
    }

    @Override
    public long numParams() {
        return underlying.numParams();
    }

    @Override
    public Pair<INDArray, MaskState> feedForwardMaskArray(INDArray maskArray, MaskState currentMaskState,
                                                          int minibatchSize) {
        underlying.feedForwardMaskArray(maskArray, currentMaskState, minibatchSize);

        //Input: 2d mask array, for masking a time series. After extracting out the last time step,
        // we no longer need the mask array
        return new Pair<>(null, currentMaskState);
    }


}
