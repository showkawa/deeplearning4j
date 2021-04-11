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
import lombok.val;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.linalg.BaseNd4jTestWithBackends;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.memory.MemoryWorkspace;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.api.ops.OpContext;
import org.nd4j.linalg.api.ops.executioner.OpExecutioner;
import org.nd4j.linalg.api.ops.impl.shape.Concat;
import org.nd4j.linalg.api.ops.impl.transforms.strict.Log;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.common.primitives.Pair;
import org.nd4j.linalg.profiler.OpProfiler;
import org.nd4j.linalg.profiler.ProfilerConfig;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@NativeTag
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class OperationProfilerTests extends BaseNd4jTestWithBackends {


    @Override
    public char ordering(){
        return 'c';
    }

    @BeforeEach
    public void setUp() {
        Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.OPERATIONS);
        OpProfiler.getInstance().reset();
    }

    @AfterEach
    public void tearDown() {
        Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.DISABLED);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testCounter1(Nd4jBackend backend) {
        INDArray array = Nd4j.createUninitialized(100);

        array.assign(10f);
        array.divi(2f);

        assertEquals(2, OpProfiler.getInstance().getInvocationsCount());
    }


    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testStack1(Nd4jBackend backend) {

        Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.ALL);

        INDArray array = Nd4j.createUninitialized(100);

        array.assign(10f);
        array.assign(20f);
        array.assign(30f);

        assertEquals(3, OpProfiler.getInstance().getInvocationsCount());

        OpProfiler.getInstance().printOutDashboard();
    }


    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBadCombos1(Nd4jBackend backend) {
        INDArray x = Nd4j.create(100);
        INDArray y = Nd4j.create(100);

        OpProfiler.PenaltyCause[] causes = OpProfiler.getInstance().processOperands(x, y);

        assertEquals(1, causes.length);
        assertTrue(ArrayUtils.contains(causes, OpProfiler.PenaltyCause.NONE));
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBadCombos2(Nd4jBackend backend) {
        INDArray x = Nd4j.create(100).reshape('f', 10, 10);
        INDArray y = Nd4j.create(100).reshape('c', 10, 10);

        OpProfiler.PenaltyCause[] causes = OpProfiler.getInstance().processOperands(x, y);

        assertEquals(1, causes.length);
        assertTrue(ArrayUtils.contains(causes, OpProfiler.PenaltyCause.MIXED_ORDER));
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBadCombos3(Nd4jBackend backend) {
        INDArray x = Nd4j.create(27).reshape('c', 3, 3, 3).tensorAlongDimension(0, 1, 2);
        INDArray y = Nd4j.create(100).reshape('f', 10, 10);

        OpProfiler.PenaltyCause[] causes = OpProfiler.getInstance().processOperands(x, y);

//        log.info("Causes: {}", Arrays.toString(causes));
        assertEquals(1, causes.length);
        assertTrue(ArrayUtils.contains(causes, OpProfiler.PenaltyCause.MIXED_ORDER));
        //assertTrue(ArrayUtils.contains(causes, OpProfiler.PenaltyCause.NON_EWS_ACCESS));
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBadCombos4(Nd4jBackend backend) {
        INDArray x = Nd4j.create(27).reshape('c', 3, 3, 3).tensorAlongDimension(0, 1, 2);
        INDArray y = Nd4j.create(100).reshape('f', 10, 10);
        INDArray z = Nd4j.create(100).reshape('f', 10, 10);

        OpProfiler.PenaltyCause[] causes = OpProfiler.getInstance().processOperands(x, y, z);

//        log.info("Causes: {}", Arrays.toString(causes));
        assertEquals(1, causes.length);
        assertTrue(ArrayUtils.contains(causes, OpProfiler.PenaltyCause.MIXED_ORDER));
        //assertTrue(ArrayUtils.contains(causes, OpProfiler.PenaltyCause.NON_EWS_ACCESS));
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBadCombos5(Nd4jBackend backend) {
        INDArray w = Nd4j.create(100).reshape('c', 10, 10);
        INDArray x = Nd4j.create(100).reshape('c', 10, 10);
        INDArray y = Nd4j.create(100).reshape('f', 10, 10);
        INDArray z = Nd4j.create(100).reshape('c', 10, 10);

        OpProfiler.PenaltyCause[] causes = OpProfiler.getInstance().processOperands(w, x, y, z);

//        log.info("Causes: {}", Arrays.toString(causes));
        assertEquals(1, causes.length);
        assertTrue(ArrayUtils.contains(causes, OpProfiler.PenaltyCause.MIXED_ORDER));
    }

    @Test
    @Disabled
    public void testBadCombos6(Nd4jBackend backend) {
        INDArray x = Nd4j.create(27).reshape('f', 3, 3, 3).slice(1);
        INDArray y = Nd4j.create(100).reshape('f', 10, 10);

        OpProfiler.PenaltyCause[] causes = OpProfiler.getInstance().processOperands(x, y);

//        log.info("Causes: {}", Arrays.toString(causes));
        assertEquals(1, causes.length);
        assertTrue(ArrayUtils.contains(causes, OpProfiler.PenaltyCause.STRIDED_ACCESS));
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBadTad1(Nd4jBackend backend) {
        INDArray x = Nd4j.create(2, 4, 5, 6);

        Pair<DataBuffer, DataBuffer> pair =
                Nd4j.getExecutioner().getTADManager().getTADOnlyShapeInfo(x, 0, 2);

        OpProfiler.PenaltyCause[] causes = OpProfiler.getInstance().processTADOperands(pair.getFirst());

//        log.info("Causes: {}", Arrays.toString(causes));
        assertEquals(1, causes.length);
        assertTrue(ArrayUtils.contains(causes, OpProfiler.PenaltyCause.TAD_NON_EWS_ACCESS));
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBadTad2(Nd4jBackend backend) {
        INDArray x = Nd4j.create(2, 4, 5, 6);

        Pair<DataBuffer, DataBuffer> pair =
                Nd4j.getExecutioner().getTADManager().getTADOnlyShapeInfo(x, 2, 3);

        OpProfiler.PenaltyCause[] causes = OpProfiler.getInstance().processTADOperands(pair.getFirst());

//        log.info("Causes: {}", Arrays.toString(causes));
        assertEquals(1, causes.length);
        assertTrue(ArrayUtils.contains(causes, OpProfiler.PenaltyCause.TAD_NON_EWS_ACCESS));
    }



    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBadTad3(Nd4jBackend backend) {
        INDArray x = Nd4j.create(new int[] {2, 4, 5, 6, 7}, 'f');

        Pair<DataBuffer, DataBuffer> pair =
                Nd4j.getExecutioner().getTADManager().getTADOnlyShapeInfo(x, 0, 2, 4);

        OpProfiler.PenaltyCause[] causes = OpProfiler.getInstance().processTADOperands(pair.getFirst());

//        log.info("Causes: {}", Arrays.toString(causes));
        assertEquals(1, causes.length);
        assertTrue(ArrayUtils.contains(causes, OpProfiler.PenaltyCause.TAD_NON_EWS_ACCESS));
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBadTad4(Nd4jBackend backend) {
        INDArray x = Nd4j.create(DataType.DOUBLE,2, 4, 5, 6);

        Pair<DataBuffer, DataBuffer> pair = Nd4j.getExecutioner().getTADManager().getTADOnlyShapeInfo(x, 3);

        OpProfiler.PenaltyCause[] causes = OpProfiler.getInstance().processTADOperands(pair.getFirst());

//        log.info("TAD: {}", Arrays.toString(pair.getFirst().asInt()));
//        log.info("Causes: {}", Arrays.toString(causes));
        assertEquals(1, causes.length);
        assertTrue(ArrayUtils.contains(causes, OpProfiler.PenaltyCause.NONE));
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBadTad5(Nd4jBackend backend) {
        INDArray x = Nd4j.create(new int[] {2, 4, 5, 6, 7}, 'f');

        Pair<DataBuffer, DataBuffer> pair = Nd4j.getExecutioner().getTADManager().getTADOnlyShapeInfo(x, 4);

        OpProfiler.PenaltyCause[] causes = OpProfiler.getInstance().processTADOperands(pair.getFirst());

//        log.info("TAD: {}", Arrays.toString(pair.getFirst().asInt()));
//        log.info("Causes: {}", Arrays.toString(causes));
        assertEquals(1, causes.length);
        assertTrue(ArrayUtils.contains(causes, OpProfiler.PenaltyCause.TAD_STRIDED_ACCESS));
    }


    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testCxFxF1(Nd4jBackend backend) {
        INDArray a = Nd4j.create(10, 10).reshape('f', 10, 10);
        INDArray b = Nd4j.create(10, 10).reshape('c', 10, 10);
        INDArray c = Nd4j.create(10, 10).reshape('f', 10, 10);

        String ret = OpProfiler.getInstance().processOrders(a, b, c);
        assertEquals("F x C x F", ret);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testCxFxF2(Nd4jBackend backend) {
        INDArray a = Nd4j.create(10, 10).reshape('c', 10, 10);
        INDArray b = Nd4j.create(10, 10).reshape('c', 10, 10);
        INDArray c = Nd4j.create(10, 10).reshape('f', 10, 10);

        String ret = OpProfiler.getInstance().processOrders(a, b, c);
        assertEquals("C x C x F", ret);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testCxFxF3(Nd4jBackend backend) {
        INDArray a = Nd4j.create(10, 10).reshape('c', 10, 10);
        INDArray b = Nd4j.create(10, 10).reshape('c', 10, 10);
        INDArray c = Nd4j.create(10, 10).reshape('c', 10, 10);

        String ret = OpProfiler.getInstance().processOrders(a, b, c);
        assertEquals("C x C x C", ret);
    }


    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBlasFF(Nd4jBackend backend) {
        Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.ALL);

        INDArray a = Nd4j.create(10, 10).reshape('f', 10, 10);
        INDArray b = Nd4j.create(10, 10).reshape('f', 10, 10);

        a.mmul(b);

        OpProfiler.getInstance().printOutDashboard();
    }


    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testNaNPanic1(Nd4jBackend backend) {
        assertThrows(ND4JIllegalStateException.class,() -> {
            Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.NAN_PANIC);

            INDArray a = Nd4j.create(new float[] {1f, 2f, 3f, Float.NaN});

            a.muli(3f);
        });

    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testNaNPanic2(Nd4jBackend backend) {
        assertThrows(ND4JIllegalStateException.class,() -> {
            Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.INF_PANIC);

            INDArray a = Nd4j.create(new float[] {1f, 2f, 3f, Float.POSITIVE_INFINITY});

            a.muli(3f);
        });

    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testNaNPanic3(Nd4jBackend backend) {
        assertThrows(ND4JIllegalStateException.class,() -> {
            Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.ANY_PANIC);

            INDArray a = Nd4j.create(new float[] {1f, 2f, 3f, Float.NEGATIVE_INFINITY});

            a.muli(3f);
        });

    }


    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testScopePanic1(Nd4jBackend backend) {
        assertThrows(ND4JIllegalStateException.class,() -> {
            Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.SCOPE_PANIC);

            INDArray array;

            try (MemoryWorkspace workspace = Nd4j.getWorkspaceManager().getAndActivateWorkspace("WS119")) {
                array = Nd4j.create(10);

                assertTrue(array.isAttached());
            }

            array.add(1.0);
        });

    }


    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testScopePanic2(Nd4jBackend backend) {
        assertThrows(ND4JIllegalStateException.class,() -> {
            Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.SCOPE_PANIC);

            INDArray array;

            try (MemoryWorkspace workspace = Nd4j.getWorkspaceManager().getAndActivateWorkspace("WS120")) {
                array = Nd4j.create(10);
                assertTrue(array.isAttached());

                assertEquals(1, workspace.getGenerationId());
            }


            try (MemoryWorkspace workspace = Nd4j.getWorkspaceManager().getAndActivateWorkspace("WS120")) {
                assertEquals(2, workspace.getGenerationId());

                array.add(1.0);

                assertTrue(array.isAttached());
            }
        });

    }


    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testScopePanic3(Nd4jBackend backend) {
        Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.SCOPE_PANIC);


        INDArray array;

        try (MemoryWorkspace workspace = Nd4j.getWorkspaceManager().getAndActivateWorkspace("WS121")) {
            array = Nd4j.create(10);
            assertTrue(array.isAttached());

            assertEquals(1, workspace.getGenerationId());


            try (MemoryWorkspace workspaceInner = Nd4j.getWorkspaceManager().getAndActivateWorkspace("WS122")) {
                array.add(1.0);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testScopePanicPerf(Nd4jBackend backend) {
        try (MemoryWorkspace workspace = Nd4j.getWorkspaceManager().getAndActivateWorkspace("WS121")) {
            INDArray x = Nd4j.create(1000, 1000).assign(1.0);
            INDArray y = Nd4j.create(1000, 1000).assign(1.0);

            int iterations = 100;

            for (int e = 0; e < iterations; e++) {
                x.addi(y);
            }

            Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.SCOPE_PANIC);

            val nanosC = System.nanoTime();
            for (int e = 0; e < iterations; e++) {
                x.addi(y);
            }
            val nanosD = System.nanoTime();

            val avgB = (nanosD - nanosC) / iterations;


            Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.DISABLED);

            val nanosA = System.nanoTime();
            for (int e = 0; e < iterations; e++) {
                x.addi(y);
            }
            val nanosB = System.nanoTime();

            val avgA = (nanosB - nanosA) / iterations;


//            log.info("A: {}; B: {}", avgA, avgB);
        }
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testExtendedStatistics(Nd4jBackend backend) {
        Nd4j.getExecutioner().setProfilingConfig(ProfilerConfig.builder().nativeStatistics(true).build());

        INDArray array = Nd4j.ones(10);
        val stats = OpProfiler.getInstance().getStatistics();

        assertEquals(10, stats.getCountPositive());
        assertEquals(0, stats.getCountNegative());
        assertEquals(0, stats.getCountZero());
        assertEquals(0, stats.getCountInf());
        assertEquals(0, stats.getCountNaN());
        assertEquals(1.0f, stats.getMeanValue(), 1e-5);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testNanPanic(Nd4jBackend backend) {
        try {
            DynamicCustomOp op = DynamicCustomOp.builder("add")
                    .addInputs(Nd4j.valueArrayOf(10, Double.NaN).castTo(DataType.DOUBLE), Nd4j.scalar(0.0))
                    .addOutputs(Nd4j.create(DataType.DOUBLE, 10))
                    .build();

            Nd4j.getExecutioner().setProfilingConfig(ProfilerConfig.builder().checkForNAN(true).build());
            try {
                Nd4j.exec(op);  //Should trigger NaN panic
                fail();
            } catch (Exception e){
                //throw new RuntimeException(e);
                log.info("Message: {}", e.getMessage());
                assertTrue(e.getMessage().contains("NaN"),e.getMessage());
            }

            INDArray in = op.getInputArgument(0);

            try {
                Transforms.sigmoid(in);
                fail();
            } catch (Exception e){
                assertTrue(e.getMessage().contains("NaN"));
            }
        } finally {
            Nd4j.getExecutioner().setProfilingConfig(ProfilerConfig.builder().checkForNAN(false).build());
        }
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testInfPanic(){
        try {
            DynamicCustomOp op = DynamicCustomOp.builder("add")
                    .addInputs(Nd4j.valueArrayOf(10, Double.POSITIVE_INFINITY).castTo(DataType.DOUBLE), Nd4j.scalar(0.0))
                    .addOutputs(Nd4j.create(DataType.DOUBLE, 10))
                    .build();

            Nd4j.getExecutioner().setProfilingConfig(ProfilerConfig.builder().checkForINF(true).build());
            try {
                Nd4j.exec(op);  //Should trigger NaN panic
                fail();
            } catch (Exception e){
                log.error("",e);
                assertTrue(e.getMessage().contains("Inf"),e.getMessage());
            }

            INDArray in = op.getInputArgument(0);

            try {
                Transforms.max(in, 1.0, false);
                fail();
            } catch (Exception e){
                assertTrue(e.getMessage().contains("Inf"));
            }
        } finally {
            Nd4j.getExecutioner().setProfilingConfig(ProfilerConfig.builder().checkForINF(false).build());
        }
    }


    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testOpProfilerOpContextLegacy(){

        for(boolean nan : new boolean[]{true, false}) {

            INDArray in = Nd4j.valueArrayOf(10, nan ? -1 : 0).castTo(DataType.FLOAT);

            Nd4j.getExecutioner().setProfilingConfig(ProfilerConfig.builder().checkForNAN(nan).checkForINF(!nan).build());

            OpContext oc = Nd4j.getExecutioner().buildContext();
            oc.setInputArray(0, in);
            oc.setOutputArray(0, in.ulike());
            try {
                Nd4j.exec(new Log(), oc);
                System.out.println(oc.getOutputArray(0));
                fail("Expected op profiler exception");
            } catch (Throwable t) {
                //OK
                assertTrue(t.getMessage().contains(nan ? "NaN" : "Inf"),t.getMessage());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testOpProfilerOpContextCustomOp(){

        for(boolean nan : new boolean[]{true, false}) {

            INDArray in = Nd4j.create(DataType.DOUBLE, 10).assign(nan ? Double.NaN : Double.POSITIVE_INFINITY);
            INDArray in2 = in.dup();


            Nd4j.getExecutioner().setProfilingConfig(ProfilerConfig.builder().checkForNAN(nan).checkForINF(!nan).build());

            OpContext oc = Nd4j.getExecutioner().buildContext();
            oc.setIArguments(0);
            oc.setInputArray(0, in);
            oc.setInputArray(1, in2);
            oc.setOutputArray(0, Nd4j.create(DataType.DOUBLE, 20));
            try {
                Nd4j.exec(new Concat(), oc);
                System.out.println(oc.getOutputArray(0));
                fail("Expected op profiler exception");
            } catch (Throwable t) {
                //OK
                assertTrue(t.getMessage().contains(nan ? "NaN" : "Inf"),t.getMessage());
            }
        }
    }
}
