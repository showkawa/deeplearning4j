/*******************************************************************************
 * Copyright (c) 2015-2018 Skymind, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

package org.deeplearning4j.nn.conf.graph;


import lombok.Data;
import lombok.Setter;
import lombok.val;
import org.deeplearning4j.nn.conf.CNN2DFormat;
import org.deeplearning4j.nn.conf.RNNFormat;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.inputs.InvalidInputTypeException;
import org.deeplearning4j.nn.conf.layers.Convolution3D;
import org.deeplearning4j.nn.conf.memory.LayerMemoryReport;
import org.deeplearning4j.nn.conf.memory.MemoryReport;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;

/** A MergeVertex is used to combine the activations of two or more layers/GraphVertex by means of concatenation/merging.<br>
 * Exactly how this is done depends on the type of input.<br>
 * For 2d (feed forward layer) inputs: {@code MergeVertex([numExamples,layerSize1],[numExamples,layerSize2]) -> [numExamples,layerSize1 + layerSize2]}<br>
 * For 3d (time series) inputs: {@code MergeVertex([numExamples,layerSize1,timeSeriesLength],[numExamples,layerSize2,timeSeriesLength])
 *      -> [numExamples,layerSize1 + layerSize2,timeSeriesLength]}<br>
 * For 4d (convolutional) inputs: {@code MergeVertex([numExamples,depth1,width,height],[numExamples,depth2,width,height])
 *      -> [numExamples,depth1 + depth2,width,height]}<br>
 * @author Alex Black
 */
@Data
public class MergeVertex extends GraphVertex {

    @Setter
    protected int mergeAxis = DEFAULT_MERGE_DIM;       //default value for backward compatibility (deserialization of old version JSON) - NCHW and NCW format


    public final static int DEFAULT_MERGE_DIM = 1;

    @Override
    public MergeVertex clone() {
        return new MergeVertex();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MergeVertex;
    }

    @Override
    public int hashCode() {
        return 433682566;
    }

    @Override
    public long numParams(boolean backprop) {
        return 0;
    }

    @Override
    public int minVertexInputs() {
        return 2;
    }

    @Override
    public int maxVertexInputs() {
        return Integer.MAX_VALUE;
    }

    @Override
    public String toString() {
        return "MergeVertex()";
    }

    @Override
    public org.deeplearning4j.nn.graph.vertex.GraphVertex instantiate(ComputationGraph graph, String name, int idx,
                                                                      INDArray paramsView, boolean initializeParams, DataType networkDatatype) {
        return new org.deeplearning4j.nn.graph.vertex.impl.MergeVertex(graph, name, idx, networkDatatype, mergeAxis);
    }

