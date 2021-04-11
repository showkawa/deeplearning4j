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
package org.nd4j.linalg.api.ops.impl.image;

import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.common.base.Preconditions;
import org.nd4j.enums.ImageResizeMethod;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;

import java.util.Collections;
import java.util.List;

@NoArgsConstructor
public class ImageResize extends DynamicCustomOp {



    @Override
    public String opName() {
        return "image_resize";
    }


    public ImageResize(@NonNull SameDiff sameDiff, @NonNull SDVariable in, @NonNull SDVariable size, boolean preserveAspectRatio, boolean antialias, ImageResizeMethod method) {
        super("image_resize", sameDiff, new SDVariable[]{in, size});
        addBArgument(preserveAspectRatio, antialias);
        addIArgument(method.ordinal());
    }

    public ImageResize(@NonNull INDArray in, @NonNull INDArray size, boolean preserveAspectRatio, boolean antialias, ImageResizeMethod method) {
        super("image_resize", new INDArray[]{in, size}, null);
        Preconditions.checkArgument(in.rank()==4,"expected input message in NHWC format i.e [batchSize, height, width, channels]");
        addBArgument(preserveAspectRatio, antialias);
        addIArgument(method.ordinal());
    }



    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> dataTypes) {
        Preconditions
                .checkArgument(dataTypes != null && dataTypes.size() == 2, "Expected exactly 2 input datatypes, got %s", dataTypes);
        Preconditions.checkArgument(dataTypes.get(0).isFPType(), "Input datatype must be floating point, got %s", dataTypes);

        return Collections.singletonList(dataTypes.get(0));
    }


}