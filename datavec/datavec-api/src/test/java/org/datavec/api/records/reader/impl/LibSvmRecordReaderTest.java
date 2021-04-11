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

import org.datavec.api.conf.Configuration;
import org.datavec.api.records.reader.impl.misc.LibSvmRecordReader;
import org.datavec.api.records.reader.impl.misc.SVMLightRecordReader;
import org.datavec.api.split.FileSplit;
import org.datavec.api.writable.DoubleWritable;
import org.datavec.api.writable.IntWritable;
import org.datavec.api.writable.Writable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.BaseND4JTest;
import org.nd4j.common.io.ClassPathResource;
import java.io.IOException;
import java.util.*;
import static org.datavec.api.records.reader.impl.misc.LibSvmRecordReader.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nd4j.common.tests.tags.TagNames;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Lib Svm Record Reader Test")
@Tag(TagNames.JAVA_ONLY)
@Tag(TagNames.FILE_IO)
class LibSvmRecordReaderTest extends BaseND4JTest {

    @Test
    @DisplayName("Test Basic Record")
    void testBasicRecord() throws IOException, InterruptedException {
        Map<Integer, List<Writable>> correct = new HashMap<>();
        // 7 2:1 4:2 6:3 8:4 10:5
        correct.put(0, Arrays.asList(ZERO, ONE, ZERO, new DoubleWritable(2), ZERO, new DoubleWritable(3), ZERO, new DoubleWritable(4), ZERO, new DoubleWritable(5), new IntWritable(7)));
        // 2 qid:42 1:0.1 2:2 6:6.6 8:80
        correct.put(1, Arrays.asList(new DoubleWritable(0.1), new DoubleWritable(2), ZERO, ZERO, ZERO, new DoubleWritable(6.6), ZERO, new DoubleWritable(80), ZERO, ZERO, new IntWritable(2)));
        // 33
        correct.put(2, Arrays.asList(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, new IntWritable(33)));
        LibSvmRecordReader rr = new LibSvmRecordReader();
        Configuration config = new Configuration();
        config.setBoolean(LibSvmRecordReader.ZERO_BASED_INDEXING, false);
        config.setBoolean(LibSvmRecordReader.APPEND_LABEL, true);
        config.setInt(LibSvmRecordReader.NUM_FEATURES, 10);
        rr.initialize(config, new FileSplit(new ClassPathResource("datavec-api/svmlight/basic.txt").getFile()));
        int i = 0;
        while (rr.hasNext()) {
            List<Writable> record = rr.next();
            assertEquals(correct.get(i), record);
            i++;
        }
        assertEquals(i, correct.size());
    }

    @Test
    @DisplayName("Test No Append Label")
    void testNoAppendLabel() throws IOException, InterruptedException {
        Map<Integer, List<Writable>> correct = new HashMap<>();
        // 7 2:1 4:2 6:3 8:4 10:5
        correct.put(0, Arrays.asList(ZERO, ONE, ZERO, new DoubleWritable(2), ZERO, new DoubleWritable(3), ZERO, new DoubleWritable(4), ZERO, new DoubleWritable(5)));
        // 2 qid:42 1:0.1 2:2 6:6.6 8:80
        correct.put(1, Arrays.asList(new DoubleWritable(0.1), new DoubleWritable(2), ZERO, ZERO, ZERO, new DoubleWritable(6.6), ZERO, new DoubleWritable(80), ZERO, ZERO));
        // 33
        correct.put(2, Arrays.asList(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO));
        SVMLightRecordReader rr = new SVMLightRecordReader();
        Configuration config = new Configuration();
        config.setBoolean(SVMLightRecordReader.ZERO_BASED_INDEXING, false);
        config.setInt(SVMLightRecordReader.NUM_FEATURES, 10);
        config.setBoolean(SVMLightRecordReader.APPEND_LABEL, false);
        rr.initialize(config, new FileSplit(new ClassPathResource("datavec-api/svmlight/basic.txt").getFile()));
        int i = 0;
        while (rr.hasNext()) {
            List<Writable> record = rr.next();
            assertEquals(correct.get(i), record);
            i++;
        }
        assertEquals(i, correct.size());
    }

