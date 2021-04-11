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
// @author Yurii Shyrma (iuriish@yahoo.com), created on 08.11.2018
// @author raver119@gmail.com
//

#include "../scalar_bool.h"
#include <system/op_boilerplate.h>
#include <types/types.h>

#include "../legacy_ops.h"

using namespace simdOps;

////////////////////////////////////////////////////////////////////////
template <typename X, typename Z, typename OpType>
__global__ void scalarAlongDimension(void const* x, Nd4jLong const* xShapeInfo,
                                    void *extraParams,
                                    void *z, Nd4jLong const* zShapeInfo,
                                    void const* scalars,
                                    int *dimension, int dimensionLength,
                                    Nd4jLong const* tadShapeInfo, Nd4jLong const* tadOffsets,
                                    Nd4jLong const* tadShapeInfoZ, Nd4jLong const* tadOffsetsZ) {

    functions::scalar::ScalarBoolTransform<X,Z>::template transformCuda<OpType>(x, xShapeInfo, extraParams, z, zShapeInfo, scalars, dimension, dimensionLength, tadShapeInfo, tadOffsets, tadShapeInfoZ, tadOffsetsZ);
}


////////////////////////////////////////////////////////////////////////
template <typename X, typename Z, typename OpType>
__global__ void scalarSimpleShaped(void const* x, void const* y, Nd4jLong const* xShapeInfo, void *params, void *z, Nd4jLong const* zShapeInfo, int *allocationBuffer) {

    functions::scalar::ScalarBoolTransform<X,Z>::template transformCuda<OpType>(y, x, xShapeInfo, params, z, zShapeInfo, allocationBuffer);
}





