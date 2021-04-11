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
 * pairwise_transform.h
 *
 *  Created on: Dec 28, 2015
 *      Author: agibsonccc
 */

#ifndef PAIRWISE_TRANSFORM_H_
#define PAIRWISE_TRANSFORM_H_
#ifdef _OPENMP
#include <omp.h>
#endif

#include <system/dll.h>
#include <stdio.h>
#include <ops/ops.h>
#include <system/op_boilerplate.h>
#include <types/types.h>
#include "legacy_ops.h"
#include <helpers/DebugHelper.h>

#ifdef __CUDACC__
#include <cuda.h>
#include <cuda_runtime.h>
#include <types/float16.h>
#endif


namespace functions {
    namespace pairwise_transforms {

/**
 * Transforms involving 2 arrays
 */
        template<typename X, typename Y, typename Z>
        class PairWiseTransform {
        public:

#ifdef __CUDABLAS__

            template <typename OpType>            
            static __host__ void intermediateShaped(dim3& launchDims, cudaStream_t *stream,
                                                    const void *vx, const Nd4jLong *xShapeInfo,
                                                    const void *vy, const Nd4jLong *yShapeInfo,
                                                    void *vz, const Nd4jLong *zShapeInfo,
                                                    void *vextraParams);

            static __host__ void executeCudaShaped(dim3& launchDims, cudaStream_t *stream,
                                                   int opNum,
                                                   const void *x, const Nd4jLong *xShapeInfo,
                                                   const void *y, const Nd4jLong *yShapeInfo,
                                                   void *z, const Nd4jLong *zShapeInfo,
                                                   void *extraParams);

#endif
        public:

            static void exec(int opNum,
                             const void *x, const Nd4jLong *xShapeInfo,
                             const void *y, const Nd4jLong *yShapeInfo,
                             void *z, const Nd4jLong *zShapeInfo,
                             void *extraParams,
                             uint64_t start, uint64_t stop);

			static void exec(int opNum,
                             const void *x, Nd4jLong xStride,
                             const void *y, Nd4jLong yStride,
                             void *z, Nd4jLong resultStride,
                             void *extraParams,
                             Nd4jLong len,
                             uint64_t start, uint64_t stop);


			template<typename OpType>
			static void exec(const void *vx, const Nd4jLong* xShapeInfo,
                             const void *vy, const Nd4jLong* yShapeInfo,
                             void *vresult, const Nd4jLong* zShapeInfo,
                             void *vextraParams,
                             uint64_t start, uint64_t stop);

            template<typename OpType>
            static void exec(const void *vx, Nd4jLong xStride,
                             const void *vy, Nd4jLong yStride,
                             void *vresult, Nd4jLong resultStride,
                             void *vextraParams,
                             Nd4jLong len,
                             uint64_t start, uint64_t stop);
        };
    }
}

#endif /* PAIRWISE_TRANSFORM_H_ */
