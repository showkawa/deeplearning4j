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

package org.deeplearning4j.nn.conf.graph;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.inputs.InvalidInputTypeException;
import org.deeplearning4j.nn.conf.memory.MemoryReport;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.shade.jackson.annotation.JsonProperty;

@Data
@EqualsAndHashCode(callSuper = false)
public class FrozenVertex extends GraphVertex {

    private GraphVertex underlying;

    public FrozenVertex(@JsonProperty("underlying") GraphVertex underlying){
        this.underlying = underlying;
    }

    @Override
    public GraphVertex clone() {
        return new FrozenVertex(underlying.clone());
    }

    @Override
    public long numParams(boolean backprop) {
        return underlying.numParams(backprop);
    }

    @Override
    public int minVertexInputs() {
        return underlying.minVertexInputs();
    }

    @Override
    public int maxVertexInputs() {
        return underlying.maxVertexInputs();
    }

    @Override
    public org.deeplearning4j.nn.graph.vertex.GraphVertex instantiate(ComputationGraph graph, String name, int idx, INDArray paramsView, boolean initializeParams, DataType networkDatatype) {
        org.deeplearning4j.nn.graph.vertex.GraphVertex u = underlying.instantiate(graph, name, idx, paramsView, initializeParams, networkDatatype);
        return new org.deeplearning4j.nn.graph.vertex.impl.FrozenVertex(u);
    }

    @Override
    public InputType getOutputType(int layerIndex, InputType... vertexInputs) throws InvalidInputTypeException {
        return underlying.getOutputType(layerIndex, vertexInputs);
    }

    @Override
    public MemoryReport getMemoryReport(InputType... inputTypes) {
        return underlying.getMemoryReport(inputTypes);
    }
}
