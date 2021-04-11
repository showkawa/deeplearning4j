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

import lombok.Getter;
import lombok.Setter;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.api.MaskState;
import org.deeplearning4j.nn.api.layers.IOutputLayer;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.RNNFormat;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.layers.BaseLayer;
import org.deeplearning4j.util.TimeSeriesUtils;
import org.nd4j.common.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.lossfunctions.ILossFunction;
import org.nd4j.common.primitives.Pair;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;

import java.util.Arrays;
import java.util.List;

public class RnnLossLayer extends BaseLayer<org.deeplearning4j.nn.conf.layers.RnnLossLayer> implements IOutputLayer {
    @Setter @Getter protected INDArray labels;

    public RnnLossLayer(NeuralNetConfiguration conf, DataType dataType) {
        super(conf, dataType);
    }

    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(true);
        INDArray input = this.input;
        INDArray labels = this.labels;
        if (input.rank() != 3)
            throw new UnsupportedOperationException(
                            "Input is not rank 3. Expected rank 3 input of shape [minibatch, size, sequenceLength]. Got input with rank " +
                                    input.rank() + " with shape " + Arrays.toString(input.shape()) + " for layer " + layerId());
        if (labels == null)
            throw new IllegalStateException("Labels are not set (null)");

        if (layerConf().getRnnDataFormat() == RNNFormat.NWC){
            input = input.permute(0, 2, 1);
            labels = labels.permute(0, 2, 1);
        }
        Preconditions.checkState(labels.rank() == 3, "Expected rank 3 labels array, got label array with shape %ndShape", labels);
        Preconditions.checkState(input.size(2) == labels.size(2), "Sequence lengths do not match for RnnOutputLayer input and labels:" +
                "Arrays should be rank 3 with shape [minibatch, size, sequenceLength] - mismatch on dimension 2 (sequence length) - input=%ndShape vs. label=%ndShape", input, labels);


        INDArray input2d = TimeSeriesUtils.reshape3dTo2d(input, workspaceMgr, ArrayType.BP_WORKING_MEM);
        INDArray labels2d = TimeSeriesUtils.reshape3dTo2d(labels, workspaceMgr, ArrayType.BP_WORKING_MEM);
        INDArray maskReshaped;
        if(this.maskArray != null){
            if(this.maskArray.rank() == 3){
                maskReshaped = TimeSeriesUtils.reshapePerOutputTimeSeriesMaskTo2d(this.maskArray, workspaceMgr, ArrayType.BP_WORKING_MEM);
            } else {
                maskReshaped = TimeSeriesUtils.reshapeTimeSeriesMaskToVector(this.maskArray, workspaceMgr, ArrayType.BP_WORKING_MEM);
            }
        } else {
            maskReshaped = null;
        }

        // delta calculation
        ILossFunction lossFunction = layerConf().getLossFn();
        INDArray delta2d = lossFunction.computeGradient(labels2d, input2d.dup(input2d.ordering()), layerConf().getActivationFn(), maskReshaped);

