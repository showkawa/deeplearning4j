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

package org.deeplearning4j.zoo;

import lombok.extern.slf4j.Slf4j;
import org.datavec.image.loader.NativeImageLoader;
import org.datavec.image.transform.ColorConversionTransform;
import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.layers.objdetect.DetectedObject;
import org.deeplearning4j.nn.layers.objdetect.YoloUtils;
import org.deeplearning4j.zoo.model.Darknet19;
import org.deeplearning4j.zoo.model.TinyYOLO;
import org.deeplearning4j.zoo.model.VGG19;
import org.deeplearning4j.zoo.model.YOLO2;
import org.deeplearning4j.zoo.util.ClassPrediction;
import org.deeplearning4j.zoo.util.Labels;
import org.deeplearning4j.zoo.util.darknet.COCOLabels;
import org.deeplearning4j.zoo.util.darknet.DarknetLabels;
import org.deeplearning4j.zoo.util.darknet.VOCLabels;
import org.deeplearning4j.zoo.util.imagenet.ImageNetLabels;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.ImagePreProcessingScaler;
import org.nd4j.linalg.dataset.api.preprocessor.VGG16ImagePreProcessor;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2RGB;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@Tag(TagNames.FILE_IO)
@Tag(TagNames.DL4J_OLD_API)
@NativeTag
@Tag(TagNames.LONG_TEST)
@Tag(TagNames.LARGE_RESOURCES)
public class TestImageNet extends BaseDL4JTest {

    @Override
    public long getTimeoutMilliseconds() {
        return 90000L;
    }

    @Override
    public DataType getDataType(){
        return DataType.FLOAT;
    }

    @Test
    public void testImageNetLabels() throws IOException {
        // set up model
        ZooModel model = VGG19.builder().numClasses(0).build(); //num labels doesn't matter since we're getting pretrained imagenet
        ComputationGraph initializedModel = (ComputationGraph) model.initPretrained();

        // set up input and feedforward
        NativeImageLoader loader = new NativeImageLoader(224, 224, 3);
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        INDArray image = loader.asMatrix(classloader.getResourceAsStream("deeplearning4j-zoo/goldenretriever.jpg"));
        DataNormalization scaler = new VGG16ImagePreProcessor();
        scaler.transform(image);
        INDArray[] output = initializedModel.output(false, image);

        // check output labels of result
        String decodedLabels = new ImageNetLabels().decodePredictions(output[0]);
        log.info(decodedLabels);
        assertTrue(decodedLabels.contains("golden_retriever"));

        // clean up for current model
        Nd4j.getWorkspaceManager().destroyAllWorkspacesForCurrentThread();
        System.gc();
    }

    @Test
    @Disabled("AB 2019/05/30 - Failing (intermittently?) on CI linux - see issue 7657")
    public void testDarknetLabels() throws IOException {
        // set up model
        ZooModel model = Darknet19.builder().numClasses(0).build(); //num labels doesn't matter since we're getting pretrained imagenet
        ComputationGraph initializedModel = (ComputationGraph) model.initPretrained();

        // set up input and feedforward
        NativeImageLoader loader = new NativeImageLoader(224, 224, 3, new ColorConversionTransform(COLOR_BGR2RGB));
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        INDArray image = loader.asMatrix(classloader.getResourceAsStream("deeplearning4j-zoo/goldenretriever.jpg"));
        DataNormalization scaler = new ImagePreProcessingScaler(0, 1);
        scaler.transform(image);
        INDArray result = initializedModel.outputSingle(image);
        Labels labels = new DarknetLabels();
        List<List<ClassPrediction>> predictions = labels.decodePredictions(result, 10);

        // check output labels of result
        log.info(predictions.toString());
        assertEquals("golden retriever", predictions.get(0).get(0).getLabel());

        // clean up for current model
        initializedModel.params().close();
        Nd4j.getWorkspaceManager().destroyAllWorkspacesForCurrentThread();
        System.gc();

        // set up model
        model = TinyYOLO.builder().numClasses(0).build(); //num labels doesn't matter since we're getting pretrained imagenet
        initializedModel = (ComputationGraph) model.initPretrained();

        // set up input and feedforward
        loader = new NativeImageLoader(416, 416, 3, new ColorConversionTransform(COLOR_BGR2RGB));
        image = loader.asMatrix(classloader.getResourceAsStream("deeplearning4j-zoo/goldenretriever.jpg"));
        scaler = new ImagePreProcessingScaler(0, 1);
        scaler.transform(image);
        INDArray outputs = initializedModel.outputSingle(image);
        List<DetectedObject> objs = YoloUtils.getPredictedObjects(Nd4j.create(((TinyYOLO) model).getPriorBoxes()), outputs, 0.6, 0.4);
        assertEquals(1, objs.size());

        // check output labels of result
        labels = new VOCLabels();
        for (DetectedObject obj : objs) {
            ClassPrediction classPrediction = labels.decodePredictions(obj.getClassPredictions(), 1).get(0).get(0);
            log.info(obj.toString() + " " + classPrediction);
            assertEquals("dog", classPrediction.getLabel());
        }

        // clean up for current model
        initializedModel.params().close();
        Nd4j.getWorkspaceManager().destroyAllWorkspacesForCurrentThread();
        System.gc();

        // set up model
        model = YOLO2.builder().numClasses(1000).build(); //num labels doesn't matter since we're getting pretrained imagenet
        initializedModel = (ComputationGraph) model.initPretrained();

        // set up input and feedforward
        loader = new NativeImageLoader(608, 608, 3, new ColorConversionTransform(COLOR_BGR2RGB));
        image = loader.asMatrix(classloader.getResourceAsStream("deeplearning4j-zoo/goldenretriever.jpg"));
        scaler = new ImagePreProcessingScaler(0, 1);
        scaler.transform(image);
        outputs = initializedModel.outputSingle(image);
        objs = YoloUtils.getPredictedObjects(Nd4j.create(((YOLO2) model).getPriorBoxes()), outputs, 0.6, 0.4);
        assertEquals(1, objs.size());

        // check output labels of result
        labels = new COCOLabels();
        for (DetectedObject obj : objs) {
            ClassPrediction classPrediction = labels.decodePredictions(obj.getClassPredictions(), 1).get(0).get(0);
            log.info(obj.toString() + " " + classPrediction);
            assertEquals("dog", classPrediction.getLabel());
        }

        initializedModel.params().close();
    }

}
