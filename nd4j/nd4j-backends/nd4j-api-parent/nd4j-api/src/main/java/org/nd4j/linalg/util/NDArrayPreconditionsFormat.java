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

package org.nd4j.linalg.util;

import org.nd4j.common.base.Preconditions;
import org.nd4j.common.base.PreconditionsFormat;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.Arrays;
import java.util.List;

public class NDArrayPreconditionsFormat implements PreconditionsFormat {

    private static final List<String> TAGS = Arrays.asList(
            "%ndRank", "%ndShape", "%ndStride", "%ndLength", "%ndSInfo", "%nd10");

    @Override
    public List<String> formatTags() {
        return TAGS;
    }

    @Override
    public String format(String tag, Object arg) {
        if(arg == null)
            return "null";
        INDArray arr = (INDArray)arg;
        switch (tag){
            case "%ndRank":
                return String.valueOf(arr.rank());
            case "%ndShape":
                return Arrays.toString(arr.shape());
            case "%ndStride":
                return Arrays.toString(arr.stride());
            case "%ndLength":
                return String.valueOf(arr.length());
            case "%ndSInfo":
                return arr.shapeInfoToString().replaceAll("\n","");
            case "%nd10":
                if(arr.isScalar() || arr.isEmpty()){
                    return arr.toString();
                }
                INDArray sub = arr.reshape(arr.length()).get(NDArrayIndex.interval(0, Math.min(arr.length(), 10)));
                return sub.toString();
            default:
                //Should never happen
                throw new IllegalStateException("Unknown format tag: " + tag);
        }
    }
}
