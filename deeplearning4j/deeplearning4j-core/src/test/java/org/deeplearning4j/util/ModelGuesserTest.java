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
package org.deeplearning4j.util;

import org.apache.commons.compress.utils.IOUtils;
import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.core.util.ModelGuesser;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.junit.jupiter.api.Disabled;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.preprocessor.Normalizer;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerMinMaxScaler;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.common.io.ClassPathResource;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.common.resources.Resources;
import java.io.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import org.junit.jupiter.api.DisplayName;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.ExtendWith;

@Disabled
@DisplayName("Model Guesser Test")
@NativeTag
@Tag(TagNames.FILE_IO)
class ModelGuesserTest extends BaseDL4JTest {

    @TempDir
    public Path testDir;



    @Test
    @DisplayName("Test Model Guess File")
    void testModelGuessFile() throws Exception {
        File f = Resources.asFile("modelimport/keras/examples/mnist_mlp/mnist_mlp_tf_keras_1_model.h5");
        assertTrue(f.exists());
        Model guess1 = ModelGuesser.loadModelGuess(f.getAbsolutePath());
        assertNotNull(guess1);
        f = Resources.asFile("modelimport/keras/examples/mnist_cnn/mnist_cnn_tf_keras_1_model.h5");
        assertTrue(f.exists());
        Model guess2 = ModelGuesser.loadModelGuess(f.getAbsolutePath());
        assertNotNull(guess2);
    }

    @Test
    @DisplayName("Test Model Guess Input Stream")
    void testModelGuessInputStream() throws Exception {
        File f = Resources.asFile("modelimport/keras/examples/mnist_mlp/mnist_mlp_tf_keras_1_model.h5");
        assertTrue(f.exists());
        try (InputStream inputStream = new FileInputStream(f)) {
            Model guess1 = ModelGuesser.loadModelGuess(inputStream);
            assertNotNull(guess1);
        }
        f = Resources.asFile("modelimport/keras/examples/mnist_cnn/mnist_cnn_tf_keras_1_model.h5");
        assertTrue(f.exists());
        try (InputStream inputStream = new FileInputStream(f)) {
            Model guess1 = ModelGuesser.loadModelGuess(inputStream);
            assertNotNull(guess1);
        }
    }

    @Test
    @DisplayName("Test Load Normalizers File")
    void testLoadNormalizersFile() throws Exception {
        MultiLayerNetwork net = getNetwork();
        File tempFile = testDir.resolve("testLoadNormalizersFile.bin").toFile();
        ModelSerializer.writeModel(net, tempFile, true);
        NormalizerMinMaxScaler normalizer = new NormalizerMinMaxScaler(0, 1);
        normalizer.fit(new DataSet(Nd4j.rand(new int[] { 2, 2 }), Nd4j.rand(new int[] { 2, 2 })));
        ModelSerializer.addNormalizerToModel(tempFile, normalizer);
        Model model = ModelGuesser.loadModelGuess(tempFile.getAbsolutePath());
        Normalizer<?> normalizer1 = ModelGuesser.loadNormalizer(tempFile.getAbsolutePath());
        assertEquals(model, net);
        assertEquals(normalizer, normalizer1);
    }

    @Test
    @DisplayName("Test Normalizer In Place")
    void testNormalizerInPlace() throws Exception {
        MultiLayerNetwork net = getNetwork();
        File tempFile = testDir.resolve("testNormalizerInPlace.bin").toFile();
        NormalizerMinMaxScaler normalizer = new NormalizerMinMaxScaler(0, 1);
        normalizer.fit(new DataSet(Nd4j.rand(new int[] { 2, 2 }), Nd4j.rand(new int[] { 2, 2 })));
        ModelSerializer.writeModel(net, tempFile, true, normalizer);
        Model model = ModelGuesser.loadModelGuess(tempFile.getAbsolutePath());
        Normalizer<?> normalizer1 = ModelGuesser.loadNormalizer(tempFile.getAbsolutePath());
        assertEquals(model, net);
        assertEquals(normalizer, normalizer1);
    }

    @Test
    @DisplayName("Test Load Normalizers Input Stream")
    void testLoadNormalizersInputStream() throws Exception {
        MultiLayerNetwork net = getNetwork();
        File tempFile = testDir.resolve("testLoadNormalizersInputStream.bin").toFile();
        ModelSerializer.writeModel(net, tempFile, true);
        NormalizerMinMaxScaler normalizer = new NormalizerMinMaxScaler(0, 1);
        normalizer.fit(new DataSet(Nd4j.rand(new int[] { 2, 2 }), Nd4j.rand(new int[] { 2, 2 })));
        ModelSerializer.addNormalizerToModel(tempFile, normalizer);
        Model model = ModelGuesser.loadModelGuess(tempFile.getAbsolutePath());
        try (InputStream inputStream = new FileInputStream(tempFile)) {
            Normalizer<?> normalizer1 = ModelGuesser.loadNormalizer(inputStream);
            assertEquals(model, net);
            assertEquals(normalizer, normalizer1);
        }
    }

