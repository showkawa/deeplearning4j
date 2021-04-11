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

package org.nd4j.parameterserver.client;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import org.junit.jupiter.api.*;
import org.nd4j.common.tests.BaseND4JTest;
import org.nd4j.aeron.ipc.AeronUtil;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.parameterserver.ParameterServerListener;
import org.nd4j.parameterserver.ParameterServerSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

@Disabled
@Tag(TagNames.FILE_IO)
@Tag(TagNames.DIST_SYSTEMS)
@NativeTag
public class ParameterServerClientTest extends BaseND4JTest {
    private static MediaDriver mediaDriver;
    private static Logger log = LoggerFactory.getLogger(ParameterServerClientTest.class);
    private static Aeron aeron;
    private static ParameterServerSubscriber masterNode, slaveNode;
    private static int parameterLength = 1000;

    @BeforeAll
    public static void beforeClass() throws Exception {
        mediaDriver = MediaDriver.launchEmbedded(AeronUtil.getMediaDriverContext(parameterLength));
        System.setProperty("play.server.dir", "/tmp");
        aeron = Aeron.connect(getContext());
        masterNode = new ParameterServerSubscriber(mediaDriver);
        masterNode.setAeron(aeron);
        int masterPort = 40323 + new java.util.Random().nextInt(3000);
        masterNode.run(new String[] {"-m", "true", "-s", "1," + String.valueOf(parameterLength), "-p",
                String.valueOf(masterPort), "-h", "localhost", "-id", "11", "-md",
                mediaDriver.aeronDirectoryName(), "-sp", "33000", "-u", String.valueOf(1)});

        assertTrue(masterNode.isMaster());
        assertEquals(masterPort, masterNode.getPort());
        assertEquals("localhost", masterNode.getHost());
        assertEquals(11, masterNode.getStreamId());
        assertEquals(12, masterNode.getResponder().getStreamId());

        slaveNode = new ParameterServerSubscriber(mediaDriver);
        slaveNode.setAeron(aeron);
        slaveNode.run(new String[] {"-p", String.valueOf(masterPort + 100), "-h", "localhost", "-id", "10", "-pm",
                masterNode.getSubscriber().connectionUrl(), "-md", mediaDriver.aeronDirectoryName(), "-sp",
                "31000", "-u", String.valueOf(1)});

        assertFalse(slaveNode.isMaster());
        assertEquals(masterPort + 100, slaveNode.getPort());
        assertEquals("localhost", slaveNode.getHost());
        assertEquals(10, slaveNode.getStreamId());

        int tries = 10;
        while (!masterNode.subscriberLaunched() && !slaveNode.subscriberLaunched() && tries < 10) {
            Thread.sleep(10000);
            tries++;
        }

        if (!masterNode.subscriberLaunched() && !slaveNode.subscriberLaunched()) {
            throw new IllegalStateException("Failed to start master and slave node");
        }

        log.info("Using media driver directory " + mediaDriver.aeronDirectoryName());
        log.info("Launched media driver");
    }



    @Test()
    @Timeout(60000L)
    @Disabled("AB 2019/05/31 - Intermittent failures on CI - see issue 7657")
    public void testServer() throws Exception {
        int subscriberPort = 40625 + new java.util.Random().nextInt(100);
        ParameterServerClient client = ParameterServerClient.builder().aeron(aeron)
                .ndarrayRetrieveUrl(masterNode.getResponder().connectionUrl())
                .ndarraySendUrl(slaveNode.getSubscriber().connectionUrl()).subscriberHost("localhost")
                .subscriberPort(subscriberPort).subscriberStream(12).build();
        assertEquals(String.format("localhost:%d:12", subscriberPort), client.connectionUrl());
        //flow 1:
        /**
         * Client (40125:12): sends array to listener on slave(40126:10)
         * which publishes to master (40123:11)
         * which adds the array for parameter averaging.
         * In this case totalN should be 1.
         */
        client.pushNDArray(Nd4j.ones(1, parameterLength));
        log.info("Pushed ndarray");
        Thread.sleep(30000);
        ParameterServerListener listener = (ParameterServerListener) masterNode.getCallback();
        assertEquals(1, listener.getUpdater().numUpdates());
        assertEquals(Nd4j.ones(1, parameterLength), listener.getUpdater().ndArrayHolder().get());
        INDArray arr = client.getArray();
        assertEquals(Nd4j.ones(1, 1000), arr);
    }



    private static Aeron.Context getContext() {
        return new Aeron.Context().driverTimeoutMs(Long.MAX_VALUE)
                .availableImageHandler(AeronUtil::printAvailableImage)
                .unavailableImageHandler(AeronUtil::printUnavailableImage)
                .aeronDirectoryName(mediaDriver.aeronDirectoryName()).keepAliveIntervalNs(100000)
                .errorHandler(e -> log.error(e.toString(), e));
    }


}
