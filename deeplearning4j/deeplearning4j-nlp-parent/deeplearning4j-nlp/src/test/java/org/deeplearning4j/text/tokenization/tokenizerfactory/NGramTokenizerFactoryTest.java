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

package org.deeplearning4j.text.tokenization.tokenizerfactory;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@Tag(TagNames.FILE_IO)
@NativeTag
public class NGramTokenizerFactoryTest extends BaseDL4JTest {

    @Test
    public void testEmptyLines_1() throws Exception {
        val string = "";
        val tokens = new NGramTokenizerFactory(new DefaultTokenizerFactory(), 1, 2).create(string).getTokens();

        assertEquals(0, tokens.size());
    }

    @Test
    public void testEmptyLines_2() throws Exception {
        val string = "";
        val tf = new NGramTokenizerFactory(new DefaultTokenizerFactory(), 1, 2);
        tf.setTokenPreProcessor(new CommonPreprocessor());
        val tokens = tf.create(string).getTokens();

        assertEquals(0, tokens.size());
    }

    @Test
    public void testEmptyLines_3() throws Exception {
        val string = "\n";
        val tokens = new NGramTokenizerFactory(new DefaultTokenizerFactory(), 1, 2).create(string).getTokens();

        assertEquals(0, tokens.size());
    }

    @Test
    public void testEmptyLines_4() throws Exception {
        val string = "   ";
        val tokens = new NGramTokenizerFactory(new DefaultTokenizerFactory(), 1, 2).create(string).getTokens();

        assertEquals(0, tokens.size());
    }
}