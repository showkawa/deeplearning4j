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
import org.deeplearning4j.ui.model.storage.impl.SbeStorageMetaData;
import org.deeplearning4j.ui.model.stats.impl.DefaultStatsUpdateConfiguration;
import org.deeplearning4j.ui.model.stats.impl.SbeStatsInitializationReport;
import org.deeplearning4j.ui.model.stats.impl.SbeStatsReport;

@Slf4j
public class StatsListener extends BaseStatsListener {

    /**
     * Create a StatsListener with network information collected at every iteration. Equivalent to {@link #StatsListener(StatsStorageRouter, int)}
     * with {@code listenerFrequency == 1}
     *
     * @param router Where/how to store the calculated stats. For example, {@link InMemoryStatsStorage} or
     *               {@link FileStatsStorage}
     */
    public StatsListener(StatsStorageRouter router) {
        this(router, null, null, null, null);
    }

    /**
     * Create a StatsListener with network information collected every n >= 1 time steps
     *
     * @param router            Where/how to store the calculated stats. For example, {@link InMemoryStatsStorage} or
     *                          {@link FileStatsStorage}
     * @param listenerFrequency Frequency with which to collect stats information
     */
    public StatsListener(StatsStorageRouter router, int listenerFrequency) {
        this(router, listenerFrequency, null);
    }

    /**
     * Create a StatsListener with network information collected every n >= 1 time steps
     *
     * @param router            Where/how to store the calculated stats. For example, {@link InMemoryStatsStorage} or
     *                          {@link FileStatsStorage}
     * @param listenerFrequency Frequency with which to collect stats information
     * @param sessionId         The Session ID for storing the stats, optional (may be null)
     */
    public StatsListener(StatsStorageRouter router, int listenerFrequency, String sessionId) {
        this(router, null, new DefaultStatsUpdateConfiguration.Builder().reportingFrequency(listenerFrequency).build(),
                sessionId, null);
    }

    public StatsListener(StatsStorageRouter router, StatsInitializationConfiguration initConfig,
                         StatsUpdateConfiguration updateConfig, String sessionID, String workerID) {
        super(router, initConfig, updateConfig, sessionID, workerID);
    }

    public StatsListener clone() {
        return new StatsListener(this.getStorageRouter(), this.getInitConfig(), this.getUpdateConfig(), null, null);
    }

    @Override
    public StatsInitializationReport getNewInitializationReport() {
        return new SbeStatsInitializationReport();
    }

    @Override
    public StatsReport getNewStatsReport() {
        return new SbeStatsReport();
    }

    @Override
    public StorageMetaData getNewStorageMetaData(long initTime, String sessionID, String workerID) {
        return new SbeStorageMetaData(initTime, sessionID, BaseStatsListener.TYPE_ID, workerID,
                        SbeStatsInitializationReport.class, SbeStatsReport.class);
    }
}
