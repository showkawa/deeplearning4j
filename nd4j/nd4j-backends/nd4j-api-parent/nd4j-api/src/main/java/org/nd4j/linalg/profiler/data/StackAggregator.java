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

package org.nd4j.linalg.profiler.data;

import org.nd4j.linalg.profiler.data.primitives.StackDescriptor;
import org.nd4j.linalg.profiler.data.primitives.StackTree;

public class StackAggregator {
    private StackTree tree = new StackTree();

    public StackAggregator() {
        // nothing to do here so far
    }

    public void renderTree() {
        tree.renderTree(false);
    }

    public void renderTree(boolean displayCounts) {
        tree.renderTree(displayCounts);
    }

    public void reset() {
        tree.reset();
    }

    public void incrementCount() {
        incrementCount(1);
    }

    public void incrementCount(long time) {
        StackDescriptor descriptor = new StackDescriptor(Thread.currentThread().getStackTrace());
        tree.consumeStackTrace(descriptor, time);
    }

    public long getTotalEventsNumber() {
        return tree.getTotalEventsNumber();
    }

    public int getUniqueBranchesNumber() {
        return tree.getUniqueBranchesNumber();
    }

    public StackDescriptor getLastDescriptor() {
        return tree.getLastDescriptor();
    }
}
