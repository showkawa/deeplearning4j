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

package org.deeplearning4j.core.loader.impl;

import org.deeplearning4j.core.loader.MultiDataSetLoader;
import org.nd4j.common.loader.Source;
import org.nd4j.linalg.dataset.api.MultiDataSet;

import java.io.IOException;
import java.io.InputStream;

public class SerializedMultiDataSetLoader implements MultiDataSetLoader {
    @Override
    public MultiDataSet load(Source source) throws IOException {
        org.nd4j.linalg.dataset.MultiDataSet ds = new org.nd4j.linalg.dataset.MultiDataSet();
        try(InputStream is = source.getInputStream()){
            ds.load(is);
        }
        return ds;
    }
}
