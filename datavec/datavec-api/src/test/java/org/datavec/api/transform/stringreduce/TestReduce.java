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

package org.datavec.api.transform.stringreduce;

import org.datavec.api.transform.StringReduceOp;
import org.datavec.api.transform.schema.Schema;
import org.datavec.api.writable.Text;
import org.datavec.api.writable.Writable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.BaseND4JTest;
import org.nd4j.common.tests.tags.TagNames;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag(TagNames.JAVA_ONLY)
@Tag(TagNames.FILE_IO)
public class TestReduce extends BaseND4JTest {

    @Test
    public void testReducerDouble() {

        List<List<Writable>> inputs = new ArrayList<>();
        inputs.add(Arrays.asList(new Text("1"), new Text("2")));
        inputs.add(Arrays.asList(new Text("1"), new Text("2")));
        inputs.add(Arrays.asList(new Text("1"), new Text("2")));

        Map<StringReduceOp, String> exp = new LinkedHashMap<>();
        exp.put(StringReduceOp.MERGE, "12");
        exp.put(StringReduceOp.APPEND, "12");
        exp.put(StringReduceOp.PREPEND, "21");
        exp.put(StringReduceOp.REPLACE, "2");

        for (StringReduceOp op : exp.keySet()) {

            Schema schema = new Schema.Builder().addColumnString("key").addColumnString("column").build();

            StringReducer reducer = new StringReducer.Builder(op).build();

            reducer.setInputSchema(schema);

            List<Writable> out = reducer.reduce(inputs);

            assertEquals(3, out.size());
            assertEquals(exp.get(op), out.get(0).toString());
        }
    }


}
