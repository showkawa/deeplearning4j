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

package org.datavec.spark.transform.sparkfunction.sequence;

import lombok.AllArgsConstructor;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.sql.Row;
import org.datavec.api.transform.schema.Schema;
import org.datavec.api.writable.Writable;
import org.datavec.spark.transform.DataFrames;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
public class DataFrameToSequenceCreateCombiner implements Function<Iterable<Row>, List<List<Writable>>> {

    private final Schema schema;

    @Override
    public List<List<Writable>> call(Iterable<Row> rows) throws Exception {
        List<List<Writable>> retSeq = new ArrayList<>();
        for (Row v1 : rows) {
            List<Writable> ret = DataFrames.rowToWritables(schema, v1);
            retSeq.add(ret);
        }
        return retSeq;
    }
}
