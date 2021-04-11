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

package org.deeplearning4j.earlystopping.saver;

import org.deeplearning4j.earlystopping.EarlyStoppingModelSaver;
import org.deeplearning4j.nn.api.Model;

import java.io.IOException;

public class InMemoryModelSaver<T extends Model> implements EarlyStoppingModelSaver<T> {

    private transient T bestModel;
    private transient T latestModel;

    @Override
    @SuppressWarnings("unchecked")
    public void saveBestModel(T net, double score) throws IOException {
        try {
            //Necessary because close is protected :S
            bestModel = (T) (net.getClass().getDeclaredMethod("clone")).invoke(net);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void saveLatestModel(T net, double score) throws IOException {
        try {
            //Necessary because close is protected :S
            latestModel = (T) (net.getClass().getDeclaredMethod("clone")).invoke(net);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public T getBestModel() throws IOException {
        return bestModel;
    }

    @Override
    public T getLatestModel() throws IOException {
        return latestModel;
    }

    @Override
    public String toString() {
        return "InMemoryModelSaver()";
    }
}
