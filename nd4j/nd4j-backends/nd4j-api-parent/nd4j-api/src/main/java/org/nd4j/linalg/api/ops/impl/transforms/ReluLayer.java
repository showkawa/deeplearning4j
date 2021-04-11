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

package org.nd4j.linalg.api.ops.impl.transforms;

import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.transforms.custom.XwPlusB;

import java.util.Collections;
import java.util.List;


@NoArgsConstructor
public class ReluLayer extends XwPlusB {


    public ReluLayer(SameDiff sameDiff, SDVariable input, SDVariable weights, SDVariable bias) {
        super(sameDiff, input, weights, bias);
    }

    public ReluLayer(@NonNull INDArray input, @NonNull INDArray weights, @NonNull INDArray bias){
        super(new INDArray[]{input, weights, bias}, null);
    }

    @Override
    public String opName() {
        return "relu_layer";
    }


    @Override
    public String tensorflowName() {
        throw new NoOpNameFoundException("No tensorflow name found for shape " + opName());
    }

    @Override
    public String onnxName() {
        throw new NoOpNameFoundException("No onnx name found for shape " + opName());
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> gradient) {
        //TODO a native implementation would be faster
        //Backprop through ReLU, then it's same as XwPlusB
        SDVariable[] args = args();
        SDVariable xwb = sameDiff.nn().linear(args[0], args[1], (args.length == 2 ? null : args[2]));
        SDVariable grad = gradient.get(0).mul(sameDiff.math().step(xwb, 0));
        return super.doDiff(Collections.singletonList(grad));
    }

}
