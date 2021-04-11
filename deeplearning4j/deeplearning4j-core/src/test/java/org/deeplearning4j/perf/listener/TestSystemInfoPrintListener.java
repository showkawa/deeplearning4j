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

package org.deeplearning4j.perf.listener;

import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.core.listener.SystemInfoFilePrintListener;
import org.deeplearning4j.core.listener.SystemInfoPrintListener;
import org.deeplearning4j.datasets.iterator.impl.IrisDataSetIterator;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.junit.jupiter.api.Disabled;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("AB 2019/05/24 - Failing on CI - \"Could not initialize class oshi.jna.platform.linux.Libc\" - Issue #7657")
public class TestSystemInfoPrintListener extends BaseDL4JTest {



    @Test
    public void testListener(@TempDir Path testDir) throws Exception {
        SystemInfoPrintListener systemInfoPrintListener = SystemInfoPrintListener.builder()
                .printOnEpochStart(true).printOnEpochEnd(true)
                .build();

        File tmpFile = Files.createTempFile(testDir,"tmpfile-log","txt").toFile();
        assertEquals(0, tmpFile.length() );

        SystemInfoFilePrintListener systemInfoFilePrintListener = SystemInfoFilePrintListener.builder()
                .printOnEpochStart(true).printOnEpochEnd(true).printFileTarget(tmpFile)
                .build();
        tmpFile.deleteOnExit();

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .list()
                .layer(new OutputLayer.Builder().nIn(4).nOut(3).activation(Activation.SOFTMAX).build())
                .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        net.setListeners(systemInfoFilePrintListener);

        DataSetIterator iter = new IrisDataSetIterator(10, 150);

        net.fit(iter, 3);

//        System.out.println(FileUtils.readFileToString(tmpFile));
    }

}
