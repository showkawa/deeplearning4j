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

package org.nd4j.linalg.api.ops.impl.transforms.custom;

import lombok.NonNull;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.common.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.api.ops.impl.reduce.bp.PowBp;

import java.util.Collections;
import java.util.List;

public class Pow extends DynamicCustomOp {

    public Pow(SameDiff sameDiff, SDVariable x, SDVariable y){
        super(sameDiff, new SDVariable[]{x, y});
    }

    public Pow(){ }

    public Pow(@NonNull INDArray x, @NonNull INDArray y){
        super(new INDArray[]{x,y}, null);
    }

    @Override
    public String opName(){
        return "Pow";
    }

    @Override
    public String tensorflowName(){
        return "Pow";
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        //TODO: replace this with discrete op once available: https://github.com/eclipse/deeplearning4j/issues/7461
        //If y=a^b, then:
        //dL/da = b*a^(b-1) * dL/dy
        //dL/db = a^b * log(a) * dL/dy

        /*SDVariable a = arg(0);
        SDVariable b = arg(1);
        SDVariable dlda = b.mul(sameDiff.math().pow(a,b.sub(1))).mul(f1.get(0));
        SDVariable dldb = outputVariable().mul(sameDiff.math().log(a)).mul(f1.get(0));
        return Arrays.asList(dlda, dldb);*/

        return new PowBp(sameDiff, arg(0), arg(1), f1.get(0)).outputs();
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> dataTypes){
        Preconditions.checkState(dataTypes != null && dataTypes.size() == 2, "Expected exactly 2 input datatypes for %s, got %s", getClass(), dataTypes);
        return Collections.singletonList(dataTypes.get(0));
    }
}
