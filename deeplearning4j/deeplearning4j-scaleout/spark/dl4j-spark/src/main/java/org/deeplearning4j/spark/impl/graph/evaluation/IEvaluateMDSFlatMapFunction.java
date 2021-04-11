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

package org.deeplearning4j.spark.impl.graph.evaluation;

import lombok.extern.slf4j.Slf4j;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.broadcast.Broadcast;
import org.deeplearning4j.spark.impl.evaluation.EvaluationRunner;
import org.nd4j.evaluation.IEvaluation;
import org.nd4j.linalg.dataset.api.MultiDataSet;

import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.Future;

public class IEvaluateMDSFlatMapFunction<T extends IEvaluation> implements FlatMapFunction<Iterator<MultiDataSet>, T[]> {

    protected Broadcast<String> json;
    protected Broadcast<byte[]> params;
    protected int evalNumWorkers;
    protected int evalBatchSize;
    protected T[] evaluations;

    /**
     * @param json Network configuration (json format)
     * @param params Network parameters
     * @param evalBatchSize Max examples per evaluation. Do multiple separate forward passes if data exceeds
     *                              this. Used to avoid doing too many at once (and hence memory issues)
     * @param evaluations Initial evaulation instance (i.e., empty Evaluation or RegressionEvaluation instance)
     */
    public IEvaluateMDSFlatMapFunction(Broadcast<String> json, Broadcast<byte[]> params, int evalNumWorkers,
                                              int evalBatchSize, T[] evaluations) {
        this.json = json;
        this.params = params;
        this.evalNumWorkers = evalNumWorkers;
        this.evalBatchSize = evalBatchSize;
        this.evaluations = evaluations;
    }

    @Override
    public Iterator<T[]> call(Iterator<MultiDataSet> dataSetIterator) throws Exception {
        if (!dataSetIterator.hasNext()) {
            return Collections.emptyIterator();
        }

        if (!dataSetIterator.hasNext()) {
            return Collections.emptyIterator();
        }

        Future<IEvaluation[]> f = EvaluationRunner.getInstance().execute(
                evaluations, evalNumWorkers, evalBatchSize, null, dataSetIterator, true, json, params);

        IEvaluation[] result = f.get();
        if(result == null){
            return Collections.emptyIterator();
        } else {
            return Collections.singletonList((T[])result).iterator();
        }
    }
}
