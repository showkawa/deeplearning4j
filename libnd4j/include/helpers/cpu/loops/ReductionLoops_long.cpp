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

//
// @author raver119@gmail.com
//

#include "ReductionLoops.hpp"

using namespace simdOps;


#include "ReductionLoops.hpp"
#include <system/pointercast.h>
#include <types/types.h>

using namespace simdOps;

namespace sd {

    template<typename X, typename Z>
    template <typename OpType>
    void ReductionLongLoops<X, Z>::innerloopReduce(sd::memory::Workspace* workspace, const X* x, const Nd4jLong* xShapeInfo, Z *z, const Nd4jLong* zShapeInfo, const int* dims, X* extraParams) {
#ifndef INLINE_LOOPS
        ReductionLoops<X,Z,X>::template loopReduce<OpType>(workspace, x, xShapeInfo, z, zShapeInfo, tadShapeInfo, tadOffsets, extraParams, start, stop);
#endif
    }

    template<typename X, typename Y>
    void ReductionLongLoops<X, Y>::wrapper(const int opNum, sd::memory::Workspace* workspace,
                                            const X *x, const Nd4jLong *xShapeInfo,
                                            Y *z, const Nd4jLong *zShapeInfo,
                                            const int *dims, X *extraParams) {
#ifndef INLINE_LOOPS
        DISPATCH_BY_OPNUM_TT(innerloopReduce, PARAMS(workspace, x, xShapeInfo, z, zShapeInfo, dims, extraParams), REDUCE_LONG_OPS);
#endif
    }

    BUILD_DOUBLE_TEMPLATE(template class ND4J_EXPORT ReductionLongLoops, , LIBND4J_TYPES, LONG_TYPES);
}
