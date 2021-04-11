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

package org.datavec.local.transforms.misc.comparator;

import lombok.AllArgsConstructor;
import org.nd4j.common.primitives.Pair;

import java.io.Serializable;
import java.util.Comparator;

@AllArgsConstructor
public class Tuple2Comparator<T> implements Comparator<Pair<T, Long>>, Serializable {

    private final boolean ascending;

    @Override
    public int compare(Pair<T, Long> o1, Pair<T, Long> o2) {
        if (ascending)
            return Long.compare(o1.getSecond(), o2.getSecond());
        else
            return -Long.compare(o1.getSecond(), o2.getSecond());
    }
}
