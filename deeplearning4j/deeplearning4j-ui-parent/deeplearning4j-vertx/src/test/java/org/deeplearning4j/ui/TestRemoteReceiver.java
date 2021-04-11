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

package org.deeplearning4j.ui;

import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.core.storage.Persistable;
import org.deeplearning4j.core.storage.StorageMetaData;
import org.deeplearning4j.core.storage.impl.CollectionStatsStorageRouter;
import org.deeplearning4j.core.storage.impl.RemoteUIStatsStorageRouter;
import org.deeplearning4j.datasets.iterator.impl.IrisDataSetIterator;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.ui.api.UIServer;
import org.deeplearning4j.ui.model.stats.StatsListener;
import org.deeplearning4j.ui.model.stats.impl.SbeStatsInitializationReport;
import org.deeplearning4j.ui.model.stats.impl.SbeStatsReport;
import org.deeplearning4j.ui.model.storage.impl.SbeStorageMetaData;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled
@Tag(TagNames.FILE_IO)
@Tag(TagNames.UI)
@Tag(TagNames.DIST_SYSTEMS)
@NativeTag
public class TestRemoteReceiver extends BaseDL4JTest {

    @Test
    @Disabled
    public void testRemoteBasic() throws Exception {

        List<Persistable> updates = new ArrayList<>();
        List<Persistable> staticInfo = new ArrayList<>();
        List<StorageMetaData> metaData = new ArrayList<>();
        CollectionStatsStorageRouter collectionRouter = new CollectionStatsStorageRouter(metaData, staticInfo, updates);


        UIServer s = UIServer.getInstance();
        Thread.sleep(1000);
        s.enableRemoteListener(collectionRouter, false);


        try(RemoteUIStatsStorageRouter remoteRouter = new RemoteUIStatsStorageRouter("http://localhost:9000")) {    //Use closeable to ensure async thread is shut down

            SbeStatsReport update1 = new SbeStatsReport();
            update1.setMemoryUsePresent(true);
            update1.setDeviceCurrentBytes(new long[]{1, 2});
            update1.setDeviceMaxBytes(new long[]{100, 200});
            update1.reportIterationCount(10);
            update1.reportIDs("sid", "tid", "wid", 123456);
            update1.reportPerformance(10, 20, 30, 40, 50);

            SbeStatsReport update2 = new SbeStatsReport();
            update2.setMemoryUsePresent(true);
            update2.setDeviceCurrentBytes(new long[]{3, 4});
            update2.setDeviceMaxBytes(new long[]{300, 400});
            update2.reportIterationCount(20);
            update2.reportIDs("sid2", "tid2", "wid2", 123456);
            update2.reportPerformance(11, 21, 31, 40, 50);

            StorageMetaData smd1 = new SbeStorageMetaData(123, "sid", "typeid", "wid", "initTypeClass", "updaterTypeClass");
            StorageMetaData smd2 =
                    new SbeStorageMetaData(456, "sid2", "typeid2", "wid2", "initTypeClass2", "updaterTypeClass2");

            SbeStatsInitializationReport init1 = new SbeStatsInitializationReport();
            init1.reportIDs("sid", "wid", "tid", 3145253452L);
            init1.reportHardwareInfo(1, 2, 3, 4, null, null, "2344253");
            init1.setHwDeviceTotalMemory(new long[]{1,2});
            init1.setHwDeviceDescription(new String[]{"d1", "d2"});
            init1.setHasHardwareInfo(true);

            remoteRouter.putUpdate(update1);
            Thread.sleep(100);
            remoteRouter.putStorageMetaData(smd1);
            Thread.sleep(100);
            remoteRouter.putStaticInfo(init1);
            Thread.sleep(100);
            remoteRouter.putUpdate(update2);
            Thread.sleep(100);
            remoteRouter.putStorageMetaData(smd2);


            Thread.sleep(2000);

            assertEquals(2, metaData.size());
            assertEquals(2, updates.size());
            assertEquals(1, staticInfo.size());

            assertEquals(Arrays.asList(update1, update2), updates);
            assertEquals(Arrays.asList(smd1, smd2), metaData);
            assertEquals(Collections.singletonList(init1), staticInfo);
        }
    }


    @Test
    @Disabled
    public void testRemoteFull() throws Exception {
        //Use this in conjunction with startRemoteUI()

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                        .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).list()
                        .layer(0, new DenseLayer.Builder().activation(Activation.TANH).nIn(4).nOut(4).build())
                        .layer(1, new OutputLayer.Builder().lossFunction(LossFunctions.LossFunction.MCXENT)
                                        .activation(Activation.SOFTMAX).nIn(4).nOut(3).build())
                        .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        try(RemoteUIStatsStorageRouter ssr = new RemoteUIStatsStorageRouter("http://localhost:9000")) {
            net.setListeners(new StatsListener(ssr), new ScoreIterationListener(1));

            DataSetIterator iter = new IrisDataSetIterator(150, 150);

            for (int i = 0; i < 500; i++) {
                net.fit(iter);
                //            Thread.sleep(100);
                Thread.sleep(100);
            }
        }
    }

    @Test
    @Disabled
    public void startRemoteUI() throws Exception {

        UIServer s = UIServer.getInstance();
        s.enableRemoteListener();

        Thread.sleep(1000000);
    }

}
