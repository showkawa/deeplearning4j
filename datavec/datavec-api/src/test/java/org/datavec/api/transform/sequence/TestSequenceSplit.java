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

package org.datavec.api.transform.sequence;

import org.datavec.api.transform.schema.Schema;
import org.datavec.api.transform.schema.SequenceSchema;
import org.datavec.api.transform.sequence.split.SequenceSplitTimeSeparation;
import org.datavec.api.writable.LongWritable;
import org.datavec.api.writable.Text;
import org.datavec.api.writable.Writable;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.BaseND4JTest;
import org.nd4j.common.tests.tags.TagNames;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
@Tag(TagNames.JAVA_ONLY)
@Tag(TagNames.FILE_IO)
public class TestSequenceSplit extends BaseND4JTest {

    @Test
    public void testSequenceSplitTimeSeparation() {

        Schema schema = new SequenceSchema.Builder().addColumnTime("time", DateTimeZone.UTC).addColumnString("text")
                        .build();

        List<List<Writable>> inputSequence = new ArrayList<>();
        inputSequence.add(Arrays.asList(new LongWritable(0), new Text("t0")));
        inputSequence.add(Arrays.asList(new LongWritable(1000), new Text("t1")));
        //Second split: 74 seconds later
        inputSequence.add(Arrays.asList(new LongWritable(75000), new Text("t2")));
        inputSequence.add(Arrays.asList(new LongWritable(100000), new Text("t3")));
        //Third split: 1 minute and 1 milliseconds later
        inputSequence.add(Arrays.asList(new LongWritable(160001), new Text("t4")));

        SequenceSplit seqSplit = new SequenceSplitTimeSeparation("time", 1, TimeUnit.MINUTES);
        seqSplit.setInputSchema(schema);

        List<List<List<Writable>>> splits = seqSplit.split(inputSequence);
        assertEquals(3, splits.size());

        List<List<Writable>> exp0 = new ArrayList<>();
        exp0.add(Arrays.asList(new LongWritable(0), new Text("t0")));
        exp0.add(Arrays.asList(new LongWritable(1000), new Text("t1")));
        List<List<Writable>> exp1 = new ArrayList<>();
        exp1.add(Arrays.asList(new LongWritable(75000), new Text("t2")));
        exp1.add(Arrays.asList(new LongWritable(100000), new Text("t3")));
        List<List<Writable>> exp2 = new ArrayList<>();
        exp2.add(Arrays.asList(new LongWritable(160001), new Text("t4")));

        assertEquals(exp0, splits.get(0));
        assertEquals(exp1, splits.get(1));
        assertEquals(exp2, splits.get(2));
    }

}
