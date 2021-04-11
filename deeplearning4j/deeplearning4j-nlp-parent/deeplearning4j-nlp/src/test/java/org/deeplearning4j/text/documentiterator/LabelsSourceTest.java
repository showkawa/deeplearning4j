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

package org.deeplearning4j.text.documentiterator;

import org.deeplearning4j.BaseDL4JTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
@Tag(TagNames.FILE_IO)
@Tag(TagNames.JAVA_ONLY)
public class LabelsSourceTest extends BaseDL4JTest {

    @BeforeEach
    public void setUp() throws Exception {

    }

    @Test
    public void testNextLabel1() throws Exception {
        LabelsSource generator = new LabelsSource("SENTENCE_");

        assertEquals("SENTENCE_0", generator.nextLabel());
    }

    @Test
    public void testNextLabel2() throws Exception {
        LabelsSource generator = new LabelsSource("SENTENCE_%d_HAHA");

        assertEquals("SENTENCE_0_HAHA", generator.nextLabel());
    }

    @Test
    public void testNextLabel3() throws Exception {
        List<String> list = Arrays.asList("LABEL0", "LABEL1", "LABEL2");
        LabelsSource generator = new LabelsSource(list);

        assertEquals("LABEL0", generator.nextLabel());
    }

    @Test
    public void testLabelsCount1() throws Exception {
        List<String> list = Arrays.asList("LABEL0", "LABEL1", "LABEL2");
        LabelsSource generator = new LabelsSource(list);

        assertEquals("LABEL0", generator.nextLabel());
        assertEquals("LABEL1", generator.nextLabel());
        assertEquals("LABEL2", generator.nextLabel());

        assertEquals(3, generator.getNumberOfLabelsUsed());
    }

    @Test
    public void testLabelsCount2() throws Exception {
        LabelsSource generator = new LabelsSource("SENTENCE_");

        assertEquals("SENTENCE_0", generator.nextLabel());
        assertEquals("SENTENCE_1", generator.nextLabel());
        assertEquals("SENTENCE_2", generator.nextLabel());
        assertEquals("SENTENCE_3", generator.nextLabel());
        assertEquals("SENTENCE_4", generator.nextLabel());

        assertEquals(5, generator.getNumberOfLabelsUsed());
    }

    @Test
    public void testLabelsCount3() throws Exception {
        LabelsSource generator = new LabelsSource("SENTENCE_");

        assertEquals("SENTENCE_0", generator.nextLabel());
        assertEquals("SENTENCE_1", generator.nextLabel());
        assertEquals("SENTENCE_2", generator.nextLabel());
        assertEquals("SENTENCE_3", generator.nextLabel());
        assertEquals("SENTENCE_4", generator.nextLabel());

        assertEquals(5, generator.getNumberOfLabelsUsed());

        generator.reset();

        assertEquals(5, generator.getNumberOfLabelsUsed());
    }
}
