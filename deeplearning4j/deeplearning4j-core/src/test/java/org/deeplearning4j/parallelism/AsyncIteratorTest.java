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
package org.deeplearning4j.parallelism;

import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.core.parallelism.AsyncIterator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;

@DisplayName("Async Iterator Test")
@NativeTag
@Tag(TagNames.DL4J_OLD_API)
class AsyncIteratorTest extends BaseDL4JTest {

    @Test
    @DisplayName("Has Next")
    void hasNext() throws Exception {
        ArrayList<Integer> integers = new ArrayList<>();
        for (int x = 0; x < 100000; x++) {
            integers.add(x);
        }
        AsyncIterator<Integer> iterator = new AsyncIterator<>(integers.iterator(), 512);
        int cnt = 0;
        Integer val = null;
        while (iterator.hasNext()) {
            val = iterator.next();
            assertEquals(cnt, val.intValue());
            cnt++;
        }
        System.out.println("Last val: " + val);
        assertEquals(integers.size(), cnt);
    }
}
