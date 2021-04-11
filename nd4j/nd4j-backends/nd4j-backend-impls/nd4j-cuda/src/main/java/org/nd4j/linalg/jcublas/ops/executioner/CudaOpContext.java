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

package org.nd4j.linalg.jcublas.ops.executioner;

import lombok.NonNull;
import lombok.val;
import org.bytedeco.javacpp.*;
import org.nd4j.jita.allocator.impl.AtomicAllocator;
import org.nd4j.jita.allocator.pointers.cuda.cudaStream_t;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.concurrency.AffinityManager;
import org.nd4j.linalg.api.memory.Deallocatable;
import org.nd4j.linalg.api.memory.Deallocator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseOpContext;
import org.nd4j.linalg.api.ops.ExecutionMode;
import org.nd4j.linalg.api.ops.OpContext;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.jcublas.buffer.BaseCudaDataBuffer;
import org.nd4j.linalg.jcublas.context.CudaContext;
import org.nd4j.common.primitives.Pair;
import org.nd4j.nativeblas.NativeOps;
import org.nd4j.nativeblas.NativeOpsHolder;
import org.nd4j.nativeblas.OpaqueContext;
import org.nd4j.nativeblas.OpaqueRandomGenerator;

/**
 * CUDA wrapper for op Context
 * @author raver119@gmail.com
 */
public class CudaOpContext extends BaseOpContext implements OpContext, Deallocatable {
    // we might want to have configurable
    private NativeOps nativeOps = NativeOpsHolder.getInstance().getDeviceNativeOps();
    private OpaqueContext context = nativeOps.createGraphContext(1);
    private final transient long id = Nd4j.getDeallocatorService().nextValue();

    public CudaOpContext() {
        Nd4j.getDeallocatorService().pickObject(this);
    }

    @Override
    public void close() {
        // no-op
    }

    @Override
    public void setIArguments(long... arguments) {
        if (arguments.length > 0) {
            super.setIArguments(arguments);
            nativeOps.setGraphContextIArguments(context, new LongPointer(arguments), arguments.length);
        }
    }

    @Override
    public void setBArguments(boolean... arguments) {
        if (arguments.length > 0) {
            super.setBArguments(arguments);
            nativeOps.setGraphContextBArguments(context, new BooleanPointer(arguments), arguments.length);
        }
    }

    @Override
    public void setTArguments(double... arguments) {
        if (arguments.length > 0) {
            super.setTArguments(arguments);
            nativeOps.setGraphContextTArguments(context, new DoublePointer(arguments), arguments.length);
        }
    }

    @Override
    public void setDArguments(DataType... arguments) {
        if (arguments.length > 0) {
            super.setDArguments(arguments);
            val args = new int[arguments.length];
            for (int e = 0; e < arguments.length; e++)
                args[e] = arguments[e].toInt();

            nativeOps.setGraphContextDArguments(context, new IntPointer(args), arguments.length);
        };
    }

    @Override
    public void setRngStates(long rootState, long nodeState) {
        nativeOps.setRandomGeneratorStates(nativeOps.getGraphContextRandomGenerator(context), rootState, nodeState);
    }

    @Override
    public Pair<Long, Long> getRngStates() {
        OpaqueRandomGenerator g = nativeOps.getGraphContextRandomGenerator(context);
        return Pair.makePair(nativeOps.getRandomGeneratorRootState(g), nativeOps.getRandomGeneratorNodeState(g));
    }

    @Override
    public void setInputArray(int index, @NonNull INDArray array) {
        //val ctx = AtomicAllocator.getInstance().getFlowController().prepareAction(null, array);
        nativeOps.setGraphContextInputBuffer(context, index, array.isEmpty() ? null : ((BaseCudaDataBuffer) array.data()).getOpaqueDataBuffer(), array.shapeInfoDataBuffer().addressPointer(), AtomicAllocator.getInstance().getPointer(array.shapeInfoDataBuffer()));

        super.setInputArray(index, array);
    }

    @Override
    public void setOutputArray(int index, @NonNull INDArray array) {
        //val ctx = AtomicAllocator.getInstance().getFlowController().prepareAction(array, null);
        nativeOps.setGraphContextOutputBuffer(context, index, array.isEmpty() ? null : ((BaseCudaDataBuffer) array.data()).getOpaqueDataBuffer(), array.shapeInfoDataBuffer().addressPointer(), AtomicAllocator.getInstance().getPointer(array.shapeInfoDataBuffer()));

        super.setOutputArray(index, array);
    }

    @Override
    public Pointer contextPointer() {
        return context;
    }


    public void setCudaStream(cudaStream_t stream, Pointer reductionPointer, Pointer allocationPointer) {
        nativeOps.setGraphContextCudaContext(context, stream, reductionPointer, allocationPointer);
    }

    @Override
    public void markInplace(boolean reallyInplace) {
        nativeOps.markGraphContextInplace(context, reallyInplace);
    }

    @Override
    public void allowHelpers(boolean reallyAllow) {
        nativeOps.ctxAllowHelpers(context, reallyAllow);
    }

    @Override
    public void shapeFunctionOverride(boolean reallyOverride) {
        nativeOps.ctxShapeFunctionOverride(context, reallyOverride);
    }

    @Override
    public void setExecutionMode(@NonNull ExecutionMode mode) {
        super.setExecutionMode(mode);
        nativeOps.ctxSetExecutionMode(context, mode.ordinal());
    }

    @Override
    public void purge() {
        super.purge();
        nativeOps.ctxPurge(context);
    }

    @Override
    public String getUniqueId() {
        return new String("CTX_" + id);
    }

    @Override
    public Deallocator deallocator() {
        return new CudaOpContextDeallocator(this);
    }

    @Override
    public int targetDevice() {
        return 0;
    }
}
