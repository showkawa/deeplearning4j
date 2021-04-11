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

package org.datavec.api.writable.batch;

import org.nd4j.shade.guava.base.Preconditions;
import lombok.Data;
import lombok.NonNull;
import org.datavec.api.writable.NDArrayWritable;
import org.datavec.api.writable.Writable;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
public class NDArrayRecordBatch extends AbstractWritableRecordBatch {

    private List<INDArray> arrays;
    private long size;

    public NDArrayRecordBatch(INDArray... arrays){
        this(Arrays.asList(arrays));
    }

    public NDArrayRecordBatch(@NonNull List<INDArray> arrays){
        Preconditions.checkArgument(arrays.size() > 0, "Input list must not be empty");
        this.arrays = arrays;
        this.size = arrays.get(0).size(0);

        //Check that dimension 0 matches:
        if(arrays.size() > 1){
            size = arrays.get(0).size(0);
            for( int i=1; i<arrays.size(); i++ ){
                if(size != arrays.get(i).size(0)){
                    throw new IllegalArgumentException("Invalid input arrays: all arrays must have same size for" +
                            "dimension 0. arrays.get(0).size(0)=" + size + ", arrays.get(" + i + ").size(0)=" +
                            arrays.get(i).size(0));
                }
            }
        }
    }

    @Override
    public int size() {
        return (int) size;
    }

    @Override
    public List<Writable> get(int index) {
        Preconditions.checkArgument(index >= 0 && index < size, "Invalid index: " + index + ", size = " + size);
        List<Writable> out = new ArrayList<>((int) size);
        for (INDArray orig : arrays) {
            INDArray view = getExample(index, orig);
            out.add(new NDArrayWritable(view));
        }
        return out;
    }


    private static INDArray getExample(int idx, INDArray from){
        INDArrayIndex[] idxs = new INDArrayIndex[from.rank()];
        idxs[0] = NDArrayIndex.interval(idx, idx, true);    //Use interval to avoid collapsing point dimension
        for( int i=1; i<from.rank(); i++){
            idxs[i] = NDArrayIndex.all();
        }
        return from.get(idxs);
    }
}
