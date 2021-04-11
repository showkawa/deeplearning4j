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

#include <ops/declarable/helpers/histogram.h>
#include <array/NDArrayFactory.h>

namespace sd {
    namespace ops {
        namespace helpers {
            template <typename X, typename Z>
            void _CUDA_G histogramKernel(void *xBuffer, const Nd4jLong *xShapeInfo, void *zBuffer, const Nd4jLong *zShapeInfo, void *allocationPointer, void *reductionPointer, Nd4jLong numBins, X* min_val, X* max_val) {
                int tid = blockIdx.x * blockDim.x + threadIdx.x;
                auto dx = reinterpret_cast<X*>(xBuffer);
                auto result = reinterpret_cast<Z*>(zBuffer);

                __shared__ Z *bins;
                __shared__ int length;
                __shared__ Z *reductor;
                if (threadIdx.x == 0) {
                    extern __shared__ unsigned char shmem[];
                    bins = (Z *) shmem;
                    reductor = ((Z *) allocationPointer) + (numBins * blockIdx.x);

                    length = shape::length(xShapeInfo);
                }
                __syncthreads();

                X binSize = X((*max_val - *min_val) / numBins);

                // nullify bins
                for (int e = threadIdx.x; e < numBins; e += blockDim.x) {
                    bins[e] = (Z) 0;
                }
                __syncthreads();

                for (int e = tid; e < length; e += blockDim.x * gridDim.x) {
                    int idx = int((dx[e] - *min_val) / binSize);
                    idx = math::nd4j_max(idx, 0); //atomicMax(&idx, 0);//atomicMax(&idx, 0);
                    idx = math::nd4j_min(idx, int(numBins - 1)); //atomicMin(&idx, int(numBins - 1));
                    sd::math::atomics::nd4j_atomicAdd<Z>(&bins[idx], (Z)1);
                }
                __syncthreads();
                // at this point all bins in shared memory are calculated, so we aggregate them now via threadfence trick

                // transfer shared memory to reduction memory
                if (gridDim.x > 1) {
                    unsigned int *tc = (unsigned int *)reductionPointer;
                    __shared__ bool amLast;

                    for (int e = threadIdx.x; e < numBins; e += blockDim.x) {
                        reductor[e] = bins[e];
                    }
                    __threadfence();
                    __syncthreads();

                    if (threadIdx.x == 0) {
                        unsigned int ticket = atomicInc(&tc[16384], gridDim.x);
                        amLast = (ticket == gridDim.x - 1);
                    }
                    __syncthreads();

                    if (amLast) {
                        tc[16384] = 0;

                        // nullify shared memory for future accumulation
                        for (int e = threadIdx.x; e < numBins; e += blockDim.x) {
                            bins[e] = (Z) 0;
                        }

                        // accumulate reduced bins
                        for (int r = 0; r < gridDim.x; r++) {
                            Z *ptrBuf = ((Z *)allocationPointer) + (r * numBins);

                            for (int e = threadIdx.x; e < numBins; e += blockDim.x) {
                                math::atomics::nd4j_atomicAdd(&bins[e], ptrBuf[e]);
                            }
                        }
                        __syncthreads();

                        // write them out to Z
                        for (int e = threadIdx.x; e < numBins; e += blockDim.x) {
                            result[e] = bins[e];
                        }
                    }
                } else {
                    // if there's only 1 block - just write away data
                    for (int e = threadIdx.x; e < numBins; e += blockDim.x) {
                        result[e] = bins[e];
                    }
                }
            }

            template <typename X, typename Z>
            static void histogram_(sd::LaunchContext *context, void *xBuffer, const Nd4jLong *xShapeInfo, const Nd4jLong *dxShapeInfo, void *zBuffer, const Nd4jLong *zShapeInfo, Nd4jLong numBins, void* min_val, void* max_val) {
                int numThreads = 256;
                int numBlocks = sd::math::nd4j_max<int>(256, sd::math::nd4j_min<int>(1, shape::length(xShapeInfo) / numThreads));
                int workspaceSize = numBlocks * numBins;
                auto tmp = NDArrayFactory::create<Z>('c', {workspaceSize}, context);

                histogramKernel<X, Z><<<numBlocks, numThreads, 32768, *context->getCudaStream()>>>(xBuffer, dxShapeInfo, zBuffer, zShapeInfo, tmp.specialBuffer(), context->getReductionPointer(), numBins, reinterpret_cast<X*>(min_val), reinterpret_cast<X*>(max_val));

                cudaStreamSynchronize(*context->getCudaStream());
            }

            void histogramHelper(sd::LaunchContext *context, NDArray &input, NDArray &output) {
                Nd4jLong numBins = output.lengthOf();
                NDArray::registerSpecialUse({&output}, {&input});

                auto min_val = input.reduceNumber(reduce::SameOps::Min);
                auto max_val = input.reduceNumber(reduce::SameOps::Max);
//                min_val.printIndexedBuffer("MIN");
//                max_val.printIndexedBuffer("MAX");
                BUILD_DOUBLE_SELECTOR(input.dataType(), output.dataType(), histogram_, (context, input.specialBuffer(), input.shapeInfo(), input.specialShapeInfo(), output.specialBuffer(), output.specialShapeInfo(), numBins, min_val.specialBuffer(), max_val.specialBuffer()), LIBND4J_TYPES, INTEGER_TYPES);
                NDArray::registerSpecialUse({&output}, {&input});
            }
        }
    }
}