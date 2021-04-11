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
import org.deeplearning4j.graph.api.Edge;
import org.deeplearning4j.graph.api.IGraph;
import org.deeplearning4j.graph.data.GraphLoader;
import org.deeplearning4j.graph.graph.Graph;
import org.deeplearning4j.graph.iterator.GraphWalkIterator;
import org.deeplearning4j.graph.iterator.RandomWalkIterator;
import org.deeplearning4j.graph.iterator.parallel.GraphWalkIteratorProvider;
import org.deeplearning4j.graph.iterator.parallel.WeightedRandomWalkGraphIteratorProvider;
import org.deeplearning4j.graph.models.GraphVectors;
import org.deeplearning4j.graph.models.loader.GraphVectorSerializer;
import org.deeplearning4j.graph.vertexfactory.StringVertexFactory;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.common.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Permissions issues on CI")
@NativeTag
@Tag(TagNames.FILE_IO)
public class TestDeepWalk extends BaseDL4JTest {


    @Override
    public long getTimeoutMilliseconds() {
        return 120_000L;        //Increase timeout due to intermittently slow CI machines
    }

    @Test()
    @Timeout(60000)
    public void testBasic() throws IOException {
        //Very basic test. Load graph, build tree, call fit, make sure it doesn't throw any exceptions

        ClassPathResource cpr = new ClassPathResource("deeplearning4j-graph/testgraph_7vertices.txt");

        Graph<String, String> graph = GraphLoader
                        .loadUndirectedGraphEdgeListFile(cpr.getTempFileFromArchive().getAbsolutePath(), 7, ",");

        int vectorSize = 5;
        int windowSize = 2;

        DeepWalk<String, String> deepWalk = new DeepWalk.Builder<String, String>().learningRate(0.01)
                        .vectorSize(vectorSize).windowSize(windowSize).learningRate(0.01).build();
        deepWalk.initialize(graph);

        for (int i = 0; i < 7; i++) {
            INDArray vector = deepWalk.getVertexVector(i);
            assertArrayEquals(new long[] {vectorSize}, vector.shape());
//            System.out.println(Arrays.toString(vector.dup().data().asFloat()));
        }

        GraphWalkIterator<String> iter = new RandomWalkIterator<>(graph, 8);

        deepWalk.fit(iter);

        for (int t = 0; t < 5; t++) {
            iter.reset();
            deepWalk.fit(iter);
//            System.out.println("--------------------");
            for (int i = 0; i < 7; i++) {
                INDArray vector = deepWalk.getVertexVector(i);
                assertArrayEquals(new long[] {vectorSize}, vector.shape());
//                System.out.println(Arrays.toString(vector.dup().data().asFloat()));
            }
        }
    }

    @Test()
    @Timeout(180000)
    public void testParallel() {

        IGraph<String, String> graph = generateRandomGraph(30, 4);

        int vectorSize = 20;
        int windowSize = 2;

        DeepWalk<String, String> deepWalk = new DeepWalk.Builder<String, String>().learningRate(0.01)
                        .vectorSize(vectorSize).windowSize(windowSize).learningRate(0.01).build();
        deepWalk.initialize(graph);



        deepWalk.fit(graph, 6);
    }


    private static Graph<String, String> generateRandomGraph(int nVertices, int nEdgesPerVertex) {

        Random r = new Random(12345);

        Graph<String, String> graph = new Graph<>(nVertices, new StringVertexFactory());
        for (int i = 0; i < nVertices; i++) {
            for (int j = 0; j < nEdgesPerVertex; j++) {
                int to = r.nextInt(nVertices);
                Edge<String> edge = new Edge<>(i, to, i + "--" + to, false);
                graph.addEdge(edge);
            }
        }
        return graph;
    }


    @Test()
    @Timeout(60000)
    public void testVerticesNearest() {

        int nVertices = 20;
        Graph<String, String> graph = generateRandomGraph(nVertices, 8);

        int vectorSize = 5;
        int windowSize = 2;
        DeepWalk<String, String> deepWalk = new DeepWalk.Builder<String, String>().learningRate(0.01)
                        .vectorSize(vectorSize).windowSize(windowSize).learningRate(0.01).build();
        deepWalk.initialize(graph);

        deepWalk.fit(graph, 10);

        int topN = 5;
        int nearestTo = 4;
        int[] nearest = deepWalk.verticesNearest(nearestTo, topN);
        double[] cosSim = new double[topN];
        double minSimNearest = 1;
        for (int i = 0; i < topN; i++) {
            cosSim[i] = deepWalk.similarity(nearest[i], nearestTo);
            minSimNearest = Math.min(minSimNearest, cosSim[i]);
            if (i > 0)
                assertTrue(cosSim[i] <= cosSim[i - 1]);
        }

        for (int i = 0; i < nVertices; i++) {
            if (i == nearestTo)
                continue;
            boolean skip = false;
            for (int j = 0; j < nearest.length; j++) {
                if (i == nearest[j]) {
                    skip = true;
                    continue;
                }
            }
            if (skip)
                continue;

            double sim = deepWalk.similarity(i, nearestTo);
//            System.out.println(i + "\t" + nearestTo + "\t" + sim);
            assertTrue(sim <= minSimNearest);
        }
    }

