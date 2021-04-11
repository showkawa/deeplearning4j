/* ******************************************************************************
 *
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 *  See the NOTICE file distributed with this work for additional
 *  information regarding copyright ownership.
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

//
// @author raver119@gmail.com
//

#ifndef DEV_TESTS_BROADCASTINTOPSTUPLE_H
#define DEV_TESTS_BROADCASTINTOPSTUPLE_H

#include <system/op_enums.h>
#include <system/dll.h>

namespace sd {
    class ND4J_EXPORT BroadcastIntOpsTuple {
    private:

    public:
        sd::scalar::IntOps s;
        sd::pairwise::IntOps p;
        sd::broadcast::IntOps b;

        BroadcastIntOpsTuple() = default;
        ~BroadcastIntOpsTuple() = default;

        BroadcastIntOpsTuple(sd::scalar::IntOps scalar, sd::pairwise::IntOps pairwise, sd::broadcast::IntOps broadcast) {
            s = scalar;
            p = pairwise;
            b = broadcast;
        }

        static BroadcastIntOpsTuple custom(sd::scalar::IntOps scalar, sd::pairwise::IntOps pairwise, sd::broadcast::IntOps broadcast);
    };
}


#endif //DEV_TESTS_BROADCASTOPSTUPLE_H
