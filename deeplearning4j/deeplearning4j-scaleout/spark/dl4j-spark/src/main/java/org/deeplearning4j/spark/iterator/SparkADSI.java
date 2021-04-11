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

package org.deeplearning4j.spark.iterator;

import lombok.extern.slf4j.Slf4j;
import org.apache.spark.TaskContext;
import org.apache.spark.TaskContextHelper;
import org.nd4j.linalg.dataset.AsyncDataSetIterator;
import org.nd4j.linalg.api.memory.MemoryWorkspace;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.callbacks.DataSetCallback;
import org.nd4j.linalg.dataset.callbacks.DefaultCallback;
import org.nd4j.linalg.factory.Nd4j;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public class SparkADSI extends AsyncDataSetIterator {
    protected TaskContext context;

    protected SparkADSI() {
        super();
    }

    public SparkADSI(DataSetIterator baseIterator) {
        this(baseIterator, 8);
    }

    public SparkADSI(DataSetIterator iterator, int queueSize, BlockingQueue<DataSet> queue) {
        this(iterator, queueSize, queue, true);
    }

    public SparkADSI(DataSetIterator baseIterator, int queueSize) {
        this(baseIterator, queueSize, new LinkedBlockingQueue<DataSet>(queueSize));
    }

    public SparkADSI(DataSetIterator baseIterator, int queueSize, boolean useWorkspace) {
        this(baseIterator, queueSize, new LinkedBlockingQueue<DataSet>(queueSize), useWorkspace);
    }

    public SparkADSI(DataSetIterator baseIterator, int queueSize, boolean useWorkspace, Integer deviceId) {
        this(baseIterator, queueSize, new LinkedBlockingQueue<DataSet>(queueSize), useWorkspace, new DefaultCallback(),
                        deviceId);
    }

    public SparkADSI(DataSetIterator baseIterator, int queueSize, boolean useWorkspace, DataSetCallback callback) {
        this(baseIterator, queueSize, new LinkedBlockingQueue<DataSet>(queueSize), useWorkspace, callback);
    }

    public SparkADSI(DataSetIterator iterator, int queueSize, BlockingQueue<DataSet> queue, boolean useWorkspace) {
        this(iterator, queueSize, queue, useWorkspace, new DefaultCallback());
    }

    public SparkADSI(DataSetIterator iterator, int queueSize, BlockingQueue<DataSet> queue, boolean useWorkspace,
                    DataSetCallback callback) {
        this(iterator, queueSize, queue, useWorkspace, callback, Nd4j.getAffinityManager().getDeviceForCurrentThread());
    }

    public SparkADSI(DataSetIterator iterator, int queueSize, BlockingQueue<DataSet> queue, boolean useWorkspace,
                    DataSetCallback callback, Integer deviceId) {
        this();

        if (queueSize < 2)
            queueSize = 2;

        this.deviceId = deviceId;
        this.callback = callback;
        this.useWorkspace = useWorkspace;
        this.buffer = queue;
        this.prefetchSize = queueSize;
        this.backedIterator = iterator;
        this.workspaceId = "SADSI_ITER-" + java.util.UUID.randomUUID().toString();

        if (iterator.resetSupported())
            this.backedIterator.reset();

        context = TaskContext.get();

        this.thread = new SparkPrefetchThread(buffer, iterator, terminator, null, Nd4j.getAffinityManager().getDeviceForCurrentThread());

        /**
         * We want to ensure, that background thread will have the same thread->device affinity, as master thread
         */

        thread.setDaemon(true);
        thread.start();
    }

    @Override
    protected void externalCall() {
        TaskContextHelper.setTaskContext(context);

    }

    public class SparkPrefetchThread extends AsyncPrefetchThread {

        protected SparkPrefetchThread(BlockingQueue<DataSet> queue, DataSetIterator iterator, DataSet terminator, MemoryWorkspace workspace, int deviceId) {
            super(queue, iterator, terminator, workspace, deviceId);
        }


    }
}
