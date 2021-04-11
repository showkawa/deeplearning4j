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

package org.deeplearning4j.spark.earlystopping;

import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaRDD;
import org.deeplearning4j.earlystopping.scorecalc.ScoreCalculator;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.spark.impl.graph.SparkComputationGraph;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.MultiDataSet;

public class SparkLossCalculatorComputationGraph implements ScoreCalculator<ComputationGraph> {

    private JavaRDD<MultiDataSet> data;
    private boolean average;
    private SparkContext sc;

    /**
     * Calculate the score (loss function value) on a given data set (usually a test set)
     *
     * @param data    Data set to calculate the score for
     * @param average Whether to return the average (sum of loss / N) or just (sum of loss)
     */
    public SparkLossCalculatorComputationGraph(JavaRDD<MultiDataSet> data, boolean average, SparkContext sc) {
        this.data = data;
        this.average = average;
        this.sc = sc;
    }


    @Override
    public double calculateScore(ComputationGraph network) {
        SparkComputationGraph net = new SparkComputationGraph(sc, network, null);
        return net.calculateScoreMultiDataSet(data, average);
    }

    @Override
    public boolean minimizeScore() {
        return true;    //Minimize loss
    }

}