    @Test
    @DisplayName("Test No Label")
    void testNoLabel() throws IOException, InterruptedException {
        Map<Integer, List<Writable>> correct = new HashMap<>();
        // 2:1 4:2 6:3 8:4 10:5
        correct.put(0, Arrays.asList(ZERO, ONE, ZERO, new DoubleWritable(2), ZERO, new DoubleWritable(3), ZERO, new DoubleWritable(4), ZERO, new DoubleWritable(5)));
        // qid:42 1:0.1 2:2 6:6.6 8:80
        correct.put(1, Arrays.asList(new DoubleWritable(0.1), new DoubleWritable(2), ZERO, ZERO, ZERO, new DoubleWritable(6.6), ZERO, new DoubleWritable(80), ZERO, ZERO));
        // 1:1.0
        correct.put(2, Arrays.asList(new DoubleWritable(1.0), ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO));
        // 
        correct.put(3, Arrays.asList(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO));
        SVMLightRecordReader rr = new SVMLightRecordReader();
        Configuration config = new Configuration();
        config.setBoolean(SVMLightRecordReader.ZERO_BASED_INDEXING, false);
        config.setInt(SVMLightRecordReader.NUM_FEATURES, 10);
        config.setBoolean(SVMLightRecordReader.APPEND_LABEL, true);
        rr.initialize(config, new FileSplit(new ClassPathResource("datavec-api/svmlight/noLabels.txt").getFile()));
        int i = 0;
        while (rr.hasNext()) {
            List<Writable> record = rr.next();
            assertEquals(correct.get(i), record);
            i++;
        }
        assertEquals(i, correct.size());
    }

    @Test
    @DisplayName("Test Multioutput Record")
    void testMultioutputRecord() throws IOException, InterruptedException {
        Map<Integer, List<Writable>> correct = new HashMap<>();
        // 7 2.45,9 2:1 4:2 6:3 8:4 10:5
        correct.put(0, Arrays.asList(ZERO, ONE, ZERO, new DoubleWritable(2), ZERO, new DoubleWritable(3), ZERO, new DoubleWritable(4), ZERO, new DoubleWritable(5), new IntWritable(7), new DoubleWritable(2.45), new IntWritable(9)));
        // 2,3,4 qid:42 1:0.1 2:2 6:6.6 8:80
        correct.put(1, Arrays.asList(new DoubleWritable(0.1), new DoubleWritable(2), ZERO, ZERO, ZERO, new DoubleWritable(6.6), ZERO, new DoubleWritable(80), ZERO, ZERO, new IntWritable(2), new IntWritable(3), new IntWritable(4)));
        // 33,32.0,31.9
        correct.put(2, Arrays.asList(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, new IntWritable(33), new DoubleWritable(32.0), new DoubleWritable(31.9)));
        LibSvmRecordReader rr = new LibSvmRecordReader();
        Configuration config = new Configuration();
        config.setBoolean(LibSvmRecordReader.ZERO_BASED_INDEXING, false);
        config.setBoolean(LibSvmRecordReader.APPEND_LABEL, true);
        config.setInt(LibSvmRecordReader.NUM_FEATURES, 10);
        rr.initialize(config, new FileSplit(new ClassPathResource("datavec-api/svmlight/multioutput.txt").getFile()));
        int i = 0;
        while (rr.hasNext()) {
            List<Writable> record = rr.next();
            assertEquals(correct.get(i), record);
            i++;
        }
        assertEquals(i, correct.size());
    }

