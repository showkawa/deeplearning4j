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

package org.datavec.spark.functions;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.input.PortableDataStream;
import org.datavec.api.conf.Configuration;
import org.datavec.api.records.reader.SequenceRecordReader;
import org.datavec.api.records.reader.impl.csv.CSVSequenceRecordReader;
import org.datavec.api.split.FileSplit;
import org.datavec.api.split.InputSplit;
import org.datavec.api.writable.ArrayWritable;
import org.datavec.api.writable.Writable;
import org.datavec.spark.BaseSparkTest;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;
import org.nd4j.common.io.ClassPathResource;
import org.nd4j.common.tests.tags.TagNames;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
@Tag(TagNames.FILE_IO)
@Tag(TagNames.JAVA_ONLY)
@Tag(TagNames.SPARK)
@Tag(TagNames.DIST_SYSTEMS)
public class TestSequenceRecordReaderFunction extends BaseSparkTest {



    @Test
    public void testSequenceRecordReaderFunctionCSV(@TempDir Path testDir) throws Exception {
        JavaSparkContext sc = getContext();

        File f = testDir.toFile();
        new ClassPathResource("datavec-spark/csvsequence/").copyDirectory(f);

        String path = f.getAbsolutePath() + "/*";

        JavaPairRDD<String, PortableDataStream> origData = sc.binaryFiles(path);
        assertEquals(3, origData.count()); //3 CSV files

        SequenceRecordReaderFunction srrf = new SequenceRecordReaderFunction(new CSVSequenceRecordReader(1, ",")); //CSV, skip 1 line
        JavaRDD<List<List<Writable>>> rdd = origData.map(srrf);
        List<List<List<Writable>>> listSpark = rdd.collect();

        assertEquals(3, listSpark.size());
        for (int i = 0; i < 3; i++) {
            List<List<Writable>> thisSequence = listSpark.get(i);
            assertEquals(4, thisSequence.size()); //Expect exactly 4 time steps in sequence
            for (List<Writable> c : thisSequence) {
                assertEquals(3, c.size()); //3 values per time step
            }
        }

        //Load normally, and check that we get the same results (order not withstanding)
        InputSplit is = new FileSplit(f, new String[] {"txt"}, true);
        //        System.out.println("Locations:");
        //        System.out.println(Arrays.toString(is.locations()));

        SequenceRecordReader srr = new CSVSequenceRecordReader(1, ",");
        srr.initialize(is);

        List<List<List<Writable>>> list = new ArrayList<>(3);
        while (srr.hasNext()) {
            list.add(srr.sequenceRecord());
        }
        assertEquals(3, list.size());

        //        System.out.println("Spark list:");
        //        for(List<List<Writable>> c : listSpark ) System.out.println(c);
        //        System.out.println("Local list:");
        //        for(List<List<Writable>> c : list ) System.out.println(c);

        //Check that each of the values from Spark equals exactly one of the values doing it normally
        boolean[] found = new boolean[3];
        for (int i = 0; i < 3; i++) {
            int foundIndex = -1;
            List<List<Writable>> collection = listSpark.get(i);
            for (int j = 0; j < 3; j++) {
                if (collection.equals(list.get(j))) {
                    if (foundIndex != -1)
                        fail(); //Already found this value -> suggests this spark value equals two or more of local version? (Shouldn't happen)
                    foundIndex = j;
                    if (found[foundIndex])
                        fail(); //One of the other spark values was equal to this one -> suggests duplicates in Spark list
                    found[foundIndex] = true; //mark this one as seen before
                }
            }
        }
        int count = 0;
        for (boolean b : found)
            if (b)
                count++;
        assertEquals(3, count); //Expect all 3 and exactly 3 pairwise matches between spark and local versions
    }




}
