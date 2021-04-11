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

package org.deeplearning4j.spark.parameterserver.functions;

import org.apache.spark.api.java.function.FlatMapFunction;
import org.deeplearning4j.spark.api.TrainingResult;
import org.deeplearning4j.spark.api.TrainingWorker;
import org.deeplearning4j.spark.parameterserver.pw.SharedTrainingWrapper;
import org.deeplearning4j.spark.parameterserver.training.SharedTrainingResult;
import org.deeplearning4j.spark.parameterserver.training.SharedTrainingWorker;
import org.nd4j.linalg.dataset.api.MultiDataSet;

import java.util.Collections;
import java.util.Iterator;

public class SharedFlatMapMultiDataSet<R extends TrainingResult> implements FlatMapFunction<Iterator<MultiDataSet>, R> {

    private final SharedTrainingWorker worker;

    public SharedFlatMapMultiDataSet(TrainingWorker<R> worker) {
        // we're not going to have anything but Shared classes here ever
        this.worker = (SharedTrainingWorker) worker;
    }

    @Override
    public Iterator<R> call(Iterator<MultiDataSet> dataSetIterator) throws Exception {
        //Under some limited circumstances, we might have an empty partition. In this case, we should return immediately
        if(!dataSetIterator.hasNext()){
            return Collections.emptyIterator();
        }
        /*
            That's the place where we do our stuff. Here's the plan:
            1) we pass given iterator to VirtualDataSetIterator, which acts as holder for them
            2) Virtual iterator will provide load balancing between available devices
            3) we'll lock out here
         */

        // iterator should be silently attached to VirtualDataSetIterator, and used appropriately
        SharedTrainingWrapper.getInstance(worker.getInstanceId()).attachMDS(dataSetIterator);

        // first callee will become master, others will obey and die
        // all threads in this executor will be blocked here until training finished
        SharedTrainingResult result = SharedTrainingWrapper.getInstance(worker.getInstanceId()).run(worker);

        return Collections.singletonList((R) result).iterator();
    }
}
