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

package org.deeplearning4j.nn.conf.graph.rnn;

import lombok.Data;
import org.deeplearning4j.nn.conf.graph.GraphVertex;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.inputs.InvalidInputTypeException;
import org.deeplearning4j.nn.conf.memory.LayerMemoryReport;
import org.deeplearning4j.nn.conf.memory.MemoryReport;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;

@Data
public class ReverseTimeSeriesVertex extends GraphVertex {
    private final String maskArrayInputName;

    /**
     * Creates a new ReverseTimeSeriesVertex that doesn't pay attention to masks
     */
    public ReverseTimeSeriesVertex() {
        this(null);
    }

    /**
     * Creates a new ReverseTimeSeriesVertex that uses the mask array of a given input
     * @param maskArrayInputName The name of the input that holds the mask.
     */
    public ReverseTimeSeriesVertex(String maskArrayInputName) {
        this.maskArrayInputName = maskArrayInputName;
    }

    public ReverseTimeSeriesVertex clone() {
        return new ReverseTimeSeriesVertex(maskArrayInputName);
    }

    public boolean equals(Object o) {
        if (!(o instanceof ReverseTimeSeriesVertex))
            return false;
        ReverseTimeSeriesVertex rsgv = (ReverseTimeSeriesVertex) o;
        if (maskArrayInputName == null && rsgv.maskArrayInputName != null
                || maskArrayInputName != null && rsgv.maskArrayInputName == null)
            return false;
        return maskArrayInputName == null || maskArrayInputName.equals(rsgv.maskArrayInputName);
    }

    @Override
    public int hashCode() {
        return maskArrayInputName != null ? maskArrayInputName.hashCode() : 0;
    }

    public long numParams(boolean backprop) {
        return 0;
    }

    public int minVertexInputs() {
        return 1;
    }

    public int maxVertexInputs() {
        return 1;
    }

    public org.deeplearning4j.nn.graph.vertex.impl.rnn.ReverseTimeSeriesVertex instantiate(ComputationGraph graph, String name, int idx, INDArray paramsView,
                                                                                           boolean initializeParams, DataType networkDatatype) {
        return new org.deeplearning4j.nn.graph.vertex.impl.rnn.ReverseTimeSeriesVertex(graph, name, idx, maskArrayInputName, networkDatatype);
    }

    public InputType getOutputType(int layerIndex, InputType... vertexInputs) throws InvalidInputTypeException {
        if (vertexInputs.length != 1)
            throw new InvalidInputTypeException("Invalid input type: cannot revert more than 1 input");
        if (vertexInputs[0].getType() != InputType.Type.RNN) {
            throw new InvalidInputTypeException(
                    "Invalid input type: cannot revert non RNN input (got: " + vertexInputs[0] + ")");
        }

        return vertexInputs[0];
    }

    public MemoryReport getMemoryReport(InputType... inputTypes) {
        //No additional working memory (beyond activations/epsilons)
        return new LayerMemoryReport.Builder(null, getClass(), inputTypes[0], getOutputType(-1, inputTypes))
                .standardMemory(0, 0)
                .workingMemory(0, 0, 0, 0)
                .cacheMemory(0, 0)
                .build();
    }

    public String toString() {
        final String paramStr = (maskArrayInputName == null) ? "" : "inputName=" + maskArrayInputName;
        return "ReverseTimeSeriesVertex(" + paramStr + ")";
    }
}
