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

package org.nd4j.linalg.api.rng;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.BaseNd4jTestWithBackends;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Adam Gibson
 */
@NativeTag
@Tag(TagNames.RNG)
public class RngTests extends BaseNd4jTestWithBackends {

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testRngConstitency(Nd4jBackend backend) {
        Nd4j.getRandom().setSeed(123);
        INDArray arr = Nd4j.rand(1, 5);
        Nd4j.getRandom().setSeed(123);
        INDArray arr2 = Nd4j.rand(1, 5);
        assertEquals(arr, arr2);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testRandomWithOrder(Nd4jBackend backend) {

        Nd4j.getRandom().setSeed(12345);

        int rows = 20;
        int cols = 20;
        int dim2 = 70;

        INDArray arr = Nd4j.rand('c', rows, cols);
        assertArrayEquals(new long[] {rows, cols}, arr.shape());
        assertEquals('c', arr.ordering());
        assertTrue(arr.minNumber().doubleValue() >= 0.0);
        assertTrue(arr.maxNumber().doubleValue() <= 1.0);

        INDArray arr2 = Nd4j.rand('f', rows, cols);
        assertArrayEquals(new long[] {rows, cols}, arr2.shape());
        assertEquals('f', arr2.ordering());
        assertTrue(arr2.minNumber().doubleValue() >= 0.0);
        assertTrue(arr2.maxNumber().doubleValue() <= 1.0);

        INDArray arr3 = Nd4j.rand('c', new int[] {rows, cols, dim2});
        assertArrayEquals(new long[] {rows, cols, dim2}, arr3.shape());
        assertEquals('c', arr3.ordering());
        assertTrue(arr3.minNumber().doubleValue() >= 0.0);
        assertTrue(arr3.maxNumber().doubleValue() <= 1.0);

        INDArray arr4 = Nd4j.rand('f', new int[] {rows, cols, dim2});
        assertArrayEquals(new long[] {rows, cols, dim2}, arr4.shape());
        assertEquals('f', arr4.ordering());
        assertTrue(arr4.minNumber().doubleValue() >= 0.0);
        assertTrue(arr4.maxNumber().doubleValue() <= 1.0);


        INDArray narr = Nd4j.randn('c', rows, cols);
        assertArrayEquals(new long[] {rows, cols}, narr.shape());
        assertEquals('c', narr.ordering());
        assertEquals(0.0, narr.meanNumber().doubleValue(), 0.05);

        INDArray narr2 = Nd4j.randn('f', rows, cols);
        assertArrayEquals(new long[] {rows, cols}, narr2.shape());
        assertEquals('f', narr2.ordering());
        assertEquals(0.0, narr2.meanNumber().doubleValue(), 0.05);

        INDArray narr3 = Nd4j.randn('c', new int[] {rows, cols, dim2});
        assertArrayEquals(new long[] {rows, cols, dim2}, narr3.shape());
        assertEquals('c', narr3.ordering());
        assertEquals(0.0, narr3.meanNumber().doubleValue(), 0.05);

        INDArray narr4 = Nd4j.randn('f', new int[] {rows, cols, dim2});
        assertArrayEquals(new long[] {rows, cols, dim2}, narr4.shape());
        assertEquals('f', narr4.ordering());
        assertEquals(0.0, narr4.meanNumber().doubleValue(), 0.05);

    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testRandomBinomial(Nd4jBackend backend) {
        Nd4j.getRandom().setSeed(12345);
        //silly tests. Just increasing the usage for randomBinomial to stop compiler warnings.
        INDArray x = Nd4j.randomBinomial(10, 0.5, 3,3);
        assertTrue(x.sum().getDouble(0) > 0.0); //silly test. Just increasing th usage for randomBinomial

        x =  Nd4j.randomBinomial(10, 0.5, x);
        assertTrue(x.sum().getDouble(0) > 0.0);

        x = Nd4j.randomExponential(0.5, 3,3);
        assertTrue(x.sum().getDouble(0) > 0.0);

        x = Nd4j.randomExponential(0.5, x);
        assertTrue(x.sum().getDouble(0) > 0.0);
    }


    @Override
    public char ordering() {
        return 'f';
    }

}
