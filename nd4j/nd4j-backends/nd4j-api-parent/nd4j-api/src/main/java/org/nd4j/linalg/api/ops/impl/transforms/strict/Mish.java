/*******************************************************************************
 * Copyright (c) 2019 Konduit K.K.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

package org.nd4j.linalg.api.ops.impl.transforms.strict;

import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseTransformStrictOp;

import java.util.Collections;
import java.util.List;

/**
 * Mish activation function
 *
 * @author raver119@gmail.com
 */
public class Mish extends BaseTransformStrictOp {
    public Mish(SameDiff sameDiff, SDVariable i_v, boolean inPlace) {
        super(sameDiff, i_v, inPlace);
    }

    public Mish() {
    }

    public Mish(INDArray x, INDArray z) {
        super(x, z);
    }

    public Mish(INDArray ndArray) {
        super(ndArray);
    }

    @Override
    public int opNum() {
        return 57;
    }

    @Override
    public String opName() {
        return "mish";
    }

    @Override
    public String onnxName() {
        throw new NoOpNameFoundException("No onnx op opName found for " + opName());
    }

    @Override
    public String tensorflowName() {
        return "Mish";
    }


    @Override
    public List<SDVariable> doDiff(List<SDVariable> i_v) {
        SDVariable ret = new MishDerivative(sameDiff, arg(), false).outputVariable().mul(i_v.get(0));
        return Collections.singletonList(ret);
    }


}
