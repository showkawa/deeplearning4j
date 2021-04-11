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

package org.deeplearning4j.graph.data.impl;

import org.deeplearning4j.graph.api.Edge;
import org.deeplearning4j.graph.data.EdgeLineProcessor;

public class DelimitedEdgeLineProcessor implements EdgeLineProcessor<String> {
    private final String delimiter;
    private final String[] skipLinesStartingWith;
    private final boolean directed;

    public DelimitedEdgeLineProcessor(String delimiter, boolean directed) {
        this(delimiter, directed, null);
    }

    public DelimitedEdgeLineProcessor(String delimiter, boolean directed, String... skipLinesStartingWith) {
        this.delimiter = delimiter;
        this.skipLinesStartingWith = skipLinesStartingWith;
        this.directed = directed;
    }

    @Override
    public Edge<String> processLine(String line) {
        if (skipLinesStartingWith != null) {
            for (String s : skipLinesStartingWith) {
                if (line.startsWith(s))
                    return null;
            }
        }

        String[] split = line.split(delimiter);
        if (split.length != 2)
            throw new IllegalArgumentException(
                            "Invalid line: expected format \"" + 0 + delimiter + 1 + "\"; received \"" + line + "\"");

        int from = Integer.parseInt(split[0]);
        int to = Integer.parseInt(split[1]);
        String edgeName = from + (directed ? "->" : "--") + to;
        return new Edge<>(from, to, edgeName, directed);
    }
}