        INDArray delta3d = TimeSeriesUtils.reshape2dTo3d(delta2d, input.size(0), workspaceMgr, ArrayType.ACTIVATION_GRAD);
        if (layerConf().getRnnDataFormat() == RNNFormat.NWC){
            delta3d = delta3d.permute(0, 2, 1);
        }
        // grab the empty gradient
        Gradient gradient = new DefaultGradient();
        return new Pair<>(gradient, delta3d);
    }

    @Override
    public double calcRegularizationScore(boolean backpropParamsOnly){
        return 0;
    }

    @Override
    public double f1Score(DataSet data) {
        return 0;
    }

    /**{@inheritDoc}
     */
    @Override
    public double f1Score(INDArray examples, INDArray labels) {
        INDArray out = activate(examples, false, null);
        Evaluation eval = new Evaluation();
        eval.evalTimeSeries(labels, out, maskArray);
        return eval.f1();
    }

    @Override
    public int numLabels() {
        return (int) labels.size(1);
    }

    @Override
    public void fit(DataSetIterator iter) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public int[] predict(INDArray examples) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public List<String> predict(DataSet dataSet) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void fit(INDArray examples, INDArray labels) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void fit(DataSet data) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void fit(INDArray examples, int[] labels) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public Type type() {
        return Type.RECURRENT;
    }

    @Override
    public INDArray activate(boolean training, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(false);
        INDArray input = this.input;
        if (layerConf().getRnnDataFormat() == RNNFormat.NWC){
            input = input.permute(0, 2, 1);
        }
        if (input.rank() != 3)
            throw new UnsupportedOperationException(
                            "Input must be rank 3. Got input with rank " + input.rank() + " " + layerId());

        INDArray as2d = TimeSeriesUtils.reshape3dTo2d(input);
        INDArray out2d = layerConf().getActivationFn().getActivation(workspaceMgr.dup(ArrayType.ACTIVATIONS, as2d, as2d.ordering()), training);
        INDArray ret = workspaceMgr.leverageTo(ArrayType.ACTIVATIONS, TimeSeriesUtils.reshape2dTo3d(out2d, input.size(0), workspaceMgr, ArrayType.ACTIVATIONS));
        if (layerConf().getRnnDataFormat() == RNNFormat.NWC){
            ret = ret.permute(0, 2, 1);
        }
        return ret;
    }

    @Override
    public void setMaskArray(INDArray maskArray) {
        this.maskArray = maskArray;
    }

    @Override
    public boolean isPretrainLayer() {
        return false;
    }

    @Override
    public Pair<INDArray, MaskState> feedForwardMaskArray(INDArray maskArray, MaskState currentMaskState,
                    int minibatchSize) {
        if(maskArray == null)
            return null;
        this.maskArray = TimeSeriesUtils.reshapeTimeSeriesMaskToVector(maskArray, LayerWorkspaceMgr.noWorkspaces(), ArrayType.INPUT);   //TODO
        this.maskState = currentMaskState;

        return null; //Last layer in network
    }

    @Override
    public boolean needsLabels() {
        return true;
    }

    @Override
    public double computeScore(double fullNetRegTerm, boolean training, LayerWorkspaceMgr workspaceMgr) {
        INDArray input = this.input;
        INDArray labels = this.labels;
        if (layerConf().getRnnDataFormat() == RNNFormat.NWC){
            input = input.permute(0, 2, 1);
            labels = input.permute(0, 2, 1);
        }
        INDArray input2d = TimeSeriesUtils.reshape3dTo2d(input, workspaceMgr, ArrayType.FF_WORKING_MEM);
        INDArray labels2d = TimeSeriesUtils.reshape3dTo2d(labels, workspaceMgr, ArrayType.FF_WORKING_MEM);
        INDArray maskReshaped;
        if(this.maskArray != null){
            if(this.maskArray.rank() == 3){
                maskReshaped = TimeSeriesUtils.reshapePerOutputTimeSeriesMaskTo2d(this.maskArray, workspaceMgr, ArrayType.FF_WORKING_MEM);
            } else {
                maskReshaped = TimeSeriesUtils.reshapeTimeSeriesMaskToVector(this.maskArray, workspaceMgr, ArrayType.FF_WORKING_MEM);
            }
        } else {
            maskReshaped = null;
        }

        ILossFunction lossFunction = layerConf().getLossFn();

        double score = lossFunction.computeScore(labels2d, input2d.dup(), layerConf().getActivationFn(), maskReshaped,false);
        score /= getInputMiniBatchSize();
        score += fullNetRegTerm;

        this.score = score;

        return score;
    }

    /**Compute the score for each example individually, after labels and input have been set.
     *
     * @param fullNetRegTerm Regularization score term for the entire network (or, 0.0 to not include regularization)
     * @return A column INDArray of shape [numExamples,1], where entry i is the score of the ith example
     */
    @Override
    public INDArray computeScoreForExamples(double fullNetRegTerm, LayerWorkspaceMgr workspaceMgr) {
        //For RNN: need to sum up the score over each time step before returning.
        INDArray input = this.input;
        INDArray labels = this.labels;
        if (input == null || labels == null)
            throw new IllegalStateException("Cannot calculate score without input and labels " + layerId());
        if (layerConf().getRnnDataFormat() == RNNFormat.NWC){
            input = input.permute(0, 2, 1);
            labels = input.permute(0, 2, 1);
        }
        INDArray input2d = TimeSeriesUtils.reshape3dTo2d(input, workspaceMgr, ArrayType.FF_WORKING_MEM);
        INDArray labels2d = TimeSeriesUtils.reshape3dTo2d(labels, workspaceMgr, ArrayType.FF_WORKING_MEM);

        INDArray maskReshaped;
        if(this.maskArray != null){
            if(this.maskArray.rank() == 3){
                maskReshaped = TimeSeriesUtils.reshapePerOutputTimeSeriesMaskTo2d(this.maskArray, workspaceMgr, ArrayType.FF_WORKING_MEM);
            } else {
                maskReshaped = TimeSeriesUtils.reshapeTimeSeriesMaskToVector(this.maskArray, workspaceMgr, ArrayType.FF_WORKING_MEM);
            }
        } else {
            maskReshaped = null;
        }

        ILossFunction lossFunction = layerConf().getLossFn();
        INDArray scoreArray =
                lossFunction.computeScoreArray(labels2d, input2d, layerConf().getActivationFn(), maskReshaped);
        //scoreArray: shape [minibatch*timeSeriesLength, 1]
        //Reshape it to [minibatch, timeSeriesLength] then sum over time step

        INDArray scoreArrayTs = TimeSeriesUtils.reshapeVectorToTimeSeriesMask(scoreArray, (int)input.size(0));
        INDArray summedScores = scoreArrayTs.sum(1);

        if (fullNetRegTerm != 0.0) {
            summedScores.addi(fullNetRegTerm);
        }

        return summedScores;
    }
}
