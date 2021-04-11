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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.eigen.Eigen;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.common.util.ArrayUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;


@Slf4j
@NativeTag
public class TestEigen extends BaseNd4jTestWithBackends {

    protected DataType initialType = Nd4j.dataType();

    @BeforeEach
    public void before() {
        Nd4j.setDataType(DataType.DOUBLE);
    }

    @AfterEach
    public void after() {
        Nd4j.setDataType(initialType);
    }

    // test of functions added by Luke Czapla
    // Compares solution of A x = L x  to solution to A x = L B x when it is simple
    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void test2Syev(Nd4jBackend backend) {
        for(DataType dt : new DataType[]{DataType.DOUBLE, DataType.FLOAT, DataType.HALF}) {
            Nd4j.setDefaultDataTypes(dt, dt);

            double[][] matrix = new double[][]{{0.0427, -0.04, 0, 0, 0, 0}, {-0.04, 0.0427, 0, 0, 0, 0},
                    {0, 0.00, 0.0597, 0, 0, 0}, {0, 0, 0, 50, 0, 0}, {0, 0, 0, 0, 50, 0}, {0, 0, 0, 0, 0, 50}};
            INDArray m = Nd4j.create(ArrayUtil.flattenDoubleArray(matrix), new int[]{6, 6});
            INDArray res = Eigen.symmetricGeneralizedEigenvalues(m, true);

            INDArray n = Nd4j.create(ArrayUtil.flattenDoubleArray(matrix), new int[]{6, 6});
            INDArray res2 = Eigen.symmetricGeneralizedEigenvalues(n, Nd4j.eye(6).mul(2.0).castTo(DataType.DOUBLE), true);

            for (int i = 0; i < 6; i++) {
                assertEquals(res.getDouble(i), 2 * res2.getDouble(i), 1e-6);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testSyev(Nd4jBackend backend) {
        for(DataType dt : new DataType[]{DataType.DOUBLE, DataType.FLOAT, DataType.HALF}) {
            //log.info("Datatype: {}", dt);
            Nd4j.setDefaultDataTypes(dt, dt);

            INDArray A = Nd4j.create(new float[][]{{1.96f, -6.49f, -0.47f, -7.20f, -0.65f},
                    {-6.49f, 3.80f, -6.39f, 1.50f, -6.34f}, {-0.47f, -6.39f, 4.17f, -1.51f, 2.67f},
                    {-7.20f, 1.50f, -1.51f, 5.70f, 1.80f}, {-0.65f, -6.34f, 2.67f, 1.80f, -7.10f}});

            INDArray B = A.dup();
            INDArray e = Eigen.symmetricGeneralizedEigenvalues(A);

            for (int i = 0; i < A.rows(); i++) {
                INDArray LHS = B.mmul(A.slice(i, 1).reshape(-1, 1));
                INDArray RHS = A.slice(i, 1).mul(e.getFloat(i));

                for (int j = 0; j < LHS.length(); j++) {
                    assertEquals(LHS.getFloat(j), RHS.getFloat(j), 0.001f);
                }
            }
        }
    }


    @Override
    public char ordering() {
        return 'f';
    }
}
