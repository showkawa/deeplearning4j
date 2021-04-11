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

import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.broadcast.Broadcast;
import org.datavec.spark.util.SerializableHadoopConfig;
import org.deeplearning4j.core.loader.DataSetLoader;
import org.deeplearning4j.core.loader.MultiDataSetLoader;
import org.deeplearning4j.datasets.iterator.loader.DataSetLoaderIterator;
import org.deeplearning4j.datasets.iterator.loader.MultiDataSetLoaderIterator;
import org.deeplearning4j.spark.data.loader.RemoteFileSourceFactory;
import org.deeplearning4j.spark.impl.evaluation.EvaluationRunner;
import org.nd4j.evaluation.IEvaluation;
import org.nd4j.linalg.dataset.adapter.MultiDataSetIteratorAdapter;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;

import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.Future;

public class IEvaluateMDSPathsFlatMapFunction implements FlatMapFunction<Iterator<String>, IEvaluation[]> {

    protected Broadcast<String> json;
    protected Broadcast<byte[]> params;
    protected int evalNumWorkers;
    protected int evalBatchSize;
    protected DataSetLoader dsLoader;
    protected MultiDataSetLoader mdsLoader;
    protected Broadcast<SerializableHadoopConfig> conf;
    protected IEvaluation[] evaluations;

    /**
     * @param json Network configuration (json format)
     * @param params Network parameters
     * @param evalBatchSize Max examples per evaluation. Do multiple separate forward passes if data exceeds
     *                              this. Used to avoid doing too many at once (and hence memory issues)
     * @param evaluations Initial evaulation instance (i.e., empty Evaluation or RegressionEvaluation instance)
     */
    public IEvaluateMDSPathsFlatMapFunction(Broadcast<String> json, Broadcast<byte[]> params, int evalNumWorkers, int evalBatchSize,
                                                   DataSetLoader dsLoader, MultiDataSetLoader mdsLoader, Broadcast<SerializableHadoopConfig> configuration, IEvaluation[] evaluations) {
        this.json = json;
        this.params = params;
        this.evalNumWorkers = evalNumWorkers;
        this.evalBatchSize = evalBatchSize;
        this.dsLoader = dsLoader;
        this.mdsLoader = mdsLoader;
        this.conf = configuration;
        this.evaluations = evaluations;
    }

    @Override
    public Iterator<IEvaluation[]> call(Iterator<String> paths) throws Exception {
        if (!paths.hasNext()) {
            return Collections.emptyIterator();
        }

        MultiDataSetIterator iter;
        if(dsLoader != null){
            DataSetIterator dsIter = new DataSetLoaderIterator(paths, dsLoader, new RemoteFileSourceFactory(conf));
            iter = new MultiDataSetIteratorAdapter(dsIter);
        } else {
            iter = new MultiDataSetLoaderIterator(paths, mdsLoader, new RemoteFileSourceFactory(conf));
        }

        Future<IEvaluation[]> f = EvaluationRunner.getInstance().execute(evaluations, evalNumWorkers, evalBatchSize, null, iter, true, json, params);
        IEvaluation[] result = f.get();
        if(result == null){
            return Collections.emptyIterator();
        } else {
            return Collections.singletonList(result).iterator();
        }
    }
}
