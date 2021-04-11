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

package org.nd4j.evaluation;

import org.nd4j.evaluation.classification.Evaluation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.shade.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY)
public interface IEvaluation<T extends IEvaluation> extends Serializable {


    /**
     *
     * @param labels
     * @param networkPredictions
     */
    void eval(INDArray labels, INDArray networkPredictions);

    /**
     *
     * @param labels
     * @param networkPredictions
     * @param recordMetaData
     */
    void eval(INDArray labels, INDArray networkPredictions, List<? extends Serializable> recordMetaData);

    void eval(INDArray labels, INDArray networkPredictions, INDArray maskArray, List<? extends Serializable> recordMetaData);

    /**
     *
     * @param labels
     * @param networkPredictions
     * @param maskArray
     */
    void eval(INDArray labels, INDArray networkPredictions, INDArray maskArray);


    /**
     * @deprecated Use {@link #eval(INDArray, INDArray)}
     */
    @Deprecated
    void evalTimeSeries(INDArray labels, INDArray predicted);

    /**
     * @deprecated Use {@link #eval(INDArray, INDArray, INDArray)}
     */
    @Deprecated
    void evalTimeSeries(INDArray labels, INDArray predicted, INDArray labelsMaskArray);

    /**
     *
     * @param other
     */
    void merge(T other);

    /**
     *
     */
    void reset();

    /**
     *
     * @return
     */
    String stats();

    /**
     *
     * @return
     */
    String toJson();

    /**
     *
     * @return
     */
    String toYaml();

    /**
     * Get the value of a given metric for this evaluation.
     */
    double getValue(IMetric metric);

    /**
     * Get a new instance of this evaluation, with the same configuration but no data.
     */
    T newInstance();
}
