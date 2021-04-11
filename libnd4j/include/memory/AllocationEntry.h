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
// Created by raver119 on 07.05.19.
//

#ifndef DEV_TESTS_ALLOCATIONENTRY_H
#define DEV_TESTS_ALLOCATIONENTRY_H

#include <system/pointercast.h>
#include <string>
#include <memory/MemoryType.h>

namespace sd {
    namespace memory {
        class AllocationEntry {
        private:
            MemoryType _memoryType;
            Nd4jLong _pointer;
            Nd4jLong _numBytes;
            std::string _stack;
        public:
            AllocationEntry() = default;
            AllocationEntry(MemoryType type, Nd4jLong ptr, Nd4jLong numBytes, std::string &stack);
            ~AllocationEntry() = default;


            Nd4jLong numBytes();
            std::string stackTrace();
            MemoryType memoryType();
        };
    }
}


#endif //DEV_TESTS_ALLOCATIONENTRY_H
