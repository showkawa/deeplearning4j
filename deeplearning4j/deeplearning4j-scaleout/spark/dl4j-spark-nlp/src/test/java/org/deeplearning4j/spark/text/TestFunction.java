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

package org.deeplearning4j.spark.text;

import org.apache.spark.api.java.function.Function;
import org.junit.jupiter.api.Tag;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;

import java.util.List;
@Tag(TagNames.FILE_IO)
@Tag(TagNames.SPARK)
@Tag(TagNames.DIST_SYSTEMS)
@NativeTag
public class TestFunction implements Function<Integer, Integer> {
    public TestFunction(List<Integer> lst) {
        this.lst = lst;
    }

    public List<Integer> getLst() {
        return lst;
    }

    public int getA() {
        return a;
    }

    private List<Integer> lst;
    private int a;


    @Override
    public Integer call(Integer i) {
        lst.add(i);
        a = 1000;
        return i + 1;
    }
}

