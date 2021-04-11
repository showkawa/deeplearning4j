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

package org.deeplearning4j.text.sentenceiterator;

import org.deeplearning4j.BaseDL4JTest;
import org.nd4j.common.io.ClassPathResource;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class StreamLineIteratorTest extends BaseDL4JTest {

    protected Logger logger = LoggerFactory.getLogger(StreamLineIteratorTest.class);

    @Test
    public void testHasNext() throws Exception {

        ClassPathResource reuters5250 = new ClassPathResource("/reuters/5250");
        File f = reuters5250.getFile();

        StreamLineIterator iterator = new StreamLineIterator.Builder(new FileInputStream(f)).setFetchSize(100).build();

        int cnt = 0;
        while (iterator.hasNext()) {
            String line = iterator.nextSentence();

            assertNotEquals(null, line);
            logger.info("Line: " + line);
            cnt++;
        }

        assertEquals(24, cnt);
    }
}
