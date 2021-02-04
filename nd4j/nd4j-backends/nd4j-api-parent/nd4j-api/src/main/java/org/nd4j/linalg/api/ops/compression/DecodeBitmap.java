/*******************************************************************************
 * Copyright (c) 2020 Konduit K.K.
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

package org.nd4j.linalg.api.ops.compression;

import lombok.NonNull;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;

import java.util.Arrays;
import java.util.List;

/**
 * Bitmap decoding op wrapper. Used in gradients sharing.
 * @author raver119@gmail.com
 */
public class DecodeBitmap extends DynamicCustomOp {

    public DecodeBitmap() {
        //
    }

    public DecodeBitmap(@NonNull INDArray encoded, @NonNull INDArray updates) {
        addInputArgument(updates, encoded);
        addOutputArgument(updates);

        // this op ALWAYS modifies updates array
        setInPlace(true);
    }

    @Override
    public String opName() {
        return "decode_bitmap";
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> dataTypes) {
        return Arrays.asList(inputArguments.get(0).dataType(), DataType.INT32);
    }
}
