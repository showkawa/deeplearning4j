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

package org.nd4j.linalg.api.indexing;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.BaseNd4jTestWithBackends;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Adam Gibson
 */
@Tag(TagNames.NDARRAY_INDEXING)
@NativeTag
public class IndexingTests extends BaseNd4jTestWithBackends {




    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testINDArrayIndexingEqualToRank(Nd4jBackend backend) {
        INDArray x = Nd4j.linspace(1,6,6, DataType.DOUBLE).reshape('c',3,2).castTo(DataType.DOUBLE);
        INDArray indexes = Nd4j.create(new double[][]{
                {0,1,2},
                {0,1,0}
        });

        INDArray assertion = Nd4j.create(new double[]{1,4,5});
        INDArray getTest = x.get(indexes);
        assertEquals(assertion,getTest);

    }


    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testINDArrayIndexingLessThanRankSimple(Nd4jBackend backend) {
        INDArray x = Nd4j.linspace(1,6,6, DataType.DOUBLE).reshape('c',3,2).castTo(DataType.DOUBLE);
        INDArray indexes = Nd4j.create(new double[][]{
                {0},
        });

        INDArray assertion = Nd4j.create(new double[]{1,2});
        INDArray getTest = x.get(indexes);
        assertEquals(assertion, getTest);
    }



    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testINDArrayIndexingLessThanRankFourDimension(Nd4jBackend backend) {
        INDArray x = Nd4j.linspace(1,16,16, DataType.DOUBLE).reshape('c',2,2,2,2).castTo(DataType.DOUBLE);
        INDArray indexes = Nd4j.create(new double[][]{
                {0},{1}
        });

        INDArray assertion = Nd4j.create(new double[]{5,6,7,8}).reshape('c',1,2,2);
        INDArray getTest = x.get(indexes);
        assertEquals(assertion,getTest);

    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testPutSimple(Nd4jBackend backend) {
        INDArray x = Nd4j.linspace(1,16,16, DataType.DOUBLE).reshape('c',2,2,2,2);
        INDArray indexes = Nd4j.create(new double[][]{
                {0},{1}
        });

        x.put(indexes,Nd4j.create(new double[] {5,5}));
        INDArray vals = Nd4j.valueArrayOf(new long[] {2,2,2,2},5, DataType.DOUBLE);
        assertEquals(vals,x);
    }
    
    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testGetScalar(Nd4jBackend backend) {
        INDArray arr = Nd4j.linspace(1, 5, 5, DataType.DOUBLE);
        INDArray d = arr.get(NDArrayIndex.point(1));
        assertTrue(d.isScalar());
        assertEquals(2.0, d.getDouble(0), 1e-1);

    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testNewAxis(Nd4jBackend backend) {
        INDArray arr = Nd4j.rand(new int[] {4, 2, 3});
        INDArray view = arr.get(NDArrayIndex.newAxis(), NDArrayIndex.all(), NDArrayIndex.point(1));
//        System.out.println(view);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testVectorIndexing(Nd4jBackend backend) {
        INDArray x = Nd4j.linspace(0, 10, 11, DataType.DOUBLE).reshape(1, 11).castTo(DataType.DOUBLE);
        int[] index = new int[] {5, 8, 9};
        INDArray columnsTest = x.getColumns(index);
        assertEquals(Nd4j.create(new double[] {5, 8, 9}, new int[]{1,3}), columnsTest);
        int[] index2 = new int[] {2, 2, 4}; //retrieve the same columns twice
        INDArray columnsTest2 = x.getColumns(index2);
        assertEquals(Nd4j.create(new double[] {2, 2, 4}, new int[]{1,3}), columnsTest2);

    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testGetRowsColumnsMatrix(Nd4jBackend backend) {
        INDArray arr = Nd4j.linspace(1, 24, 24, DataType.DOUBLE).reshape(4, 6);
        INDArray firstAndSecondColumnsAssertion = Nd4j.create(new double[][] {{1, 5}, {2, 6}, {3, 7}, {4, 8}});

//        System.out.println(arr);
        INDArray firstAndSecondColumns = arr.getColumns(0, 1);
        assertEquals(firstAndSecondColumnsAssertion, firstAndSecondColumns);

        INDArray firstAndSecondRows = Nd4j.create(new double[][] {{1.00, 5.00, 9.00, 13.00, 17.00, 21.00},
                {1.00, 5.00, 9.00, 13.00, 17.00, 21.00}, {2.00, 6.00, 10.00, 14.00, 18.00, 22.00}});

        INDArray rows = arr.getRows(0, 0, 1);
        assertEquals(firstAndSecondRows, rows);
    }



    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testSlicing(Nd4jBackend backend) {
        INDArray arange = Nd4j.arange(1, 17).reshape(4, 4).castTo(DataType.DOUBLE);
        INDArray slice1Assert = Nd4j.create(new double[] {2, 6, 10, 14});
        INDArray slice1Test = arange.slice(1);
        assertEquals(slice1Assert, slice1Test);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testArangeMul(Nd4jBackend backend) {
        INDArray arange = Nd4j.arange(1, 17).reshape('f', 4, 4).castTo(DataType.DOUBLE);
        INDArrayIndex index = NDArrayIndex.interval(0, 2);
        INDArray get = arange.get(index, index);
        INDArray zeroPointTwoFive = Nd4j.ones(DataType.DOUBLE, 2, 2).mul(0.25);
        INDArray mul = get.mul(zeroPointTwoFive);
        INDArray assertion = Nd4j.create(new double[][] {{0.25, 1.25}, {0.5, 1.5}}, 'f');
        assertEquals(assertion, mul);

    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testGetIndicesVector(Nd4jBackend backend) {
        INDArray line = Nd4j.linspace(1, 4, 4, DataType.DOUBLE).reshape(1, -1);
        INDArray test = Nd4j.create(new double[] {2, 3});
        INDArray result = line.get(NDArrayIndex.point(0), NDArrayIndex.interval(1, 3));
        assertEquals(test, result);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testGetIndicesVectorView(Nd4jBackend backend) {
        INDArray matrix = Nd4j.linspace(1, 25, 25, DataType.DOUBLE).reshape('c',5, 5);
        INDArray column = matrix.getColumn(0).reshape(1,5);
        INDArray test = Nd4j.create(new double[] {6, 11});
        INDArray result = null; //column.get(NDArrayIndex.point(0), NDArrayIndex.interval(1, 3));
//        assertEquals(test, result);
//
        INDArray column3 = matrix.getColumn(2).reshape(1,5);
//        INDArray exp = Nd4j.create(new double[] {8, 13});
//        result = column3.get(NDArrayIndex.point(0), NDArrayIndex.interval(1, 3));
//        assertEquals(exp, result);

        INDArray exp2 = Nd4j.create(new double[] {8, 18});
        result = column3.get(NDArrayIndex.point(0), NDArrayIndex.interval(1, 2, 4));
        assertEquals(exp2, result);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void test2dGetPoint(Nd4jBackend backend){
        INDArray arr = Nd4j.linspace(1,12,12, DataType.DOUBLE).reshape('c',3,4);
        for( int i=0; i<3; i++ ){
            INDArray exp = Nd4j.create(new double[]{i*4+1, i*4+2, i*4+3, i*4+4});
            INDArray row = arr.getRow(i);
            INDArray get = arr.get(NDArrayIndex.point(i), NDArrayIndex.all());

            assertEquals(1, row.rank());
            assertEquals(1, get.rank());
            assertEquals(exp, row);
            assertEquals(exp, get);
        }

        for( int i = 0; i < 4; i++) {
            INDArray exp = Nd4j.create(new double[]{1+i, 5+i, 9+i});
            INDArray col = arr.getColumn(i);
            INDArray get = arr.get(NDArrayIndex.all(), NDArrayIndex.point(i));

            assertEquals(1, col.rank());
            assertEquals(1, get.rank());
            assertEquals(exp, col);
            assertEquals(exp, get);
        }
    }


    @Override
    public char ordering() {
        return 'f';
    }
}
