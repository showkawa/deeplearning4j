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

package org.nd4j.linalg.crash;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.BaseNd4jTestWithBackends;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.CustomOp;
import org.nd4j.linalg.api.ops.impl.indexaccum.custom.ArgMax;
import org.nd4j.linalg.api.ops.impl.reduce3.ManhattanDistance;
import org.nd4j.linalg.api.ops.impl.transforms.custom.LogSoftMax;
import org.nd4j.linalg.api.ops.impl.transforms.custom.SoftMax;
import org.nd4j.linalg.api.ops.impl.transforms.floating.Sqrt;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.linalg.indexing.BooleanIndexing;
import org.nd4j.linalg.indexing.conditions.Conditions;

@Slf4j

@Disabled
@NativeTag
@Tag(TagNames.NDARRAY_INDEXING)
public class CrashTest extends BaseNd4jTestWithBackends {

    private static final int ITERATIONS = 10;
    private static final boolean[] paramsA = new boolean[] {true, false};
    private static final boolean[] paramsB = new boolean[] {true, false};


    /**
     * tensorAlongDimension() produces shapeInfo without EWS defined
     */
    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testNonEWSViews1(Nd4jBackend backend) {
        log.debug("non-EWS 1");
        INDArray x = Nd4j.create(64, 1024, 64);
        INDArray y = Nd4j.create(64, 64, 1024);

        for (int i = 0; i < ITERATIONS; i++) {
            int slice = RandomUtils.nextInt(0, (int) x.size(0));
            op(x.tensorAlongDimension(slice, 1, 2), y.tensorAlongDimension(slice, 1, 2), i);
        }
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testNonEWSViews2(Nd4jBackend backend) {
        log.debug("non-EWS 2");
        INDArray x = Nd4j.create(new int[] {64, 1024, 64}, 'f');
        INDArray y = Nd4j.create(new int[] {64, 64, 1024}, 'f');

        for (int i = 0; i < ITERATIONS; i++) {
            int slice = RandomUtils.nextInt(0, (int) x.size(0));
            op(x.tensorAlongDimension(slice, 1, 2), y.tensorAlongDimension(slice, 1, 2), i);
        }
    }

    /**
     * slice() produces shapeInfo with EWS being 1 in our case
     */
    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testEWSViews1(Nd4jBackend backend) {
        log.debug("EWS 1");
        INDArray x = Nd4j.create(64, 1024, 64);
        INDArray y = Nd4j.create(64, 64, 1024);

        for (int i = 0; i < ITERATIONS; i++) {
            long slice = RandomUtils.nextLong(0, x.shape()[0]);
            op(x.slice(slice), y.slice(slice), i);
        }
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testEWSViews2(Nd4jBackend backend) {
        log.debug("EWS 2");
        INDArray x = Nd4j.create(new int[] {96, 1024, 64}, 'f');
        INDArray y = Nd4j.create(new int[] {96, 64, 1024}, 'f');

        for (int i = 0; i < 1; i++) {
            int slice = 0; //RandomUtils.nextInt(0, x.shape()[0]);
            op(x.slice(slice), y.slice(slice), i);
        }
    }

    protected void op(INDArray x, INDArray y, int i) {
        // broadcast along row & column
        INDArray row = Nd4j.ones(64);
        INDArray column = Nd4j.ones(1024, 1);

        x.addiRowVector(row);
        x.addiColumnVector(column);

        // casual scalar
        x.addi(i * 2);

        // reduction along all dimensions
        float sum = x.sumNumber().floatValue();

        // index reduction
        Nd4j.getExecutioner().exec(new ArgMax(x));

        // casual transform
        Nd4j.getExecutioner().exec(new Sqrt(x, x));

        //  dup
        INDArray x1 = x.dup(x.ordering());
        INDArray x2 = x.dup(x.ordering());
        INDArray x3 = x.dup('c');
        INDArray x4 = x.dup('f');


        // vstack && hstack
        INDArray vstack = Nd4j.vstack(x, x1, x2, x3, x4);

        INDArray hstack = Nd4j.hstack(x, x1, x2, x3, x4);

        // reduce3 call
        Nd4j.getExecutioner().exec(new ManhattanDistance(x, x2));


        // flatten call
        INDArray flat = Nd4j.toFlattened(x, x1, x2, x3, x4);


        // reduction along dimension: row & column
        INDArray max_0 = x.max(0);
        INDArray max_1 = x.max(1);


        // index reduction along dimension: row & column
        INDArray imax_0 = Nd4j.argMax(x, 0);
        INDArray imax_1 = Nd4j.argMax(x, 1);


        // logisoftmax, softmax & softmax derivative
        Nd4j.getExecutioner().exec((CustomOp) new SoftMax(x));
        Nd4j.getExecutioner().exec((CustomOp) new LogSoftMax(x));


        // BooleanIndexing
        BooleanIndexing.replaceWhere(x, 5f, Conditions.lessThan(8f));

        // assing on view
        BooleanIndexing.assignIf(x, x1, Conditions.greaterThan(-1000000000f));

        // std var along all dimensions
        float std = x.stdNumber().floatValue();

        // std var along row & col
        INDArray xStd_0 = x.std(0);
        INDArray xStd_1 = x.std(1);

        // blas call
        float dot = (float) Nd4j.getBlasWrapper().dot(x, x1);

        // mmul
        for (boolean tA : paramsA) {
            for (boolean tB : paramsB) {

                INDArray xT = tA ? x.dup() : x.dup().transpose();
                INDArray yT = tB ? y.dup() : y.dup().transpose();

                Nd4j.gemm(xT, yT, tA, tB);
            }
        }

        // specially for views, checking here without dup and rollover
        Nd4j.gemm(x, y, false, false);

        log.debug("Iteration passed: " + i);
    }

    @Override
    public char ordering() {
        return 'c';
    }
}
