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

/*
 *
 *  Created on: Dec 28, 2015
 *      Author: agibsonccc
 */

#ifndef INDEXREDUCE_H_
#define INDEXREDUCE_H_
#include <helpers/shape.h>
#ifdef _OPENMP
#include <omp.h>
#endif
#include <system/dll.h>
#include <ops/ops.h>
#include <system/op_boilerplate.h>
#include <helpers/OmpLaunchHelper.h>
#include <helpers/DebugHelper.h>

#ifdef __CUDACC__
#include <cuda.h>
#include <cuda_runtime.h>
#endif


#include <helpers/TAD.h>


#include "system/pairwise_util.h"

#include "legacy_ops.h"

namespace functions {
	namespace indexreduce {

		template<typename X, typename Z>
		class IndexReduce {
		public:
#ifdef __CUDABLAS__

	static __device__ void transform(int opNum,
                                     const void *x, const Nd4jLong *xShapeInfo,
                                     void *extraParams,
                                     void *result, const Nd4jLong *resultShapeInfo,
                                     int *dimension,int dimensionLength,
                                     int postProcessOrNot,
                                     int *allocationBuffer, void *reductionBuffer,
                                     const Nd4jLong *tadShapeInfo, const Nd4jLong *tadOffset);

    template<typename OpType>
	static __device__ void aggregatePartials(IndexValue<X> *sPartialsRef, Nd4jLong tid, Nd4jLong numElements, void *extraParams);


    template<typename OpType>
	static __device__ void transform(const void *dx, const Nd4jLong *xShapeInfo,
                                     void *extraParams,
                                     void *result, const Nd4jLong *resultShapeInfo,
                                     int *dimension, int dimensionLength,
                                     int postProcessOrNot,
                                     int *allocationBuffer, void *reductionBuffer,
                                     const Nd4jLong *tadOnlyShapeInfo, const Nd4jLong *tadOffsets);


    static _CUDA_H void executeIndexReduceScalar(dim3 launchDims, cudaStream_t *stream,
                                                 int op,
                                                 const void *dx, const Nd4jLong *xShapeInfo,
                                                 int xRank,
                                                 void *extraParams,
                                                 void *result, const Nd4jLong *resultShapeInfo,
                                                 int zRank,
                                                 int *dimension, int dimensionLength,
                                                 int postProcessOrNot,
                                                 int *allocationBuffer, void *reductionBuffer,
                                                 const Nd4jLong *tadOnlyShapeInfo, const Nd4jLong *tadOffsets);

    static _CUDA_H void executeIndexReduce(dim3 launchDims, cudaStream_t *stream,
                                           int op,
                                           const void *dx, const Nd4jLong *xShapeInfo,
                                           int xRank,
                                           void *extraParams,
                                           void *result, const Nd4jLong *resultShapeInfo,
                                           int zRank,
                                           int *dimension, int dimensionLength,
                                           int postProcessOrNot,
                                           int *allocationBuffer, void *reductionBuffer,
                                           const Nd4jLong *tadOnlyShapeInfo, const Nd4jLong *tadOffsets);
#else

		static Nd4jLong execScalar(int opNum, const void *x, const Nd4jLong *xShapeInfo, void *extraParams);

		static void exec(int opNum,
                         const void *x, const Nd4jLong *xShapeInfo,
                         void *extraParams,
                         void *result, const Nd4jLong *resultShapeInfoBuffer,
                         int *dimension, int dimensionLength,
                         const Nd4jLong *tadShapeInfo, const Nd4jLong *tadOffset);

		template<typename OpType>
		static _CUDA_H Nd4jLong execScalar(const void *x, const Nd4jLong *xShapeInfo, void *extraParams);

		template<typename OpType>
		static _CUDA_H void exec(const void *x, const Nd4jLong *xShapeInfo,
                                 void *extraParams,
                                 void *result, const Nd4jLong *resultShapeInfoBuffer,
                                 int *dimension, int dimensionLength,
                                 const Nd4jLong *tadShapeInfo, const Nd4jLong *tadOffset);
#endif
		};
	}
}

#endif /* INDEXREDUCE_H_ */

