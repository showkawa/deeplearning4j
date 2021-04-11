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

package org.deeplearning4j.ui.api;

import org.deeplearning4j.core.storage.StatsStorage;
import org.deeplearning4j.core.storage.StatsStorageEvent;
import org.deeplearning4j.ui.i18n.I18NResource;

import java.util.Collection;
import java.util.List;

public interface UIModule {

    /**
     * Get the list of Type IDs that should be collected from the registered {@link StatsStorage} instances, and
     * passed on to the {@link #reportStorageEvents(Collection)} method.
     *
     * @return List of relevant Type IDs
     */
    List<String> getCallbackTypeIDs();

    /**
     * Get a list of {@link Route} objects, that specify GET/SET etc methods, and how these should be handled.
     *
     * @return List of routes
     */
    List<Route> getRoutes();

    /**
     * Whenever the {@link UIServer} receives some {@link StatsStorageEvent}s from one of the registered {@link StatsStorage}
     * instances, it will filter these and pass on to the UI module those ones that match one of the Type IDs from
     * {@link #getCallbackTypeIDs()}.<br>
     * Typically, these will be batched together at least somewhat, rather than being reported individually.
     *
     * @param events       List of relevant events (type IDs match one of those from {@link #getCallbackTypeIDs()}
     */
    void reportStorageEvents(Collection<StatsStorageEvent> events);

    /**
     * Notify the UI module that the given {@link StatsStorage} instance has been attached to the UI
     *
     * @param statsStorage    Stats storage that has been attached
     */
    void onAttach(StatsStorage statsStorage);

    /**
     * Notify the UI module that the given {@link StatsStorage} instance has been detached from the UI
     *
     * @param statsStorage    Stats storage that has been detached
     */
    void onDetach(StatsStorage statsStorage);

    /**
     * @return List of internationalization resources
     */
    List<I18NResource> getInternationalizationResources();
}
