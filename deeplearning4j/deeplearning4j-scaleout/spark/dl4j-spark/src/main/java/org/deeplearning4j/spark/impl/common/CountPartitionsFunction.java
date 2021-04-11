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

package org.deeplearning4j.spark.impl.common;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.Function2;
import org.deeplearning4j.spark.api.Repartition;
import scala.Tuple2;

import java.util.Collections;
import java.util.Iterator;

public class CountPartitionsFunction<T> implements Function2<Integer, Iterator<T>, Iterator<Tuple2<Integer, Integer>>> {
    @Override
    public Iterator<Tuple2<Integer, Integer>> call(Integer v1, Iterator<T> v2) throws Exception {

        int count = 0;
        while (v2.hasNext()) {
            v2.next();
            count++;
        }

        return Collections.singletonList(new Tuple2<>(v1, count)).iterator();
    }
}
