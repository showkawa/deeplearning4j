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

package org.nd4j.parameterserver.distributed.logic;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.nd4j.common.tests.BaseND4JTest;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.parameterserver.distributed.logic.completion.Clipboard;
import org.nd4j.parameterserver.distributed.messages.aggregations.InitializationAggregation;
import org.nd4j.parameterserver.distributed.messages.aggregations.VectorAggregation;
import org.nd4j.parameterserver.distributed.messages.VoidAggregation;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@Disabled
@Deprecated
@Tag(TagNames.FILE_IO)
@Tag(TagNames.DIST_SYSTEMS)
@NativeTag
public class ClipboardTest extends BaseND4JTest {
    @BeforeEach
    public void setUp() throws Exception {

    }

    @AfterEach
    public void tearDown() throws Exception {

    }



    @Test
    public void testPin1() throws Exception {
        Clipboard clipboard = new Clipboard();

        Random rng = new Random(12345L);

        for (int i = 0; i < 100; i++) {
            VectorAggregation aggregation =
                            new VectorAggregation(rng.nextLong(), (short) 100, (short) i, Nd4j.create(5));

            clipboard.pin(aggregation);
        }

        assertEquals(false, clipboard.hasCandidates());
        assertEquals(0, clipboard.getNumberOfCompleteStacks());
        assertEquals(100, clipboard.getNumberOfPinnedStacks());
    }

    @Test
    public void testPin2() throws Exception {
        Clipboard clipboard = new Clipboard();

        Random rng = new Random(12345L);

        Long validId = 123L;

        short shardIdx = 0;
        for (int i = 0; i < 300; i++) {
            VectorAggregation aggregation =
                            new VectorAggregation(rng.nextLong(), (short) 100, (short) 1, Nd4j.create(5));

            // imitating valid
            if (i % 2 == 0 && shardIdx < 100) {
                aggregation.setTaskId(validId);
                aggregation.setShardIndex(shardIdx++);
            }

            clipboard.pin(aggregation);
        }

        VoidAggregation aggregation = clipboard.getStackFromClipboard(0L, validId);
        assertNotEquals(null, aggregation);

        assertEquals(0, aggregation.getMissingChunks());

        assertEquals(true, clipboard.hasCandidates());
        assertEquals(1, clipboard.getNumberOfCompleteStacks());
    }

    /**
     * This test checks how clipboard handles singular aggregations
     * @throws Exception
     */
    @Test
    public void testPin3() throws Exception {
        Clipboard clipboard = new Clipboard();

        Random rng = new Random(12345L);

        Long validId = 123L;
        InitializationAggregation aggregation = new InitializationAggregation(1, 0);
        clipboard.pin(aggregation);

        assertTrue(clipboard.isTracking(0L, aggregation.getTaskId()));
        assertTrue(clipboard.isReady(0L, aggregation.getTaskId()));
    }
}
