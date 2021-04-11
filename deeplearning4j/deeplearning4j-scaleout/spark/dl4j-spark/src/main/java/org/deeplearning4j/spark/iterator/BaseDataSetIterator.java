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

import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.exception.ND4JArraySizeException;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public abstract class BaseDataSetIterator<T> implements DataSetIterator {
    protected Collection<T> dataSetStreams;
    protected DataSetPreProcessor preprocessor;
    protected Iterator<T> iter;
    protected long totalOutcomes = -1;
    protected long inputColumns = -1;
    protected int batch = -1;
    protected DataSet preloadedDataSet;
    protected int cursor = 0;

    @Override
    public DataSet next(int num) {
        return next();
    }

    @Override
    public int inputColumns() {
        if (inputColumns == -1)
            preloadDataSet();
        return (int)inputColumns;
    }

    @Override
    public int totalOutcomes() {
        if (totalOutcomes == -1)
            preloadDataSet();
        if(preloadedDataSet == null || preloadedDataSet.getLabels() == null){
            return 0;
        }
        return (int)preloadedDataSet.getLabels().size(1);
    }

    @Override
    public boolean resetSupported() {
        return dataSetStreams != null;
    }

    @Override
    public boolean asyncSupported() {
        return true;
    }

    @Override
    public void reset() {
        if (dataSetStreams == null)
            throw new IllegalStateException("Cannot reset iterator constructed with an iterator");
        iter = dataSetStreams.iterator();
        cursor = 0;
    }

    @Override
    public int batch() {
        if (batch == -1)
            preloadDataSet();
        return batch;
    }

    @Override
    public void setPreProcessor(DataSetPreProcessor preProcessor) {
        this.preprocessor = preProcessor;
    }

    @Override
    public DataSetPreProcessor getPreProcessor() {
        return this.preprocessor;
    }

    @Override
    public List<String> getLabels() {
        return null;
    }

    @Override
    public boolean hasNext() {
        return preloadedDataSet != null || iter.hasNext();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    private void preloadDataSet() {
        preloadedDataSet = load(iter.next());

        if (preloadedDataSet.getLabels().size(1) > Integer.MAX_VALUE ||
            preloadedDataSet.getFeatures().size(1) > Integer.MAX_VALUE)
            throw new ND4JArraySizeException();
        totalOutcomes = (int) preloadedDataSet.getLabels().size(1);
        inputColumns = (int) preloadedDataSet.getFeatures().size(1);
    }


    protected abstract DataSet load(T ds);
}
