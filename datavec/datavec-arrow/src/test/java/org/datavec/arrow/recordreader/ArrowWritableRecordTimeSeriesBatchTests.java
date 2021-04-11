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

package org.datavec.arrow.recordreader;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.FieldVector;
import org.datavec.api.transform.schema.Schema;
import org.datavec.api.writable.DoubleWritable;
import org.datavec.api.writable.IntWritable;
import org.datavec.api.writable.Text;
import org.datavec.api.writable.Writable;
import org.datavec.arrow.ArrowConverter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.BaseND4JTest;
import org.nd4j.common.tests.tags.TagNames;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
@Tag(TagNames.JAVA_ONLY)
@Tag(TagNames.FILE_IO)
public class ArrowWritableRecordTimeSeriesBatchTests extends BaseND4JTest {

    private static BufferAllocator bufferAllocator = new RootAllocator(Long.MAX_VALUE);


    @Test
    @Tag(TagNames.NEEDS_VERIFY)
    @Disabled
    public void testBasicIndexing() {
        Schema.Builder schema = new Schema.Builder();
        for(int i = 0; i < 3; i++) {
            schema.addColumnInteger(String.valueOf(i));
        }


        List<List<Writable>> timeStep = Arrays.asList(
                Arrays.asList(new IntWritable(0),new IntWritable(1),new IntWritable(2)),
                Arrays.asList(new IntWritable(1),new IntWritable(2),new IntWritable(3)),
                Arrays.asList(new IntWritable(4),new IntWritable(5),new IntWritable(6))
        );

        int numTimeSteps = 5;
        List<List<List<Writable>>> timeSteps = new ArrayList<>(numTimeSteps);
        for(int i = 0; i < numTimeSteps; i++) {
            timeSteps.add(timeStep);
        }

        List<FieldVector> fieldVectors = ArrowConverter.toArrowColumnsTimeSeries(bufferAllocator, schema.build(), timeSteps);
        assertEquals(3,fieldVectors.size());
        for(FieldVector fieldVector : fieldVectors) {
            for(int i = 0; i < fieldVector.getValueCount(); i++) {
                assertFalse( fieldVector.isNull(i),"Index " + i + " was null for field vector " + fieldVector);
            }
        }

        ArrowWritableRecordTimeSeriesBatch arrowWritableRecordTimeSeriesBatch = new ArrowWritableRecordTimeSeriesBatch(fieldVectors,schema.build(),timeStep.size() * timeStep.get(0).size());
        assertEquals(timeSteps,arrowWritableRecordTimeSeriesBatch.toArrayList());
    }

    @Test
    @Tag(TagNames.NEEDS_VERIFY)
    @Disabled
    //not worried about this till after next release
    public void testVariableLengthTS() {
        Schema.Builder schema = new Schema.Builder()
                .addColumnString("str")
                .addColumnInteger("int")
                .addColumnDouble("dbl");

        List<List<Writable>> firstSeq = Arrays.asList(
                Arrays.asList(new Text("00"),new IntWritable(0),new DoubleWritable(2.0)),
                Arrays.asList(new Text("01"),new IntWritable(1),new DoubleWritable(2.1)),
                Arrays.asList(new Text("02"),new IntWritable(2),new DoubleWritable(2.2)));

        List<List<Writable>> secondSeq = Arrays.asList(
                Arrays.asList(new Text("10"),new IntWritable(10),new DoubleWritable(12.0)),
                Arrays.asList(new Text("11"),new IntWritable(11),new DoubleWritable(12.1)));

        List<List<List<Writable>>> sequences = Arrays.asList(firstSeq, secondSeq);


        List<FieldVector> fieldVectors = ArrowConverter.toArrowColumnsTimeSeries(bufferAllocator, schema.build(), sequences);
        assertEquals(3,fieldVectors.size());

        int timeSeriesStride = -1;  //Can't sequences of different length...
        ArrowWritableRecordTimeSeriesBatch arrowWritableRecordTimeSeriesBatch
                = new ArrowWritableRecordTimeSeriesBatch(fieldVectors,schema.build(),timeSeriesStride);

        List<List<List<Writable>>> asList = arrowWritableRecordTimeSeriesBatch.toArrayList();
        assertEquals(sequences, asList);
    }
  

}
