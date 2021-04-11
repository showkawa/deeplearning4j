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

package org.deeplearning4j.ui.i18n;

import org.deeplearning4j.ui.api.I18N;


public class I18NProvider {

    /**
     * Current I18N instance
     */
    private static I18N i18n = DefaultI18N.getInstance();

    /**
     * Get the current/global I18N instance (used in single-session mode)
     *
     * @return global instance
     */
    public static I18N getInstance() {
        return i18n;
    }


    /**
     * Get instance for session (used in multi-session mode)
     *
     * @param sessionId session
     * @return instance for session
     */
    public static I18N getInstance(String sessionId) {
        return DefaultI18N.getInstance(sessionId);
    }

    /**
     * Remove I18N instance for session
     *
     * @param sessionId session ID
     * @return the previous value associated with {@code sessionId} or null if there was no mapping for {@code sessionId}
     */
    public static synchronized I18N removeInstance(String sessionId) {
        return DefaultI18N.removeInstance(sessionId);
    }

}
