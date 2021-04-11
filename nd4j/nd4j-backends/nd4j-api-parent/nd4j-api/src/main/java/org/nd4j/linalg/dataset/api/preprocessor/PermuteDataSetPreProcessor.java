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

package org.nd4j.linalg.dataset.api.preprocessor;

import org.nd4j.common.base.Preconditions;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;

public class PermuteDataSetPreProcessor implements DataSetPreProcessor {

    private final PermutationTypes permutationType;
    private final int[] rearrange;

    public enum PermutationTypes { NCHWtoNHWC, NHWCtoNCHW, Custom }

    public PermuteDataSetPreProcessor(PermutationTypes permutationType) {
        Preconditions.checkArgument(permutationType != PermutationTypes.Custom, "Use the ctor PermuteDataSetPreProcessor(int... rearrange) for custom permutations.");

        this.permutationType = permutationType;
        rearrange = null;
    }

    /**
     * @param rearrange The new order. For example PermuteDataSetPreProcessor(1, 2, 0) will rearrange the middle dimension first, the last one in the middle and the first one last.
     */
    public PermuteDataSetPreProcessor(int... rearrange) {

        this.permutationType = PermutationTypes.Custom;
        this.rearrange = rearrange;
    }

    @Override
    public void preProcess(DataSet dataSet) {
        Preconditions.checkNotNull(dataSet, "Encountered null dataSet");

        if(dataSet.isEmpty()) {
            return;
        }

        INDArray input = dataSet.getFeatures();
        INDArray output;
        switch (permutationType) {
            case NCHWtoNHWC:
                output = input.permute(0, 2, 3, 1);
                break;

            case NHWCtoNCHW:
                output = input.permute(0, 3, 1, 2);
                break;

            case Custom:
                output = input.permute(rearrange);
                break;

            default:
                output = input;
                break;
        }

        dataSet.setFeatures(output);
    }
}
