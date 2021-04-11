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
package org.deeplearning4j.datasets.iterator;

import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.datasets.iterator.callbacks.InterleavedDataSetCallback;
import org.deeplearning4j.datasets.iterator.tools.VariableTimeseriesGenerator;
import org.deeplearning4j.nn.util.TestDataSetConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
@DisplayName("Async Data Set Iterator Test")
@NativeTag
class AsyncDataSetIteratorTest extends BaseDL4JTest {

    private ExistingDataSetIterator backIterator;

    private static final int TEST_SIZE = 100;

    private static final int ITERATIONS = 10;

    // time spent in consumer thread, milliseconds
    private static final long EXECUTION_TIME = 5;

    private static final long EXECUTION_SMALL = 1;

    @BeforeEach
    void setUp() throws Exception {
        List<DataSet> iterable = new ArrayList<>();
        for (int i = 0; i < TEST_SIZE; i++) {
            iterable.add(new DataSet(Nd4j.create(new float[100]), Nd4j.create(new float[10])));
        }
        backIterator = new ExistingDataSetIterator(iterable);
    }

    @Test
    @DisplayName("Has Next 1")
    void hasNext1() throws Exception {
        for (int iter = 0; iter < ITERATIONS; iter++) {
            for (int prefetchSize = 2; prefetchSize <= 8; prefetchSize++) {
                AsyncDataSetIterator iterator = new AsyncDataSetIterator(backIterator, prefetchSize);
                int cnt = 0;
                while (iterator.hasNext()) {
                    DataSet ds = iterator.next();
                    assertNotEquals(null, ds);
                    cnt++;
                }
                assertEquals( TEST_SIZE, cnt,"Failed on iteration: " + iter + ", prefetchSize: " + prefetchSize);
                iterator.shutdown();
            }
        }
    }

    @Test
    @DisplayName("Has Next With Reset And Load")
    void hasNextWithResetAndLoad() throws Exception {
        int[] prefetchSizes;
        if (isIntegrationTests()) {
            prefetchSizes = new int[] { 2, 3, 4, 5, 6, 7, 8 };
        } else {
            prefetchSizes = new int[] { 2, 3, 8 };
        }
        for (int iter = 0; iter < ITERATIONS; iter++) {
            for (int prefetchSize : prefetchSizes) {
                AsyncDataSetIterator iterator = new AsyncDataSetIterator(backIterator, prefetchSize);
                TestDataSetConsumer consumer = new TestDataSetConsumer(EXECUTION_SMALL);
                int cnt = 0;
                while (iterator.hasNext()) {
                    DataSet ds = iterator.next();
                    consumer.consumeOnce(ds, false);
                    cnt++;
                    if (cnt == TEST_SIZE / 2)
                        iterator.reset();
                }
                assertEquals(TEST_SIZE + (TEST_SIZE / 2), cnt);
                iterator.shutdown();
            }
        }
    }

    @Test
    @DisplayName("Test With Load")
    void testWithLoad() {
        for (int iter = 0; iter < ITERATIONS; iter++) {
            AsyncDataSetIterator iterator = new AsyncDataSetIterator(backIterator, 8);
            TestDataSetConsumer consumer = new TestDataSetConsumer(iterator, EXECUTION_TIME);
            consumer.consumeWhileHasNext(true);
            assertEquals(TEST_SIZE, consumer.getCount());
            iterator.shutdown();
        }
    }

