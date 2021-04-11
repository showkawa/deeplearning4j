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

import org.deeplearning4j.BaseDL4JTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("Random Data Set Iterator Test")
@NativeTag
@Tag(TagNames.FILE_IO)
class RandomDataSetIteratorTest extends BaseDL4JTest {

    @Test
    @DisplayName("Test DSI")
    void testDSI() {
        DataSetIterator iter = new RandomDataSetIterator(5, new long[] { 3, 4 }, new long[] { 3, 5 }, RandomDataSetIterator.Values.RANDOM_UNIFORM, RandomDataSetIterator.Values.ONE_HOT);
        int count = 0;
        while (iter.hasNext()) {
            count++;
            DataSet ds = iter.next();
            assertArrayEquals(new long[] { 3, 4 }, ds.getFeatures().shape());
            assertArrayEquals(new long[] { 3, 5 }, ds.getLabels().shape());
            assertTrue(ds.getFeatures().minNumber().doubleValue() >= 0.0 && ds.getFeatures().maxNumber().doubleValue() <= 1.0);
            assertEquals(Nd4j.ones(3), ds.getLabels().sum(1));
        }
        assertEquals(5, count);
    }

    @Test
    @DisplayName("Test MDSI")
    void testMDSI() {
        Nd4j.getRandom().setSeed(12345);
        MultiDataSetIterator iter = new RandomMultiDataSetIterator.Builder(5).addFeatures(new long[] { 3, 4 }, RandomMultiDataSetIterator.Values.INTEGER_0_100).addFeatures(new long[] { 3, 5 }, RandomMultiDataSetIterator.Values.BINARY).addLabels(new long[] { 3, 6 }, RandomMultiDataSetIterator.Values.ZEROS).build();
        int count = 0;
        while (iter.hasNext()) {
            count++;
            MultiDataSet mds = iter.next();
            assertEquals(2, mds.numFeatureArrays());
            assertEquals(1, mds.numLabelsArrays());
            assertArrayEquals(new long[] { 3, 4 }, mds.getFeatures(0).shape());
            assertArrayEquals(new long[] { 3, 5 }, mds.getFeatures(1).shape());
            assertArrayEquals(new long[] { 3, 6 }, mds.getLabels(0).shape());
            assertTrue(mds.getFeatures(0).minNumber().doubleValue() >= 0 && mds.getFeatures(0).maxNumber().doubleValue() <= 100.0 && mds.getFeatures(0).maxNumber().doubleValue() > 2.0);
            assertTrue(mds.getFeatures(1).minNumber().doubleValue() == 0.0 && mds.getFeatures(1).maxNumber().doubleValue() == 1.0);
            assertEquals(0.0, mds.getLabels(0).sumNumber().doubleValue(), 0.0);
        }
        assertEquals(5, count);
    }
}