    @Test
    @DisplayName("Test Multilabel Record")
    void testMultilabelRecord() throws IOException, InterruptedException {
        Map<Integer, List<Writable>> correct = new HashMap<>();
        // 1,3 2:1 4:2 6:3 8:4 10:5
        correct.put(0, Arrays.asList(ZERO, ONE, ZERO, new DoubleWritable(2), ZERO, new DoubleWritable(3), ZERO, new DoubleWritable(4), ZERO, new DoubleWritable(5), LABEL_ONE, LABEL_ZERO, LABEL_ONE, LABEL_ZERO));
        // 2 qid:42 1:0.1 2:2 6:6.6 8:80
        correct.put(1, Arrays.asList(new DoubleWritable(0.1), new DoubleWritable(2), ZERO, ZERO, ZERO, new DoubleWritable(6.6), ZERO, new DoubleWritable(80), ZERO, ZERO, LABEL_ZERO, LABEL_ONE, LABEL_ZERO, LABEL_ZERO));
        // 1,2,4
        correct.put(2, Arrays.asList(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, LABEL_ONE, LABEL_ONE, LABEL_ZERO, LABEL_ONE));
        // 1:1.0
        correct.put(3, Arrays.asList(new DoubleWritable(1.0), ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, LABEL_ZERO, LABEL_ZERO, LABEL_ZERO, LABEL_ZERO));
        // 
        correct.put(4, Arrays.asList(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, LABEL_ZERO, LABEL_ZERO, LABEL_ZERO, LABEL_ZERO));
        LibSvmRecordReader rr = new LibSvmRecordReader();
        Configuration config = new Configuration();
        config.setBoolean(LibSvmRecordReader.ZERO_BASED_INDEXING, false);
        config.setBoolean(LibSvmRecordReader.APPEND_LABEL, true);
        config.setInt(LibSvmRecordReader.NUM_FEATURES, 10);
        config.setBoolean(LibSvmRecordReader.MULTILABEL, true);
        config.setInt(LibSvmRecordReader.NUM_LABELS, 4);
        rr.initialize(config, new FileSplit(new ClassPathResource("datavec-api/svmlight/multilabel.txt").getFile()));
        int i = 0;
        while (rr.hasNext()) {
            List<Writable> record = rr.next();
            assertEquals(correct.get(i), record);
            i++;
        }
        assertEquals(i, correct.size());
    }

    @Test
    @DisplayName("Test Zero Based Indexing")
    void testZeroBasedIndexing() throws IOException, InterruptedException {
        Map<Integer, List<Writable>> correct = new HashMap<>();
        // 1,3 2:1 4:2 6:3 8:4 10:5
        correct.put(0, Arrays.asList(ZERO, ZERO, ONE, ZERO, new DoubleWritable(2), ZERO, new DoubleWritable(3), ZERO, new DoubleWritable(4), ZERO, new DoubleWritable(5), LABEL_ZERO, LABEL_ONE, LABEL_ZERO, LABEL_ONE, LABEL_ZERO));
        // 2 qid:42 1:0.1 2:2 6:6.6 8:80
        correct.put(1, Arrays.asList(ZERO, new DoubleWritable(0.1), new DoubleWritable(2), ZERO, ZERO, ZERO, new DoubleWritable(6.6), ZERO, new DoubleWritable(80), ZERO, ZERO, LABEL_ZERO, LABEL_ZERO, LABEL_ONE, LABEL_ZERO, LABEL_ZERO));
        // 1,2,4
        correct.put(2, Arrays.asList(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, LABEL_ZERO, LABEL_ONE, LABEL_ONE, LABEL_ZERO, LABEL_ONE));
        // 1:1.0
        correct.put(3, Arrays.asList(ZERO, new DoubleWritable(1.0), ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, LABEL_ZERO, LABEL_ZERO, LABEL_ZERO, LABEL_ZERO, LABEL_ZERO));
        // 
        correct.put(4, Arrays.asList(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, LABEL_ZERO, LABEL_ZERO, LABEL_ZERO, LABEL_ZERO, LABEL_ZERO));
        LibSvmRecordReader rr = new LibSvmRecordReader();
        Configuration config = new Configuration();
        // Zero-based indexing is default
        // NOT STANDARD!
        config.setBoolean(SVMLightRecordReader.ZERO_BASED_LABEL_INDEXING, true);
        config.setBoolean(LibSvmRecordReader.APPEND_LABEL, true);
        config.setInt(LibSvmRecordReader.NUM_FEATURES, 11);
        config.setBoolean(LibSvmRecordReader.MULTILABEL, true);
        config.setInt(LibSvmRecordReader.NUM_LABELS, 5);
        rr.initialize(config, new FileSplit(new ClassPathResource("datavec-api/svmlight/multilabel.txt").getFile()));
        int i = 0;
        while (rr.hasNext()) {
            List<Writable> record = rr.next();
            assertEquals(correct.get(i), record);
            i++;
        }
        assertEquals(i, correct.size());
    }

    @Test
    @DisplayName("Test No Such Element Exception")
    void testNoSuchElementException() {
        assertThrows(NoSuchElementException.class, () -> {
            LibSvmRecordReader rr = new LibSvmRecordReader();
            Configuration config = new Configuration();
            config.setInt(LibSvmRecordReader.NUM_FEATURES, 11);
            rr.initialize(config, new FileSplit(new ClassPathResource("datavec-api/svmlight/basic.txt").getFile()));
            while (rr.hasNext()) rr.next();
            rr.next();
        });
    }

