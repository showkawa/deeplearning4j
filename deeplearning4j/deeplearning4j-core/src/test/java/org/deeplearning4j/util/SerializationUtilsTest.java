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
package org.deeplearning4j.util;

import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.datasets.iterator.impl.IrisDataSetIterator;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.common.util.SerializationUtils;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import java.io.File;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("Serialization Utils Test")
@NativeTag
@Tag(TagNames.FILE_IO)
class SerializationUtilsTest extends BaseDL4JTest {

    @TempDir
    public Path testDir;

    @Test
    @DisplayName("Test Write Read")
    void testWriteRead() throws Exception {
        DataSetIterator iter = new IrisDataSetIterator(150, 150);
        String irisData = "irisData.dat";
        DataSet freshDataSet = iter.next(150);
        File f = testDir.resolve(irisData).toFile();
        SerializationUtils.saveObject(freshDataSet, f);
        DataSet readDataSet = SerializationUtils.readObject(f);
        assertEquals(freshDataSet.getFeatures(), readDataSet.getFeatures());
        assertEquals(freshDataSet.getLabels(), readDataSet.getLabels());
    }
}
