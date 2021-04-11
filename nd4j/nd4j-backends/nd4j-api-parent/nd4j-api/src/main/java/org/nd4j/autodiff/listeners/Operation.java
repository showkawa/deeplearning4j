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

package org.nd4j.autodiff.listeners;

import java.util.Map;

import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;

public enum Operation {
    /**
     * The training operation: {@link SameDiff#fit()} methods training step (everything except validation).
     */
    TRAINING,
    /**
     * The training validation operation: the validation step during {@link SameDiff#fit()} methods.
     */
    TRAINING_VALIDATION,
    /**
     * Inference operations: {@link SameDiff#output()}, {@link SameDiff#batchOutput()} and {@link SameDiff#exec(Map, String...)} ()} methods,
     * as well as {@link SameDiff#execBackwards(Map, Operation, String...)} methods.
     */
    INFERENCE,
    /**
     * Evaluation operations: {@link SameDiff#evaluate()} methods.
     */
    EVALUATION;

    public boolean isTrainingPhase() {
        return this == TRAINING || this == TRAINING_VALIDATION;
    }

    public boolean isValidation() {
        return this == TRAINING_VALIDATION;
    }

}