    @Test
    @DisplayName("Failed To Set Num Features Exception")
    void failedToSetNumFeaturesException() {
        assertThrows(UnsupportedOperationException.class, () -> {
            LibSvmRecordReader rr = new LibSvmRecordReader();
            Configuration config = new Configuration();
            rr.initialize(config, new FileSplit(new ClassPathResource("datavec-api/svmlight/basic.txt").getFile()));
            while (rr.hasNext()) rr.next();
        });
    }

    @Test
    @DisplayName("Test Inconsistent Num Labels Exception")
    void testInconsistentNumLabelsException() {
        assertThrows(UnsupportedOperationException.class, () -> {
            LibSvmRecordReader rr = new LibSvmRecordReader();
            Configuration config = new Configuration();
            config.setBoolean(LibSvmRecordReader.ZERO_BASED_INDEXING, false);
            rr.initialize(config, new FileSplit(new ClassPathResource("datavec-api/svmlight/inconsistentNumLabels.txt").getFile()));
            while (rr.hasNext()) rr.next();
        });
    }

    @Test
    @DisplayName("Test Inconsistent Num Multiabels Exception")
    void testInconsistentNumMultiabelsException() {
        assertThrows(UnsupportedOperationException.class, () -> {
            LibSvmRecordReader rr = new LibSvmRecordReader();
            Configuration config = new Configuration();
            config.setBoolean(LibSvmRecordReader.MULTILABEL, false);
            config.setBoolean(LibSvmRecordReader.ZERO_BASED_INDEXING, false);
            rr.initialize(config, new FileSplit(new ClassPathResource("datavec-api/svmlight/multilabel.txt").getFile()));
            while (rr.hasNext()) rr.next();
        });
    }

    @Test
    @DisplayName("Test Feature Index Exceeds Num Features")
    void testFeatureIndexExceedsNumFeatures() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            LibSvmRecordReader rr = new LibSvmRecordReader();
            Configuration config = new Configuration();
            config.setInt(LibSvmRecordReader.NUM_FEATURES, 9);
            rr.initialize(config, new FileSplit(new ClassPathResource("datavec-api/svmlight/basic.txt").getFile()));
            rr.next();
        });
    }

    @Test
    @DisplayName("Test Label Index Exceeds Num Labels")
    void testLabelIndexExceedsNumLabels() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            LibSvmRecordReader rr = new LibSvmRecordReader();
            Configuration config = new Configuration();
            config.setBoolean(LibSvmRecordReader.APPEND_LABEL, true);
            config.setInt(LibSvmRecordReader.NUM_FEATURES, 10);
            config.setInt(LibSvmRecordReader.NUM_LABELS, 6);
            rr.initialize(config, new FileSplit(new ClassPathResource("datavec-api/svmlight/basic.txt").getFile()));
            rr.next();
        });
    }

    @Test
    @DisplayName("Test Zero Index Feature Without Using Zero Indexing")
    void testZeroIndexFeatureWithoutUsingZeroIndexing() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            LibSvmRecordReader rr = new LibSvmRecordReader();
            Configuration config = new Configuration();
            config.setBoolean(LibSvmRecordReader.ZERO_BASED_INDEXING, false);
            config.setBoolean(LibSvmRecordReader.APPEND_LABEL, true);
            config.setInt(LibSvmRecordReader.NUM_FEATURES, 10);
            rr.initialize(config, new FileSplit(new ClassPathResource("datavec-api/svmlight/zeroIndexFeature.txt").getFile()));
            rr.next();
        });
    }

    @Test
    @DisplayName("Test Zero Index Label Without Using Zero Indexing")
    void testZeroIndexLabelWithoutUsingZeroIndexing() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            LibSvmRecordReader rr = new LibSvmRecordReader();
            Configuration config = new Configuration();
            config.setBoolean(LibSvmRecordReader.APPEND_LABEL, true);
            config.setInt(LibSvmRecordReader.NUM_FEATURES, 10);
            config.setBoolean(LibSvmRecordReader.MULTILABEL, true);
            config.setInt(LibSvmRecordReader.NUM_LABELS, 2);
            rr.initialize(config, new FileSplit(new ClassPathResource("datavec-api/svmlight/zeroIndexLabel.txt").getFile()));
            rr.next();
        });
    }
}
