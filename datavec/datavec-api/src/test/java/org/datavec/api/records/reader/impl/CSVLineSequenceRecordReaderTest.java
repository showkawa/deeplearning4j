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
import org.datavec.api.records.reader.impl.csv.CSVLineSequenceRecordReader;
import org.datavec.api.split.FileSplit;
import org.datavec.api.writable.Text;
import org.datavec.api.writable.Writable;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nd4j.common.tests.BaseND4JTest;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nd4j.common.tests.tags.TagNames;

@DisplayName("Csv Line Sequence Record Reader Test")
@Tag(TagNames.JAVA_ONLY)
@Tag(TagNames.FILE_IO)
class CSVLineSequenceRecordReaderTest extends BaseND4JTest {

    @TempDir
    public Path testDir;

    @Test
    @DisplayName("Test")
    void test(@TempDir Path testDir) throws Exception {
        File f = testDir.toFile();
        File source = new File(f, "temp.csv");
        String str = "a,b,c\n1,2,3,4";
        FileUtils.writeStringToFile(source, str, StandardCharsets.UTF_8);
        SequenceRecordReader rr = new CSVLineSequenceRecordReader();
        rr.initialize(new FileSplit(source));
        List<List<Writable>> exp0 = Arrays.asList(Collections.singletonList(new Text("a")), Collections.singletonList(new Text("b")), Collections.<Writable>singletonList(new Text("c")));
        List<List<Writable>> exp1 = Arrays.asList(Collections.singletonList(new Text("1")), Collections.singletonList(new Text("2")), Collections.<Writable>singletonList(new Text("3")), Collections.<Writable>singletonList(new Text("4")));
        for (int i = 0; i < 3; i++) {
            int count = 0;
            while (rr.hasNext()) {
                List<List<Writable>> next = rr.sequenceRecord();
                if (count++ == 0) {
                    assertEquals(exp0, next);
                } else {
                    assertEquals(exp1, next);
                }
            }
            assertEquals(2, count);
            rr.reset();
        }
    }

    @Override
    public long getTimeoutMilliseconds() {
        return Long.MAX_VALUE;
    }
}
