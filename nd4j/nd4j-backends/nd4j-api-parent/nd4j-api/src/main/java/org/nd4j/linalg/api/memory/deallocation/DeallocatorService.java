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

package org.nd4j.linalg.api.memory.deallocation;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.RandomUtils;
import org.nd4j.linalg.api.memory.Deallocatable;
import org.nd4j.linalg.factory.Nd4j;


import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class DeallocatorService {
    private Thread[] deallocatorThreads;
    private ReferenceQueue<Deallocatable>[] queues;
    private Map<String, DeallocatableReference> referenceMap = new ConcurrentHashMap<>();
    private List<List<ReferenceQueue<Deallocatable>>> deviceMap = new ArrayList<>();

    private final transient AtomicLong counter = new AtomicLong(0);

    public DeallocatorService() {
        // we need to have at least 2 threads, but for CUDA we'd need at least numDevices threads, due to thread->device affinity
        int numDevices = Nd4j.getAffinityManager().getNumberOfDevices();
        int numThreads = Math.max(2, numDevices * 2);

        for (int e = 0; e < numDevices; e++)
            deviceMap.add(new ArrayList<ReferenceQueue<Deallocatable>>());

        deallocatorThreads = new Thread[numThreads];
        queues = new ReferenceQueue[numThreads];
        for (int e = 0; e < numThreads; e++) {
            log.trace("Starting deallocator thread {}", e + 1);
            queues[e] = new ReferenceQueue<>();

            int deviceId = e % numDevices;
            // attaching queue to its own thread
            deallocatorThreads[e] = new DeallocatorServiceThread(queues[e], e, deviceId);
            deallocatorThreads[e].setName("DeallocatorServiceThread_" + e);
            deallocatorThreads[e].setDaemon(true);

            deviceMap.get(deviceId).add(queues[e]);
            
            deallocatorThreads[e].start();
        }
    }

    public long nextValue() {
        return counter.incrementAndGet();
    }

    /**
     * This method adds Deallocatable object instance to tracking system
     *
     * @param deallocatable object to track
     */
    public void pickObject(@NonNull Deallocatable deallocatable) {
        val desiredDevice = deallocatable.targetDevice();
        val map = deviceMap.get(desiredDevice);
        val reference = new DeallocatableReference(deallocatable, map.get(RandomUtils.nextInt(0, map.size())));
        referenceMap.put(deallocatable.getUniqueId(), reference);
    }


    private class DeallocatorServiceThread extends Thread implements Runnable {
        private final ReferenceQueue<Deallocatable> queue;
        private final int threadIdx;
        public static final String DeallocatorThreadNamePrefix = "DeallocatorServiceThread thread ";
        private final int deviceId;

        private DeallocatorServiceThread(@NonNull ReferenceQueue<Deallocatable> queue, int threadIdx, int deviceId) {
            this.queue = queue;
            this.threadIdx = threadIdx;
            this.setName(DeallocatorThreadNamePrefix + threadIdx);
            this.deviceId = deviceId;
            setContextClassLoader(null);
        }

        @Override
        public void run() {
            Nd4j.getAffinityManager().unsafeSetDevice(deviceId);
            boolean canRun = true;
            long cnt = 0;
            while (canRun) {
                // if periodicGc is enabled, only first thread will call for it
                if (Nd4j.getMemoryManager().isPeriodicGcActive() && threadIdx == 0 && Nd4j.getMemoryManager().getAutoGcWindow() > 0) {
                    val reference = (DeallocatableReference) queue.poll();
                    if (reference == null) {
                        val timeout = Nd4j.getMemoryManager().getAutoGcWindow();
                        try {
                            Thread.sleep(Nd4j.getMemoryManager().getAutoGcWindow());
                            Nd4j.getMemoryManager().invokeGc();
                        } catch (InterruptedException e) {
                            canRun = false;
                        }
                    } else {
                        // invoking deallocator
                        reference.getDeallocator().deallocate();
                        referenceMap.remove(reference.getId());
                    }
                } else {
                    try {
                        val reference = (DeallocatableReference) queue.remove();
                        if (reference == null)
                            continue;

                        // invoking deallocator
                        reference.getDeallocator().deallocate();
                        referenceMap.remove(reference.getId());
                    } catch (InterruptedException e) {
                        canRun = false;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }
}