    @Override
    public InputType getOutputType(int layerIndex, InputType... vertexInputs) throws InvalidInputTypeException {
        if (vertexInputs.length == 1)
            return vertexInputs[0];
        InputType first = vertexInputs[0];
        if (first.getType() == InputType.Type.CNNFlat) {
            //TODO
            //Merging flattened CNN format data could be messy?
            throw new InvalidInputTypeException(
                    "Invalid input: MergeVertex cannot currently merge CNN data in flattened format. Got: "
                            + vertexInputs);
        } else if (first.getType() == InputType.Type.CNN3D) {
            // CNN3D inputs: check that the channels, width and height match:
            InputType.InputTypeConvolutional3D firstConv = (InputType.InputTypeConvolutional3D) first;

            val fd = firstConv.getDepth();
            val fw = firstConv.getWidth();
            val fh = firstConv.getHeight();
            val fc = firstConv.getChannels();

            long depthSum = fc;
            InputType.InputTypeConvolutional3D otherConv = null;
            for (int i = 1; i < vertexInputs.length; i++) {
                if (vertexInputs[i].getType() != InputType.Type.CNN3D) {
                    throw new InvalidInputTypeException(
                            "Invalid input: MergeVertex cannot process activations of different types:" + " first type = " + InputType.Type.CNN3D + ", input type " + (i + 1) + " = " + vertexInputs[i].getType());
                }

                otherConv = (InputType.InputTypeConvolutional3D) vertexInputs[i];
                val od = otherConv.getDepth();
                val ow = otherConv.getWidth();
                val oh = otherConv.getHeight();
                val oc = otherConv.getChannels();

                if (fd != od || fw != ow || fh != oh) {
                    throw new InvalidInputTypeException("Invalid input: MergeVertex cannot merge CNN3D activations of different width/heights:" + "first [channels,width,height] = [" + fd + "," + fw + "," + fh
                            + "], input " + i + " = [" + od + "," + ow + "," + oh + "]");
                }

                depthSum += oc;
            }

            return InputType.convolutional3D(Convolution3D.DataFormat.NDHWC, fd, fh, fw, depthSum);
        } else if (first.getType() != InputType.Type.CNN) {
            //FF or RNN data inputs
            int size = 0;
            InputType.Type type = null;
            RNNFormat format = null;
            long timeSeriesLength = -1;
            //scan for input type for recurrent
            for (int i = 0; i < vertexInputs.length; i++) {
                if(vertexInputs[i].getType() == InputType.Type.RNN) {
                    if(format == null) {
                        InputType.InputTypeRecurrent input = (InputType.InputTypeRecurrent) vertexInputs[i];
                        format = input.getFormat();
                        timeSeriesLength = ((InputType.InputTypeRecurrent) vertexInputs[i]).getTimeSeriesLength();
                    }
                    else if(format != null) {
                        InputType.InputTypeRecurrent input = (InputType.InputTypeRecurrent) vertexInputs[i];
                        if(input.getFormat() != null && format != input.getFormat()) {
                            throw new IllegalArgumentException("Unable to merge inputs with 2 different layouts of input type: " + input.getType() + " and type " + vertexInputs[i].getType());
                        }
                    }
                }
            }

            for (int i = 0; i < vertexInputs.length; i++) {
                if (vertexInputs[i].getType() != first.getType()) {
                    if(vertexInputs[i].getType() != InputType.Type.FF && vertexInputs[i].getType() != InputType.Type.RNN)
                        throw new InvalidInputTypeException(
                                "Invalid input: MergeVertex cannot merge activations of different types:"
                                        + " first type = " + first.getType() + ", input type " + (i + 1)
                                        + " = " + vertexInputs[i].getType());
                    else {
                        type = InputType.Type.RNN;
                    }
                }

                long thisSize = 0;
                switch (vertexInputs[i].getType()) {
                    case FF:
                        //ignore feedforward, rnn trumps feedforward and can be merged
                        if(format != null) {
                            thisSize = ((InputType.InputTypeFeedForward) vertexInputs[i]).getSize();
                            type = InputType.Type.FF;
                        }
                        //feedforward case
                        else {
                            thisSize = ((InputType.InputTypeFeedForward) vertexInputs[i]).getSize();
                            type = InputType.Type.FF;
                        }
                        break;
                    case RNN:
                        thisSize = ((InputType.InputTypeRecurrent) vertexInputs[i]).getSize();
                        //don't change dimension if it was already modified
                        if(this.mergeAxis == DEFAULT_MERGE_DIM)
                            this.mergeAxis = format == RNNFormat.NCW ? 1 : 2;
                        break;
                    default:
                        throw new IllegalStateException("Unknown input type: " + vertexInputs[i]); //Should never happen
                }

                if (thisSize <= 0) {//Size is not defined
                    size = -1;
                } else {
                    size += thisSize;
                }
            }

            if (size > 0) {
                //Size is specified
                if (type == InputType.Type.FF) {
                    return InputType.feedForward(size);
                } else {
                    val tsLength = ((InputType.InputTypeRecurrent) vertexInputs[0]).getTimeSeriesLength();
                    return InputType.recurrent(size, tsLength, format);
                }
            } else {
                //size is unknown
                if (type == InputType.Type.FF) {
                    return InputType.feedForward(-1);
                } else {
                    if(first.getType() == InputType.Type.FF) {
                        InputType.InputTypeFeedForward inputTypeFeedForward = (InputType.InputTypeFeedForward) first;
                        return InputType.recurrent(inputTypeFeedForward.getSize(), timeSeriesLength, format);
                    }
                    else
                        return InputType.recurrent(-1, timeSeriesLength, format);
                }
            }

        } else {
            //CNN inputs... also check that the channels, width and heights match:
            InputType.InputTypeConvolutional firstConv = (InputType.InputTypeConvolutional) first;
            CNN2DFormat format = firstConv.getFormat();

            val fd = firstConv.getChannels();
            val fw = firstConv.getWidth();
            val fh = firstConv.getHeight();

            long depthSum = fd;

            for (int i = 1; i < vertexInputs.length; i++) {
                if (vertexInputs[i].getType() != InputType.Type.CNN) {
                    throw new InvalidInputTypeException(
                            "Invalid input: MergeVertex cannot process activations of different types:"
                                    + " first type = " + InputType.Type.CNN + ", input type " + (i + 1)
                                    + " = " + vertexInputs[i].getType());
                }

                InputType.InputTypeConvolutional otherConv = (InputType.InputTypeConvolutional) vertexInputs[i];

                val od = otherConv.getChannels();
                val ow = otherConv.getWidth();
                val oh = otherConv.getHeight();

                if (fw != ow || fh != oh) {
                    throw new InvalidInputTypeException(
                            "Invalid input: MergeVertex cannot merge CNN activations of different width/heights:"
                                    + "first [channels,width,height] = [" + fd + "," + fw + "," + fh
                                    + "], input " + i + " = [" + od + "," + ow + "," + oh + "]");
                }

                depthSum += od;
            }

            //don't change dimension if it was already modified
            if(this.mergeAxis == DEFAULT_MERGE_DIM)
                this.mergeAxis = format == CNN2DFormat.NCHW ? 1 : 3;
            return InputType.convolutional(fh, fw, depthSum, format);
        }
    }

    @Override
    public MemoryReport getMemoryReport(InputType... inputTypes) {
        InputType outputType = getOutputType(-1, inputTypes);

        //TODO multiple input types
        return new LayerMemoryReport.Builder(null, MergeVertex.class, inputTypes[0], outputType).standardMemory(0, 0) //No params
                .workingMemory(0, 0, 0, 0) //No working memory in addition to activations/epsilons
                .cacheMemory(0, 0) //No caching
                .build();
    }
}
