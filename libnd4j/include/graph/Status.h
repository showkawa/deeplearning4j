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
//  @author raver119@gmail.com
//

#ifndef ND4J_STATUS_H
#define ND4J_STATUS_H

#include <system/pointercast.h>
#include <system/op_boilerplate.h>
#include <system/dll.h>
#include <helpers/logger.h>

namespace sd {
    class ND4J_EXPORT Status {
    public:
        static FORCEINLINE Nd4jStatus OK() {
            return ND4J_STATUS_OK;
        };

        static FORCEINLINE Nd4jStatus CODE(Nd4jStatus code, const char *message) {
            nd4j_printf("%s\n", message);
            return code;
        }

        static FORCEINLINE Nd4jStatus THROW(const char *message = nullptr) {
            if (message != nullptr) {
                nd4j_printf("%s\n", message);
            }
            return ND4J_STATUS_KERNEL_FAILURE;
        }
    };
}

#endif // STATUS_H