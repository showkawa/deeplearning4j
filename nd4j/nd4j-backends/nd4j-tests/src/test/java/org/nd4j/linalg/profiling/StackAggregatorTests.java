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

package org.nd4j.linalg.profiling;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.linalg.BaseNd4jTestWithBackends;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.executioner.OpExecutioner;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.linalg.profiler.OpProfiler;
import org.nd4j.linalg.profiler.ProfilerConfig;
import org.nd4j.linalg.profiler.data.StackAggregator;
import org.nd4j.linalg.profiler.data.primitives.StackDescriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@NativeTag
public class StackAggregatorTests extends BaseNd4jTestWithBackends {


    @Override
    public char ordering(){
        return 'c';
    }

    @BeforeEach
    public void setUp() {
        Nd4j.getExecutioner().setProfilingConfig(ProfilerConfig.builder().stackTrace(true).build());
        Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.ALL);
        OpProfiler.getInstance().reset();
    }

    @AfterEach
    public void tearDown() {
        Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.DISABLED);
    }


    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBasicBranching1(Nd4jBackend backend) {
        StackAggregator aggregator = new StackAggregator();

        aggregator.incrementCount();

        aggregator.incrementCount();

        assertEquals(2, aggregator.getTotalEventsNumber());
        assertEquals(2, aggregator.getUniqueBranchesNumber());
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBasicBranching2(Nd4jBackend backend) {
        StackAggregator aggregator = new StackAggregator();

        for (int i = 0; i < 10; i++) {
            aggregator.incrementCount();
        }

        assertEquals(10, aggregator.getTotalEventsNumber());

        // simnce method is called in loop, there should be only 1 unique code branch
        assertEquals(1, aggregator.getUniqueBranchesNumber());
    }


    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testTrailingFrames1(Nd4jBackend backend) {
        StackAggregator aggregator = new StackAggregator();
        aggregator.incrementCount();


        StackDescriptor descriptor = aggregator.getLastDescriptor();

        log.info("Trace: {}", descriptor.toString());

        // we just want to make sure that OpProfiler methods are NOT included in trace
        assertTrue(descriptor.getStackTrace()[descriptor.size() - 1].getClassName().contains("StackAggregatorTests"));
    }

    /*@ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testTrailingFrames2(Nd4jBackend backend) {
        INDArray x = Nd4j.create(new int[] {10, 10}, 'f');
        INDArray y = Nd4j.create(new int[] {10, 10}, 'c');

        x.assign(y);


        x.assign(y);

        Nd4j.getExecutioner().commit();

        StackAggregator aggregator = OpProfiler.getInstance().getMixedOrderAggregator();

        StackDescriptor descriptor = aggregator.getLastDescriptor();

        log.info("Trace: {}", descriptor.toString());

        assertEquals(2, aggregator.getTotalEventsNumber());
        assertEquals(2, aggregator.getUniqueBranchesNumber());

        aggregator.renderTree();
    }*/

    @Test
    @Disabled
    public void testScalarAggregator(Nd4jBackend backend) {
        INDArray x = Nd4j.create(10);

        x.putScalar(0, 1.0);

        double x_0 = x.getDouble(0);

        assertEquals(1.0, x_0, 1e-5);

        StackAggregator aggregator = OpProfiler.getInstance().getScalarAggregator();

        assertEquals(2, aggregator.getTotalEventsNumber());
        assertEquals(2, aggregator.getUniqueBranchesNumber());

        aggregator.renderTree(false);
    }
}
