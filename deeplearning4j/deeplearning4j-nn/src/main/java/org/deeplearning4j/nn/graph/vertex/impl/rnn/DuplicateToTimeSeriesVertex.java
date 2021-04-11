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

package org.deeplearning4j.nn.graph.vertex.impl.rnn;

import lombok.val;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.MaskState;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.graph.vertex.BaseGraphVertex;
import org.deeplearning4j.nn.graph.vertex.VertexIndices;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.common.primitives.Pair;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;

public class DuplicateToTimeSeriesVertex extends BaseGraphVertex {

    private String inputName;
    private int inputVertexIndex;

    public DuplicateToTimeSeriesVertex(ComputationGraph graph, String name, int vertexIndex, String inputVertexName, DataType dataType) {
        this(graph, name, vertexIndex, null, null, inputVertexName, dataType);
    }

    public DuplicateToTimeSeriesVertex(ComputationGraph graph, String name, int vertexIndex,
                    VertexIndices[] inputVertices, VertexIndices[] outputVertices, String inputName, DataType dataType) {
        super(graph, name, vertexIndex, inputVertices, outputVertices, dataType);
        this.inputName = inputName;
        this.inputVertexIndex = graph.getConfiguration().getNetworkInputs().indexOf(inputName);
        if (inputVertexIndex == -1)
            throw new IllegalArgumentException("Invalid input name: \"" + inputName + "\" not found in list "
                            + "of network inputs (" + graph.getConfiguration().getNetworkInputs() + ")");
    }

    @Override
    public boolean hasLayer() {
        return false;
    }

    @Override
    public boolean isOutputVertex() {
        return false;
    }

    @Override
    public Layer getLayer() {
        return null;
    }

    @Override
    public INDArray doForward(boolean training, LayerWorkspaceMgr workspaceMgr) {

        //First: work out the time series length
        val tsLength = graph.getInput(inputVertexIndex).size(2);
        val outShape = new long[] {inputs[0].size(0), inputs[0].size(1), tsLength};

        INDArray out = workspaceMgr.create(ArrayType.ACTIVATIONS, inputs[0].dataType(), outShape, 'f');
        for (int i = 0; i < tsLength; i++) {
            out.put(new INDArrayIndex[] {NDArrayIndex.all(), NDArrayIndex.all(), NDArrayIndex.point(i)}, inputs[0]);
        }
        return out;
    }

    @Override
    public Pair<Gradient, INDArray[]> doBackward(boolean tbptt, LayerWorkspaceMgr workspaceMgr) {
        //Because we duplicated for each time step: simply need to sum along time for errors/epsilons
        INDArray ret = epsilon.sum(workspaceMgr.create(ArrayType.ACTIVATION_GRAD, epsilon.dataType(), epsilon.size(0), epsilon.size(1)), 2);
        return new Pair<>(null, new INDArray[] {ret});
    }

    @Override
    public void setBackpropGradientsViewArray(INDArray backpropGradientsViewArray) {
        if (backpropGradientsViewArray != null)
            throw new RuntimeException("Vertex does not have gradients; gradients view array cannot be set here");
    }

    @Override
    public Pair<INDArray, MaskState> feedForwardMaskArrays(INDArray[] maskArrays, MaskState currentMaskState,
                    int minibatchSize) {
        //Present for all time steps, or as per the corresponding input mask (if present)
        INDArray[] allMasks = graph.getInputMaskArrays();
        if (allMasks == null || allMasks[inputVertexIndex] == null) {
            //No mask
            return null;
        }
        return new Pair<>(allMasks[inputVertexIndex], MaskState.Active);
    }

    @Override
    public String toString() {
        return "DuplicateToTimeSeriesVertex(inputName=" + inputName + ")";
    }
}