// *********************************************************************//
// *********************************************************************//
namespace functions {
namespace scalar    {

////////////////////////////////////////////////////////////////////////
template<typename X, typename Z>
template<typename OpType>
__device__ void  ScalarBoolTransform<X, Z>::transformCuda(void const* vscalar,
                                                        void const* vy, Nd4jLong const* yShapeInfo,
                                                        void *vparams,
                                                        void *vz, Nd4jLong const* zShapeInfo,
                                                        int *allocationBuffer) {
    auto scalar = reinterpret_cast<X const* >(vscalar)[0];
    auto y      = reinterpret_cast<X const* >(vy);
    auto params = reinterpret_cast<X*>(vparams);
    auto z      = reinterpret_cast<Z*>(vz);

    auto yRank   = shape::rank(yShapeInfo);
    auto yEWS    = shape::elementWiseStride(yShapeInfo);
    auto yShape  = shape::shapeOf(yShapeInfo);
    auto yStride = shape::stride(yShapeInfo);

    auto zRank   = shape::rank(zShapeInfo);
    auto zEWS    = shape::elementWiseStride(zShapeInfo);
    auto zShape  = shape::shapeOf(zShapeInfo);
    auto zStride = shape::stride(zShapeInfo);

    int totalThreads = gridDim.x * blockDim.x;
    int tid = blockIdx.x * blockDim.x + threadIdx.x;

    __shared__ int len;
    if(threadIdx.x == 0)
        len = shape::length(yShapeInfo);
    __syncthreads();

    if(yEWS >= 1 && zEWS >= 1 && shape::order(yShapeInfo) == shape::order(zShapeInfo)) {
            transformCuda<OpType>(len, vscalar, vy, yEWS, vparams, vz, zEWS, allocationBuffer);
    }
    else {
        for (Nd4jLong i = tid; i < len; i+= totalThreads)
            z[shape::getIndexOffset(i, zShapeInfo)] = OpType::op(y[shape::getIndexOffset(i, yShapeInfo)], scalar, params);
    }
}

////////////////////////////////////////////////////////////////////////
template<typename X, typename Z>
template<typename OpType>
__device__ void  ScalarBoolTransform<X, Z>::transformCuda(Nd4jLong len,
                                                          void const* vx,
                                                          void const* vy, Nd4jLong yEWS,
                                                          void *vparams,
                                                          void *vz, Nd4jLong zEWS,
                                                          int *allocationBuffer) {

    auto x = reinterpret_cast<X const*>(vx)[0];
    auto y = reinterpret_cast<X const*>(vy);
    auto z = reinterpret_cast<Z*>(vz);
    auto params = reinterpret_cast<X*>(vparams);

    int totalThreads = gridDim.x * blockDim.x;
    int tid = blockIdx.x * blockDim.x + threadIdx.x;

    Nd4jLong i = tid;
    if(yEWS == 1 && zEWS == 1) {
        for (; i < len; i += totalThreads)
            z[i] = OpType::op(y[i], x, params);
    }
    else {
        for (; i < len; i += totalThreads)
            z[i * zEWS] = OpType::op(y[i * yEWS], x, params);
    }
}


////////////////////////////////////////////////////////////////////////
template<typename X, typename Z>
template<typename OpType>
__device__ void  ScalarBoolTransform<X, Z>::transformCuda(void const* vx, Nd4jLong const* xShapeInfo,
                                                        void *vextraParams,
                                                        void *vz, Nd4jLong const* zShapeInfo,
                                                        void const* vscalars,
                                                        int *dimension, int dimensionLength,
                                                        Nd4jLong const* tadShapeInfo, Nd4jLong const* tadOffsets,
                                                        Nd4jLong const* tadShapeInfoZ, Nd4jLong const* tadOffsetsZ) {
    auto x = reinterpret_cast<X const*>(vx);
    auto scalars = reinterpret_cast<X const*>(vscalars);
    auto z = reinterpret_cast<Z*>(vz);
    auto extraParams = reinterpret_cast<X*>(vextraParams);

    if (tadShapeInfoZ == nullptr) {
        tadShapeInfoZ = tadShapeInfo;
        tadOffsetsZ = tadOffsets;
    }

    // tad preparation
    auto tadEws = shape::elementWiseStride(tadShapeInfo);
    auto zEws = shape::elementWiseStride(tadShapeInfoZ);
    auto tadLength = shape::length(tadShapeInfo);//shape::tadLength(xShapeInfo, dimension, dimensionLength);
    auto numTads =shape::length(xShapeInfo) / tadLength;

    if (tadEws > 0 && zEws > 0 && shape::order(tadShapeInfo) == shape::order(zShapeInfo)) {

        // main loop, rolling over tads
        for (int r = blockIdx.x; r < numTads; r += gridDim.x) {
            Z *oZ = z + tadOffsetsZ[r];
            auto oX = x + tadOffsets[r];

            auto s = scalars[r];

            for (int f = threadIdx.x; f < tadLength; f += blockDim.x)
                oZ[f * zEws] = OpType::op(oX[f * tadEws], s, extraParams);
        }
    } else {
        // main loop, rolling over tads
        for (int r = blockIdx.x; r < numTads; r += gridDim.x) {
            Z *oZ = z + tadOffsetsZ[r];
            auto oX = x + tadOffsets[r];

            auto s = scalars[r];

            for (int f = threadIdx.x; f < tadLength; f += blockDim.x)
                oZ[shape::getIndexOffset(f, tadShapeInfoZ)] = OpType::op(oX[shape::getIndexOffset(f, tadShapeInfo)], s, extraParams);
        }
    }
}


////////////////////////////////////////////////////////////////////////
template<typename X, typename Z>
template <typename OpType>
_CUDA_H void ScalarBoolTransform<X, Z>::intermediateAlongDimension(dim3& launchDims, cudaStream_t *stream,
                                                                void const* x, Nd4jLong const* xShapeInfo,
                                                                void *z, Nd4jLong const* zShapeInfo,
                                                                void const* scalars,
                                                                void *extraParams,
                                                                int *dimension, int dimensionLength,
                                                                Nd4jLong const* tadShapeInfo, Nd4jLong const* tadOffsets,
                                                                Nd4jLong const* tadShapeInfoZ, Nd4jLong const* tadOffsetsZ) {

    scalarAlongDimension<X, Z, OpType><<<launchDims.x, launchDims.y, launchDims.z, *stream>>>(x, xShapeInfo, extraParams, z, zShapeInfo, scalars, dimension, dimensionLength, tadShapeInfo, tadOffsets, tadShapeInfoZ, tadOffsetsZ);
    sd::DebugHelper::checkErrorCode(stream, "scalarAlongDim(...) failed");
}

////////////////////////////////////////////////////////////////////////
template<typename X, typename Z>
template<typename OpType>
void _CUDA_H ScalarBoolTransform<X,Z>::intermediateShaped(dim3& launchDims, cudaStream_t *stream,
                                                            void const* vx, Nd4jLong const* xShapeInfo,
                                                            void *vz, Nd4jLong const* zShapeInfo,
                                                            void const* vscalar,
                                                            void *vextraParams, int *allocPointer){

    scalarSimpleShaped<X, Z, OpType><<<launchDims.x, launchDims.y, launchDims.z, *stream>>>(vx, vscalar, xShapeInfo, vextraParams, vz, zShapeInfo, allocPointer);
    sd::DebugHelper::checkErrorCode(stream, "scalarSimpleShaped(...) failed");
}

////////////////////////////////////////////////////////////////////////
template<typename X, typename Y>
void ScalarBoolTransform<X,Y>::executeCudaShaped(dim3& launchDims, cudaStream_t *stream,
                                                int opNum,
                                                void const* vx, Nd4jLong const* xShapeInfo,
                                                void *vz, Nd4jLong const* zShapeInfo,
                                                void const* vscalar,
                                                void const* vextraParams) {

    if (sd::Environment::getInstance().isDebugAndVerbose())
        printf("H14 opNum:[%i]\n", opNum);

    DISPATCH_BY_OPNUM_TT(intermediateShaped, PARAMS(launchDims, stream, vx, xShapeInfo, vz, zShapeInfo, vscalar, const_cast<void*>(vextraParams), nullptr), SCALAR_BOOL_OPS);
}

////////////////////////////////////////////////////////////////////////
template<typename X, typename Y>
void ScalarBoolTransform<X,Y>::executeCudaAlongDimension(dim3& launchDims, cudaStream_t *stream, int opNum, void const* vx, Nd4jLong const* xShapeInfo, void *vz, Nd4jLong const* zShapeInfo, void const* vscalars, void *vextraParams, int *dimension, int dimensionLength, Nd4jLong const* tadShapeInfo, Nd4jLong const* tadOffsets, Nd4jLong const* tadShapeInfoZ, Nd4jLong const* tadOffsetsZ) {
    DISPATCH_BY_OPNUM_TT(intermediateAlongDimension, PARAMS(launchDims, stream, vx, xShapeInfo, vz, zShapeInfo, vscalars, vextraParams, dimension, dimensionLength, tadShapeInfo, tadOffsets, tadShapeInfoZ, tadOffsetsZ), SCALAR_BOOL_OPS);
}

    BUILD_DOUBLE_TEMPLATE(template class ND4J_EXPORT ScalarBoolTransform, , LIBND4J_TYPES, BOOL_TYPES);
}
}

