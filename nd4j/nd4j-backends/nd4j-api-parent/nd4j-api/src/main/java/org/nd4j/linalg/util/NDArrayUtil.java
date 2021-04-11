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

import org.nd4j.common.util.ArrayUtil;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.nd4j.linalg.factory.Nd4j;

@Deprecated
public class NDArrayUtil {

    private NDArrayUtil() {}

    @Deprecated
    public static INDArray toNDArray(int[][] nums) {
        if (Nd4j.dataType() == DataType.DOUBLE) {
            double[] doubles = ArrayUtil.toDoubles(nums);
            INDArray create = Nd4j.create(doubles, new int[] {nums[0].length, nums.length});
            return create;
        } else {
            float[] doubles = ArrayUtil.toFloats(nums);
            INDArray create = Nd4j.create(doubles, new int[] {nums[0].length, nums.length});
            return create;
        }

    }

    @Deprecated
    public static INDArray toNDArray(int[] nums) {
        if (Nd4j.dataType() == DataType.DOUBLE) {
            double[] doubles = ArrayUtil.toDoubles(nums);
            INDArray create = Nd4j.create(doubles, new int[] {1, nums.length});
            return create;
        } else {
            float[] doubles = ArrayUtil.toFloats(nums);
            INDArray create = Nd4j.create(doubles, new int[] {1, nums.length});
            return create;
        }
    }

    @Deprecated
    public static INDArray toNDArray(long[] nums) {
        if (Nd4j.dataType() == DataType.DOUBLE) {
            double[] doubles = ArrayUtil.toDoubles(nums);
            INDArray create = Nd4j.create(doubles, new int[] {1, nums.length});
            return create;
        } else {
            float[] doubles = ArrayUtil.toFloats(nums);
            INDArray create = Nd4j.create(doubles, new int[] {1, nums.length});
            return create;
        }
    }

    @Deprecated
    public static int[] toInts(INDArray n) {
        if (n.length() > Integer.MAX_VALUE)
            throw new ND4JIllegalStateException("Can't convert INDArray with length > Integer.MAX_VALUE");

        n = n.reshape(-1);
        int[] ret = new int[(int) n.length()];
        for (int i = 0; i < n.length(); i++)
            ret[i] = (int) n.getFloat(i);
        return ret;
    }

    @Deprecated
    public static long[] toLongs(INDArray n) {
        if (n.length() > Integer.MAX_VALUE)
            throw new ND4JIllegalStateException("Can't convert INDArray with length > Integer.MAX_VALUE");

        n = n.reshape(-1);

        long[] ret = new long[(int) n.length()];
        for (int i = 0; i < n.length(); i++)
            ret[i] = (long) n.getFloat(i);

        return ret;
    }

}
