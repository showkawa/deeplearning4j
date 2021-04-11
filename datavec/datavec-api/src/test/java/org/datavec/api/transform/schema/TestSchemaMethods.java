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

package org.datavec.api.transform.schema;

import org.datavec.api.transform.ColumnType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.BaseND4JTest;
import org.nd4j.common.tests.tags.TagNames;

import static org.junit.jupiter.api.Assertions.assertEquals;
@Tag(TagNames.JAVA_ONLY)
@Tag(TagNames.FILE_IO)
public class TestSchemaMethods extends BaseND4JTest {

    @Test
    public void testNumberedColumnAdding() {

        Schema schema = new Schema.Builder().addColumnsDouble("doubleCol_%d", 0, 2).addColumnsLong("longCol_%d", 3, 5)
                        .addColumnsString("stringCol_%d", 6, 8).build();

        assertEquals(9, schema.numColumns());

        for (int i = 0; i < 9; i++) {
            if (i <= 2) {
                assertEquals("doubleCol_" + i, schema.getName(i));
                assertEquals(ColumnType.Double, schema.getType(i));
            } else if (i <= 5) {
                assertEquals("longCol_" + i, schema.getName(i));
                assertEquals(ColumnType.Long, schema.getType(i));
            } else {
                assertEquals("stringCol_" + i, schema.getName(i));
                assertEquals(ColumnType.String, schema.getType(i));
            }
        }

    }

}
