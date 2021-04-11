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
import org.deeplearning4j.core.util.MovingWindowMatrix;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("Moving Window Matrix Test")
@NativeTag
@Tag(TagNames.NDARRAY_ETL)
class MovingWindowMatrixTest extends BaseDL4JTest {

    @Test
    @DisplayName("Test Moving Window")
    void testMovingWindow() {
        INDArray ones = Nd4j.ones(4, 4);
        MovingWindowMatrix m = new MovingWindowMatrix(ones, 2, 2);
        List<INDArray> windows = m.windows();
        assertEquals(4, windows.size());
        MovingWindowMatrix m2 = new MovingWindowMatrix(ones, 2, 2, true);
        List<INDArray> windowsRotate = m2.windows();
        assertEquals(16, windowsRotate.size());
    }
}
