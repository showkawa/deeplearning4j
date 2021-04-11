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
package org.datavec.api.records.reader.impl;

import org.apache.commons.io.FileUtils;
import org.datavec.api.records.reader.SequenceRecordReader;
import org.datavec.api.records.reader.impl.csv.CSVMultiSequenceRecordReader;
import org.datavec.api.split.FileSplit;
import org.datavec.api.writable.Text;
import org.datavec.api.writable.Writable;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nd4j.common.tests.BaseND4JTest;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.DisplayName;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nd4j.common.tests.tags.TagNames;

@DisplayName("Csv Multi Sequence Record Reader Test")
@Tag(TagNames.JAVA_ONLY)
@Tag(TagNames.FILE_IO)
class CSVMultiSequenceRecordReaderTest extends BaseND4JTest {

    @TempDir
    public Path testDir;

    @Test
    @DisplayName("Test Concat Mode")
    @Disabled
    void testConcatMode() throws Exception {
        for (int i = 0; i < 3; i++) {
            String seqSep;
            String seqSepRegex;
            switch(i) {
                case 0:
                    seqSep = "";
                    seqSepRegex = "^$";
                    break;
                case 1:
                    seqSep = "---";
                    seqSepRegex = seqSep;
                    break;
                case 2:
                    seqSep = "&";
                    seqSepRegex = seqSep;
                    break;
                default:
                    throw new RuntimeException();
            }
            String str = "a,b,c\n1,2,3,4\nx,y\n" + seqSep + "\nA,B,C";
            File f = testDir.toFile();
            FileUtils.writeStringToFile(f, str, StandardCharsets.UTF_8);
            SequenceRecordReader seqRR = new CSVMultiSequenceRecordReader(seqSepRegex, CSVMultiSequenceRecordReader.Mode.CONCAT);
            seqRR.initialize(new FileSplit(f));
            List<List<Writable>> exp0 = new ArrayList<>();
            for (String s : "a,b,c,1,2,3,4,x,y".split(",")) {
                exp0.add(Collections.<Writable>singletonList(new Text(s)));
            }
            List<List<Writable>> exp1 = new ArrayList<>();
            for (String s : "A,B,C".split(",")) {
                exp1.add(Collections.<Writable>singletonList(new Text(s)));
            }
            assertEquals(exp0, seqRR.sequenceRecord());
            assertEquals(exp1, seqRR.sequenceRecord());
            assertFalse(seqRR.hasNext());
            seqRR.reset();
            assertEquals(exp0, seqRR.sequenceRecord());
            assertEquals(exp1, seqRR.sequenceRecord());
            assertFalse(seqRR.hasNext());
        }
    }

    @Test
    @DisplayName("Test Equal Length")
    @Disabled
    void testEqualLength() throws Exception {
        for (int i = 0; i < 3; i++) {
            String seqSep;
            String seqSepRegex;
            switch(i) {
                case 0:
                    seqSep = "";
                    seqSepRegex = "^$";
                    break;
                case 1:
                    seqSep = "---";
                    seqSepRegex = seqSep;
                    break;
                case 2:
                    seqSep = "&";
                    seqSepRegex = seqSep;
                    break;
                default:
                    throw new RuntimeException();
            }
            String str = "a,b\n1,2\nx,y\n" + seqSep + "\nA\nB\nC";
            File f = testDir.toFile();
            FileUtils.writeStringToFile(f, str, StandardCharsets.UTF_8);
            SequenceRecordReader seqRR = new CSVMultiSequenceRecordReader(seqSepRegex, CSVMultiSequenceRecordReader.Mode.EQUAL_LENGTH);
            seqRR.initialize(new FileSplit(f));
            List<List<Writable>> exp0 = Arrays.asList(Arrays.<Writable>asList(new Text("a"), new Text("1"), new Text("x")), Arrays.<Writable>asList(new Text("b"), new Text("2"), new Text("y")));
            List<List<Writable>> exp1 = Collections.singletonList(Arrays.<Writable>asList(new Text("A"), new Text("B"), new Text("C")));
            assertEquals(exp0, seqRR.sequenceRecord());
            assertEquals(exp1, seqRR.sequenceRecord());
            assertFalse(seqRR.hasNext());
            seqRR.reset();
            assertEquals(exp0, seqRR.sequenceRecord());
            assertEquals(exp1, seqRR.sequenceRecord());
            assertFalse(seqRR.hasNext());
        }
    }

    @Test
    @DisplayName("Test Padding")
    @Disabled
    void testPadding() throws Exception {
        for (int i = 0; i < 3; i++) {
            String seqSep;
            String seqSepRegex;
            switch(i) {
                case 0:
                    seqSep = "";
                    seqSepRegex = "^$";
                    break;
                case 1:
                    seqSep = "---";
                    seqSepRegex = seqSep;
                    break;
                case 2:
                    seqSep = "&";
                    seqSepRegex = seqSep;
                    break;
                default:
                    throw new RuntimeException();
            }
            String str = "a,b\n1\nx\n" + seqSep + "\nA\nB\nC";
            File f = testDir.toFile();
            FileUtils.writeStringToFile(f, str, StandardCharsets.UTF_8);
            SequenceRecordReader seqRR = new CSVMultiSequenceRecordReader(seqSepRegex, CSVMultiSequenceRecordReader.Mode.PAD, new Text("PAD"));
            seqRR.initialize(new FileSplit(f));
            List<List<Writable>> exp0 = Arrays.asList(Arrays.<Writable>asList(new Text("a"), new Text("1"), new Text("x")), Arrays.<Writable>asList(new Text("b"), new Text("PAD"), new Text("PAD")));
            List<List<Writable>> exp1 = Collections.singletonList(Arrays.<Writable>asList(new Text("A"), new Text("B"), new Text("C")));
            assertEquals(exp0, seqRR.sequenceRecord());
            assertEquals(exp1, seqRR.sequenceRecord());
            assertFalse(seqRR.hasNext());
            seqRR.reset();
            assertEquals(exp0, seqRR.sequenceRecord());
            assertEquals(exp1, seqRR.sequenceRecord());
            assertFalse(seqRR.hasNext());
        }
    }
}
