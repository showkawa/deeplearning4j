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

package org.deeplearning4j.nn.layers.samediff;

import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.TestUtils;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.nn.conf.graph.ElementWiseVertex;
import org.deeplearning4j.nn.conf.graph.ScaleVertex;
import org.deeplearning4j.nn.conf.graph.ShiftVertex;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.layers.samediff.testlayers.SameDiffSimpleLambdaLayer;
import org.deeplearning4j.nn.layers.samediff.testlayers.SameDiffSimpleLambdaVertex;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@NativeTag
@Tag(TagNames.SAMEDIFF)
@Tag(TagNames.CUSTOM_FUNCTIONALITY)
@Tag(TagNames.DL4J_OLD_API)
public class TestSameDiffLambda extends BaseDL4JTest {

    private static final boolean PRINT_RESULTS = true;
    private static final boolean RETURN_ON_FIRST_FAILURE = false;
    private static final double DEFAULT_EPS = 1e-6;
    private static final double DEFAULT_MAX_REL_ERROR = 1e-3;
    private static final double DEFAULT_MIN_ABS_ERROR = 1e-8;

    @Test
    public void testSameDiffLamdaLayerBasic(){
        for(WorkspaceMode wsm : new WorkspaceMode[]{WorkspaceMode.ENABLED, WorkspaceMode.NONE}) {
            log.info("--- Workspace Mode: {} ---", wsm);


            Nd4j.getRandom().setSeed(12345);
            ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                    .trainingWorkspaceMode(wsm)
                    .inferenceWorkspaceMode(wsm)
                    .seed(12345)
                    .updater(new Adam(0.01))
                    .graphBuilder()
                    .addInputs("in")
                    .addLayer("0", new DenseLayer.Builder().nIn(5).nOut(5).activation(Activation.TANH).build(), "in")
                    .addLayer("1", new SameDiffSimpleLambdaLayer(), "0")
                    .addLayer("2", new OutputLayer.Builder().nIn(5).nOut(5).activation(Activation.SOFTMAX)
                            .lossFunction(LossFunctions.LossFunction.MCXENT).build(), "1")
                    .setOutputs("2")
                    .build();

            //Equavalent, not using SameDiff Lambda:
            ComputationGraphConfiguration confStd = new NeuralNetConfiguration.Builder()
                    .trainingWorkspaceMode(wsm)
                    .inferenceWorkspaceMode(wsm)
                    .seed(12345)
                    .updater(new Adam(0.01))
                    .graphBuilder()
                    .addInputs("in")
                    .addLayer("0", new DenseLayer.Builder().nIn(5).nOut(5).activation(Activation.TANH).build(), "in")
                    .addVertex("1", new ShiftVertex(1.0), "0")
                    .addVertex("2", new ScaleVertex(2.0), "1")
                    .addLayer("3", new OutputLayer.Builder().nIn(5).nOut(5).activation(Activation.SOFTMAX)
                            .lossFunction(LossFunctions.LossFunction.MCXENT).build(), "2")
                    .setOutputs("3")
                    .build();

            ComputationGraph lambda = new ComputationGraph(conf);
            lambda.init();

            ComputationGraph std = new ComputationGraph(confStd);
            std.init();

            lambda.setParams(std.params());

            INDArray in = Nd4j.rand(3, 5);
            INDArray labels = TestUtils.randomOneHot(3, 5);
            DataSet ds = new DataSet(in, labels);

            INDArray outLambda = lambda.outputSingle(in);
            INDArray outStd = std.outputSingle(in);

            assertEquals(outLambda, outStd);

            double scoreLambda = lambda.score(ds);
            double scoreStd = std.score(ds);

            assertEquals(scoreStd, scoreLambda, 1e-6);

            for (int i = 0; i < 3; i++) {
                lambda.fit(ds);
                std.fit(ds);

                String s = String.valueOf(i);
                assertEquals(std.params(), lambda.params(), s);
                assertEquals(std.getFlattenedGradients(), lambda.getFlattenedGradients(), s);
            }

            ComputationGraph loaded = TestUtils.testModelSerialization(lambda);
            outLambda = loaded.outputSingle(in);
            outStd = std.outputSingle(in);

            assertEquals(outStd, outLambda);

            //Sanity check on different minibatch sizes:
            INDArray newIn = Nd4j.vstack(in, in);
            INDArray outMbsd = lambda.output(newIn)[0];
            INDArray outMb = std.output(newIn)[0];
            assertEquals(outMb, outMbsd);
        }
    }

