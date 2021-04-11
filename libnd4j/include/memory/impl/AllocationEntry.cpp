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

#include <memory/AllocationEntry.h>

namespace sd {
    namespace memory {
        AllocationEntry::AllocationEntry(MemoryType type, Nd4jLong ptr, Nd4jLong numBytes, std::string &stack) {
            _pointer = ptr;
            _numBytes = numBytes;
            _stack = stack;
            _memoryType = type;
        }

        std::string AllocationEntry::stackTrace() {
            return _stack;
        }

        Nd4jLong AllocationEntry::numBytes() {
            return _numBytes;
        }

        MemoryType AllocationEntry::memoryType() {
            return _memoryType;
        }
    }
}