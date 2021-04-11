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
package org.nd4j.autodiff.listeners.profiler.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TraceEvent {

    private String name;                //Name of event (usually op name)
    private List<String> categories;    //Comma separated list of categories
    private Phase ph;                   //Event type - phase (see table for options)
    private long ts;                    //Timestamp, in microseconds (us)
    private Long dur;                   //Duration, optional
    private Long tts;                   //Optional, thlread timestamp, in microseconds
    private long pid;                   //Process ID
    private long tid;                   //Thread ID
    private Map<String, Object> args;    //Args
    private ColorName cname;            //Optional, color name (must be one of reserved color names: https://github.com/catapult-project/catapult/blob/master/tracing/tracing/base/color_scheme.html )

}
