/*******************************************************************************
 * Copyright (c) 2015-2018 Skymind, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

package org.nd4j.linalg.cpu.nativecpu.buffer;


import lombok.NonNull;
import org.bytedeco.javacpp.LongPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.indexer.Indexer;
import org.bytedeco.javacpp.indexer.LongIndexer;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.memory.MemoryWorkspace;
import org.nd4j.linalg.api.memory.pointers.PagedPointer;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.nativeblas.OpaqueDataBuffer;

import java.nio.ByteBuffer;

/**
 * Int buffer
 *
 * @author Adam Gibson
 */
public class LongBuffer extends BaseCpuDataBuffer {

    /**
     * Meant for creating another view of a buffer
     *
     * @param pointer the underlying buffer to create a view from
     * @param indexer the indexer for the pointer
     * @param length  the length of the view
     */
    public LongBuffer(Pointer pointer, Indexer indexer, long length) {
        super(pointer, indexer, length);
    }

    public LongBuffer(long length) {
        super(length);
    }

    public LongBuffer(long length, boolean initialize) {
        super(length, initialize);
    }

    public LongBuffer(long length, boolean initialize, MemoryWorkspace workspace) {
        super(length, initialize, workspace);
    }

    public LongBuffer(ByteBuffer buffer, DataType dataType, long length, long offset) {
        super(buffer, dataType, length, offset);
    }

    public LongBuffer(int[] ints, boolean copy, MemoryWorkspace workspace) {
        super(ints, copy, workspace);
    }


    public LongBuffer(double[] data, boolean copy) {
        super(data, copy);
    }

    public LongBuffer(double[] data, boolean copy, long offset) {
        super(data, copy, offset);
    }

    public LongBuffer(float[] data, boolean copy) {
        super(data, copy);
    }

    public LongBuffer(long[] data, boolean copy) {
        super(data, copy);
    }

    public LongBuffer(long[] data, boolean copy, MemoryWorkspace workspace) {
        super(data, copy, workspace);
    }

    public LongBuffer(float[] data, boolean copy, long offset) {
        super(data, copy, offset);
    }

    public LongBuffer(int[] data, boolean copy, long offset) {
        super(data, copy, offset);
    }

    public LongBuffer(int length, int elementSize) {
        super(length, elementSize);
    }

    public LongBuffer(int length, int elementSize, long offset) {
        super(length, elementSize, offset);
    }

    public LongBuffer(DataBuffer underlyingBuffer, long length, long offset) {
        super(underlyingBuffer, length, offset);
    }

    public LongBuffer(@NonNull Pointer hostPointer, long numberOfElements) {
        this.allocationMode = AllocationMode.MIXED_DATA_TYPES;
        this.offset = 0;
        this.originalOffset = 0;
        this.underlyingLength = numberOfElements;
        this.length = numberOfElements;
        initTypeAndSize();

        this.pointer = new PagedPointer(hostPointer, numberOfElements).asLongPointer();
        indexer = LongIndexer.create((LongPointer) this.pointer);

        // we still want this buffer to have native representation

        ptrDataBuffer = OpaqueDataBuffer.externalizedDataBuffer(numberOfElements, DataType.INT64, this.pointer, null);

        Nd4j.getDeallocatorService().pickObject(this);
    }

    @Override
    protected DataBuffer create(long length) {
        return new LongBuffer(length);
    }

    public LongBuffer(int[] data) {
        super(data);
    }

    public LongBuffer(double[] data) {
        super(data);
    }

    public LongBuffer(float[] data) {
        super(data);
    }

    public LongBuffer(long[] data) {
        super(data, true);
    }

    @Override
    public DataBuffer create(double[] data) {
        return new LongBuffer(data);
    }

    @Override
    public DataBuffer create(float[] data) {
        return new LongBuffer(data);
    }

    @Override
    public DataBuffer create(int[] data) {
        return new LongBuffer(data);
    }

    public LongBuffer(int[] data, boolean copy) {
        super(data, copy);
    }

    /**
     * Initialize the opType of this buffer
     */
    @Override
    protected void initTypeAndSize() {
        elementSize = 8;
        type = DataType.LONG;
    }


}
