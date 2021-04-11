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

package org.nd4j.linalg.learning.config;

import lombok.Data;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.learning.GradientUpdater;
import org.nd4j.linalg.learning.NoOpUpdater;
import org.nd4j.linalg.schedule.ISchedule;

import java.util.Map;

@Data
public class NoOp implements IUpdater {
    @Override
    public long stateSize(long numParams) {
        return 0;
    }

    @Override
    public GradientUpdater instantiate(INDArray viewArray, boolean initializeViewArray) {
        if (viewArray != null) {
            throw new IllegalStateException("Cannot use view array with NoOp updater");
        }
        return new NoOpUpdater(this);
    }

    @Override
    public GradientUpdater instantiate(Map<String, INDArray> updaterState, boolean initializeStateArrays) {
        NoOpUpdater u = new NoOpUpdater(this);
        u.setState(updaterState, initializeStateArrays);
        return u;
    }

    @Override
    public NoOp clone() {
        return new NoOp();
    }

    @Override
    public double getLearningRate(int iteration, int epoch) {
        return Double.NaN;  //No LR
    }

    @Override
    public boolean hasLearningRate() {
        return false;
    }

    @Override
    public void setLrAndSchedule(double lr, ISchedule lrSchedule) {
        throw new UnsupportedOperationException("Cannot set LR/schedule for NoOp updater");
    }
}
