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

import org.apache.spark.api.java.function.PairFlatMapFunction;
import org.apache.spark.broadcast.Broadcast;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.DataSetUtil;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.common.primitives.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Tuple2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class FeedForwardWithKeyFunction<K>
                implements PairFlatMapFunction<Iterator<Tuple2<K, Tuple2<INDArray,INDArray>>>, K, INDArray> {

    protected static Logger log = LoggerFactory.getLogger(FeedForwardWithKeyFunction.class);

    private final Broadcast<INDArray> params;
    private final Broadcast<String> jsonConfig;
    private final int batchSize;

    /**
     * @param params     MultiLayerNetwork parameters
     * @param jsonConfig MultiLayerConfiguration, as json
     * @param batchSize  Batch size to use for forward pass (use > 1 for efficiency)
     */
    public FeedForwardWithKeyFunction(Broadcast<INDArray> params, Broadcast<String> jsonConfig, int batchSize) {
        this.params = params;
        this.jsonConfig = jsonConfig;
        this.batchSize = batchSize;
    }


    @Override
    public Iterator<Tuple2<K, INDArray>> call(Iterator<Tuple2<K, Tuple2<INDArray,INDArray>>> iterator) throws Exception {
        if (!iterator.hasNext()) {
            return Collections.emptyIterator();
        }

        MultiLayerNetwork network = new MultiLayerNetwork(MultiLayerConfiguration.fromJson(jsonConfig.getValue()));
        network.init();
        INDArray val = params.value().unsafeDuplication();
        if (val.length() != network.numParams(false))
            throw new IllegalStateException(
                            "Network did not have same number of parameters as the broadcasted set parameters");
        network.setParameters(val);

        //Issue: for 2d data (MLPs etc) we can just stack the examples.
        //But: for 3d and 4d: in principle the data sizes could be different
        //We could handle that with mask arrays - but it gets messy. The approach used here is simpler but less efficient

        List<INDArray> featuresList = new ArrayList<>(batchSize);
        List<INDArray> fMaskList = new ArrayList<>(batchSize);
        List<K> keyList = new ArrayList<>(batchSize);
        List<Integer> origSizeList = new ArrayList<>();

        long[] firstShape = null;
        boolean sizesDiffer = false;
        int tupleCount = 0;
        while (iterator.hasNext()) {
            Tuple2<K, Tuple2<INDArray,INDArray>> t2 = iterator.next();

            if (firstShape == null) {
                firstShape = t2._2()._1().shape();
            } else if (!sizesDiffer) {
                for (int i = 1; i < firstShape.length; i++) {
                    if (firstShape[i] != featuresList.get(tupleCount - 1).size(i)) {
                        sizesDiffer = true;
                        break;
                    }
                }
            }
            featuresList.add(t2._2()._1());
            fMaskList.add(t2._2()._2());
            keyList.add(t2._1());

            origSizeList.add((int) t2._2()._1().size(0));
            tupleCount++;
        }

        if (tupleCount == 0) {
            return Collections.emptyIterator();
        }

        List<Tuple2<K, INDArray>> output = new ArrayList<>(tupleCount);
        int currentArrayIndex = 0;

        while (currentArrayIndex < featuresList.size()) {
            int firstIdx = currentArrayIndex;
            int nextIdx = currentArrayIndex;
            int examplesInBatch = 0;
            List<INDArray> toMerge = new ArrayList<>();
            List<INDArray> toMergeMask = new ArrayList<>();
            firstShape = null;
            while (nextIdx < featuresList.size() && examplesInBatch < batchSize) {
                if (firstShape == null) {
                    firstShape = featuresList.get(nextIdx).shape();
                } else if (sizesDiffer) {
                    boolean breakWhile = false;
                    for (int i = 1; i < firstShape.length; i++) {
                        if (firstShape[i] != featuresList.get(nextIdx).size(i)) {
                            //Next example has a different size. So: don't add it to the current batch, just process what we have
                            breakWhile = true;
                            break;
                        }
                    }
                    if (breakWhile) {
                        break;
                    }
                }

                INDArray f = featuresList.get(nextIdx);
                INDArray fm = fMaskList.get(nextIdx);
                nextIdx++;
                toMerge.add(f);
                toMergeMask.add(fm);
                examplesInBatch += f.size(0);
            }

            Pair<INDArray,INDArray> p = DataSetUtil.mergeFeatures(toMerge.toArray(new INDArray[toMerge.size()]), toMergeMask.toArray(new INDArray[toMergeMask.size()]));
//            INDArray batchFeatures = Nd4j.concat(0, toMerge.toArray(new INDArray[toMerge.size()]));
            INDArray out = network.output(p.getFirst(), false, p.getSecond(), null);

            examplesInBatch = 0;
            for (int i = firstIdx; i < nextIdx; i++) {
                int numExamples = origSizeList.get(i);
                INDArray outputSubset = getSubset(examplesInBatch, examplesInBatch + numExamples, out);
                examplesInBatch += numExamples;

                output.add(new Tuple2<>(keyList.get(i), outputSubset));
            }

            currentArrayIndex += (nextIdx - firstIdx);
        }

        Nd4j.getExecutioner().commit();

        return output.iterator();
    }

    private INDArray getSubset(int exampleStart, int exampleEnd, INDArray from) {
        switch (from.rank()) {
            case 2:
                return from.get(NDArrayIndex.interval(exampleStart, exampleEnd), NDArrayIndex.all());
            case 3:
                return from.get(NDArrayIndex.interval(exampleStart, exampleEnd), NDArrayIndex.all(),
                                NDArrayIndex.all());
            case 4:
                return from.get(NDArrayIndex.interval(exampleStart, exampleEnd), NDArrayIndex.all(), NDArrayIndex.all(),
                                NDArrayIndex.all());
            default:
                throw new RuntimeException("Invalid rank: " + from.rank());
        }
    }
}
