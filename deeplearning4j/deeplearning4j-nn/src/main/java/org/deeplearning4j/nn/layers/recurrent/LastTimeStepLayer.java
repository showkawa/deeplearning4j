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

import lombok.NonNull;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.MaskState;
import org.deeplearning4j.nn.conf.RNNFormat;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.layers.wrapper.BaseWrapperLayer;
import org.deeplearning4j.util.TimeSeriesUtils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.common.primitives.Pair;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;

import java.util.Arrays;

import static org.nd4j.linalg.indexing.NDArrayIndex.all;
import static org.nd4j.linalg.indexing.NDArrayIndex.point;

public class LastTimeStepLayer extends BaseWrapperLayer {

    private int[] lastTimeStepIdxs;
    private long[] origOutputShape;

    public LastTimeStepLayer(@NonNull Layer underlying){
        super(underlying);
    }

    @Override
    public Type type() {
        return Type.FEED_FORWARD;
    }

    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon, LayerWorkspaceMgr workspaceMgr) {
        long[] newEpsShape = origOutputShape;

        boolean nwc = TimeSeriesUtils.getFormatFromRnnLayer(underlying.conf().getLayer()) == RNNFormat.NWC;
        INDArray newEps = Nd4j.create(epsilon.dataType(), newEpsShape, 'f');
        if(lastTimeStepIdxs == null){
            //no mask case
            if (nwc){
                newEps.put(new INDArrayIndex[]{all(), point(origOutputShape[1]-1), all()}, epsilon);
            }
            else{
                newEps.put(new INDArrayIndex[]{all(), all(), point(origOutputShape[2]-1)}, epsilon);
            }
        } else {
            if (nwc){
                INDArrayIndex[] arr = new INDArrayIndex[]{null, null, all()};
                //TODO probably possible to optimize this with reshape + scatter ops...
                for( int i=0; i<lastTimeStepIdxs.length; i++ ){
                    arr[0] = point(i);
                    arr[1] = point(lastTimeStepIdxs[i]);
                    newEps.put(arr, epsilon.getRow(i));
                }
            }
            else{
                INDArrayIndex[] arr = new INDArrayIndex[]{null, all(), null};
                //TODO probably possible to optimize this with reshape + scatter ops...
                for( int i=0; i<lastTimeStepIdxs.length; i++ ){
                    arr[0] = point(i);
                    arr[2] = point(lastTimeStepIdxs[i]);
                    newEps.put(arr, epsilon.getRow(i));
                }
            }

        }
        return underlying.backpropGradient(newEps, workspaceMgr);
    }

    @Override
    public INDArray activate(boolean training, LayerWorkspaceMgr workspaceMgr) {
        return getLastStep(underlying.activate(training, workspaceMgr), workspaceMgr, ArrayType.ACTIVATIONS);
    }

    @Override
    public INDArray activate(INDArray input, boolean training, LayerWorkspaceMgr workspaceMgr) {
        INDArray a = underlying.activate(input, training, workspaceMgr);
        return getLastStep(a, workspaceMgr, ArrayType.ACTIVATIONS);
    }


    @Override
    public Pair<INDArray, MaskState> feedForwardMaskArray(INDArray maskArray, MaskState currentMaskState, int minibatchSize) {
        underlying.feedForwardMaskArray(maskArray, currentMaskState, minibatchSize);
        this.setMaskArray(maskArray);

        //Input: 2d mask array, for masking a time series. After extracting out the last time step, we no longer need the mask array
        return new Pair<>(null, currentMaskState);
    }


    private INDArray getLastStep(INDArray in, LayerWorkspaceMgr workspaceMgr, ArrayType arrayType){
        if(in.rank() != 3){
            throw new IllegalArgumentException("Expected rank 3 input with shape [minibatch, layerSize, tsLength]. Got " +
                    "rank " + in.rank() + " with shape " + Arrays.toString(in.shape()));
        }
        origOutputShape = in.shape();
        boolean nwc = TimeSeriesUtils.getFormatFromRnnLayer(underlying.conf().getLayer()) == RNNFormat.NWC;
//        underlying instanceof  BaseRecurrentLayer && ((BaseRecurrentLayer)underlying).getDataFormat() == RNNFormat.NWC)||
//                underlying instanceof MaskZeroLayer && ((MaskZeroLayer)underlying).getUnderlying() instanceof BaseRecurrentLayer &&
//                        ((BaseRecurrentLayer)((MaskZeroLayer)underlying).getUnderlying()).getDataFormat() == RNNFormat.NWC;
        if (nwc){
            in = in.permute(0, 2, 1);
        }

        INDArray mask = underlying.getMaskArray();
        Pair<INDArray,int[]> p = TimeSeriesUtils.pullLastTimeSteps(in, mask, workspaceMgr, arrayType);
        lastTimeStepIdxs = p.getSecond();

        return p.getFirst();
    }
}
