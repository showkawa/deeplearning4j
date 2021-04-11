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

package org.datavec.api.transform.condition.column;

import lombok.Data;
import org.datavec.api.transform.condition.SequenceConditionMode;
import org.datavec.api.writable.Writable;

@Data
public class NaNColumnCondition extends BaseColumnCondition {

    /**
     * @param columnName Name of the column to check the condition for
     */
    public NaNColumnCondition(String columnName) {
        this(columnName, DEFAULT_SEQUENCE_CONDITION_MODE);
    }

    /**
     * @param columnName Name of the column to check the condition for
     * @param sequenceConditionMode Sequence condition mode
     */
    public NaNColumnCondition(String columnName, SequenceConditionMode sequenceConditionMode) {
        super(columnName, sequenceConditionMode);
    }

    @Override
    public boolean columnCondition(Writable writable) {
        return Double.isNaN(writable.toDouble());
    }

    @Override
    public boolean condition(Object input) {
        return Double.isNaN(((Number) input).doubleValue());
    }

    @Override
    public String toString() {
        return "NaNColumnCondition()";
    }

}
