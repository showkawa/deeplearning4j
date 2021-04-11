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

import org.apache.commons.lang3.RandomUtils;
import org.deeplearning4j.BaseDL4JTest;
import org.junit.jupiter.api.Test;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.common.primitives.Pair;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("Abstract Data Set Iterator Test")
class AbstractDataSetIteratorTest extends BaseDL4JTest {

    @Test
    @DisplayName("Next")
    void next() throws Exception {
        int numFeatures = 128;
        int batchSize = 10;
        int numRows = 1000;
        AtomicInteger cnt = new AtomicInteger(0);
        FloatsDataSetIterator iterator = new FloatsDataSetIterator(floatIterable(numRows, numFeatures), batchSize);
        assertTrue(iterator.hasNext());
        while (iterator.hasNext()) {
            DataSet dataSet = iterator.next();
            INDArray features = dataSet.getFeatures();
            assertEquals(batchSize, features.rows());
            assertEquals(numFeatures, features.columns());
            cnt.incrementAndGet();
        }
        assertEquals(numRows / batchSize, cnt.get());
    }

    protected static Iterable<Pair<float[], float[]>> floatIterable(final int totalRows, final int numColumns) {
        return new Iterable<Pair<float[], float[]>>() {

            @Override
            public Iterator<Pair<float[], float[]>> iterator() {
                return new Iterator<Pair<float[], float[]>>() {

                    private AtomicInteger cnt = new AtomicInteger(0);

                    @Override
                    public boolean hasNext() {
                        return cnt.incrementAndGet() <= totalRows;
                    }

                    @Override
                    public Pair<float[], float[]> next() {
                        float[] features = new float[numColumns];
                        float[] labels = new float[numColumns];
                        for (int i = 0; i < numColumns; i++) {
                            features[i] = (float) i;
                            labels[i] = RandomUtils.nextFloat(0, 5);
                        }
                        return Pair.makePair(features, labels);
                    }

                    @Override
                    public void remove() {
                        // no-op
                    }
                };
            }
        };
    }
}