    @Test()
    @Timeout(60000)
    public void testLoadingSaving(@TempDir Path testDir) throws IOException {
        String out = "dl4jdwtestout.txt";

        int nVertices = 20;
        Graph<String, String> graph = generateRandomGraph(nVertices, 8);

        int vectorSize = 5;
        int windowSize = 2;
        DeepWalk<String, String> deepWalk = new DeepWalk.Builder<String, String>().learningRate(0.01)
                        .vectorSize(vectorSize).windowSize(windowSize).learningRate(0.01).build();
        deepWalk.initialize(graph);

        deepWalk.fit(graph, 10);

        File f = new File(testDir.toFile(),out);
        GraphVectorSerializer.writeGraphVectors(deepWalk, f.getAbsolutePath());

        GraphVectors<String, String> vectors =
                        (GraphVectors<String, String>) GraphVectorSerializer.loadTxtVectors(f);

        assertEquals(deepWalk.numVertices(), vectors.numVertices());
        assertEquals(deepWalk.getVectorSize(), vectors.getVectorSize());

        for (int i = 0; i < nVertices; i++) {
            INDArray vecDW = deepWalk.getVertexVector(i);
            INDArray vecLoaded = vectors.getVertexVector(i);

            for (int j = 0; j < vectorSize; j++) {
                double d1 = vecDW.getDouble(j);
                double d2 = vecLoaded.getDouble(j);
                double relError = Math.abs(d1 - d2) / (Math.abs(d1) + Math.abs(d2));
                assertTrue(relError < 1e-6);
            }
        }
    }

    @Test()
    @Timeout(180000)
    public void testDeepWalk13Vertices() throws IOException {

        int nVertices = 13;

        ClassPathResource cpr = new ClassPathResource("deeplearning4j-graph/graph13.txt");
        Graph<String, String> graph = GraphLoader
                        .loadUndirectedGraphEdgeListFile(cpr.getTempFileFromArchive().getAbsolutePath(), 13, ",");

//        System.out.println(graph);

        Nd4j.getRandom().setSeed(12345);

        int nEpochs = 5;

        //Set up network
        DeepWalk<String, String> deepWalk =
                        new DeepWalk.Builder<String, String>().vectorSize(50).windowSize(4).seed(12345).build();

        //Run learning
        for (int i = 0; i < nEpochs; i++) {
            deepWalk.setLearningRate(0.03 / nEpochs * (nEpochs - i));
            deepWalk.fit(graph, 10);
        }

        //Calculate similarity(0,i)
        for (int i = 0; i < nVertices; i++) {
//            System.out.println(deepWalk.similarity(0, i));
            deepWalk.similarity(0, i);
        }

        for (int i = 0; i < nVertices; i++)
//            System.out.println(deepWalk.getVertexVector(i));
            deepWalk.getVertexVector(i);
    }

    @Test()
    @Timeout(60000)
    public void testDeepWalkWeightedParallel() throws IOException {

        //Load graph
        String path = new ClassPathResource("deeplearning4j-graph/WeightedGraph.txt").getTempFileFromArchive().getAbsolutePath();
        int numVertices = 9;
        String delim = ",";
        String[] ignoreLinesStartingWith = new String[] {"//"}; //Comment lines start with "//"
        IGraph<String, Double> graph =
                        GraphLoader.loadWeightedEdgeListFile(path, numVertices, delim, true, ignoreLinesStartingWith);

        //Set up DeepWalk
        int vectorSize = 5;
        int windowSize = 2;
        DeepWalk<String, Double> deepWalk = new DeepWalk.Builder<String, Double>().learningRate(0.01)
                        .vectorSize(vectorSize).windowSize(windowSize).learningRate(0.01).build();
        deepWalk.initialize(graph);

        //Can't use the following method here: defaults to unweighted random walk
        //deepWalk.fit(graph, 10);  //Unweighted random walk

        //Create GraphWalkIteratorProvider. The GraphWalkIteratorProvider is used to create multiple GraphWalkIterator objects.
        //Here, it is used to create a GraphWalkIterator, one for each thread
        int walkLength = 5;
        GraphWalkIteratorProvider<String> iteratorProvider =
                        new WeightedRandomWalkGraphIteratorProvider<>(graph, walkLength);

        //Fit in parallel
        deepWalk.fit(iteratorProvider);

    }
}
