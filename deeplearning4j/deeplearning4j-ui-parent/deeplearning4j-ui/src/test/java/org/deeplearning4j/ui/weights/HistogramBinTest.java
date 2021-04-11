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

package org.deeplearning4j.ui.weights;

import org.deeplearning4j.ui.model.weights.HistogramBin;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
@Tag(TagNames.FILE_IO)
@Tag(TagNames.UI)
@Tag(TagNames.DIST_SYSTEMS)
@NativeTag
public class HistogramBinTest {


    @Test
    public void testGetBins() throws Exception {
        INDArray array = Nd4j.create(new double[] {0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.0});

        HistogramBin histogram = new HistogramBin.Builder(array).setBinCount(10).build();

        assertEquals(0.1, histogram.getMin(), 0.001);
        assertEquals(1.0, histogram.getMax(), 0.001);

        System.out.println("Result: " + histogram.getBins());

        assertEquals(2, histogram.getBins().getDouble(9), 0.001);
    }

    @Test
    public void testGetData1() throws Exception {
        INDArray array = Nd4j.create(new double[] {0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.0});

        HistogramBin histogram = new HistogramBin.Builder(array).setBinCount(10).build();

        assertEquals(0.1, histogram.getMin(), 0.001);
        assertEquals(1.0, histogram.getMax(), 0.001);

        System.out.println("Result: " + histogram.getData());

        assertEquals(10, histogram.getData().size());
    }

    @Test
    public void testGetData2() throws Exception {
        INDArray array = Nd4j.create(new double[] {-1.0f, -0.50f, 0.0f, 0.50f, 1.0f, -1.0f, -0.50f, 0.0f, 0.50f, 1.0f});

        HistogramBin histogram = new HistogramBin.Builder(array).setBinCount(10).build();

        assertEquals(-1.0, histogram.getMin(), 0.001);
        assertEquals(1.0, histogram.getMax(), 0.001);

        System.out.println("Result: " + histogram.getData());

        assertEquals(10, histogram.getData().size());

        assertEquals(2, histogram.getData().get(new BigDecimal("1.00")).get());
    }

    @Test
    public void testGetData4() throws Exception {
        INDArray array = Nd4j.create(new double[] {-1.0f, -0.50f, 0.0f, 0.50f, 1.0f, -1.0f, -0.50f, 0.0f, 0.50f, 1.0f});

        HistogramBin histogram = new HistogramBin.Builder(array).setBinCount(50).build();

        assertEquals(-1.0, histogram.getMin(), 0.001);
        assertEquals(1.0, histogram.getMax(), 0.001);

        System.out.println("Result: " + histogram.getData());

        assertEquals(50, histogram.getData().size());

        assertEquals(2, histogram.getData().get(new BigDecimal("1.00")).get());
    }
}
