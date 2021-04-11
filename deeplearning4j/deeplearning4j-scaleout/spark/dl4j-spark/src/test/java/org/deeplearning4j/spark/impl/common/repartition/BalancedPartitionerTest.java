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

package org.deeplearning4j.spark.impl.common.repartition;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag(TagNames.FILE_IO)
@Tag(TagNames.SPARK)
@Tag(TagNames.DIST_SYSTEMS)
@NativeTag
public class BalancedPartitionerTest {


    @Test
    public void balancedPartitionerFirstElements() {
        BalancedPartitioner bp = new BalancedPartitioner(10, 10, 0);
        // the 10 first elements should go in the 1st partition
        for (int i = 0; i < 10; i++) {
            int p = bp.getPartition(i);
            assertEquals(0, p,"Found wrong partition output " + p + ", not 0");
        }
    }

    @Test
    public void balancedPartitionerFirstElementsWithRemainder() {
        BalancedPartitioner bp = new BalancedPartitioner(10, 10, 1);
        // the 10 first elements should go in the 1st partition
        for (int i = 0; i < 10; i++) {
            int p = bp.getPartition(i);
            assertEquals( 0, p,"Found wrong partition output " + p + ", not 0");
        }
    }

    @Test
    public void balancedPartitionerDoesBalance() {
        BalancedPartitioner bp = new BalancedPartitioner(10, 10, 0);
        int[] countPerPartition = new int[10];
        for (int i = 0; i < 10 * 10; i++) {
            int p = bp.getPartition(i);
            countPerPartition[p] += 1;
        }
        for (int i = 0; i < 10; i++) {
            assertEquals(10, countPerPartition[i]);
        }
    }

    @Test
    public void balancedPartitionerDoesBalanceWithRemainder() {
        BalancedPartitioner bp = new BalancedPartitioner(10, 10, 7);
        int[] countPerPartition = new int[10];
        for (int i = 0; i < 10 * 10 + 7; i++) {
            int p = bp.getPartition(i);
            countPerPartition[p] += 1;
        }
        for (int i = 0; i < 10; i++) {
            if (i < 7)
                assertEquals(10 + 1, countPerPartition[i]);
            else
                assertEquals(10, countPerPartition[i]);
        }
    }

}
