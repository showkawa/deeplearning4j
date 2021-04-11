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

package org.deeplearning4j.graph.models.deepwalk;

import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.graph.data.GraphLoader;
import org.deeplearning4j.graph.graph.Graph;
import org.deeplearning4j.graph.iterator.GraphWalkIterator;
import org.deeplearning4j.graph.iterator.RandomWalkIterator;
import org.deeplearning4j.graph.models.embeddings.InMemoryGraphLookupTable;
import org.junit.jupiter.api.*;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.common.io.ClassPathResource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Permissions issues on CI")
@NativeTag
@Tag(TagNames.FILE_IO)
public class DeepWalkGradientCheck extends BaseDL4JTest {

    public static final double epsilon = 1e-8;
    public static final double MAX_REL_ERROR = 1e-3;
    public static final double MIN_ABS_ERROR = 1e-5;

    @BeforeEach
    public void before() {
        Nd4j.setDataType(DataType.DOUBLE);
    }

    @Test()
    @Timeout(10000)
    public void checkGradients() throws IOException {

        ClassPathResource cpr = new ClassPathResource("deeplearning4j-graph/testgraph_7vertices.txt");

        Graph<String, String> graph = GraphLoader
                        .loadUndirectedGraphEdgeListFile(cpr.getTempFileFromArchive().getAbsolutePath(), 7, ",");

        int vectorSize = 5;
        int windowSize = 2;

        Nd4j.getRandom().setSeed(12345);
        DeepWalk<String, String> deepWalk = new DeepWalk.Builder<String, String>().learningRate(0.01)
                        .vectorSize(vectorSize).windowSize(windowSize).build();
        deepWalk.initialize(graph);

        for (int i = 0; i < 7; i++) {
            INDArray vector = deepWalk.getVertexVector(i);
            assertArrayEquals(new long[] {vectorSize}, vector.shape());
//            System.out.println(Arrays.toString(vector.dup().data().asFloat()));
        }

        GraphWalkIterator<String> iter = new RandomWalkIterator<>(graph, 8);

        deepWalk.fit(iter);

        //Now, to check gradients:
        InMemoryGraphLookupTable table = (InMemoryGraphLookupTable) deepWalk.lookupTable();
        GraphHuffman tree = (GraphHuffman) table.getTree();

        //For each pair of input/output vertices: check gradients
        for (int i = 0; i < 7; i++) { //in

            //First: check probabilities p(out|in)
            double[] probs = new double[7];
            double sumProb = 0.0;
            for (int j = 0; j < 7; j++) {
                probs[j] = table.calculateProb(i, j);
                assertTrue(probs[j] >= 0.0 && probs[j] <= 1.0);
                sumProb += probs[j];
            }
            assertTrue(Math.abs(sumProb - 1.0) < 1e-5, "Output probabilities do not sum to 1.0");

            for (int j = 0; j < 7; j++) { //out
                //p(j|i)

                int[] pathInnerNodes = tree.getPathInnerNodes(j);

                //Calculate gradients:
                INDArray[][] vecAndGrads = table.vectorsAndGradients(i, j);
                assertEquals(2, vecAndGrads.length);
                assertEquals(pathInnerNodes.length + 1, vecAndGrads[0].length);
                assertEquals(pathInnerNodes.length + 1, vecAndGrads[1].length);

                //Calculate gradients:
                //Two types of gradients to test:
                //(a) gradient of loss fn. wrt inner node vector representation
                //(b) gradient of loss fn. wrt vector for input word


                INDArray vertexVector = table.getVector(i);

                //Check gradients for inner nodes:
                for (int p = 0; p < pathInnerNodes.length; p++) {
                    int innerNodeIdx = pathInnerNodes[p];
                    INDArray innerNodeVector = table.getInnerNodeVector(innerNodeIdx);

                    INDArray innerNodeGrad = vecAndGrads[1][p + 1];

                    for (int v = 0; v < innerNodeVector.length(); v++) {
                        double backpropGradient = innerNodeGrad.getDouble(v);

                        double origParamValue = innerNodeVector.getDouble(v);
                        innerNodeVector.putScalar(v, origParamValue + epsilon);
                        double scorePlus = table.calculateScore(i, j);
                        innerNodeVector.putScalar(v, origParamValue - epsilon);
                        double scoreMinus = table.calculateScore(i, j);
                        innerNodeVector.putScalar(v, origParamValue); //reset param so it doesn't affect later calcs


                        double numericalGradient = (scorePlus - scoreMinus) / (2 * epsilon);

                        double relError;
                        double absErr;
                        if (backpropGradient == 0.0 && numericalGradient == 0.0) {
                            relError = 0.0;
                            absErr = 0.0;
                        } else {
                            absErr = Math.abs(backpropGradient - numericalGradient);
                            relError = absErr / (Math.abs(backpropGradient) + Math.abs(numericalGradient));
                        }

                        String msg = "innerNode grad: i=" + i + ", j=" + j + ", p=" + p + ", v=" + v + " - relError: "
                                        + relError + ", scorePlus=" + scorePlus + ", scoreMinus=" + scoreMinus
                                        + ", numGrad=" + numericalGradient + ", backpropGrad = " + backpropGradient;

                        if (relError > MAX_REL_ERROR && absErr > MIN_ABS_ERROR)
                            fail(msg);
//                        else
//                            System.out.println(msg);
                    }
                }

                //Check gradients for input word vector:
                INDArray vectorGrad = vecAndGrads[1][0];
                assertArrayEquals(vectorGrad.shape(), vertexVector.shape());
                for (int v = 0; v < vectorGrad.length(); v++) {

                    double backpropGradient = vectorGrad.getDouble(v);

                    double origParamValue = vertexVector.getDouble(v);
                    vertexVector.putScalar(v, origParamValue + epsilon);
                    double scorePlus = table.calculateScore(i, j);
                    vertexVector.putScalar(v, origParamValue - epsilon);
                    double scoreMinus = table.calculateScore(i, j);
                    vertexVector.putScalar(v, origParamValue);

                    double numericalGradient = (scorePlus - scoreMinus) / (2 * epsilon);

                    double relError;
                    double absErr;
                    if (backpropGradient == 0.0 && numericalGradient == 0.0){
                        relError = 0.0;
                        absErr = 0.0;
                    } else {
                        absErr = Math.abs(backpropGradient - numericalGradient);
                        relError = absErr / (Math.abs(backpropGradient) + Math.abs(numericalGradient));
                    }

                    String msg = "vector grad: i=" + i + ", j=" + j + ", v=" + v + " - relError: " + relError
                                    + ", scorePlus=" + scorePlus + ", scoreMinus=" + scoreMinus + ", numGrad="
                                    + numericalGradient + ", backpropGrad = " + backpropGradient;

                    if (relError > MAX_REL_ERROR && absErr > MIN_ABS_ERROR)
                        fail(msg);
//                    else
//                        System.out.println(msg);
                }
//                System.out.println();
            }

        }

    }



