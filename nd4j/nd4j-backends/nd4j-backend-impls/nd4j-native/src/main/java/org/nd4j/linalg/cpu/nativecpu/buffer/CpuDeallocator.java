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

package org.nd4j.linalg.cpu.nativecpu.buffer;

import lombok.extern.slf4j.Slf4j;
import org.nd4j.linalg.api.memory.Deallocator;
import org.nd4j.nativeblas.NativeOpsHolder;
import org.nd4j.nativeblas.OpaqueDataBuffer;

@Slf4j
public class CpuDeallocator implements Deallocator {
    private final transient OpaqueDataBuffer opaqueDataBuffer;

    public CpuDeallocator(BaseCpuDataBuffer buffer) {
        opaqueDataBuffer = buffer.getOpaqueDataBuffer();
    }

    @Override
    public void deallocate() {
        if (opaqueDataBuffer == null)
            throw new RuntimeException("opaqueDataBuffer is null");

        NativeOpsHolder.getInstance().getDeviceNativeOps().deleteDataBuffer(opaqueDataBuffer);
    }
}
