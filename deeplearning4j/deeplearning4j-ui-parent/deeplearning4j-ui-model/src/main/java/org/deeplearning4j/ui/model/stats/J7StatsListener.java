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

package org.deeplearning4j.ui.model.stats;

import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.core.storage.StatsStorageRouter;
import org.deeplearning4j.core.storage.StorageMetaData;
import org.deeplearning4j.ui.model.stats.api.StatsInitializationConfiguration;
import org.deeplearning4j.ui.model.stats.api.StatsInitializationReport;
import org.deeplearning4j.ui.model.stats.api.StatsReport;
import org.deeplearning4j.ui.model.stats.api.StatsUpdateConfiguration;
import org.deeplearning4j.ui.model.storage.FileStatsStorage;
import org.deeplearning4j.ui.model.storage.InMemoryStatsStorage;
import org.deeplearning4j.ui.model.storage.impl.JavaStorageMetaData;
import org.deeplearning4j.ui.model.stats.impl.DefaultStatsUpdateConfiguration;
import org.deeplearning4j.ui.model.stats.impl.java.JavaStatsInitializationReport;
import org.deeplearning4j.ui.model.stats.impl.java.JavaStatsReport;

@Slf4j
public class J7StatsListener extends BaseStatsListener {

    /**
     * Create a StatsListener with network information collected at every iteration. Equivalent to {@link #J7StatsListener(StatsStorageRouter, int)}
     * with {@code listenerFrequency == 1}
     *
     * @param router Where/how to store the calculated stats. For example, {@link InMemoryStatsStorage} or
     *               {@link FileStatsStorage}
     */
    public J7StatsListener(StatsStorageRouter router) {
        this(router, null, null, null, null);
    }

    /**
     * Create a StatsListener with network information collected every n >= 1 time steps
     *
     * @param router            Where/how to store the calculated stats. For example, {@link InMemoryStatsStorage} or
     *                          {@link FileStatsStorage}
     * @param listenerFrequency Frequency with which to collect stats information
     */
    public J7StatsListener(StatsStorageRouter router, int listenerFrequency) {
        this(router, null, new DefaultStatsUpdateConfiguration.Builder().reportingFrequency(listenerFrequency).build(),
                        null, null);
    }

    public J7StatsListener(StatsStorageRouter router, StatsInitializationConfiguration initConfig,
                           StatsUpdateConfiguration updateConfig, String sessionID, String workerID) {
        super(router, initConfig, updateConfig, sessionID, workerID);
    }

    @Override
    public StatsInitializationReport getNewInitializationReport() {
        return new JavaStatsInitializationReport();
    }

    @Override
    public StatsReport getNewStatsReport() {
        return new JavaStatsReport();
    }

    @Override
    public StorageMetaData getNewStorageMetaData(long initTime, String sessionID, String workerID) {
        return new JavaStorageMetaData(initTime, sessionID, TYPE_ID, workerID,
                        JavaStatsInitializationReport.class, JavaStatsReport.class);
    }

    @Override
    public J7StatsListener clone() {
        return new J7StatsListener(this.getStorageRouter(), this.getInitConfig(), this.getUpdateConfig(), null, null);
    }
}
