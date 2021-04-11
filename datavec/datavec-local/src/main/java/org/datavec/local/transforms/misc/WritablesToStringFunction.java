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

package org.datavec.local.transforms.misc;

import lombok.AllArgsConstructor;
import org.datavec.api.writable.Writable;
import org.nd4j.common.function.Function;

import java.util.List;

@AllArgsConstructor
public class WritablesToStringFunction implements Function<List<Writable>, String> {

    private final String delim;
    private final String quote;

    public WritablesToStringFunction(String delim) {
        this(delim, null);
    }

    @Override
    public String apply(List<Writable> c) {

        StringBuilder sb = new StringBuilder();
        append(c, sb, delim, quote);

        return sb.toString();
    }

    public static void append(List<Writable> c, StringBuilder sb, String delim, String quote) {
        boolean first = true;
        for (Writable w : c) {
            if (!first)
                sb.append(delim);
            String s = w.toString();
            boolean needQuotes = s.contains(delim);
            if (needQuotes && quote != null) {
                sb.append(quote);
                s = s.replace(quote, quote + quote);
            }
            sb.append(s);
            if (needQuotes && quote != null) {
                sb.append(quote);
            }
            first = false;
        }
    }
}
