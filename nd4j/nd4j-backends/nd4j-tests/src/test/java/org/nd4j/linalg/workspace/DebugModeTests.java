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

package org.nd4j.linalg.workspace;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.BaseNd4jTestWithBackends;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.memory.conf.WorkspaceConfiguration;
import org.nd4j.linalg.api.memory.enums.AllocationPolicy;
import org.nd4j.linalg.api.memory.enums.DebugMode;
import org.nd4j.linalg.api.memory.enums.LearningPolicy;
import org.nd4j.linalg.api.memory.enums.MirroringPolicy;
import org.nd4j.linalg.api.memory.enums.SpillPolicy;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.linalg.api.memory.abstracts.Nd4jWorkspace;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@Tag(TagNames.WORKSPACES)
@NativeTag
@Execution(ExecutionMode.SAME_THREAD)
public class DebugModeTests extends BaseNd4jTestWithBackends {



    @BeforeEach
    public void turnMeUp() {
        Nd4j.getWorkspaceManager().setDebugMode(DebugMode.DISABLED);
    }

    @AfterEach
    public void turnMeDown() {
        Nd4j.getWorkspaceManager().setDebugMode(DebugMode.DISABLED);
        Nd4j.getMemoryManager().setCurrentWorkspace(null);
        Nd4j.getWorkspaceManager().destroyAllWorkspacesForCurrentThread();
    }

    @Override
    public char ordering() {
        return 'c';
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testDebugMode_1(Nd4jBackend backend) {
        assertEquals(DebugMode.DISABLED, Nd4j.getWorkspaceManager().getDebugMode());

        Nd4j.getWorkspaceManager().setDebugMode(DebugMode.SPILL_EVERYTHING);

        assertEquals(DebugMode.SPILL_EVERYTHING, Nd4j.getWorkspaceManager().getDebugMode());
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testSpillMode_1(Nd4jBackend backend) {
        Nd4j.getWorkspaceManager().setDebugMode(DebugMode.SPILL_EVERYTHING);

        val basicConfig = WorkspaceConfiguration.builder()
                .initialSize(10 * 1024 * 1024).maxSize(10 * 1024 * 1024).overallocationLimit(0.1)
                .policyAllocation(AllocationPolicy.STRICT).policyLearning(LearningPolicy.FIRST_LOOP)
                .policyMirroring(MirroringPolicy.FULL).policySpill(SpillPolicy.EXTERNAL).build();

        try (val ws = (Nd4jWorkspace) Nd4j.getWorkspaceManager().getAndActivateWorkspace(basicConfig, "R_119_1993")) {
            assertEquals(10 * 1024 * 1024L, ws.getCurrentSize());
            assertEquals(0, ws.getDeviceOffset());
            assertEquals(0, ws.getPrimaryOffset());

            val array = Nd4j.create(DataType.DOUBLE, 10, 10).assign(1.0f);
            assertTrue(array.isAttached());

            // nothing should get into workspace
            assertEquals(0, ws.getPrimaryOffset());
            assertEquals(0, ws.getDeviceOffset());

            // array buffer should be spilled now
            assertEquals(10 * 10 * Nd4j.sizeOfDataType(DataType.DOUBLE), ws.getSpilledSize());
        }
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testSpillMode_2(Nd4jBackend backend) {
        Nd4j.getWorkspaceManager().setDebugMode(DebugMode.SPILL_EVERYTHING);

        val basicConfig = WorkspaceConfiguration.builder()
                .initialSize(0).maxSize(10 * 1024 * 1024).overallocationLimit(0.1)
                .policyAllocation(AllocationPolicy.STRICT).policyLearning(LearningPolicy.FIRST_LOOP)
                .policyMirroring(MirroringPolicy.FULL).policySpill(SpillPolicy.EXTERNAL).build();

        try (val ws = (Nd4jWorkspace) Nd4j.getWorkspaceManager().getAndActivateWorkspace(basicConfig, "R_119_1992")) {
            assertEquals(0L, ws.getCurrentSize());
            assertEquals(0, ws.getDeviceOffset());
            assertEquals(0, ws.getPrimaryOffset());

            val array = Nd4j.create(DataType.DOUBLE, 10, 10).assign(1.0f);

            assertTrue(array.isAttached());

            // nothing should get into workspace
            assertEquals(0, ws.getPrimaryOffset());
            assertEquals(0, ws.getDeviceOffset());

            // array buffer should be spilled now
            assertEquals(10 * 10 * Nd4j.sizeOfDataType(DataType.DOUBLE), ws.getSpilledSize());
        }

        try (val ws = (Nd4jWorkspace) Nd4j.getWorkspaceManager().getAndActivateWorkspace(basicConfig, "R_119_1992")) {
            assertEquals(0L, ws.getCurrentSize());
            assertEquals(0, ws.getDeviceOffset());
            assertEquals(0, ws.getPrimaryOffset());
            assertEquals(0, ws.getSpilledSize());
        }
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBypassMode_1(Nd4jBackend backend) {
        Nd4j.getWorkspaceManager().setDebugMode(DebugMode.BYPASS_EVERYTHING);

        val basicConfig = WorkspaceConfiguration.builder()
                .initialSize(0).maxSize(10 * 1024 * 1024).overallocationLimit(0.1)
                .policyAllocation(AllocationPolicy.STRICT).policyLearning(LearningPolicy.FIRST_LOOP)
                .policyMirroring(MirroringPolicy.FULL).policySpill(SpillPolicy.EXTERNAL).build();

        try (val ws = Nd4j.getWorkspaceManager().getAndActivateWorkspace(basicConfig, "R_119_1994")) {

            val array = Nd4j.create(10, 10).assign(1.0f);
            assertFalse(array.isAttached());
        }
    }
}
