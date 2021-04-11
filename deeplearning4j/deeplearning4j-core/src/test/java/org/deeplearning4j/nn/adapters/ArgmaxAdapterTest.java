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
package org.deeplearning4j.nn.adapters;

import lombok.val;
import org.deeplearning4j.BaseDL4JTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.factory.Nd4j;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("Argmax Adapter Test")
@NativeTag
@Tag(TagNames.DL4J_OLD_API)
class ArgmaxAdapterTest extends BaseDL4JTest {

    @Test
    @DisplayName("Test Softmax _ 2 D _ 1")
    void testSoftmax_2D_1() {
        val in = new double[][] { { 1, 3, 2 }, { 4, 5, 6 } };
        val adapter = new ArgmaxAdapter();
        val result = adapter.apply(Nd4j.create(in));
        assertArrayEquals(new int[] { 1, 2 }, result);
    }

    @Test
    @DisplayName("Test Softmax _ 1 D _ 1")
    void testSoftmax_1D_1() {
        val in = new double[] { 1, 3, 2 };
        val adapter = new ArgmaxAdapter();
        val result = adapter.apply(Nd4j.create(in));
        assertArrayEquals(new int[] { 1 }, result);
    }
}