    @Test()
    @Timeout(60000)
    public void checkGradients2() throws IOException {

        double minAbsError = 1e-5;

        ClassPathResource cpr = new ClassPathResource("deeplearning4j-graph/graph13.txt");

        int nVertices = 13;
        Graph<String, String> graph = GraphLoader
                        .loadUndirectedGraphEdgeListFile(cpr.getTempFileFromArchive().getAbsolutePath(), 13, ",");

        int vectorSize = 10;
        int windowSize = 3;

        Nd4j.getRandom().setSeed(12345);
        DeepWalk<String, String> deepWalk = new DeepWalk.Builder<String, String>().learningRate(0.01)
                        .vectorSize(vectorSize).windowSize(windowSize).learningRate(0.01).build();
        deepWalk.initialize(graph);

        for (int i = 0; i < nVertices; i++) {
            INDArray vector = deepWalk.getVertexVector(i);
            assertArrayEquals(new long[] {vectorSize}, vector.shape());
//            System.out.println(Arrays.toString(vector.dup().data().asFloat()));
        }

        GraphWalkIterator<String> iter = new RandomWalkIterator<>(graph, 10);

        deepWalk.fit(iter);

        //Now, to check gradients:
        InMemoryGraphLookupTable table = (InMemoryGraphLookupTable) deepWalk.lookupTable();
        GraphHuffman tree = (GraphHuffman) table.getTree();

        //For each pair of input/output vertices: check gradients
        for (int i = 0; i < nVertices; i++) { //in

            //First: check probabilities p(out|in)
            double[] probs = new double[nVertices];
            double sumProb = 0.0;
            for (int j = 0; j < nVertices; j++) {
                probs[j] = table.calculateProb(i, j);
                assertTrue(probs[j] >= 0.0 && probs[j] <= 1.0);
                sumProb += probs[j];
            }
            assertTrue(Math.abs(sumProb - 1.0) < 1e-5,
                    "Output probabilities do not sum to 1.0 (i=" + i + "), sum=" + sumProb);

            for (int j = 0; j < nVertices; j++) { //out
                //p(j|i)

                int[] pathInnerNodes = tree.getPathInnerNodes(j);

                //Calculate gradients:
                INDArray[][] vecAndGrads = table.vectorsAndGradients(i, j);
                assertEquals(2, vecAndGrads.length);
                assertEquals(pathInnerNodes.length + 1, vecAndGrads[0].length);
                assertEquals(pathInnerNodes.length + 1, vecAndGrads[1].length);

                //Calculate gradients:
                //Two types of gradients to test:
                //(a) gradient of loss fn. wrt inner node vector representation
                //(b) gradient of loss fn. wrt vector for input word


                INDArray vertexVector = table.getVector(i);

                //Check gradients for inner nodes:
                for (int p = 0; p < pathInnerNodes.length; p++) {
                    int innerNodeIdx = pathInnerNodes[p];
                    INDArray innerNodeVector = table.getInnerNodeVector(innerNodeIdx);

                    INDArray innerNodeGrad = vecAndGrads[1][p + 1];

                    for (int v = 0; v < innerNodeVector.length(); v++) {
                        double backpropGradient = innerNodeGrad.getDouble(v);

                        double origParamValue = innerNodeVector.getDouble(v);
                        innerNodeVector.putScalar(v, origParamValue + epsilon);
                        double scorePlus = table.calculateScore(i, j);
                        innerNodeVector.putScalar(v, origParamValue - epsilon);
                        double scoreMinus = table.calculateScore(i, j);
                        innerNodeVector.putScalar(v, origParamValue); //reset param so it doesn't affect later calcs


                        double numericalGradient = (scorePlus - scoreMinus) / (2 * epsilon);

                        double relError;
                        if (backpropGradient == 0.0 && numericalGradient == 0.0)
                            relError = 0.0;
                        else {
                            relError = Math.abs(backpropGradient - numericalGradient)
                                            / (Math.abs(backpropGradient) + Math.abs(numericalGradient));
                        }
                        double absErr = Math.abs(backpropGradient - numericalGradient);

                        String msg = "innerNode grad: i=" + i + ", j=" + j + ", p=" + p + ", v=" + v + " - relError: "
                                        + relError + ", scorePlus=" + scorePlus + ", scoreMinus=" + scoreMinus
                                        + ", numGrad=" + numericalGradient + ", backpropGrad = " + backpropGradient;

                        if (relError > MAX_REL_ERROR && absErr > minAbsError)
                            fail(msg);
//                        else
//                            System.out.println(msg);
                    }
                }

                //Check gradients for input word vector:
                INDArray vectorGrad = vecAndGrads[1][0];
                assertArrayEquals(vectorGrad.shape(), vertexVector.shape());
                for (int v = 0; v < vectorGrad.length(); v++) {

                    double backpropGradient = vectorGrad.getDouble(v);

                    double origParamValue = vertexVector.getDouble(v);
                    vertexVector.putScalar(v, origParamValue + epsilon);
                    double scorePlus = table.calculateScore(i, j);
                    vertexVector.putScalar(v, origParamValue - epsilon);
                    double scoreMinus = table.calculateScore(i, j);
                    vertexVector.putScalar(v, origParamValue);

                    double numericalGradient = (scorePlus - scoreMinus) / (2 * epsilon);

                    double relError;
                    double absErr;
                    if (backpropGradient == 0.0 && numericalGradient == 0.0) {
                        relError = 0.0;
                        absErr = 0.0;
                    } else {
                        relError = Math.abs(backpropGradient - numericalGradient)
                                        / (Math.abs(backpropGradient) + Math.abs(numericalGradient));
                        absErr = Math.abs(backpropGradient - numericalGradient);
                    }

                    String msg = "vector grad: i=" + i + ", j=" + j + ", v=" + v + " - relError: " + relError
                                    + ", scorePlus=" + scorePlus + ", scoreMinus=" + scoreMinus + ", numGrad="
                                    + numericalGradient + ", backpropGrad = " + backpropGradient;

                    if (relError > MAX_REL_ERROR && absErr > MIN_ABS_ERROR)
                        fail(msg);
//                    else
//                        System.out.println(msg);
                }
//                System.out.println();
            }

        }

    }

    private static boolean getBit(long in, int bitNum) {
        long mask = 1L << bitNum;
        return (in & mask) != 0L;
    }
}