    @Test
    @DisplayName("Test Model Guesser Dl 4 j Model File")
    void testModelGuesserDl4jModelFile() throws Exception {
        MultiLayerNetwork net = getNetwork();
        File tempFile = testDir.resolve("testModelGuesserDl4jModelFile.bin").toFile();
        ModelSerializer.writeModel(net, tempFile, true);
        MultiLayerNetwork network = (MultiLayerNetwork) ModelGuesser.loadModelGuess(tempFile.getAbsolutePath());
        assertEquals(network.getLayerWiseConfigurations().toJson(), net.getLayerWiseConfigurations().toJson());
        assertEquals(net.params(), network.params());
        assertEquals(net.getUpdater().getStateViewArray(), network.getUpdater().getStateViewArray());
    }

    @Test
    @DisplayName("Test Model Guesser Dl 4 j Model Input Stream")
    void testModelGuesserDl4jModelInputStream() throws Exception {
        MultiLayerNetwork net = getNetwork();
        File tempFile = testDir.resolve("testModelGuesserDl4jModelInputStream.bin").toFile();
        ModelSerializer.writeModel(net, tempFile, true);
        try (InputStream inputStream = new FileInputStream(tempFile)) {
            MultiLayerNetwork network = (MultiLayerNetwork) ModelGuesser.loadModelGuess(inputStream);
            assertNotNull(network);
            assertEquals(network.getLayerWiseConfigurations().toJson(), net.getLayerWiseConfigurations().toJson());
            assertEquals(net.params(), network.params());
            assertEquals(net.getUpdater().getStateViewArray(), network.getUpdater().getStateViewArray());
        }
    }

    @Test
    @DisplayName("Test Model Guess Config File")
    void testModelGuessConfigFile() throws Exception {
        ClassPathResource resource = new ClassPathResource("modelimport/keras/configs/cnn_tf_config.json", ModelGuesserTest.class.getClassLoader());
        File f = getTempFile(resource);
        String configFilename = f.getAbsolutePath();
        Object conf = ModelGuesser.loadConfigGuess(configFilename);
        assertTrue(conf instanceof MultiLayerConfiguration);
        ClassPathResource sequenceResource = new ClassPathResource("/keras/simple/mlp_fapi_multiloss_config.json");
        File f2 = getTempFile(sequenceResource);
        Object sequenceConf = ModelGuesser.loadConfigGuess(f2.getAbsolutePath());
        assertTrue(sequenceConf instanceof ComputationGraphConfiguration);
        ClassPathResource resourceDl4j = new ClassPathResource("model.json");
        File fDl4j = getTempFile(resourceDl4j);
        String configFilenameDl4j = fDl4j.getAbsolutePath();
        Object confDl4j = ModelGuesser.loadConfigGuess(configFilenameDl4j);
        assertTrue(confDl4j instanceof ComputationGraphConfiguration);
    }

    @Test
    @DisplayName("Test Model Guess Config Input Stream")
    void testModelGuessConfigInputStream() throws Exception {
        ClassPathResource resource = new ClassPathResource("modelimport/keras/configs/cnn_tf_config.json", ModelGuesserTest.class.getClassLoader());
        File f = getTempFile(resource);
        try (InputStream inputStream = new FileInputStream(f)) {
            Object conf = ModelGuesser.loadConfigGuess(inputStream);
            assertTrue(conf instanceof MultiLayerConfiguration);
        }
        ClassPathResource sequenceResource = new ClassPathResource("/keras/simple/mlp_fapi_multiloss_config.json");
        File f2 = getTempFile(sequenceResource);
        try (InputStream inputStream = new FileInputStream(f2)) {
            Object sequenceConf = ModelGuesser.loadConfigGuess(inputStream);
            assertTrue(sequenceConf instanceof ComputationGraphConfiguration);
        }
        ClassPathResource resourceDl4j = new ClassPathResource("model.json");
        File fDl4j = getTempFile(resourceDl4j);
        try (InputStream inputStream = new FileInputStream(fDl4j)) {
            Object confDl4j = ModelGuesser.loadConfigGuess(inputStream);
            assertTrue(confDl4j instanceof ComputationGraphConfiguration);
        }
    }

    private File getTempFile(ClassPathResource classPathResource) throws Exception {
        InputStream is = classPathResource.getInputStream();
        File f = testDir.toFile();
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(f));
        IOUtils.copy(is, bos);
        bos.flush();
        bos.close();
        return f;
    }

    private MultiLayerNetwork getNetwork() {
        int nIn = 5;
        int nOut = 6;
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().seed(12345).l1(0.01).l2(0.01).updater(new Sgd(0.1)).activation(Activation.TANH).weightInit(WeightInit.XAVIER).list().layer(0, new DenseLayer.Builder().nIn(nIn).nOut(20).build()).layer(1, new DenseLayer.Builder().nIn(20).nOut(30).build()).layer(2, new OutputLayer.Builder().lossFunction(LossFunctions.LossFunction.MSE).nIn(30).nOut(nOut).build()).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        return net;
    }
}
