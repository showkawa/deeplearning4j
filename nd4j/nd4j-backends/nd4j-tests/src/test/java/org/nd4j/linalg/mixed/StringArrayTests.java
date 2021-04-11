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

package org.nd4j.linalg.mixed;

import com.google.flatbuffers.FlatBufferBuilder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.graph.FlatArray;
import org.nd4j.linalg.BaseNd4jTestWithBackends;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@NativeTag
public class StringArrayTests extends BaseNd4jTestWithBackends {


    @Override
    public char ordering(){
        return 'c';
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBasicStrings_1(Nd4jBackend backend) {
        val array = Nd4j.scalar("alpha");

        assertNotNull(array);
        assertEquals(1, array.length());
        assertEquals(0, array.rank());
        assertEquals(DataType.UTF8, array.dataType());

        assertEquals("alpha", array.getString(0));
        String s = array.toString();
        assertTrue(s.contains("alpha"),s);
        System.out.println(s);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBasicStrings_2(Nd4jBackend backend) {
        val array = Nd4j.create("alpha","beta", "gamma");

        assertNotNull(array);
        assertEquals(3, array.length());
        assertEquals(1, array.rank());
        assertEquals(DataType.UTF8, array.dataType());

        assertEquals("alpha", array.getString(0));
        assertEquals("beta", array.getString(1));
        assertEquals("gamma", array.getString(2));
        String s = array.toString();
        assertTrue(s.contains("alpha"),s);
        assertTrue(s.contains("beta"),s);
        assertTrue(s.contains("gamma"),s);
        System.out.println(s);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBasicStrings_3() {
        val arrayX = Nd4j.create("alpha", "beta", "gamma");
        val arrayY = Nd4j.create("alpha", "beta", "gamma");
        val arrayZ = Nd4j.create("Alpha", "bEta", "gamma");

        assertEquals(arrayX, arrayX);
        assertEquals(arrayX, arrayY);
        assertNotEquals(arrayX, arrayZ);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBasicStrings_4() {
        val arrayX = Nd4j.create("alpha", "beta", "gamma");

        val fb = new FlatBufferBuilder();
        val i = arrayX.toFlatArray(fb);
        fb.finish(i);
        val db = fb.dataBuffer();

        val flat = FlatArray.getRootAsFlatArray(db);
        val restored = Nd4j.createFromFlatArray(flat);

        assertEquals(arrayX, restored);
        assertEquals("alpha", restored.getString(0));
        assertEquals("beta", restored.getString(1));
        assertEquals("gamma", restored.getString(2));
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBasicStrings_4a() {
        val arrayX = Nd4j.scalar("alpha");

        val fb = new FlatBufferBuilder();
        val i = arrayX.toFlatArray(fb);
        fb.finish(i);
        val db = fb.dataBuffer();

        val flat = FlatArray.getRootAsFlatArray(db);
        val restored = Nd4j.createFromFlatArray(flat);

        assertEquals("alpha", arrayX.getString(0));

        assertEquals(arrayX, restored);
        assertEquals("alpha", restored.getString(0));
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBasicStrings_5() {
        val arrayX = Nd4j.create("alpha", "beta", "gamma");
        val arrayZ0 = arrayX.dup();
        val arrayZ1 = arrayX.dup(arrayX.ordering());

        assertEquals(arrayX, arrayZ0);
        assertEquals(arrayX, arrayZ1);
    }
}
