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

package org.deeplearning4j.spark.impl.common;

import lombok.AllArgsConstructor;
import org.apache.spark.api.java.function.Function;
import org.nd4j.common.loader.Loader;
import org.nd4j.common.loader.Source;
import org.nd4j.common.loader.SourceFactory;
import org.nd4j.linalg.dataset.DataSet;

import java.io.InputStream;

@AllArgsConstructor
public class LoadDataSetFunction implements Function<String, DataSet> {

    private final Loader<DataSet> loader;
    private final SourceFactory factory;

    @Override
    public DataSet call(String path) throws Exception {
        Source s = factory.getSource(path);
        return loader.load(s);
    }
}
