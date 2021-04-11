/*
 *  ******************************************************************************
 *  *
 *  *
 *  * This program and the accompanying materials are made available under the
 *  * terms of the Apache License, Version 2.0 which is available at
 *  * https://www.apache.org/licenses/LICENSE-2.0.
 *  *
 *  * See the NOTICE file distributed with this work for additional
 *  * information regarding copyright ownership.
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations
 *  * under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *  *****************************************************************************
 */
package org.nd4j.codegen.dsl;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.nd4j.codegen.impl.java.DocsGenerator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("Docs Generator Test")
class DocsGeneratorTest {

    @Test
    @DisplayName("Test J Dto MD Adapter")
    void testJDtoMDAdapter() {
        String original = "{@code %INPUT_TYPE% eye = eye(3,2)\n" + "                eye:\n" + "                [ 1, 0]\n" + "                [ 0, 1]\n" + "                [ 0, 0]}";
        String expected = "{ INDArray eye = eye(3,2)\n" + "                eye:\n" + "                [ 1, 0]\n" + "                [ 0, 1]\n" + "                [ 0, 0]}";
        DocsGenerator.JavaDocToMDAdapter adapter = new DocsGenerator.JavaDocToMDAdapter(original);
        String out = adapter.filter("@code", StringUtils.EMPTY).filter("%INPUT_TYPE%", "INDArray").toString();
        assertEquals(out, expected);
    }
}
