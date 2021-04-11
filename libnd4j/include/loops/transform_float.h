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

/*
 * transform.h
 *
 *  Created on: Dec 28, 2015
 *  @author: agibsonccc
 *  @author: raver119@gmail.com
 */

#ifndef TRANSFORM_FLOAT_H_
#define TRANSFORM_FLOAT_H_
#include <vector>
#include <math/templatemath.h>
#include <ops/ops.h>

#ifdef _OPENMP
#include <omp.h>
#endif
#include <system/pairwise_util.h>
#include <system/dll.h>

//#include <loops/reduce.h>
//#include <loops/scalar.h>
//#include <loops/indexreduce.h>
//#include <loops/broadcasting.h>

#ifdef __CUDACC__
#include <cuda.h>
#include <cuda_runtime.h>
#endif

#include "legacy_ops.h"


namespace functions {
    namespace transform {

        template<typename X, typename Z>
        class TransformFloat {
        public:

#ifdef __CUDACC__

	template<typename OpType>
	static  __device__ void transformCuda(const void *dy, const Nd4jLong *shapeInfo,
                                          void *params,
                                          void *result, const Nd4jLong *resultShapeInfo,
                                          int *allocationPointer, void *reductionPointer,
                                          const Nd4jLong *tadShapeInfo, const Nd4jLong *tadOffsets);

	static  __device__ void transformCudaLegacy(int opNum,
                                                const void *dy, const Nd4jLong *shapeInfo,
                                                void *params,
                                                void *result, const Nd4jLong *resultShapeInfo,
                                                int *allocationPointer, void *reductionPointer,
                                                const Nd4jLong *tadShapeInfo, const Nd4jLong *tadOffsets);

	template<typename OpType>
	static  __device__ void transformCuda(Nd4jLong n,
                                          const void *dy, Nd4jLong incy,
                                          void *params,
                                          void *result, Nd4jLong resultStride,
                                          int *allocationPointer, void *reductionPointer);


	template <typename OpType>
	static _CUDA_H void intermediateShaped(dim3 launchDims, cudaStream_t *stream,
                                           const void *x, const Nd4jLong *xShape, int xRank,
                                           void *extraParams,
                                           void *z, const Nd4jLong *zShape, int zRank,
                                           int *allocationPointer, void *reductionPointer,
                                           const Nd4jLong *tadShapeInfo, const Nd4jLong *tadOffsets);

	static _CUDA_H void executeTransformShaped(dim3 launchDims, cudaStream_t *stream,
                                               int opNum,
                                               const void *x, const Nd4jLong *xShape, int xRank,
                                               void *extraParams,
                                               void *z, const Nd4jLong *zShape, int zRank,
                                               int *allocationPointer, void *reductionPointer,
                                               const Nd4jLong *tadShapeInfo, const Nd4jLong *tadOffsets);

#else
			static void exec(int opNum,
                             const void *dx, const Nd4jLong *xShapeInfo,
                             void *result, const Nd4jLong *resultShapeInfo,
                             void *extraParams,
                             uint64_t threadId, uint64_t numThreads);

			template<typename OpType>
			static ND4J_EXPORT void exec(const void *dx, const Nd4jLong *xShapeInfo,
                                         void *result, const Nd4jLong *resultShapeInfo,
                                         void *extraParams,
                                         uint64_t threadId, uint64_t numThreads);
#endif
        };
    }
}


#endif /* TRANSFORM_H_ */
