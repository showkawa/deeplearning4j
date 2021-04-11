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

package org.deeplearning4j.spark.data.shuffle;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.api.java.function.PairFlatMapFunction;
import org.nd4j.linalg.dataset.DataSet;
import scala.Tuple2;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class SplitDataSetExamplesPairFlatMapFunction implements PairFlatMapFunction<DataSet, Integer, DataSet> {

    private transient Random r;
    private int maxKeyIndex;

    public SplitDataSetExamplesPairFlatMapFunction(int maxKeyIndex) {
        this.maxKeyIndex = maxKeyIndex;
    }

    @Override
    public Iterator<Tuple2<Integer, DataSet>> call(DataSet dataSet) throws Exception {
        if (r == null) {
            r = new Random();
        }

        List<DataSet> singleExamples = dataSet.asList();
        List<Tuple2<Integer, DataSet>> out = new ArrayList<>(singleExamples.size());
        for (DataSet ds : singleExamples) {
            out.add(new Tuple2<>(r.nextInt(maxKeyIndex), ds));
        }

        return out.iterator();
    }
}
