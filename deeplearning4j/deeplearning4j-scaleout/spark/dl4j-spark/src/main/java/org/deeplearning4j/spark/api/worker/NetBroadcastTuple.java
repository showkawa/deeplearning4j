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

package org.deeplearning4j.spark.api.worker;

import lombok.Data;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class NetBroadcastTuple implements Serializable {

    private final MultiLayerConfiguration configuration;
    private final ComputationGraphConfiguration graphConfiguration;
    private final INDArray parameters;
    private final INDArray updaterState;
    private final AtomicInteger counter;

    public NetBroadcastTuple(MultiLayerConfiguration configuration, INDArray parameters, INDArray updaterState) {
        this(configuration, null, parameters, updaterState);
    }

    public NetBroadcastTuple(ComputationGraphConfiguration graphConfiguration, INDArray parameters,
                    INDArray updaterState) {
        this(null, graphConfiguration, parameters, updaterState);

    }

    public NetBroadcastTuple(MultiLayerConfiguration configuration, ComputationGraphConfiguration graphConfiguration,
                    INDArray parameters, INDArray updaterState) {
        this(configuration, graphConfiguration, parameters, updaterState, new AtomicInteger(0));
    }

    public NetBroadcastTuple(MultiLayerConfiguration configuration, ComputationGraphConfiguration graphConfiguration,
                    INDArray parameters, INDArray updaterState, AtomicInteger counter) {
        this.configuration = configuration;
        this.graphConfiguration = graphConfiguration;
        this.parameters = parameters;
        this.updaterState = updaterState;
        this.counter = counter;
    }
}
