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
#include <execution/ContextBuffers.h>
#include <execution/AffinityManager.h>

namespace sd {
    ContextBuffers::ContextBuffers() {
        _deviceId = AffinityManager::currentDeviceId();
    }

    ContextBuffers::~ContextBuffers() {
        // no-op
    }

    ContextBuffers::ContextBuffers(void* rPointer, void* sPointer, void* aPointer, bool isOwner) {
        _reductionPointer = rPointer;
        _scalarPointer = sPointer;
        _allocationPointer = aPointer;
        _allocated = isOwner;
    }

    ContextBuffers::ContextBuffers(const ContextBuffers &other) {
        //
    }

    void ContextBuffers::initialize() {
        // no-op
    }

    void* ContextBuffers::reductionBuffer() {
        return _reductionPointer;
    }

    void* ContextBuffers::scalarBuffer() {
        return _scalarPointer;
    }

    void* ContextBuffers::allocationBuffer() {
        return _allocationPointer;
    }

    void ContextBuffers::setReductionBuffer(void* pointer) {
        _reductionPointer = pointer;
    }

    void ContextBuffers::setScalarBuffer(void* pointer) {
        _scalarPointer = pointer;
    }

    void ContextBuffers::setAllocationBuffer(void* pointer) {
        _allocationPointer = pointer;
    }

    void ContextBuffers::triggerOwnership(bool isOwner) {
        _allocated = isOwner;
    }

    int ContextBuffers::deviceId() {
        return _deviceId;
    }

    void* ContextBuffers::execStream() {
        return _execStream;
    }

    void* ContextBuffers::specialStream() {
        return _specialStream;
    }

    bool ContextBuffers::isInitialized() {
        return true;
    }

    void ContextBuffers::release() {
        //
    }

    ContextBuffers& ContextBuffers::operator=(const ContextBuffers& other) {
        return *this;
    }

    ContextBuffers& ContextBuffers::operator=(ContextBuffers&& other) {
        return *this;
    }

    sd::ErrorReference* ContextBuffers::errorReference() {
        return &_errorReference;
    }
}