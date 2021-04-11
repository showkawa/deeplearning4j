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
package org.datavec.poi.excel;

import lombok.val;
import org.datavec.api.split.FileSplit;
import org.datavec.api.split.partition.NumberOfRecordsPartitioner;
import org.datavec.api.transform.schema.Schema;
import org.datavec.api.writable.IntWritable;
import org.datavec.api.writable.Writable;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nd4j.common.primitives.Triple;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nd4j.common.tests.tags.TagNames;

@DisplayName("Excel Record Writer Test")
@Tag(TagNames.FILE_IO)
@Tag(TagNames.JAVA_ONLY)
class ExcelRecordWriterTest {

    @TempDir
    public Path testDir;

    @Test
    @DisplayName("Test Writer")
    void testWriter() throws Exception {
        ExcelRecordWriter excelRecordWriter = new ExcelRecordWriter();
        val records = records();
        File tmpDir = testDir.toFile();
        File outputFile = new File(tmpDir, "testexcel.xlsx");
        outputFile.deleteOnExit();
        FileSplit fileSplit = new FileSplit(outputFile);
        excelRecordWriter.initialize(fileSplit, new NumberOfRecordsPartitioner());
        excelRecordWriter.writeBatch(records.getRight());
        excelRecordWriter.close();
        File parentFile = outputFile.getParentFile();
        assertEquals(1, parentFile.list().length);
        ExcelRecordReader excelRecordReader = new ExcelRecordReader();
        excelRecordReader.initialize(fileSplit);
        List<List<Writable>> next = excelRecordReader.next(10);
        assertEquals(10, next.size());
    }

    private Triple<String, Schema, List<List<Writable>>> records() {
        List<List<Writable>> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int numColumns = 3;
        for (int i = 0; i < 10; i++) {
            List<Writable> temp = new ArrayList<>();
            for (int j = 0; j < numColumns; j++) {
                int v = 100 * i + j;
                temp.add(new IntWritable(v));
                sb.append(v);
                if (j < 2)
                    sb.append(",");
                else if (i != 9)
                    sb.append("\n");
            }
            list.add(temp);
        }
        Schema.Builder schemaBuilder = new Schema.Builder();
        for (int i = 0; i < numColumns; i++) {
            schemaBuilder.addColumnInteger(String.valueOf(i));
        }
        return Triple.of(sb.toString(), schemaBuilder.build(), list);
    }
}
