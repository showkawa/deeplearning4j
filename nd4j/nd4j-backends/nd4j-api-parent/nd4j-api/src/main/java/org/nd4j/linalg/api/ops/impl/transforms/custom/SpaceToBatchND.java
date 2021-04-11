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


import lombok.val;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ops.DynamicCustomOp;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SpaceToBatchND extends DynamicCustomOp {

    protected int[] blocks;
    protected int[][] padding;

    public SpaceToBatchND() {
    }

    public SpaceToBatchND(SameDiff sameDiff, SDVariable[] args, int[] blocks, int[][] padding, boolean inPlace) {
        super(null, sameDiff, args, inPlace);

        this.blocks = blocks;
        this.padding = padding;

        for (val b : blocks)
            addIArgument(b);

        for (int e = 0; e < padding.length; e++)
            addIArgument(padding[e][0], padding[e][1]);
    }

    @Override
    public String opName() {
        return "space_to_batch_nd";
    }

    @Override
    public String onnxName() {
        return "space_to_batch_nd";
    }

    @Override
    public String tensorflowName() {
        return "SpaceToBatchND";
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> i_v) {
        // Inverse of space to batch is batch to space with same blocks and crops as padding
        SDVariable gradient = sameDiff.setupFunction(i_v.get(0));
        return Arrays.asList(sameDiff.cnn().batchToSpace(gradient, blocks, padding[0], padding[1]));
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> dataTypes){
        return Collections.singletonList(dataTypes.get(0));
    }

}
