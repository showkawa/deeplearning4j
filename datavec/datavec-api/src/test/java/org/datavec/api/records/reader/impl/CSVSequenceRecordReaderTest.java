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

import org.datavec.api.records.SequenceRecord;
import org.datavec.api.records.metadata.RecordMetaData;
import org.datavec.api.records.reader.SequenceRecordReader;
import org.datavec.api.records.reader.impl.csv.CSVSequenceRecordReader;
import org.datavec.api.split.InputSplit;
import org.datavec.api.split.NumberedFileInputSplit;
import org.datavec.api.writable.Writable;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nd4j.common.tests.BaseND4JTest;
import org.nd4j.common.io.ClassPathResource;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nd4j.common.tests.tags.TagNames;

@DisplayName("Csv Sequence Record Reader Test")
@Tag(TagNames.JAVA_ONLY)
@Tag(TagNames.FILE_IO)
class CSVSequenceRecordReaderTest extends BaseND4JTest {

    @TempDir
    public Path tempDir;

    @Test
    @DisplayName("Test")
    void test() throws Exception {
        CSVSequenceRecordReader seqReader = new CSVSequenceRecordReader(1, ",");
        seqReader.initialize(new TestInputSplit());
        int sequenceCount = 0;
        while (seqReader.hasNext()) {
            List<List<Writable>> sequence = seqReader.sequenceRecord();
            // 4 lines, plus 1 header line
            assertEquals(4, sequence.size());
            Iterator<List<Writable>> timeStepIter = sequence.iterator();
            int lineCount = 0;
            while (timeStepIter.hasNext()) {
                List<Writable> timeStep = timeStepIter.next();
                assertEquals(3, timeStep.size());
                Iterator<Writable> lineIter = timeStep.iterator();
                int countInLine = 0;
                while (lineIter.hasNext()) {
                    Writable entry = lineIter.next();
                    int expValue = 100 * sequenceCount + 10 * lineCount + countInLine;
                    assertEquals(String.valueOf(expValue), entry.toString());
                    countInLine++;
                }
                lineCount++;
            }
            sequenceCount++;
        }
    }

    @Test
    @DisplayName("Test Reset")
    void testReset() throws Exception {
        CSVSequenceRecordReader seqReader = new CSVSequenceRecordReader(1, ",");
        seqReader.initialize(new TestInputSplit());
        int nTests = 5;
        for (int i = 0; i < nTests; i++) {
            seqReader.reset();
            int sequenceCount = 0;
            while (seqReader.hasNext()) {
                List<List<Writable>> sequence = seqReader.sequenceRecord();
                // 4 lines, plus 1 header line
                assertEquals(4, sequence.size());
                Iterator<List<Writable>> timeStepIter = sequence.iterator();
                int lineCount = 0;
                while (timeStepIter.hasNext()) {
                    timeStepIter.next();
                    lineCount++;
                }
                sequenceCount++;
                assertEquals(4, lineCount);
            }
            assertEquals(3, sequenceCount);
        }
    }

    @Test
    @DisplayName("Test Meta Data")
    void testMetaData() throws Exception {
        CSVSequenceRecordReader seqReader = new CSVSequenceRecordReader(1, ",");
        seqReader.initialize(new TestInputSplit());
        List<List<List<Writable>>> l = new ArrayList<>();
        while (seqReader.hasNext()) {
            List<List<Writable>> sequence = seqReader.sequenceRecord();
            // 4 lines, plus 1 header line
            assertEquals(4, sequence.size());
            Iterator<List<Writable>> timeStepIter = sequence.iterator();
            int lineCount = 0;
            while (timeStepIter.hasNext()) {
                timeStepIter.next();
                lineCount++;
            }
            assertEquals(4, lineCount);
            l.add(sequence);
        }
        List<SequenceRecord> l2 = new ArrayList<>();
        List<RecordMetaData> meta = new ArrayList<>();
        seqReader.reset();
        while (seqReader.hasNext()) {
            SequenceRecord sr = seqReader.nextSequence();
            l2.add(sr);
            meta.add(sr.getMetaData());
        }
        assertEquals(3, l2.size());
        List<SequenceRecord> fromMeta = seqReader.loadSequenceFromMetaData(meta);
        for (int i = 0; i < 3; i++) {
            assertEquals(l.get(i), l2.get(i).getSequenceRecord());
            assertEquals(l.get(i), fromMeta.get(i).getSequenceRecord());
        }
    }

    @DisplayName("Test Input Split")
    private static class TestInputSplit implements InputSplit {

        @Override
        public boolean canWriteToLocation(URI location) {
            return false;
        }

        @Override
        public String addNewLocation() {
            return null;
        }

        @Override
        public String addNewLocation(String location) {
            return null;
        }

        @Override
        public void updateSplitLocations(boolean reset) {
        }

        @Override
        public boolean needsBootstrapForWrite() {
            return false;
        }

        @Override
        public void bootStrapForWrite() {
        }

        @Override
        public OutputStream openOutputStreamFor(String location) throws Exception {
            return null;
        }

        @Override
        public InputStream openInputStreamFor(String location) throws Exception {
            return null;
        }

        @Override
        public long length() {
            return 3;
        }

        @Override
        public URI[] locations() {
            URI[] arr = new URI[3];
            try {
                arr[0] = new ClassPathResource("datavec-api/csvsequence_0.txt").getFile().toURI();
                arr[1] = new ClassPathResource("datavec-api/csvsequence_1.txt").getFile().toURI();
                arr[2] = new ClassPathResource("datavec-api/csvsequence_2.txt").getFile().toURI();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return arr;
        }

        @Override
        public Iterator<URI> locationsIterator() {
            return Arrays.asList(locations()).iterator();
        }

        @Override
        public Iterator<String> locationsPathIterator() {
            URI[] loc = locations();
            String[] arr = new String[loc.length];
            for (int i = 0; i < loc.length; i++) {
                arr[i] = loc[i].toString();
            }
            return Arrays.asList(arr).iterator();
        }

        @Override
        public void reset() {
            // No op
        }

        @Override
        public boolean resetSupported() {
            return true;
        }
    }

    @Test
    @DisplayName("Test Csv Seq And Numbered File Split")
    void testCsvSeqAndNumberedFileSplit(@TempDir Path tempDir) throws Exception {
        File baseDir = tempDir.toFile();
        // Simple sanity check unit test
        for (int i = 0; i < 3; i++) {
            new ClassPathResource(String.format("csvsequence_%d.txt", i)).getTempFileFromArchive(baseDir);
        }
        // Load time series from CSV sequence files; compare to SequenceRecordReaderDataSetIterator
        ClassPathResource resource = new ClassPathResource("csvsequence_0.txt");
        String featuresPath = new File(baseDir, "csvsequence_%d.txt").getAbsolutePath();
        SequenceRecordReader featureReader = new CSVSequenceRecordReader(1, ",");
        featureReader.initialize(new NumberedFileInputSplit(featuresPath, 0, 2));
        while (featureReader.hasNext()) {
            featureReader.nextSequence();
        }
    }
}
