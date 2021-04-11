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

import org.datavec.api.records.Record;
import org.datavec.api.records.metadata.RecordMetaData;
import org.datavec.api.split.CollectionInputSplit;
import org.datavec.api.split.FileSplit;
import org.datavec.api.split.InputSplit;
import org.datavec.api.writable.Writable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.BaseND4JTest;
import org.nd4j.common.io.ClassPathResource;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nd4j.common.tests.tags.TagNames;

@DisplayName("File Record Reader Test")
@Tag(TagNames.JAVA_ONLY)
@Tag(TagNames.FILE_IO)
class FileRecordReaderTest extends BaseND4JTest {

    @Test
    @DisplayName("Test Reset")
    void testReset() throws Exception {
        FileRecordReader rr = new FileRecordReader();
        rr.initialize(new FileSplit(new ClassPathResource("datavec-api/iris.dat").getFile()));
        int nResets = 5;
        for (int i = 0; i < nResets; i++) {
            int lineCount = 0;
            while (rr.hasNext()) {
                List<Writable> line = rr.next();
                assertEquals(1, line.size());
                lineCount++;
            }
            assertFalse(rr.hasNext());
            assertEquals(1, lineCount);
            rr.reset();
        }
    }

    @Test
    @DisplayName("Test Meta")
    void testMeta() throws Exception {
        FileRecordReader rr = new FileRecordReader();
        URI[] arr = new URI[3];
        arr[0] = new ClassPathResource("datavec-api/csvsequence_0.txt").getFile().toURI();
        arr[1] = new ClassPathResource("datavec-api/csvsequence_1.txt").getFile().toURI();
        arr[2] = new ClassPathResource("datavec-api/csvsequence_2.txt").getFile().toURI();
        InputSplit is = new CollectionInputSplit(Arrays.asList(arr));
        rr.initialize(is);
        List<List<Writable>> out = new ArrayList<>();
        while (rr.hasNext()) {
            out.add(rr.next());
        }
        assertEquals(3, out.size());
        rr.reset();
        List<List<Writable>> out2 = new ArrayList<>();
        List<Record> out3 = new ArrayList<>();
        List<RecordMetaData> meta = new ArrayList<>();
        int count = 0;
        while (rr.hasNext()) {
            Record r = rr.nextRecord();
            out2.add(r.getRecord());
            out3.add(r);
            meta.add(r.getMetaData());
            assertEquals(arr[count++], r.getMetaData().getURI());
        }
        assertEquals(out, out2);
        List<Record> fromMeta = rr.loadFromMetaData(meta);
        assertEquals(out3, fromMeta);
    }
}
