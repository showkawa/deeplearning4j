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

package org.datavec.api.transform.ops;

import lombok.Getter;
import lombok.NonNull;
import org.datavec.api.transform.condition.Condition;
import org.datavec.api.writable.Writable;

import java.util.List;

import static org.nd4j.shade.guava.base.Preconditions.checkArgument;
import static org.nd4j.shade.guava.base.Preconditions.checkNotNull;

public class DispatchWithConditionOp<U> extends DispatchOp<Writable, U>
                implements IAggregableReduceOp<List<Writable>, List<U>> {


    @Getter
    @NonNull
    private List<Condition> conditions;


    public DispatchWithConditionOp(List<IAggregableReduceOp<Writable, List<U>>> ops, List<Condition> conds) {
        super(ops);
        checkNotNull(conds, "Empty condtions for a DispatchWitConditionsOp, use DispatchOp instead");

        checkArgument(ops.size() == conds.size(), "Found conditions size " + conds.size() + " expected " + ops.size());
        conditions = conds;
    }

    @Override
    public void accept(List<Writable> ts) {
        for (int i = 0; i < Math.min(super.getOperations().size(), ts.size()); i++) {
            Condition cond = conditions.get(i);
            if (cond.condition(ts))
                super.getOperations().get(i).accept(ts.get(i));
        }
    }

}
