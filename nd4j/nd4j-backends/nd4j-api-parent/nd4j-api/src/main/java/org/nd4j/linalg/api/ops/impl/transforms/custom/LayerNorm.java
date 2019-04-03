/*******************************************************************************
 * Copyright (c) 2015-2019 Skymind, Inc.
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

package org.nd4j.linalg.api.ops.impl.transforms.custom;

import lombok.NoArgsConstructor;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * Composed op: g*standarize(x) + b
 *
 * Bias is optional, and can be set as null
 *
 * @author Paul Dubs
 */
@NoArgsConstructor
public class LayerNorm extends DynamicCustomOp {

    private boolean noBias = false;

    public LayerNorm(SameDiff sameDiff, SDVariable input, SDVariable gain, SDVariable bias, int... dimensions) {
        super(null, sameDiff, new SDVariable[] {input, gain, bias}, false);
        Preconditions.checkArgument(bias != null, "LayerNorm: Use constructor without bias argument if bias is null / not available.");
        setDimensions(dimensions);
    }

    public LayerNorm(SameDiff sameDiff, SDVariable input, SDVariable gain, int... dimensions) {
        super(null, sameDiff, new SDVariable[] {input, gain}, false);
        noBias = true;
        setDimensions(dimensions);
    }

    public LayerNorm(INDArray input, INDArray gain, INDArray bias, INDArray result, int... dimensions) {
        super("layer_norm", new INDArray[]{input, gain, bias}, new INDArray[]{result});
        Preconditions.checkArgument(bias != null, "LayerNorm: Use different constructor if bias is null.");

        setDimensions(dimensions);
    }

    public LayerNorm(INDArray input, INDArray gain, INDArray result, int... dimensions) {
        super("layer_norm", new INDArray[]{input, gain}, new INDArray[]{result});
        noBias = true;
        setDimensions(dimensions);
    }

    @Override
    public void setDimensions(int[] dimensions) {
        Preconditions.checkArgument(dimensions != null, "LayerNorm: You have to provide dimensions");
        Preconditions.checkArgument(dimensions.length > 0, "LayerNorm: You have to provide dimensions");

        this.dimensions = dimensions;
        addIArgument(dimensions);
    }

    @Override
    public String opName() {
        return "layer_norm";
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
        SDVariable[] ret;
        if(noBias){
            ret = f().layerNormBp(arg(0), arg(1), gradient.get(0), dimensions);
        }else{
            ret = f().layerNormBp(arg(0), arg(1), arg(2), gradient.get(0), dimensions);
        }
        return Arrays.asList(ret);
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> dataTypes){
        Preconditions.checkState(dataTypes != null && dataTypes.size() >= 2 && dataTypes.size() <= 3, "Expected exactly 2 or 3 input datatypes, got %s", dataTypes);
        DataType first = dataTypes.get(0);
        for (DataType dataType : dataTypes) {
            Preconditions.checkState(dataType.isFPType(), "Input %s datatype must be a floating point type, got datypes %s", dataTypes);
            Preconditions.checkState(first == dataType, "All datatypes must be same type, got input datatypes %s", dataTypes);
        }

        return Collections.singletonList(first);
    }

}