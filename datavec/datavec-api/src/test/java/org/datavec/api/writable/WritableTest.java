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
package org.datavec.api.writable;

import org.datavec.api.writable.batch.NDArrayRecordBatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.BaseND4JTest;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Writable Test")
@Tag(TagNames.JAVA_ONLY)
@Tag(TagNames.FILE_IO)
class WritableTest extends BaseND4JTest {

    @Test
    @DisplayName("Test Writable Equality Reflexive")
    void testWritableEqualityReflexive() {
        assertEquals(new IntWritable(1), new IntWritable(1));
        assertEquals(new LongWritable(1), new LongWritable(1));
        assertEquals(new DoubleWritable(1), new DoubleWritable(1));
        assertEquals(new FloatWritable(1), new FloatWritable(1));
        assertEquals(new Text("Hello"), new Text("Hello"));
        assertEquals(new BytesWritable("Hello".getBytes()), new BytesWritable("Hello".getBytes()));
        INDArray ndArray = Nd4j.rand(new int[] { 1, 100 });
        assertEquals(new NDArrayWritable(ndArray), new NDArrayWritable(ndArray));
        assertEquals(new NullWritable(), new NullWritable());
        assertEquals(new BooleanWritable(true), new BooleanWritable(true));
        byte b = 0;
        assertEquals(new ByteWritable(b), new ByteWritable(b));
    }

    @Test
    @DisplayName("Test Bytes Writable Indexing")
    void testBytesWritableIndexing() {
        byte[] doubleWrite = new byte[16];
        ByteBuffer wrapped = ByteBuffer.wrap(doubleWrite);
        Buffer buffer = (Buffer) wrapped;
        wrapped.putDouble(1.0);
        wrapped.putDouble(2.0);
        buffer.rewind();
        BytesWritable byteWritable = new BytesWritable(doubleWrite);
        assertEquals(2, byteWritable.getDouble(1), 1e-1);
        DataBuffer dataBuffer = Nd4j.createBuffer(new double[] { 1, 2 });
        double[] d1 = dataBuffer.asDouble();
        double[] d2 = byteWritable.asNd4jBuffer(DataType.DOUBLE, 8).asDouble();
        assertArrayEquals(d1, d2, 0.0);
    }

    @Test
    @DisplayName("Test Byte Writable")
    void testByteWritable() {
        byte b = 0xfffffffe;
        assertEquals(new IntWritable(-2), new ByteWritable(b));
        assertEquals(new LongWritable(-2), new ByteWritable(b));
        assertEquals(new ByteWritable(b), new IntWritable(-2));
        assertEquals(new ByteWritable(b), new LongWritable(-2));
        // those would cast to the same Int
        byte minus126 = 0xffffff82;
        assertNotEquals(new ByteWritable(minus126), new IntWritable(130));
    }

    @Test
    @DisplayName("Test Int Long Writable")
    void testIntLongWritable() {
        assertEquals(new IntWritable(1), new LongWritable(1l));
        assertEquals(new LongWritable(2l), new IntWritable(2));
        long l = 1L << 34;
        // those would cast to the same Int
        assertNotEquals(new LongWritable(l), new IntWritable(4));
    }

    @Test
    @DisplayName("Test Double Float Writable")
    void testDoubleFloatWritable() {
        assertEquals(new DoubleWritable(1d), new FloatWritable(1f));
        assertEquals(new FloatWritable(2f), new DoubleWritable(2d));
        // we defer to Java equality for Floats
        assertNotEquals(new DoubleWritable(1.1d), new FloatWritable(1.1f));
        // same idea as above
        assertNotEquals(new DoubleWritable(1.1d), new FloatWritable((float) 1.1d));
        assertNotEquals(new DoubleWritable((double) Float.MAX_VALUE + 1), new FloatWritable(Float.POSITIVE_INFINITY));
    }

    @Test
    @DisplayName("Test Fuzzies")
    void testFuzzies() {
        assertTrue(new DoubleWritable(1.1d).fuzzyEquals(new FloatWritable(1.1f), 1e-6d));
        assertTrue(new FloatWritable(1.1f).fuzzyEquals(new DoubleWritable(1.1d), 1e-6d));
        byte b = 0xfffffffe;
        assertTrue(new ByteWritable(b).fuzzyEquals(new DoubleWritable(-2.0), 1e-6d));
        assertFalse(new IntWritable(1).fuzzyEquals(new FloatWritable(1.1f), 1e-2d));
        assertTrue(new IntWritable(1).fuzzyEquals(new FloatWritable(1.05f), 1e-1d));
        assertTrue(new LongWritable(1).fuzzyEquals(new DoubleWritable(1.05f), 1e-1d));
    }

    @Test
    @DisplayName("Test ND Array Record Batch")
    void testNDArrayRecordBatch() {
        Nd4j.getRandom().setSeed(12345);
        // Outer list over writables/columns, inner list over examples
        List<List<INDArray>> orig = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            orig.add(new ArrayList<INDArray>());
        }
        for (int i = 0; i < 5; i++) {
            orig.get(0).add(Nd4j.rand(1, 10));
            orig.get(1).add(Nd4j.rand(new int[] { 1, 5, 6 }));
            orig.get(2).add(Nd4j.rand(new int[] { 1, 3, 4, 5 }));
        }
        // Outer list over examples, inner list over writables
        List<List<INDArray>> origByExample = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            origByExample.add(Arrays.asList(orig.get(0).get(i), orig.get(1).get(i), orig.get(2).get(i)));
        }
        List<INDArray> batched = new ArrayList<>();
        for (List<INDArray> l : orig) {
            batched.add(Nd4j.concat(0, l.toArray(new INDArray[5])));
        }
        NDArrayRecordBatch batch = new NDArrayRecordBatch(batched);
        assertEquals(5, batch.size());
        for (int i = 0; i < 5; i++) {
            List<Writable> act = batch.get(i);
            List<INDArray> unboxed = new ArrayList<>();
            for (Writable w : act) {
                unboxed.add(((NDArrayWritable) w).get());
            }
            List<INDArray> exp = origByExample.get(i);
            assertEquals(exp.size(), unboxed.size());
            for (int j = 0; j < exp.size(); j++) {
                assertEquals(exp.get(j), unboxed.get(j));
            }
        }
        Iterator<List<Writable>> iter = batch.iterator();
        int count = 0;
        while (iter.hasNext()) {
            List<Writable> next = iter.next();
            List<INDArray> unboxed = new ArrayList<>();
            for (Writable w : next) {
                unboxed.add(((NDArrayWritable) w).get());
            }
            List<INDArray> exp = origByExample.get(count++);
            assertEquals(exp.size(), unboxed.size());
            for (int j = 0; j < exp.size(); j++) {
                assertEquals(exp.get(j), unboxed.get(j));
            }
        }
        assertEquals(5, count);
    }
}
