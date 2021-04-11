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

package org.deeplearning4j.spark.time;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
@Tag(TagNames.FILE_IO)
@Tag(TagNames.SPARK)
@Tag(TagNames.DIST_SYSTEMS)
@NativeTag
public class TestTimeSource {

    @Test
    public void testTimeSourceNTP() throws Exception {
        TimeSource timeSource = TimeSourceProvider.getInstance();
        assertTrue(timeSource instanceof NTPTimeSource);

        for (int i = 0; i < 10; i++) {
            long systemTime = System.currentTimeMillis();
            long ntpTime = timeSource.currentTimeMillis();
            long offset = ntpTime - systemTime;
//            System.out.println("System: " + systemTime + "\tNTPTimeSource: " + ntpTime + "\tOffset: " + offset);
            Thread.sleep(500);
        }
    }

    @Test
    public void testTimeSourceSystem() throws Exception {
        TimeSource timeSource = TimeSourceProvider.getInstance("org.deeplearning4j.spark.time.SystemClockTimeSource");
        assertTrue(timeSource instanceof SystemClockTimeSource);

        for (int i = 0; i < 10; i++) {
            long systemTime = System.currentTimeMillis();
            long ntpTime = timeSource.currentTimeMillis();
            long offset = ntpTime - systemTime;
//            System.out.println("System: " + systemTime + "\tSystemClockTimeSource: " + ntpTime + "\tOffset: " + offset);
            assertEquals(systemTime, ntpTime, 2); //Should be exact, but we might randomly tick over between one ms and the next
            Thread.sleep(500);
        }
    }

}
