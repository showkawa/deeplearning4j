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

package org.nd4j.linalg;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.buffer.util.DataTypeUtil;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@NativeTag
public class AveragingTests extends BaseNd4jTestWithBackends {
    private final int THREADS = 16;
    private final int LENGTH = 51200 * 4;




    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void shutUp() {
    }



    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testSingleDeviceAveraging1(Nd4jBackend backend) {
        INDArray array1 = Nd4j.valueArrayOf(LENGTH, 1.0);
        INDArray array2 = Nd4j.valueArrayOf(LENGTH, 2.0);
        INDArray array3 = Nd4j.valueArrayOf(LENGTH, 3.0);
        INDArray array4 = Nd4j.valueArrayOf(LENGTH, 4.0);
        INDArray array5 = Nd4j.valueArrayOf(LENGTH, 5.0);
        INDArray array6 = Nd4j.valueArrayOf(LENGTH, 6.0);
        INDArray array7 = Nd4j.valueArrayOf(LENGTH, 7.0);
        INDArray array8 = Nd4j.valueArrayOf(LENGTH, 8.0);
        INDArray array9 = Nd4j.valueArrayOf(LENGTH, 9.0);
        INDArray array10 = Nd4j.valueArrayOf(LENGTH, 10.0);
        INDArray array11 = Nd4j.valueArrayOf(LENGTH, 11.0);
        INDArray array12 = Nd4j.valueArrayOf(LENGTH, 12.0);
        INDArray array13 = Nd4j.valueArrayOf(LENGTH, 13.0);
        INDArray array14 = Nd4j.valueArrayOf(LENGTH, 14.0);
        INDArray array15 = Nd4j.valueArrayOf(LENGTH, 15.0);
        INDArray array16 = Nd4j.valueArrayOf(LENGTH, 16.0);


        long time1 = System.currentTimeMillis();
        INDArray arrayMean = Nd4j.averageAndPropagate(new INDArray[] {array1, array2, array3, array4, array5, array6,
                        array7, array8, array9, array10, array11, array12, array13, array14, array15, array16});
        long time2 = System.currentTimeMillis();
        System.out.println("Execution time: " + (time2 - time1));

        assertNotEquals(null, arrayMean);

        assertEquals(8.5f, arrayMean.getFloat(12), 0.1f);
        assertEquals(8.5f, arrayMean.getFloat(150), 0.1f);
        assertEquals(8.5f, arrayMean.getFloat(475), 0.1f);


        assertEquals(8.5f, array1.getFloat(475), 0.1f);
        assertEquals(8.5f, array2.getFloat(475), 0.1f);
        assertEquals(8.5f, array3.getFloat(475), 0.1f);
        assertEquals(8.5f, array5.getFloat(475), 0.1f);
        assertEquals(8.5f, array16.getFloat(475), 0.1f);


        assertEquals(8.5, arrayMean.meanNumber().doubleValue(), 0.01);
        assertEquals(8.5, array1.meanNumber().doubleValue(), 0.01);
        assertEquals(8.5, array2.meanNumber().doubleValue(), 0.01);

        assertEquals(arrayMean, array16);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    @Execution(ExecutionMode.SAME_THREAD)
    public void testSingleDeviceAveraging2(Nd4jBackend backend) {
        INDArray exp = Nd4j.linspace(1, LENGTH, LENGTH);
        List<INDArray> arrays = new ArrayList<>();
        for (int i = 0; i < THREADS; i++)
            arrays.add(exp.dup());

        INDArray mean = Nd4j.averageAndPropagate(arrays);

        assertEquals(exp, mean);

        for (int i = 0; i < THREADS; i++)
            assertEquals(exp, arrays.get(i));
    }



    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testAccumulation1(Nd4jBackend backend) {
        INDArray array1 = Nd4j.create(100).assign(1.0);
        INDArray array2 = Nd4j.create(100).assign(2.0);
        INDArray array3 = Nd4j.create(100).assign(3.0);
        INDArray exp = Nd4j.create(100).assign(6.0);

        INDArray accum = Nd4j.accumulate(array1, array2, array3);

        assertEquals(exp, accum);
    }


    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testAccumulation2(Nd4jBackend backend) {
        INDArray array1 = Nd4j.create(100).assign(1.0);
        INDArray array2 = Nd4j.create(100).assign(2.0);
        INDArray array3 = Nd4j.create(100).assign(3.0);
        INDArray target = Nd4j.create(100);
        INDArray exp = Nd4j.create(100).assign(6.0);

        INDArray accum = Nd4j.accumulate(target, new INDArray[] {array1, array2, array3});

        assertEquals(exp, accum);
        assertTrue(accum == target);
    }


    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testAccumulation3(Nd4jBackend backend) {
        // we want to ensure that cuda backend is able to launch this op on cpu
        Nd4j.getAffinityManager().allowCrossDeviceAccess(false);

        INDArray array1 = Nd4j.create(100).assign(1.0);
        INDArray array2 = Nd4j.create(100).assign(2.0);
        INDArray array3 = Nd4j.create(100).assign(3.0);
        INDArray target = Nd4j.create(100);
        INDArray exp = Nd4j.create(100).assign(6.0);

        INDArray accum = Nd4j.accumulate(target, new INDArray[] {array1, array2, array3});

        assertEquals(exp, accum);
        assertTrue(accum == target);

        Nd4j.getAffinityManager().allowCrossDeviceAccess(true);
    }

    @Override
    public char ordering() {
        return 'c';
    }
}
