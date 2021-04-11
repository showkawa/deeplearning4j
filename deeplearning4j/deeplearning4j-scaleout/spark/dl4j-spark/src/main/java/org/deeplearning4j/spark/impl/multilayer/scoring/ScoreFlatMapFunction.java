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

package org.deeplearning4j.spark.impl.multilayer.scoring;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.broadcast.Broadcast;
import org.deeplearning4j.datasets.iterator.IteratorDataSetIterator;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import scala.Tuple2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@Slf4j
@AllArgsConstructor
public class ScoreFlatMapFunction implements FlatMapFunction<Iterator<DataSet>, Tuple2<Integer, Double>> {

    private String json;
    private Broadcast<INDArray> params;
    private int minibatchSize;

    @Override
    public Iterator<Tuple2<Integer, Double>> call(Iterator<DataSet> dataSetIterator) throws Exception {
        if (!dataSetIterator.hasNext()) {
            return Collections.singletonList(new Tuple2<>(0, 0.0)).iterator();
        }

        DataSetIterator iter = new IteratorDataSetIterator(dataSetIterator, minibatchSize); //Does batching where appropriate

        MultiLayerNetwork network = new MultiLayerNetwork(MultiLayerConfiguration.fromJson(json));
        network.init();
        INDArray val = params.value().unsafeDuplication(); //.value() object will be shared by all executors on each machine -> OK, as params are not modified by score function
        if (val.length() != network.numParams(false))
            throw new IllegalStateException(
                            "Network did not have same number of parameters as the broadcast set parameters");
        network.setParameters(val);

        List<Tuple2<Integer, Double>> out = new ArrayList<>();
        while (iter.hasNext()) {
            DataSet ds = iter.next();
            double score = network.score(ds, false);

            val numExamples = (int) ds.getFeatures().size(0);
            out.add(new Tuple2<>(numExamples, score * numExamples));
        }

        Nd4j.getExecutioner().commit();

        return out.iterator();
    }
}