    @Test
    public void testSameDiffLamdaVertexBasic(){
        for(WorkspaceMode wsm : new WorkspaceMode[]{WorkspaceMode.ENABLED, WorkspaceMode.NONE}) {
            log.info("--- Workspace Mode: {} ---", wsm);

            Nd4j.getRandom().setSeed(12345);
            ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                    .trainingWorkspaceMode(wsm)
                    .inferenceWorkspaceMode(wsm)
                    .dataType(DataType.DOUBLE)
                    .seed(12345)
                    .updater(new Adam(0.01))
                    .graphBuilder()
                    .addInputs("in1", "in2")
                    .addLayer("0", new DenseLayer.Builder().nIn(5).nOut(5).activation(Activation.TANH).build(), "in1")
                    .addLayer("1", new DenseLayer.Builder().nIn(5).nOut(5).activation(Activation.TANH).build(), "in2")
                    .addVertex("lambda", new SameDiffSimpleLambdaVertex(), "0", "1")
                    .addLayer("2", new OutputLayer.Builder().nIn(5).nOut(5).activation(Activation.SOFTMAX)
                            .lossFunction(LossFunctions.LossFunction.MCXENT).build(), "lambda")
                    .setOutputs("2")
                    .build();

            //Equavalent, not using SameDiff Lambda:
            ComputationGraphConfiguration confStd = new NeuralNetConfiguration.Builder()
                    .trainingWorkspaceMode(wsm)
                    .inferenceWorkspaceMode(wsm)
                    .dataType(DataType.DOUBLE)
                    .seed(12345)
                    .updater(new Adam(0.01))
                    .graphBuilder()
                    .addInputs("in1", "in2")
                    .addLayer("0", new DenseLayer.Builder().nIn(5).nOut(5).activation(Activation.TANH).build(), "in1")
                    .addLayer("1", new DenseLayer.Builder().nIn(5).nOut(5).activation(Activation.TANH).build(), "in2")
                    .addVertex("elementwise", new ElementWiseVertex(ElementWiseVertex.Op.Product), "0", "1")
                    .addLayer("3", new OutputLayer.Builder().nIn(5).nOut(5).activation(Activation.SOFTMAX)
                            .lossFunction(LossFunctions.LossFunction.MCXENT).build(), "elementwise")
                    .setOutputs("3")
                    .build();

            ComputationGraph lambda = new ComputationGraph(conf);
            lambda.init();

            ComputationGraph std = new ComputationGraph(confStd);
            std.init();

            lambda.setParams(std.params());

            INDArray in1 = Nd4j.rand(3, 5);
            INDArray in2 = Nd4j.rand(3, 5);
            INDArray labels = TestUtils.randomOneHot(3, 5);
            MultiDataSet mds = new org.nd4j.linalg.dataset.MultiDataSet(new INDArray[]{in1, in2}, new INDArray[]{labels});

            INDArray outLambda = lambda.output(in1, in2)[0];
            INDArray outStd = std.output(in1, in2)[0];

            assertEquals(outLambda, outStd);

            double scoreLambda = lambda.score(mds);
            double scoreStd = std.score(mds);

            assertEquals(scoreStd, scoreLambda, 1e-6);

            for (int i = 0; i < 3; i++) {
                lambda.fit(mds);
                std.fit(mds);

                String s = String.valueOf(i);
                assertEquals(std.params(), lambda.params(), s);
                assertEquals(std.getFlattenedGradients(), lambda.getFlattenedGradients(), s);
            }

            ComputationGraph loaded = TestUtils.testModelSerialization(lambda);
            outLambda = loaded.output(in1, in2)[0];
            outStd = std.output(in1, in2)[0];

            assertEquals(outStd, outLambda);

            //Sanity check on different minibatch sizes:
            INDArray newIn1 = Nd4j.vstack(in1, in1);
            INDArray newIn2 = Nd4j.vstack(in2, in2);
            INDArray outMbsd = lambda.output(newIn1, newIn2)[0];
            INDArray outMb = std.output(newIn1, newIn2)[0];
            assertEquals(outMb, outMbsd);
        }
    }
}
