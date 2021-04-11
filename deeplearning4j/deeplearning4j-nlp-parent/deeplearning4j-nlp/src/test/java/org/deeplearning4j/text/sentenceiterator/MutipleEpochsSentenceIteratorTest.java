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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.nd4j.common.resources.Resources;
import org.nd4j.common.tests.tags.TagNames;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MutipleEpochsSentenceIteratorTest extends BaseDL4JTest {
    @Test()
    @Timeout(30000)
    @Disabled("Downloads need verification ile hash does not match expected hash: https://dl4jtest.blob.core.windows.net/resources/big/raw_sentences.txt.gzx.v1")
    @Tag(TagNames.NEEDS_VERIFY)
    public void hasNext() throws Exception {
        SentenceIterator iterator = new MutipleEpochsSentenceIterator(
                        new BasicLineIterator(Resources.asFile("big/raw_sentences.txt")), 100);

        int cnt = 0;
        while (iterator.hasNext()) {
            iterator.nextSentence();
            cnt++;
        }

        assertEquals(9716200, cnt);
    }

}