    @Test
    @DisplayName("Test With Exception")
    void testWithException() {
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> {
            ExistingDataSetIterator crashingIterator = new ExistingDataSetIterator(new IterableWithException(100));
            AsyncDataSetIterator iterator = new AsyncDataSetIterator(crashingIterator, 8);
            TestDataSetConsumer consumer = new TestDataSetConsumer(iterator, EXECUTION_SMALL);
            consumer.consumeWhileHasNext(true);
            iterator.shutdown();
        });
    }

    @DisplayName("Iterable With Exception")
    private class IterableWithException implements Iterable<DataSet> {

        private final AtomicLong counter = new AtomicLong(0);

        private final int crashIteration;

        public IterableWithException(int iteration) {
            crashIteration = iteration;
        }

        @Override
        public Iterator<DataSet> iterator() {
            counter.set(0);
            return new Iterator<DataSet>() {

                @Override
                public boolean hasNext() {
                    return true;
                }

                @Override
                public DataSet next() {
                    if (counter.incrementAndGet() >= crashIteration)
                        throw new ArrayIndexOutOfBoundsException("Thrown as expected");
                    return new DataSet(Nd4j.create(10), Nd4j.create(10));
                }

                @Override
                public void remove() {
                }
            };
        }
    }

    @Test
    @DisplayName("Test Variable Time Series 1")
    void testVariableTimeSeries1() throws Exception {
        int numBatches = isIntegrationTests() ? 1000 : 100;
        int batchSize = isIntegrationTests() ? 32 : 8;
        int timeStepsMin = 10;
        int timeStepsMax = isIntegrationTests() ? 500 : 100;
        int valuesPerTimestep = isIntegrationTests() ? 128 : 16;
        AsyncDataSetIterator adsi = new AsyncDataSetIterator(new VariableTimeseriesGenerator(1192, numBatches, batchSize, valuesPerTimestep, timeStepsMin, timeStepsMax, 10), 2, true);
        for (int e = 0; e < 10; e++) {
            int cnt = 0;
            while (adsi.hasNext()) {
                DataSet ds = adsi.next();
                // log.info("Features ptr: {}", AtomicAllocator.getInstance().getPointer(mds.getFeatures()[0].data()).address());
                assertEquals( (double) cnt, ds.getFeatures().meanNumber().doubleValue(), 1e-10,"Failed on epoch " + e + "; iteration: " + cnt + ";");
                assertEquals( (double) cnt + 0.25, ds.getLabels().meanNumber().doubleValue(), 1e-10,"Failed on epoch " + e + "; iteration: " + cnt + ";");
                assertEquals( (double) cnt + 0.5, ds.getFeaturesMaskArray().meanNumber().doubleValue(), 1e-10,"Failed on epoch " + e + "; iteration: " + cnt + ";");
                assertEquals( (double) cnt + 0.75, ds.getLabelsMaskArray().meanNumber().doubleValue(), 1e-10,"Failed on epoch " + e + "; iteration: " + cnt + ";");
                cnt++;
            }
            adsi.reset();
            // log.info("Epoch {} finished...", e);
        }
    }

    @Test
    @DisplayName("Test Variable Time Series 2")
    void testVariableTimeSeries2() throws Exception {
        AsyncDataSetIterator adsi = new AsyncDataSetIterator(new VariableTimeseriesGenerator(1192, 100, 32, 128, 100, 100, 100), 2, true, new InterleavedDataSetCallback(2 * 2));
        for (int e = 0; e < 5; e++) {
            int cnt = 0;
            while (adsi.hasNext()) {
                DataSet ds = adsi.next();
                ds.detach();
                // log.info("Features ptr: {}", AtomicAllocator.getInstance().getPointer(mds.getFeatures()[0].data()).address());
                assertEquals((double) cnt, ds.getFeatures().meanNumber().doubleValue(), 1e-10,"Failed on epoch " + e + "; iteration: " + cnt + ";");
                assertEquals((double) cnt + 0.25, ds.getLabels().meanNumber().doubleValue(), 1e-10,"Failed on epoch " + e + "; iteration: " + cnt + ";");
                assertEquals( (double) cnt + 0.5, ds.getFeaturesMaskArray().meanNumber().doubleValue(), 1e-10,"Failed on epoch " + e + "; iteration: " + cnt + ";");
                assertEquals((double) cnt + 0.75, ds.getLabelsMaskArray().meanNumber().doubleValue(), 1e-10,"Failed on epoch " + e + "; iteration: " + cnt + ";");
                cnt++;
            }
            adsi.reset();
            // log.info("Epoch {} finished...", e);
        }
    }
}
