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

package org.deeplearning4j.parallelism.main;

import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.datasets.iterator.impl.MnistDataSetIterator;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.SubsamplingLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.util.ModelSerializer;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.learning.config.Nesterovs;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Disabled("Permissions issues on CI")
@Tag(TagNames.FILE_IO)
@NativeTag
@Tag(TagNames.LONG_TEST)
@Tag(TagNames.LARGE_RESOURCES)
public class ParallelWrapperMainTest extends BaseDL4JTest {


    @Test
    public void runParallelWrapperMain(@TempDir Path testDir) throws Exception {

        int nChannels = 1;
        int outputNum = 10;

        // for GPU you usually want to have higher batchSize
        int batchSize = 128;
        int seed = 123;
        int uiPort = 9500;
        System.setProperty("org.deeplearning4j.ui.port", String.valueOf(uiPort));
        log.info("Load data....");
        DataSetIterator mnistTrain = new MnistDataSetIterator(batchSize, true, 12345);
        DataSetIterator mnistTest = new MnistDataSetIterator(batchSize, false, 12345);

        log.info("Build model....");
        MultiLayerConfiguration.Builder builder = new NeuralNetConfiguration.Builder().seed(seed)
                        .l2(0.0005)
                        .weightInit(WeightInit.XAVIER)
                        .updater(new Nesterovs(0.01, 0.9)).list()
                        .layer(0, new ConvolutionLayer.Builder(5, 5)
                                        //nIn and nOut specify channels. nIn here is the nChannels and nOut is the number of filters to be applied
                                        .nIn(nChannels).stride(1, 1).nOut(20).activation(Activation.IDENTITY).build())
                        .layer(1, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX).kernelSize(2, 2)
                                        .stride(2, 2).build())
                        .layer(2, new ConvolutionLayer.Builder(5, 5)
                                        //Note that nIn needed be specified in later layers
                                        .stride(1, 1).nOut(50).activation(Activation.IDENTITY).build())
                        .layer(3, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX).kernelSize(2, 2)
                                        .stride(2, 2).build())
                        .layer(4, new DenseLayer.Builder().activation(Activation.RELU).nOut(500).build())
                        .layer(5, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                                        .nOut(outputNum).activation(Activation.SOFTMAX).build())
                        .setInputType(InputType.convolutionalFlat(28, 28, nChannels));

        MultiLayerConfiguration conf = builder.build();
        MultiLayerNetwork model = new MultiLayerNetwork(conf);
        model.init();
        File tempModel = Files.createTempFile(testDir,"tmpmodel","zip").toFile();
        tempModel.deleteOnExit();
        ModelSerializer.writeModel(model, tempModel, false);
        File tmp = Files.createTempFile(testDir,"tmpmodel","bin").toFile();
        tmp.deleteOnExit();
        ParallelWrapperMain parallelWrapperMain = new ParallelWrapperMain();
        try {
            parallelWrapperMain.runMain(new String[]{"--modelPath", tempModel.getAbsolutePath(),
                    "--dataSetIteratorFactoryClazz", MnistDataSetIteratorProviderFactory.class.getName(),
                    "--modelOutputPath", tmp.getAbsolutePath(), "--uiUrl", "localhost:" + uiPort});
        } finally {
            parallelWrapperMain.stop();
        }
    }
}
