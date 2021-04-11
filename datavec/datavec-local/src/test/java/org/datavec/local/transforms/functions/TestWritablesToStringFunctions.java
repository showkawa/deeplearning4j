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

package org.datavec.local.transforms.functions;




import org.datavec.api.writable.DoubleWritable;
import org.datavec.api.writable.Text;
import org.datavec.api.writable.Writable;


import org.datavec.local.transforms.misc.SequenceWritablesToStringFunction;
import org.datavec.local.transforms.misc.WritablesToStringFunction;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.TagNames;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
@Tag(TagNames.FILE_IO)
@Tag(TagNames.JAVA_ONLY)
public class TestWritablesToStringFunctions  {



    @Test
    public void testWritablesToString() throws Exception {

        List<Writable> l = Arrays.asList(new DoubleWritable(1.5), new Text("someValue"));
        String expected = l.get(0).toString() + "," + l.get(1).toString();

        assertEquals(expected, new WritablesToStringFunction(",").apply(l));
    }

    @Test
    public void testSequenceWritablesToString() throws Exception {

        List<List<Writable>> l = Arrays.asList(Arrays.asList(new DoubleWritable(1.5), new Text("someValue")),
                        Arrays.asList(new DoubleWritable(2.5), new Text("otherValue")));

        String expected = l.get(0).get(0).toString() + "," + l.get(0).get(1).toString() + "\n"
                        + l.get(1).get(0).toString() + "," + l.get(1).get(1).toString();

        assertEquals(expected, new SequenceWritablesToStringFunction(",").apply(l));
    }
}
