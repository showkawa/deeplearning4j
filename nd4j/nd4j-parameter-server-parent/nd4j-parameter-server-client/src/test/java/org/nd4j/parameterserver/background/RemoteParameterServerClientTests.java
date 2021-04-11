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

package org.nd4j.parameterserver.background;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import lombok.extern.slf4j.Slf4j;
import org.agrona.CloseHelper;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.junit.jupiter.api.*;
import org.nd4j.common.tests.BaseND4JTest;
import org.nd4j.aeron.ipc.AeronUtil;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.parameterserver.client.ParameterServerClient;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@Disabled
@Tag(TagNames.FILE_IO)
@Tag(TagNames.DIST_SYSTEMS)
@NativeTag
public class RemoteParameterServerClientTests extends BaseND4JTest {
    private int parameterLength = 1000;
    private Aeron.Context ctx;
    private MediaDriver mediaDriver;
    private AtomicInteger masterStatus = new AtomicInteger(0);
    private AtomicInteger slaveStatus = new AtomicInteger(0);
    private Aeron aeron;

    @BeforeEach
    public void before() throws Exception {
        final MediaDriver.Context ctx =
                        new MediaDriver.Context().threadingMode(ThreadingMode.DEDICATED).dirDeleteOnStart(true)
                                        .termBufferSparseFile(false).conductorIdleStrategy(new BusySpinIdleStrategy())
                                        .receiverIdleStrategy(new BusySpinIdleStrategy())
                                        .senderIdleStrategy(new BusySpinIdleStrategy());

        mediaDriver = MediaDriver.launchEmbedded(ctx);
        aeron = Aeron.connect(getContext());

        Thread t = new Thread(() -> {
            try {
                masterStatus.set(
                                BackgroundDaemonStarter.startMaster(parameterLength, mediaDriver.aeronDirectoryName()));
            } catch (Exception e) {
                log.error("",e);
            }
        });

        t.start();
        log.info("Started master");
        Thread t2 = new Thread(() -> {
            try {
                slaveStatus.set(BackgroundDaemonStarter.startSlave(parameterLength, mediaDriver.aeronDirectoryName()));
            } catch (Exception e) {
                log.error("",e);
            }
        });
        t2.start();
        log.info("Started slave");
        //wait on the http servers
        Thread.sleep(30000);

    }


    @AfterEach
    public void after() throws Exception {
        CloseHelper.close(mediaDriver);
        CloseHelper.close(aeron);
    }

    @Test()
    @Timeout(60000L)
    @Disabled //AB 20200425 https://github.com/eclipse/deeplearning4j/issues/8882
    public void remoteTests() throws Exception {
        if (masterStatus.get() != 0 || slaveStatus.get() != 0)
            throw new IllegalStateException("Master or slave failed to start. Exiting");

        ParameterServerClient client = ParameterServerClient.builder().aeron(aeron)
                        .ndarrayRetrieveUrl(BackgroundDaemonStarter.masterResponderUrl())
                        .ndarraySendUrl(BackgroundDaemonStarter.slaveConnectionUrl()).subscriberHost("localhost")
                        .masterStatusHost("localhost").masterStatusPort(9200).subscriberPort(40125).subscriberStream(12)
                        .build();

        assertEquals("localhost:40125:12", client.connectionUrl());
        while (!client.masterStarted()) {
            Thread.sleep(1000);
            log.info("Waiting on master starting.");
        }

        //flow 1:
        /**
         * Client (40125:12): sends array to listener on slave(40126:10)
         * which publishes to master (40123:11)
         * which adds the array for parameter averaging.
         * In this case totalN should be 1.
         */
        log.info("Pushing ndarray");
        client.pushNDArray(Nd4j.ones(parameterLength));
        while (client.arraysSentToResponder() < 1) {
            Thread.sleep(1000);
            log.info("Waiting on ndarray responder to receive array");
        }

        log.info("Pushed ndarray");
        INDArray arr = client.getArray();
        assertEquals(Nd4j.ones(1000), arr);

        /*
        StopWatch stopWatch = new StopWatch();
        long nanoTimeTotal = 0;
        int n = 1000;
        for(int i = 0; i < n; i++) {
            stopWatch.start();
            client.getArray();
            stopWatch.stop();
            nanoTimeTotal += stopWatch.getNanoTime();
            stopWatch.reset();
        }
        
        System.out.println(nanoTimeTotal / n);
        */



    }



    private Aeron.Context getContext() {
        if (ctx == null)
            ctx = new Aeron.Context().driverTimeoutMs(Long.MAX_VALUE)
                            .availableImageHandler(AeronUtil::printAvailableImage)
                            .unavailableImageHandler(AeronUtil::printUnavailableImage)
                            .aeronDirectoryName(mediaDriver.aeronDirectoryName()).keepAliveIntervalNs(1000)
                            .errorHandler(e -> log.error(e.toString(), e));
        return ctx;
    }

}
