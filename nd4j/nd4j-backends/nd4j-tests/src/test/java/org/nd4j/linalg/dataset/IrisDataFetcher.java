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

package org.nd4j.linalg.dataset;

import org.junit.jupiter.api.Tag;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.dataset.api.iterator.fetcher.BaseDataFetcher;

import java.io.IOException;

@Tag(TagNames.NDARRAY_ETL)
@NativeTag
@Tag(TagNames.FILE_IO)
public class IrisDataFetcher extends BaseDataFetcher {


    /**
     * 
     */
    private static final long serialVersionUID = 4566329799221375262L;
    public final static int NUM_EXAMPLES = 150;

    public IrisDataFetcher() {
        numOutcomes = 3;
        inputColumns = 4;
        totalExamples = NUM_EXAMPLES;
    }

    @Override
    public void fetch(int numExamples) {
        int from = cursor;
        int to = cursor + numExamples;
        if (to > totalExamples)
            to = totalExamples;

        try {
            initializeCurrFromList(IrisUtils.loadIris(from, to));
            cursor += numExamples;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load iris.dat",e);
        }

    }


}
