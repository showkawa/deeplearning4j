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

#include <legacy/NativeOpExecutioner.h>
#include <cuda.h>
#include <system/op_boilerplate.h>
#include <helpers/DebugHelper.h>
#include <array/DataTypeUtils.h>
#include <exceptions/datatype_exception.h>
#include <exceptions/cuda_exception.h>
#include <helpers/CudaLaunchHelper.h>
#include <helpers/ShapeBuilders.h>
#include <helpers/PointersManager.h>

#include <array/ConstantDataBuffer.h>
#include <array/ShapeDescriptor.h>
#include <helpers/ConstantShapeHelper.h>

#include <loops/transform_float.h>
#include <loops/transform_bool.h>
#include <loops/transform_any.h>
#include <loops/transform_same.h>
#include <loops/transform_strict.h>
#include <loops/reduce_float.h>
#include <loops/reduce_same.h>
#include <loops/reduce_bool.h>
#include <loops/reduce_long.h>
#include <loops/indexreduce.h>
#include <loops/pairwise_transform.h>
#include <loops/pairwise_bool.h>
#include <loops/pairwise_int.h>
#include <loops/broadcasting_bool.h>
#include <loops/broadcasting_int.h>
#include <loops/broadcasting.h>
#include <loops/reduce_float.h>
#include <loops/reduce3.h>
#include <loops/summarystatsreduce.h>
#include <loops/transform_same.h>
#include <loops/random.h>
#include <loops/special_kernels.h>
#include <loops/scalar.h>
#include <loops/scalar_bool.h>
#include <loops/scalar_int.h>

using namespace sd;

/**
* This is utility kernel, that updates given special buffer with proper values in device memory
*/
extern "C" __global__ void prepareShapeBuffer(int *dimension, int *maxDimension, Nd4jLong *specialPointer, int rows, sd::DataType dataType) {
    Nd4jLong tid = blockIdx.x * blockDim.x + threadIdx.x;
    if (tid > 0)
        return;

    dimension[0] = 0;
    maxDimension[0] = 1;

    specialPointer[0] = 2;
    specialPointer[1] = rows;
    specialPointer[2] = 1;
    specialPointer[3] = 1;
    specialPointer[4] = 1;
    specialPointer[5] = 0;
    specialPointer[6] = 1;
    specialPointer[7] = 99;

    ArrayOptions::setDataType(specialPointer, dataType);

    //printf("special[0]: [%lld]\n", (long long) specialPointer[0]);
    //shape::printShapeInfoLinear("prepareShapeBuffer", specialPointer);
}


////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execPairwiseTransform(sd::LaunchContext  *lc,
                                    int opNum,
                                    void const* hX, Nd4jLong const* hXShapeInfo,
                                    void const* dX, Nd4jLong const* dXShapeInfo,
                                    void const* hY, Nd4jLong const* hYShapeInfo,
                                    void const* dY, Nd4jLong const* dYShapeInfo,
                                    void *hZ, Nd4jLong const* hZShapeInfo,
                                    void *dZ, Nd4jLong const* dZShapeInfo,
                                    void *extraParams) {

    auto stream = lc->getCudaStream();

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto yType = sd::ArrayOptions::dataType(hYShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo) || shape::isEmpty(hYShapeInfo))
        return;

    if (xType != zType && yType != zType)
        throw std::runtime_error("NativeOpExecutioner::execPairwiseTransform requires Z operand to have either X or Y type");
    if (lc == nullptr)
        throw std::runtime_error("NativeOpExecutioner::execPairwiseTransform: launch context cannot be nullptr !");
    if (stream == nullptr)
        throw std::runtime_error("NativeOpExecutioner::execPairwiseTransform: CUDA stream cannot be nullptr !");

    dim3 launchDims(256, 1024, 8192);

#ifdef __ND4J_EXPERIMENTAL__
    BUILD_PAIRWISE_SELECTOR(xType, yType, zType, functions::pairwise_transforms::PairWiseTransform, ::executeCudaShaped(launchDims, stream, opNum, dX, dXShapeInfo, dY, dYShapeInfo, dZ, dZShapeInfo, extraParams), LIBND4J_TYPES, LIBND4J_TYPES)
#else
    BUILD_SINGLE_SELECTOR_THRICE(xType, functions::pairwise_transforms::PairWiseTransform, ::executeCudaShaped(launchDims, stream, opNum, dX, dXShapeInfo, dY, dYShapeInfo, dZ, dZShapeInfo, extraParams), LIBND4J_TYPES)
#endif

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execPairwiseTransform failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execPairwiseBoolTransform( sd::LaunchContext  *lc,
                                                    int opNum,
                                                    void const* hX, Nd4jLong const* hXShapeInfo,
                                                    void const* dX, Nd4jLong const* dXShapeInfo,
                                                    void const* hY, Nd4jLong const* hYShapeInfo,
                                                    void const* dY, Nd4jLong const* dYShapeInfo,
                                                    void *hZ, Nd4jLong const* hZShapeInfo,
                                                    void *dZ, Nd4jLong const* dZShapeInfo,
                                                    void *extraParams) {

	auto stream = lc->getCudaStream();

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto yType = sd::ArrayOptions::dataType(hYShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo) || shape::isEmpty(hYShapeInfo))
        return;

    if (!DataTypeUtils::isB(zType))
		throw sd::datatype_exception::build("NativeOpExecutioner::execPairwiseBoolTransform wrong Z operand data type", sd::DataType::BOOL, zType);

    if (yType != xType)
        throw sd::datatype_exception::build("NativeOpExecutioner::execPairwiseBoolTransform both operands must have same data type", xType, yType);

    dim3 launchDims(256, 1024, 16384);

    BUILD_DOUBLE_SELECTOR(xType, zType, functions::pairwise_transforms::PairWiseBoolTransform, ::executeCudaShaped(launchDims, stream, opNum, dX, dXShapeInfo, dY, dYShapeInfo, dZ, dZShapeInfo, extraParams), LIBND4J_TYPES, BOOL_TYPES)

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execPairwiseBoolTransform failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execPairwiseIntTransform( sd::LaunchContext  *lc,
                                                     int opNum,
                                                     void const* hX, Nd4jLong const* hXShapeInfo,
                                                     void const* dX, Nd4jLong const* dXShapeInfo,
                                                     void const* hY, Nd4jLong const* hYShapeInfo,
                                                     void const* dY, Nd4jLong const* dYShapeInfo,
                                                     void * hZ, Nd4jLong const* hZShapeInfo,
                                                     void * dZ, Nd4jLong const* dZShapeInfo,
                                                     void *extraParams) {

    auto stream = lc->getCudaStream();

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto yType = sd::ArrayOptions::dataType(hYShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo) || shape::isEmpty(hYShapeInfo))
        return;

    if (!DataTypeUtils::isZ(zType))
        throw sd::datatype_exception::build("NativeOpExecutioner::execPairwiseIntTransform wrong Z operand data type", sd::DataType::BOOL, zType);

    if (yType != xType || zType != xType)
        throw sd::datatype_exception::build("NativeOpExecutioner::execPairwiseIntTransform both operands must have same data type", xType, yType);

    dim3 launchDims(256, 1024, 16384);

    BUILD_SINGLE_SELECTOR(xType, functions::pairwise_transforms::PairWiseIntTransform, ::executeCudaShaped(launchDims, stream, opNum, dX, dXShapeInfo, dY, dYShapeInfo, dZ, dZShapeInfo, extraParams), INTEGER_TYPES)

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execPairwiseIntTransform failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execSummaryStatsScalar(sd::LaunchContext  *lc,
                                    int opNum,
                                    void const* hX, Nd4jLong const* hXShapeInfo,
                                    void const* dX, Nd4jLong const* dXShapeInfo,
                                    void *extraParams,
                                    void *hZ, Nd4jLong const* hZShapeInfo,
                                    void *dZ, Nd4jLong const* dZShapeInfo,
                                    bool biasCorrected) {

	auto stream = lc->getCudaStream();
    auto reductionPointer = lc->getReductionPointer();

    dim3 launchDims = dim3(256, CUDA_BLOCK_SIZE, 1024);

	auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
	auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    BUILD_DOUBLE_SELECTOR(xType, zType, functions::summarystats::SummaryStatsReduce, ::execSummaryStatsReduceScalar(launchDims, stream, opNum, dX, dXShapeInfo, hXShapeInfo, extraParams, dZ, dZShapeInfo, hZShapeInfo, nullptr, nullptr, biasCorrected, reductionPointer), LIBND4J_TYPES, FLOAT_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execSummaryStatsScalar failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execBroadcastBool(sd::LaunchContext  *lc,
                            int opNum,
                            void const* hX, Nd4jLong const* hXShapeInfo,
                            void const* dX, Nd4jLong const* dXShapeInfo,
                            void const* hY, Nd4jLong const* hYShapeInfo,
                            void const* dY, Nd4jLong const* dYShapeInfo,
                            void *hZ, Nd4jLong const* hZShapeInfo,
                            void *dZ, Nd4jLong const* dZShapeInfo,
                            void *extraParams,
                            int *dimension, int dimensionLength,
                            Nd4jLong const* tadOnlyShapeInfo,  Nd4jLong const* tadOffsets,
                            Nd4jLong const* tadOnlyShapeInfoZ, Nd4jLong const* tadOffsetsZ) {

	auto stream = lc->getCudaStream();

	auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
	auto yType = sd::ArrayOptions::dataType(hYShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo) || shape::isEmpty(hYShapeInfo))
        return;

	if (!DataTypeUtils::isB(zType))
        throw std::runtime_error("NativeOpExecutioner::execBroadcastBool requires Z operand to have BOOL type");

    if (yType != xType)
        throw std::runtime_error("NativeOpExecutioner::execBroadcastBool requires both X & Y operands to have same type");

	if (sd::Environment::getInstance().isDebugAndVerbose())
		printf("F3B opNum:[%i]\n", opNum);

	dim3 launchDims(256, 256, 1024);

	BUILD_DOUBLE_SELECTOR(xType, zType, functions::broadcast::BroadcastBool, ::execBroadcast(launchDims, stream, opNum, dX, dXShapeInfo, dY, dYShapeInfo, dZ, dZShapeInfo, extraParams, dimension, dimensionLength, tadOnlyShapeInfo, tadOffsets, tadOnlyShapeInfoZ, tadOffsetsZ), LIBND4J_TYPES, BOOL_TYPES)

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execBroadcastBool failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execBroadcastBool(sd::LaunchContext* lc, const int opNum,
                                        const void *hX, const Nd4jLong *hXShapeInfo,
                                        const void *dX, const Nd4jLong *dXShapeInfo,
                                        const void *hY, const Nd4jLong *hYShapeInfo,
                                        const void *dY, const Nd4jLong *dYShapeInfo,
                                              void *hZ, const Nd4jLong *hZShapeInfo,
                                              void *dZ, const Nd4jLong *dZShapeInfo,
                                              void *extraParams) {

    if (shape::isEmpty(hXShapeInfo) || shape::isEmpty(hYShapeInfo))
        return;

    auto stream = lc->getCudaStream();

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    dim3 launchDims;

    launchDims.y = MAX_NUM_THREADS / 4; // threadsPerBlock
    launchDims.x = (shape::length(hZShapeInfo) + launchDims.y - 1) / launchDims.y; // blocksPerGrid
    launchDims.z = 1024; // shared memory

    BUILD_DOUBLE_SELECTOR(xType, zType, functions::broadcast::BroadcastBool, ::execBroadcast(launchDims, stream, opNum, dX, dXShapeInfo, dY, dYShapeInfo, dZ, dZShapeInfo, extraParams), LIBND4J_TYPES, BOOL_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execBroadcastBool failed", res);
}


void NativeOpExecutioner::execInverseBroadcastBool(sd::LaunchContext  *lc,
                                                   int opNum,
                                                   void const* hX, Nd4jLong const* hXShapeInfo,
                                                   void const* dX, Nd4jLong const* dXShapeInfo,
                                                   void const* hY, Nd4jLong const* hYShapeInfo,
                                                   void const* dY, Nd4jLong const* dYShapeInfo,
                                                   void* hZ, Nd4jLong const* hZShapeInfo,
                                                   void *dZ, Nd4jLong const* dZShapeInfo,
                                                   void *extraParams,
                                                   int *dimension, int dimensionLength,
                                                   Nd4jLong const* tadOnlyShapeInfo,  Nd4jLong const* tadOffsets,
                                                   Nd4jLong const* tadOnlyShapeInfoZ, Nd4jLong const* tadOffsetsZ) {
    auto stream = lc->getCudaStream();

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto yType = sd::ArrayOptions::dataType(hYShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo) || shape::isEmpty(hYShapeInfo))
        return;

    if (!DataTypeUtils::isB(zType))
        throw std::runtime_error("NativeOpExecutioner::execBroadcastBool requires Z operand to have BOOL type");

    if (yType != xType)
        throw std::runtime_error("NativeOpExecutioner::execBroadcastBool requires both X & Y operands to have same type");

    dim3 launchDims(256, 256, 1024);

    BUILD_DOUBLE_SELECTOR(xType, zType, functions::broadcast::BroadcastBool, ::execInverseBroadcast(launchDims, stream, opNum, dX, dXShapeInfo, dY, dYShapeInfo, dZ, dZShapeInfo, extraParams, dimension, dimensionLength, tadOnlyShapeInfo, tadOffsets, tadOnlyShapeInfoZ, tadOffsetsZ), LIBND4J_TYPES, BOOL_TYPES)

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execInverseBroadcastBool failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execBroadcastInt(sd::LaunchContext  *lc,
                                            int opNum,
                                            void const* hX, Nd4jLong const* hXShapeInfo,
                                            void const* dX, Nd4jLong const* dXShapeInfo,
                                            void const* hY, Nd4jLong const* hYShapeInfo,
                                            void const* dY, Nd4jLong const* dYShapeInfo,
                                            void *hZ, Nd4jLong const* hZShapeInfo,
                                            void *dZ, Nd4jLong const* dZShapeInfo,
                                            int *dimension, int dimensionLength,
                                            Nd4jLong const* tadOnlyShapeInfo, Nd4jLong const* tadOffsets,
                                            Nd4jLong const* tadOnlyShapeInfoZ,Nd4jLong const* tadOffsetsZ) {

    auto stream = lc->getCudaStream();

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto yType = sd::ArrayOptions::dataType(hYShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo) || shape::isEmpty(hYShapeInfo))
        return;

    if (!DataTypeUtils::isZ(zType))
        throw std::runtime_error("NativeOpExecutioner::execBroadcastInt requires Z operand to have INT type");

    if (yType != xType || zType != xType)
        throw std::runtime_error("NativeOpExecutioner::execBroadcastInt requires both X & Y operands to have same type");

    dim3 launchDims(256, 256, 1024);

    BUILD_SINGLE_SELECTOR(xType, functions::broadcast::BroadcastInt, ::execBroadcast(launchDims, stream, opNum, dX, dXShapeInfo, dY, dYShapeInfo, dZ, dZShapeInfo, dimension, dimensionLength, tadOnlyShapeInfo, tadOffsets, tadOnlyShapeInfoZ, tadOffsetsZ), INTEGER_TYPES)

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execBroadcastBool failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execBroadcastInt(sd::LaunchContext* lc, const int opNum,
                                           const void *hX, const Nd4jLong *hXShapeInfo,
                                           const void *dX, const Nd4jLong *dXShapeInfo,
                                           const void *hY, const Nd4jLong *hYShapeInfo,
                                           const void *dY, const Nd4jLong *dYShapeInfo,
                                                 void *hZ, const Nd4jLong *hZShapeInfo,
                                                 void *dZ, const Nd4jLong *dZShapeInfo) {

    auto stream = lc->getCudaStream();

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto yType = sd::ArrayOptions::dataType(hYShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo) || shape::isEmpty(hYShapeInfo))
        return;

    if (!DataTypeUtils::isZ(zType))
        throw std::runtime_error("NativeOpExecutioner::execBroadcastInt requires Z operand to have INT type");

    if (yType != xType || zType != xType)
        throw std::runtime_error("NativeOpExecutioner::execBroadcastInt requires both X & Y operands to have same type");

    dim3 launchDims;

    launchDims.y = MAX_NUM_THREADS / 4; // threadsPerBlock
    launchDims.x = (shape::length(hZShapeInfo) + launchDims.y - 1) / launchDims.y; // blocksPerGrid
    launchDims.z = 1024; // shared memory

    BUILD_SINGLE_SELECTOR(xType, functions::broadcast::BroadcastInt, ::execBroadcast(launchDims, stream, opNum, dX, dXShapeInfo, dY, dYShapeInfo, dZ, dZShapeInfo), INTEGER_TYPES)

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execBroadcastBool failed", res);
}

void NativeOpExecutioner::execInverseBroadcastInt(sd::LaunchContext  *lc,
                                                   int opNum,
                                                   void const* hX, Nd4jLong const* hXShapeInfo,
                                                   void const* dX, Nd4jLong const* dXShapeInfo,
                                                   void const* hY, Nd4jLong const* hYShapeInfo,
                                                   void const* dY, Nd4jLong const* dYShapeInfo,
                                                   void *hZ, Nd4jLong const* hZShapeInfo,
                                                   void *dZ, Nd4jLong const* dZShapeInfo,
                                                   int *dimension, int dimensionLength,
                                                   Nd4jLong const* tadOnlyShapeInfo, Nd4jLong const* tadOffsets,
                                                   Nd4jLong const* tadOnlyShapeInfoZ,Nd4jLong const* tadOffsetsZ) {
    auto stream = lc->getCudaStream();

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto yType = sd::ArrayOptions::dataType(hYShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo) || shape::isEmpty(hYShapeInfo))
        return;

    if (!DataTypeUtils::isZ(zType))
        throw std::runtime_error("NativeOpExecutioner::execBroadcastInt requires Z operand to have INT type");

    if (yType != xType || zType != xType)
        throw std::runtime_error("NativeOpExecutioner::execBroadcastInt requires both X & Y operands to have same type");

    if (sd::Environment::getInstance().isDebugAndVerbose())
        printf("F3BI opNum:[%i]\n", opNum);

    dim3 launchDims(256, 256, 1024);

    BUILD_SINGLE_SELECTOR(xType, functions::broadcast::BroadcastInt, ::execInverseBroadcast(launchDims, stream, opNum, dX, dXShapeInfo, dY, dYShapeInfo, dZ, dZShapeInfo, dimension, dimensionLength, tadOnlyShapeInfo, tadOffsets, tadOnlyShapeInfoZ, tadOffsetsZ), INTEGER_TYPES)

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execInverseBroadcastInt failed", res);
}

////////////////////////////////////////////////////////////////////////
/**
 *
 * @param opNum
 * @param dX
 * @param dXShapeInfo
 * @param dY
 * @param dYShapeInfo
 * @param dZ
 * @param dZShapeInfo
 * @param dimension
 * @param dimensionLength
 */
void NativeOpExecutioner::execBroadcast(sd::LaunchContext  *lc,
		                              int opNum,
		                              void const* hX, Nd4jLong const* hXShapeInfo,
		                              void const* dX, Nd4jLong const* dXShapeInfo,
		                              void const* hY, Nd4jLong const* hYShapeInfo,
		                              void const* dY, Nd4jLong const* dYShapeInfo,
		                              void *hZ, Nd4jLong const* hZShapeInfo,
		                              void *dZ, Nd4jLong const* dZShapeInfo,
		                              int *dimension, int dimensionLength,
		                              Nd4jLong const* tadOnlyShapeInfo, Nd4jLong const* tadOffsets,
		                              Nd4jLong const* tadOnlyShapeInfoZ,Nd4jLong const* tadOffsetsZ) {

	auto stream = lc->getCudaStream();

	auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
	auto yType = sd::ArrayOptions::dataType(hYShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo) || shape::isEmpty(hYShapeInfo))
        return;

	dim3 launchDims(256, 256, 1024);

#ifdef __ND4J_EXPERIMENTAL__
	BUILD_PAIRWISE_SELECTOR(xType, yType, zType, functions::broadcast::Broadcast, ::execBroadcast(launchDims, stream, opNum, dX, dXShapeInfo, dY, dYShapeInfo, dZ, dZShapeInfo, dimension, dimensionLength, tadOnlyShapeInfo, tadOffsets, tadOnlyShapeInfoZ, tadOffsetsZ), LIBND4J_TYPES, LIBND4J_TYPES);
#else
    BUILD_SINGLE_SELECTOR_THRICE(xType, functions::broadcast::Broadcast, ::execBroadcast(launchDims, stream, opNum, dX, dXShapeInfo, dY, dYShapeInfo, dZ, dZShapeInfo, dimension, dimensionLength, tadOnlyShapeInfo, tadOffsets, tadOnlyShapeInfoZ, tadOffsetsZ), LIBND4J_TYPES);
#endif

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execBroadcast failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execBroadcast(sd::LaunchContext  *lc, const int opNum,
                                      const void *hX, const Nd4jLong *hXShapeInfo,
                                      const void *dX, const Nd4jLong *dXShapeInfo,
                                      const void *hY, const Nd4jLong *hYShapeInfo,
                                      const void *dY, const Nd4jLong *dYShapeInfo,
                                            void *hZ, const Nd4jLong *hZShapeInfo,
                                            void *dZ, const Nd4jLong *dZShapeInfo) {

    auto stream = lc->getCudaStream();

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto yType = sd::ArrayOptions::dataType(hYShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo) || shape::isEmpty(hYShapeInfo))
        return;

    dim3 launchDims;

    launchDims.y = MAX_NUM_THREADS / 4; // threadsPerBlock
    launchDims.x = (shape::length(hZShapeInfo) + launchDims.y - 1) / launchDims.y; // blocksPerGrid
    launchDims.z = 1024; // shared memory

#ifdef __ND4J_EXPERIMENTAL__
    BUILD_PAIRWISE_SELECTOR(xType, yType, zType, functions::broadcast::Broadcast, ::execBroadcast(launchDims, stream, opNum, dX, dXShapeInfo, dY, dYShapeInfo, dZ, dZShapeInfo), LIBND4J_TYPES, LIBND4J_TYPES);
#else
    BUILD_SINGLE_SELECTOR_THRICE(xType, functions::broadcast::Broadcast, ::execBroadcast(launchDims, stream, opNum, dX, dXShapeInfo, dY, dYShapeInfo, dZ, dZShapeInfo), LIBND4J_TYPES);
#endif

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execBroadcast failed", res);
}

void NativeOpExecutioner::execInverseBroadcast(sd::LaunchContext  *lc,
                                               int opNum,
                                               void const* hX, Nd4jLong const* hXShapeInfo,
                                               void const* dX, Nd4jLong const* dXShapeInfo,
                                               void const* hY, Nd4jLong const* hYShapeInfo,
                                               void const* dY, Nd4jLong const* dYShapeInfo,
                                               void *hZ, Nd4jLong const* hZShapeInfo,
                                               void *dZ, Nd4jLong const* dZShapeInfo,
                                               int *dimension, int dimensionLength,
                                               Nd4jLong const* tadOnlyShapeInfo, Nd4jLong const* tadOffsets,
                                               Nd4jLong const* tadOnlyShapeInfoZ,Nd4jLong const* tadOffsetsZ) {

    auto stream = lc->getCudaStream();

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto yType = sd::ArrayOptions::dataType(hYShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo) || shape::isEmpty(hYShapeInfo))
        return;

    dim3 launchDims(256, 256, 1024);

#ifdef __ND4J_EXPERIMENTAL__
    BUILD_PAIRWISE_SELECTOR(xType, yType, zType, functions::broadcast::Broadcast, ::execInverseBroadcast(launchDims, stream, opNum, dX, dXShapeInfo, dY, dYShapeInfo, dZ, dZShapeInfo, dimension, dimensionLength, tadOnlyShapeInfo, tadOffsets, tadOnlyShapeInfoZ, tadOffsetsZ), LIBND4J_TYPES, LIBND4J_TYPES);
#else
    BUILD_SINGLE_SELECTOR_THRICE(xType, functions::broadcast::Broadcast, ::execInverseBroadcast(launchDims, stream, opNum, dX, dXShapeInfo, dY, dYShapeInfo, dZ, dZShapeInfo, dimension, dimensionLength, tadOnlyShapeInfo, tadOffsets, tadOnlyShapeInfoZ, tadOffsetsZ), LIBND4J_TYPES);
#endif

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execInverseBroadcast failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execReduceSame(sd::LaunchContext  *lc,
                            int opNum,
                            void const* hX, Nd4jLong const* hXShapeInfo,
                            void const* dX, Nd4jLong const* dXShapeInfo,
                            void *extraParams,
                            void *hZ, Nd4jLong const* hZShapeInfo,
                            void *dZ, Nd4jLong const* dZShapeInfo,
                            int *dimension, int dimensionLength) {

	auto stream = lc->getCudaStream();
	auto reductionPointer = lc->getReductionPointer();

    if (sd::Environment::getInstance().isDebugAndVerbose())
        printf("SF7 opNum:[%i]\n", opNum);

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (zType != xType)
        throw datatype_exception::build("NativeOpExecutioner::execReduceSame requires both X & Z operands to have same type", xType, zType);

    auto numBlocks = shape::length(hZShapeInfo);
    dim3 launchDims(numBlocks == 0 ? 1 : numBlocks, CUDA_BLOCK_SIZE, 1024);

    BUILD_SINGLE_SELECTOR(xType, functions::reduce::ReduceSameFunction, ::execReduceXD(launchDims, stream, opNum, dX, dXShapeInfo, hXShapeInfo, extraParams, reductionPointer, dZ, dZShapeInfo, hZShapeInfo, dimension), LIBND4J_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execReduceSame failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execReduceLong(sd::LaunchContext  *lc,
                            int opNum,
                            void const* hX, Nd4jLong const* hXShapeInfo,
                            void const* dX, Nd4jLong const* dXShapeInfo,
                            void *extraParams,
                            void *hZ, Nd4jLong const* hZShapeInfo,
                            void *dZ, Nd4jLong const* dZShapeInfo,
                            int *dimension,int dimensionLength) {

	auto stream = lc->getCudaStream();
	auto reductionPointer = lc->getReductionPointer();

    if (sd::Environment::getInstance().isDebugAndVerbose())
        printf("LF7 opNum:[%i]\n", opNum);

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (zType != sd::DataType::INT64)
        throw datatype_exception::build("NativeOpExecutioner::execReduceLong wrong Z data type", sd::DataType::INT64, zType);

    auto numBlocks = shape::length(hZShapeInfo);
    dim3 launchDims(numBlocks == 0 ? 1 : numBlocks, CUDA_BLOCK_SIZE, 1024);

    BUILD_DOUBLE_SELECTOR(xType, zType, functions::reduce::ReduceLongFunction, ::execReduceXD(launchDims, stream, opNum, dX, dXShapeInfo, hXShapeInfo, extraParams, reductionPointer, dZ, dZShapeInfo, hZShapeInfo, dimension), LIBND4J_TYPES, LONG_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execReduceLong failed", res);

}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execReduceBool(sd::LaunchContext  *lc,
                            int opNum,
                            void const* hX, Nd4jLong const* hXShapeInfo,
                            void const* dX, Nd4jLong const* dXShapeInfo,
                            void *extraParams,
                            void *hZ, Nd4jLong const* hZShapeInfo,
                            void *dZ, Nd4jLong const* dZShapeInfo,
                            int *dimension, int dimensionLength) {

	auto stream = lc->getCudaStream();
	auto reductionPointer = lc->getReductionPointer();

    if (sd::Environment::getInstance().isDebugAndVerbose())
        printf("BF7 opNum:[%i]\n", opNum);

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (zType != sd::DataType::BOOL)
        throw std::runtime_error("NativeOpExecutioner::execReduceBool requires Z operand to have BOOL type");

    auto numBlocks = shape::length(hZShapeInfo);
    dim3 launchDims(numBlocks == 0 ? 1 : numBlocks, CUDA_BLOCK_SIZE, 1024);

    BUILD_DOUBLE_SELECTOR(xType, zType, functions::reduce::ReduceBoolFunction, ::execReduceXD(launchDims, stream, opNum, dX, dXShapeInfo, hXShapeInfo, extraParams, reductionPointer, dZ, dZShapeInfo, hZShapeInfo, dimension), LIBND4J_TYPES, BOOL_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execReduceBool failed", res);
}

////////////////////////////////////////////////////////////////////////
/**
 *
 * @param opNum
 * @param dX
 * @param dXShapeInfo
 * @param extraParams
 * @param dZ
 * @param dZShapeInfo
 */
void  NativeOpExecutioner::execReduceFloat(sd::LaunchContext  *lc,
                                          int opNum,
                                          const void *hX, const Nd4jLong *hXShapeInfo,
                                          const void *dX, const Nd4jLong *dXShapeInfo,
                                          void *extraParams,
                                          void *hZ, const Nd4jLong *hZShapeInfo,
                                          void *dZ, const Nd4jLong *dZShapeInfo,
                                          int *dimension, int dimensionLength) {

    auto stream = lc->getCudaStream();
    auto reductionPointer = lc->getReductionPointer();

    if (sd::Environment::getInstance().isDebugAndVerbose())
        printf("F8 opNum:[%i]\n", opNum);

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    auto numBlocks = shape::length(hZShapeInfo);
    dim3 launchDims(numBlocks == 0 ? 1 : numBlocks, 256, 32768);

    BUILD_DOUBLE_SELECTOR(xType, zType, functions::reduce::ReduceFloatFunction, ::execReduceXD(launchDims, stream, opNum, dX, dXShapeInfo, hXShapeInfo, extraParams, reductionPointer, dZ, dZShapeInfo, hZShapeInfo, dimension), LIBND4J_TYPES, FLOAT_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execReduceFloat failed", res);
}

////////////////////////////////////////////////////////////////////////
/**
 *
 * @param opNum
 * @param dX
 * @param dXShapeInfo
 * @param extraParams
 * @param dZ
 * @param dZShapeInfo
 * @param dimension
 * @param dimensionLength
 */
void NativeOpExecutioner::execIndexReduce(sd::LaunchContext  *lc,
                                int opNum,
                                void const* hX, Nd4jLong const* hXShapeInfo,
                                void const* dX, Nd4jLong const* dXShapeInfo,
                                void *extraParams,
                                void *hZ, Nd4jLong const* hZShapeInfo,
                                void *dZ, Nd4jLong const* dZShapeInfo,
                                int *dimension, int dimensionLength,
                                Nd4jLong const* tadShapeInfo, Nd4jLong const* tadOffsets) {

	auto stream = lc->getCudaStream();
	auto reductionPointer = lc->getReductionPointer();
	auto allocationPointer = lc->getAllocationPointer();

	if (sd::Environment::getInstance().isDebugAndVerbose())
		printf("F2 opNum:[%i]\n", opNum);

	auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);
	auto numBlocks = shape::length(hZShapeInfo);
	auto tadLength = shape::length(hXShapeInfo) / numBlocks;
    dim3 launchDims(numBlocks == 0 ? 1 : numBlocks, tadLength < CUDA_BLOCK_SIZE ? tadLength : CUDA_BLOCK_SIZE, 1024);

    if (zType != sd::DataType::INT64 && zType != sd::DataType::INT32)
        throw datatype_exception::build("NativeOpExecutioner::execIndexReduce requires Z operand to have INT32/INT64 type", zType);

	auto dz = reinterpret_cast<Nd4jLong*>(dZ);

	BUILD_DOUBLE_SELECTOR(xType, zType, functions::indexreduce::IndexReduce,  ::executeIndexReduce(launchDims, stream, opNum, dX, dXShapeInfo, shape::rank(hXShapeInfo), extraParams, dz, dZShapeInfo, shape::rank(hZShapeInfo), dimension, dimensionLength, 1, allocationPointer, reductionPointer, tadShapeInfo, tadOffsets), LIBND4J_TYPES, INDEXING_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execIndexReduce failed", res);
}



/**
 *
 * @param opNum
 * @param dX
 * @param dXShapeInfo
 * @param extraParams
 */
////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execIndexReduceScalar(sd::LaunchContext  *lc,
											int opNum,
											void const* hX, Nd4jLong const* hXShapeInfo,
        									void const* dX, Nd4jLong const* dXShapeInfo,
        									void *extraParams,
        									void *hZ, Nd4jLong const* hZShapeInfo,
											void *dZ, Nd4jLong const* dZShapeInfo){

	if (sd::Environment::getInstance().isDebug())
		printf("F1 opNum:[%i]\n", opNum);

	auto stream = lc->getCudaStream();
	auto reductionPointer = lc->getReductionPointer();
	auto allocationPointer = lc->getAllocationPointer();

    auto xLength = shape::length(hXShapeInfo);
    auto blockWidth = 256;
    auto numBlocks = CudaLaunchHelper::getReductionBlocks(xLength, blockWidth);
    dim3 launchDims(numBlocks == 0 ? 1 : numBlocks, CUDA_BLOCK_SIZE, 1024);

	if (sd::Environment::getInstance().isDebugAndVerbose() && launchDims.x == 1)
		printf("AF1 opNum:[%i]\n", opNum);

	auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    // FIXME: we want Z to be one of integer types
	//if (!DataTypeUtils::isZ(zType))
	//    throw sd::datatype_exception("NativeOpExecutioner::execIndexReduceScalar requires Z operand to have one of integer types")
	if (zType != sd::DataType::INT64 && zType != sd::DataType::INT32)
        throw sd::datatype_exception::build("NativeOpExecutioner::execIndexReduceScalar requires Z operand to have INT32/INT64 data type", zType);

    auto dz = reinterpret_cast<Nd4jLong*>(dZ);

    BUILD_DOUBLE_SELECTOR(xType, zType, functions::indexreduce::IndexReduce, ::executeIndexReduceScalar(launchDims, stream,
                                                                                                opNum,
                                                                                                dX, dXShapeInfo, shape::rank(hXShapeInfo),
                                                                                                extraParams,
                                                                                                dz, dZShapeInfo, 0,
                                                                                                nullptr, 0,
                                                                                                1,
                                                                                                allocationPointer, reductionPointer,
                                                                                                nullptr, nullptr), LIBND4J_TYPES, INDEXING_TYPES);
    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execIndexReduceScalar failed", res);
}


////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execReduceFloatScalar(sd::LaunchContext  *lc,
                                                int opNum,
                                                void const* hX, Nd4jLong const* hXShapeInfo,
                                                void const* dX, Nd4jLong const* dXShapeInfo,
                                                void *extraParams,
                                                void *hZ, Nd4jLong const* hZShapeInfo,
                                                void *dZ, Nd4jLong const* dZShapeInfo) {

    auto stream = lc->getCudaStream();
    auto reductionPointer = lc->getReductionPointer();

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    auto xLength = shape::length(hXShapeInfo);
    auto blockWidth = 256;
    auto numBlocks = CudaLaunchHelper::getReductionBlocks(xLength, blockWidth);
    dim3 launchDims(numBlocks == 0 ? 1 : numBlocks, CUDA_BLOCK_SIZE, 1024);

    BUILD_DOUBLE_SELECTOR(xType, zType, functions::reduce::ReduceFloatFunction, ::execReduceScalar(launchDims, stream, opNum, dX,dXShapeInfo, hXShapeInfo, extraParams, dZ,dZShapeInfo, hZShapeInfo, nullptr, 0, reductionPointer, nullptr), LIBND4J_TYPES, FLOAT_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execReduceFloatScalar failed", res);
}


////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execReduceBoolScalar(sd::LaunchContext  *lc,
                                        int opNum,
                                        void const* hX, Nd4jLong const* hXShapeInfo,
                                        void const* dX, Nd4jLong const* dXShapeInfo,
                                        void *extraParams,
                                        void *hZ, Nd4jLong const* hZShapeInfo,
                                        void *dZ, Nd4jLong const* dZShapeInfo) {

    auto stream = lc->getCudaStream();
    auto reductionPointer = lc->getReductionPointer();

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (zType != sd::DataType::BOOL)
        throw std::runtime_error("NativeOpExecutioner::execReduceBoolScalar requires Z operand to have BOOL type");

    auto xLength = shape::length(hXShapeInfo);
    auto blockWidth = CUDA_BLOCK_SIZE;
    auto numBlocks = CudaLaunchHelper::getReductionBlocks(xLength, blockWidth);
    dim3 launchDims(numBlocks == 0 ? 1 : numBlocks, blockWidth, 1024);

    BUILD_DOUBLE_SELECTOR(xType, zType, functions::reduce::ReduceBoolFunction, ::execReduceScalar(launchDims, stream, opNum, dX, dXShapeInfo, hXShapeInfo, extraParams, dZ, dZShapeInfo, hZShapeInfo, nullptr, 0, reductionPointer, nullptr), LIBND4J_TYPES, BOOL_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execReduceBoolScalar failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execReduceSameScalar(sd::LaunchContext  *lc,
                                        int opNum,
                                        void const* hX, Nd4jLong const* hXShapeInfo,
                                        void const* dX, Nd4jLong const* dXShapeInfo,
                                        void *extraParams,
                                        void *hZ, Nd4jLong const* hZShapeInfo,
                                        void *dZ, Nd4jLong const* dZShapeInfo) {

    auto stream = lc->getCudaStream();
    auto reductionPointer = lc->getReductionPointer();

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (zType != xType)
        throw datatype_exception::build("NativeOpExecutioner::execReduceSameScalar requires both X & Z operands to have same type", xType, zType);

    auto xLength = shape::length(hXShapeInfo);
    auto blockWidth = CUDA_BLOCK_SIZE;
    auto numBlocks = CudaLaunchHelper::getReductionBlocks(xLength, blockWidth);
    dim3 launchDims(numBlocks == 0 ? 1 : numBlocks, blockWidth, 1024);

    BUILD_SINGLE_SELECTOR(xType, functions::reduce::ReduceSameFunction, ::execReduceScalar(launchDims, stream, opNum, dX, dXShapeInfo, hXShapeInfo, extraParams, dZ, dZShapeInfo, hZShapeInfo, nullptr, 0, reductionPointer, nullptr), LIBND4J_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execReduceSameScalar failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execReduceLongScalar(sd::LaunchContext  *lc,
                                    int opNum,
                                    void const* hX, Nd4jLong const* hXShapeInfo,
                                    void const* dX, Nd4jLong const* dXShapeInfo,
                                    void *extraParams,
                                    void *hZ, Nd4jLong const* hZShapeInfo,
                                    void *dZ, Nd4jLong const* dZShapeInfo) {

    auto stream = lc->getCudaStream();
    auto reductionPointer = lc->getReductionPointer();

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (zType != sd::DataType::INT64)
        throw datatype_exception::build("NativeOpExecutioner::execReduceLongScalar wrong Z data type", sd::DataType::INT64, zType);

    auto xLength = shape::length(hXShapeInfo);
    auto blockWidth = CUDA_BLOCK_SIZE;
    auto numBlocks = CudaLaunchHelper::getReductionBlocks(xLength, blockWidth);
    dim3 launchDims(numBlocks == 0 ? 1 : numBlocks, blockWidth, 1024);

    BUILD_DOUBLE_SELECTOR(xType, zType, functions::reduce::ReduceLongFunction, ::execReduceScalar(launchDims, stream, opNum, dX, dXShapeInfo, hXShapeInfo, extraParams, dZ, dZShapeInfo, hZShapeInfo, nullptr, 0, reductionPointer, nullptr), LIBND4J_TYPES, LONG_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execReduceLongScalar failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execTransformSame(sd::LaunchContext  *lc,
									int opNum,
                                   	void const* hX, Nd4jLong const* hXShapeInfo,
                                   	void const* dX, Nd4jLong const* dXShapeInfo,
                                   	void *hZ, Nd4jLong const* hZShapeInfo,
                                   	void *dZ, Nd4jLong const* dZShapeInfo,
                                   	void *extraParams,
                                   	Nd4jLong const* tadShapeInfo, Nd4jLong const* tadOffsets) {

    auto stream = lc->getCudaStream();

    auto xRank = shape::rank(hXShapeInfo);
    auto zRank = shape::rank(hZShapeInfo);
    auto xType = ArrayOptions::dataType(hXShapeInfo);
    auto zType = ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo)) {
        return;
    }

    if (xType != zType) {
        throw std::runtime_error("NativeOpExecutioner::execTransformSame requires X & Z to have same type");
    }

    dim3 launchDims(512, 512, 16384);
    BUILD_SINGLE_SELECTOR(xType, functions::transform::TransformSame, ::executeTransformShaped(launchDims, stream, opNum, dX, dXShapeInfo, xRank, extraParams, dZ, dZShapeInfo, zRank, nullptr, nullptr, nullptr, nullptr), LIBND4J_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execTransformSame failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execTransformBool(sd::LaunchContext  *lc,
                                int opNum,
                                void const* hX, Nd4jLong const* hXShapeInfo,
                                void const* dX, Nd4jLong const* dXShapeInfo,
                                void *hZ, Nd4jLong const* hZShapeInfo,
                                void *dZ, Nd4jLong const* dZShapeInfo,
                                void *extraParams,
                                Nd4jLong const* tadShapeInfo, Nd4jLong const* tadOffsets) {

	auto stream = lc->getCudaStream();

    auto xRank = shape::rank(hXShapeInfo);
    auto zRank = shape::rank(hZShapeInfo);
    auto xType = ArrayOptions::dataType(hXShapeInfo);
    auto zType = ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo)) {
        return;
    }

    if (!DataTypeUtils::isB(zType)) {
        throw std::runtime_error("NativeOpExecutioner::execTransformBool requires Z to have same boolean type");
    }

    dim3 launchDims(512, 512, 16384);
    BUILD_DOUBLE_SELECTOR(xType, zType, functions::transform::TransformBool, ::executeTransformShaped(launchDims, stream, opNum, dX, dXShapeInfo, xRank, extraParams, dZ, dZShapeInfo, zRank, nullptr, nullptr, nullptr, nullptr), LIBND4J_TYPES, BOOL_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execTransformBool failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execTransformAny(sd::LaunchContext  *lc,
                                		int opNum,
                                		void const* hX, Nd4jLong const* hXShapeInfo,
                                		void const* dX, Nd4jLong const* dXShapeInfo,
                                		void *hZ, Nd4jLong const* hZShapeInfo,
                                		void *dZ, Nd4jLong const* dZShapeInfo,
                                		void *extraParams,
                                		Nd4jLong const* tadShapeInfo, Nd4jLong const* tadOffsets, bool allowParallelism) {

	auto stream = lc->getCudaStream();

	auto xRank = shape::rank(hXShapeInfo);
	auto zRank = shape::rank(hZShapeInfo);
	auto xType = ArrayOptions::dataType(hXShapeInfo);
	auto zType = ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo))
        return;

    if (opNum == sd::transform::Assign && shape::order(hXShapeInfo) == shape::order(hZShapeInfo) && shape::order(hXShapeInfo) == 'c' && xType == zType && shape::elementWiseStride(hXShapeInfo) == 1 && shape::elementWiseStride(hZShapeInfo) == 1) {
        cudaMemcpyAsync(dZ, dX, shape::length(hXShapeInfo) * sd::DataTypeUtils::sizeOfElement(xType), cudaMemcpyDeviceToDevice, *stream);
    }
    else {

        dim3 launchDims(512, 512, 2048);
        BUILD_DOUBLE_SELECTOR(xType, zType, functions::transform::TransformAny, ::executeTransformShaped(launchDims, stream, opNum, dX, dXShapeInfo, xRank, extraParams, dZ, dZShapeInfo, zRank, nullptr, nullptr, nullptr, nullptr), LIBND4J_TYPES, LIBND4J_TYPES);
    }

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execTransformAny failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execTransformStrict(sd::LaunchContext  *lc,
                                    int opNum,
                                    void const* hX, Nd4jLong const* hXShapeInfo,
                                    void const* dX, Nd4jLong const* dXShapeInfo,
                                    void *hZ, Nd4jLong const* hZShapeInfo,
                                    void *dZ, Nd4jLong const* dZShapeInfo,
                                    void *extraParams,
                                    Nd4jLong const* tadShapeInfo, Nd4jLong const* tadOffsets) {

    auto stream = lc->getCudaStream();

    auto xRank = shape::rank(hXShapeInfo);
    auto zRank = shape::rank(hZShapeInfo);
    auto xType = ArrayOptions::dataType(hXShapeInfo);
    auto zType = ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo)) {
        return;
    }

    if (xType != zType || !DataTypeUtils::isR(xType)) {
        throw datatype_exception::build("NativeOpExecutioner::execTransformStrict requires X & Z to have same floating point type", xType, zType);
    }

    dim3 launchDims(512, 512, 16384);
    BUILD_SINGLE_SELECTOR(xType, functions::transform::TransformStrict, ::executeTransformShaped(launchDims, stream, opNum, dX, dXShapeInfo, xRank, extraParams, dZ, dZShapeInfo, zRank, nullptr, nullptr, nullptr, nullptr), FLOAT_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execTransformStrict failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execTransformFloat(sd::LaunchContext  *lc,
                                int opNum,
                                void const* hX, Nd4jLong const* hXShapeInfo,
                                void const* dX, Nd4jLong const* dXShapeInfo,
                                void *hZ, Nd4jLong const* hZShapeInfo,
                                void *dZ, Nd4jLong const* dZShapeInfo,
                                void *extraParams,
                                Nd4jLong const* tadShapeInfo, Nd4jLong const* tadOffsets) {

    auto stream = lc->getCudaStream();
    auto reductionPointer = lc->getReductionPointer();

    auto xRank = shape::rank(hXShapeInfo);
    auto zRank = shape::rank(hZShapeInfo);
    auto xType = ArrayOptions::dataType(hXShapeInfo);
    auto zType = ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo))
        return;

    if (!DataTypeUtils::isR(zType))
        throw datatype_exception::build("NativeOpExecutioner::execTransformFloat requires Z to have floating point type", zType);

    dim3 launchDims(512, 512, 2048);
    BUILD_DOUBLE_SELECTOR(xType, zType, functions::transform::TransformFloat, ::executeTransformShaped(launchDims, stream, opNum, dX, dXShapeInfo, xRank, extraParams, dZ, dZShapeInfo, zRank, nullptr, nullptr, nullptr, nullptr), LIBND4J_TYPES, FLOAT_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execTransformFloat failed", res);
}


////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execSummaryStats(sd::LaunchContext  *lc,
                                int opNum,
                                void const* hX, Nd4jLong const* hXShapeInfo,
                                void const* dX, Nd4jLong const* dXShapeInfo,
                                void *extraParams,
                                void *hZ, Nd4jLong const* hZShapeInfo,
                                void *dZ, Nd4jLong const* dZShapeInfo,
                                bool biasCorrected) {

    auto stream = lc->getCudaStream();
    auto reductionPointer = lc->getReductionPointer();

    dim3 launchDims = dim3(256, CUDA_BLOCK_SIZE, 1024);

	auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
	auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (!DataTypeUtils::isR(zType))
        throw sd::datatype_exception::build("NativeOpExecutioner::execSummaryStats requires Z operand to have floating point data type", zType);

    BUILD_DOUBLE_SELECTOR(xType, zType, functions::summarystats::SummaryStatsReduce, ::execSummaryStatsReduce(launchDims, stream, opNum, dX, dXShapeInfo, hXShapeInfo, extraParams, dZ, dZShapeInfo, hZShapeInfo, nullptr, nullptr, biasCorrected, reductionPointer), LIBND4J_TYPES, FLOAT_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execSummaryStats A failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execSummaryStats(sd::LaunchContext  *lc,
                                			int opNum,
                                			void const* hX, Nd4jLong const* hXShapeInfo,
                                			void const* dX, Nd4jLong const* dXShapeInfo,
                                			void *extraParams,
                                			void *hZ, Nd4jLong const* hZShapeInfo,
                                			void *dZ, Nd4jLong const* dZShapeInfo,
                                			int *dimension, int dimensionLength,
                                            Nd4jLong const* tadShapeInfo, Nd4jLong const* tadOffsets,
                                			bool biasCorrected) {
	auto stream = lc->getCudaStream();
	auto reductionPointer = lc->getReductionPointer();

    dim3 launchDims = dim3(256, CUDA_BLOCK_SIZE, 1024);

	auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
	auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (!DataTypeUtils::isR(zType))
        throw sd::datatype_exception::build("NativeOpExecutioner::execSummaryStats requires Z operand to have floating point data type", zType);

    BUILD_DOUBLE_SELECTOR(xType, zType, functions::summarystats::SummaryStatsReduce, ::execSummaryStatsReduce(launchDims, stream, opNum, dX, dXShapeInfo, hXShapeInfo, extraParams, dZ, dZShapeInfo, hZShapeInfo, dimension, dimensionLength, tadShapeInfo, tadOffsets, biasCorrected, reductionPointer), LIBND4J_TYPES, FLOAT_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execSummaryStats B failed", res);
}


////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execReduce3(sd::LaunchContext  *lc,
                            int opNum,
                            void const* hX, Nd4jLong const* hXShapeInfo,
                            void const* dX, Nd4jLong const* dXShapeInfo,
                            void *extraParams,
                            void const* hY, Nd4jLong const* hYShapeInfo,
                            void const* dY, Nd4jLong const* dYShapeInfo,
                            void *hZ, Nd4jLong const* hZShapeInfo,
                            void *dZ, Nd4jLong const* dZShapeInfo) {

	auto stream = lc->getCudaStream();
    auto reductionPointer = lc->getReductionPointer();
	auto allocationPointer = lc->getAllocationPointer();

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto yType = sd::ArrayOptions::dataType(hYShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    auto blockWidth = CUDA_BLOCK_SIZE;
    auto numBlocks = CudaLaunchHelper::getReductionBlocks(shape::length(hXShapeInfo), blockWidth);
    dim3 launchDims(numBlocks == 0 ? 1 : numBlocks, blockWidth, 1024);

    if (xType != yType)
        throw sd::datatype_exception::build("NativeOpExecutioner::execReduce3 requires Y operand to have X type", xType, yType);

    if (!DataTypeUtils::isR(zType))
        throw sd::datatype_exception::build("NativeOpExecutioner::execReduce3 requires Z operand to have floating point data type", zType);

    BUILD_DOUBLE_SELECTOR(xType, zType, functions::reduce3::Reduce3, ::execScalar(launchDims, stream, opNum, dX, dXShapeInfo, dY, dYShapeInfo, extraParams, dZ, dZShapeInfo, allocationPointer, reductionPointer, nullptr), LIBND4J_TYPES, FLOAT_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execReduce3 failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execReduce3(sd::LaunchContext  *lc,
                            int opNum,
                            void const* hX, Nd4jLong const* hXShapeInfo,
                            void const* dX, Nd4jLong const* dXShapeInfo,
                            void *extraParams,
                            void const* hY, Nd4jLong const* hYShapeInfo,
                            void const* dY, Nd4jLong const* dYShapeInfo,
                            void *hZ, Nd4jLong const* hZShapeInfo,
                            void *dZ, Nd4jLong const* dZShapeInfo,
                            int *dimension, int dimensionLength,
                            Nd4jLong const*  tadOnlyShapeInfo,  Nd4jLong const* tadOffsets,
                            Nd4jLong const*  yTadOnlyShapeInfo, Nd4jLong const* yTadOffsets) {

    if(shape::isScalar(hZShapeInfo)) {
        NativeOpExecutioner::execReduce3(lc, opNum, hX, hXShapeInfo, dX, dXShapeInfo, extraParams, hY, hYShapeInfo, dY, dYShapeInfo, hZ, hZShapeInfo, dZ, dZShapeInfo);
        return;
    }

    auto stream = lc->getCudaStream();
    auto allocationPointer = lc->getAllocationPointer();

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto yType = sd::ArrayOptions::dataType(hYShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

     if (xType != yType)
        throw sd::datatype_exception::build("NativeOpExecutioner::execReduce3 requires Y operand to have X type", xType, yType);

    if (!DataTypeUtils::isR(zType))
        throw sd::datatype_exception::build("NativeOpExecutioner::execReduce3 requires Z operand to have floating point data type", zType);


    auto numBlocks = shape::length(hZShapeInfo);
    dim3 launchDims(numBlocks == 0 ? 1 : numBlocks, CUDA_BLOCK_SIZE, 1024);

    BUILD_DOUBLE_SELECTOR(xType, zType, functions::reduce3::Reduce3, ::exec(launchDims, stream, opNum,
                                                                    dX, dXShapeInfo,
                                                                    dY, dYShapeInfo,
                                                                    extraParams,
                                                                    dZ, dZShapeInfo,
                                                                    dimension, dimensionLength,
                                                                    1,
                                                                    allocationPointer,
                                                                    tadOnlyShapeInfo, tadOffsets,
                                                                    yTadOnlyShapeInfo, yTadOffsets), LIBND4J_TYPES, FLOAT_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execReduce3 B failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execReduce3Scalar(sd::LaunchContext  *lc,
								  int opNum,
                                  void const* hX, Nd4jLong const* hXShapeInfo,
                                  void const* dX, Nd4jLong const* dXShapeInfo,
                                  void *extraParams,
                                  void const* hY, Nd4jLong const* hYShapeInfo,
                                  void const* dY, Nd4jLong const* dYShapeInfo,
                                  void *hZ, Nd4jLong const* hZShapeInfo,
                                  void *dZ, Nd4jLong const* dZShapeInfo) {


	auto stream 		   = lc->getCudaStream();
	auto allocationPointer = lc->getAllocationPointer();
	auto reductionPointer  = lc->getReductionPointer();

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto yType = sd::ArrayOptions::dataType(hYShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    auto xLength = shape::length(hXShapeInfo);
    auto blockWidth = CUDA_BLOCK_SIZE;
    auto numBlocks = CudaLaunchHelper::getReductionBlocks(xLength, blockWidth);
    dim3 launchDims(numBlocks == 0 ? 1 : numBlocks, blockWidth, 1024);

    if (xType != yType)
        throw sd::datatype_exception::build("NativeOpExecutioner::execReduce3Scalar requires Y operand to have X type", xType, yType);

    if (!DataTypeUtils::isR(zType))
        throw sd::datatype_exception::build("NativeOpExecutioner::execReduce3Scalar requires Z operand to have floating point data type", zType);

    BUILD_DOUBLE_SELECTOR(xType, zType, functions::reduce3::Reduce3, ::execScalar(launchDims, stream, opNum, dX, dXShapeInfo, dY, dYShapeInfo, extraParams, dZ, dZShapeInfo, allocationPointer, reductionPointer, nullptr), LIBND4J_TYPES, FLOAT_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execReduce3Scalar failed", res);
}


////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execScalarBool(sd::LaunchContext  *lc,
										int opNum,
										void const* hX, Nd4jLong const* hXShapeInfo,
										void const* dX, Nd4jLong const* dXShapeInfo,
										void *hZ, Nd4jLong const* hZShapeInfo,
										void *dZ, Nd4jLong const* dZShapeInfo,
										void const* hScalar, Nd4jLong const* hScalarShapeInfo,
										void const* dScalar, Nd4jLong const* dScalarShapeInfo,
										void *extraParams, bool allowParallelism) {

	auto stream = lc->getCudaStream();

	dim3 launchDims = dim3(256, 512, 8192);

	auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
	auto yType = sd::ArrayOptions::dataType(hScalarShapeInfo);
	auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo) || shape::isEmpty(hScalarShapeInfo))
        return;

	if (xType != yType )
		throw std::runtime_error("NativeOpExecutioner::execScalarBool requires X & Y to have same type");

	if (!DataTypeUtils::isB(zType) )
		throw std::runtime_error("NativeOpExecutioner::execScalarBool requires Z operand to have BOOL type");

	BUILD_DOUBLE_SELECTOR(xType, zType, functions::scalar::ScalarBoolTransform, ::executeCudaShaped(launchDims, stream, opNum, dX, dXShapeInfo, dZ, dZShapeInfo, dScalar, extraParams), LIBND4J_TYPES, BOOL_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execScalarBool failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execScalarBool(sd::LaunchContext  *lc,
						   				int opNum,
						   				void const* hX, Nd4jLong const* hXShapeInfo,
						   				void const* dX, Nd4jLong const* dXShapeInfo,
                                        void *extraParams,
						   				void *hZ, Nd4jLong const* hZShapeInfo,
						   				void *dZ, Nd4jLong const* dZShapeInfo,
						   				void const* hScalars, Nd4jLong const* hScalarShapeInfo,
						   				void const* dScalars, Nd4jLong const* dScalarShapeInfo,
						   				int *dimension, int dimensionLength,
                           				Nd4jLong const* tadShapeInfo,  Nd4jLong const* tadOffsets,
                           				Nd4jLong const* tadShapeInfoZ, Nd4jLong const* tadOffsetsZ) {

	auto stream = lc->getCudaStream();

	dim3 launchDims(256, 512, 8192);

	auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
	auto yType = sd::ArrayOptions::dataType(hScalarShapeInfo);
	auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo) || shape::isEmpty(hScalarShapeInfo))
        return;

	if (xType != yType )
		throw std::runtime_error("NativeOpExecutioner::execScalarBool requires X & Y to have same type");

	if (!DataTypeUtils::isB(zType) )
		throw std::runtime_error("NativeOpExecutioner::execScalarBool requires Z operand to have BOOL type");

	BUILD_DOUBLE_SELECTOR(xType, zType, functions::scalar::ScalarBoolTransform, ::executeCudaAlongDimension(launchDims, stream, opNum, dX, dXShapeInfo, dZ, dZShapeInfo, dScalars, extraParams, dimension, dimensionLength, tadShapeInfo, tadOffsets, tadShapeInfoZ, tadOffsetsZ), LIBND4J_TYPES, BOOL_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execScalarBool B failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execScalarInt(sd::LaunchContext  *lc,
                                         int opNum,
                                         void const* hX, Nd4jLong const* hXShapeInfo,
                                         void const* dX, Nd4jLong const* dXShapeInfo,
                                         void *hZ, Nd4jLong const* hZShapeInfo,
                                         void *dZ, Nd4jLong const* dZShapeInfo,
                                         void const* hScalar, Nd4jLong const* hScalarShapeInfo,
                                         void const* dScalar, Nd4jLong const* dScalarShapeInfo,
                                         void *extraParams, bool allowParallelism) {

    auto stream = lc->getCudaStream();

    dim3 launchDims = dim3(256, 512, 8192);

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto yType = sd::ArrayOptions::dataType(hScalarShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo) || shape::isEmpty(hScalarShapeInfo))
        return;

    if (xType != yType || zType != xType)
        throw std::runtime_error("NativeOpExecutioner::execScalarInt requires X & Y to have same type");

    if (!DataTypeUtils::isZ(zType) )
        throw std::runtime_error("NativeOpExecutioner::execScalarInt requires Z operand to have INT type");

    BUILD_SINGLE_SELECTOR(xType, functions::scalar::ScalarIntTransform, ::executeCudaShaped(launchDims, stream, opNum, dX, dXShapeInfo, dZ, dZShapeInfo, dScalar, extraParams), INTEGER_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execScalarInt failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execScalarInt(sd::LaunchContext  *lc,
                                         int opNum,
                                         void const* hX, Nd4jLong const* hXShapeInfo,
                                         void const* dX, Nd4jLong const* dXShapeInfo,
                                         void *extraParams,
                                         void *hZ, Nd4jLong const* hZShapeInfo,
                                         void *dZ, Nd4jLong const* dZShapeInfo,
                                         void const* hScalars, Nd4jLong const* hScalarShapeInfo,
                                         void const* dScalars, Nd4jLong const* dScalarShapeInfo,
                                         int *dimension, int dimensionLength,
                                         Nd4jLong const* tadShapeInfo,  Nd4jLong const* tadOffsets,
                                         Nd4jLong const* tadShapeInfoZ, Nd4jLong const* tadOffsetsZ) {

    auto stream = lc->getCudaStream();

    dim3 launchDims(256, 512, 8192);

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto yType = sd::ArrayOptions::dataType(hScalarShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo) || shape::isEmpty(hScalarShapeInfo))
        return;

    if (xType != yType || zType != xType)
        throw std::runtime_error("NativeOpExecutioner::execScalarInt requires X & Y to have same type");

    if (!DataTypeUtils::isZ(zType) )
        throw std::runtime_error("NativeOpExecutioner::execScalarInt requires Z operand to have INT type");

    BUILD_SINGLE_SELECTOR(xType, functions::scalar::ScalarIntTransform, ::executeCudaAlongDimension(launchDims, stream, opNum, dX, dXShapeInfo, dZ, dZShapeInfo, dScalars, extraParams, dimension, dimensionLength, tadShapeInfo, tadOffsets, tadShapeInfoZ, tadOffsetsZ), INTEGER_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execScalarInt B failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execScalar(sd::LaunchContext  *lc,
									int opNum,
									void const* hX, Nd4jLong const* hXShapeInfo,
									void const* dX, Nd4jLong const* dXShapeInfo,
									void* hZ, Nd4jLong const* hZShapeInfo,
									void* dZ, Nd4jLong const* dZShapeInfo,
									void const* hScalar, Nd4jLong const* hScalarShapeInfo,
									void const* dScalar, Nd4jLong const* dScalarShapeInfo,
									void *extraParams, bool allowParallelism) {

	auto stream = lc->getCudaStream();

	dim3 launchDims(256, 512, 8192);

	auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
	auto yType = sd::ArrayOptions::dataType(hScalarShapeInfo);
	auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo) || shape::isEmpty(hScalarShapeInfo))
        return;


#ifdef __ND4J_EXPERIMENTAL__
	BUILD_PAIRWISE_SELECTOR(xType, yType, zType, functions::scalar::ScalarTransform, ::executeCudaShaped(launchDims, stream, opNum, dX, dXShapeInfo, hXShapeInfo, dZ, dZShapeInfo, hZShapeInfo, dScalar, extraParams), LIBND4J_TYPES, LIBND4J_TYPES);
#else
	BUILD_SINGLE_SELECTOR_THRICE(xType, functions::scalar::ScalarTransform, ::executeCudaShaped(launchDims, stream, opNum, dX, dXShapeInfo, hXShapeInfo, dZ, dZShapeInfo, hZShapeInfo, dScalar, extraParams), LIBND4J_TYPES);
#endif

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execScalar failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execScalar(sd::LaunchContext  *lc,
					 				int opNum,
					 				void const* hX, Nd4jLong const* hXShapeInfo,
                     				void const* dX, Nd4jLong const* dXShapeInfo,
                                    void *extraParams,
                     				void *hZ, Nd4jLong const* hZShapeInfo,
                     				void *dZ, Nd4jLong const* dZShapeInfo,
                     				void const* hScalars, Nd4jLong const* hScalarShapeInfo,
                     				void const* dScalars, Nd4jLong const* dScalarShapeInfo,
					 				int *dimension, int dimensionLength,
                     				Nd4jLong const* tadShapeInfo,  Nd4jLong const* tadOffsets,
                     				Nd4jLong const* tadShapeInfoZ, Nd4jLong const* tadOffsetsZ) {

    auto stream = lc->getCudaStream();

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto yType = sd::ArrayOptions::dataType(hScalarShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (shape::isEmpty(hXShapeInfo) || shape::isEmpty(hScalarShapeInfo))
        return;

	dim3 launchDims(256, 256, 16384);

#ifdef __ND4J_EXPERIMENTAL__
    BUILD_PAIRWISE_SELECTOR(xType, yType, zType, functions::scalar::ScalarTransform, ::executeCudaAlongDimension(launchDims, stream, opNum, dX, dXShapeInfo, dZ, dZShapeInfo, dScalars, extraParams, dimension, dimensionLength, tadShapeInfo, tadOffsets, tadShapeInfoZ, tadOffsetsZ), LIBND4J_TYPES, LIBND4J_TYPES);
#else
	BUILD_SINGLE_SELECTOR_THRICE(xType, functions::scalar::ScalarTransform, ::executeCudaAlongDimension(launchDims, stream, opNum, dX, dXShapeInfo, dZ, dZShapeInfo, dScalars, extraParams, dimension, dimensionLength, tadShapeInfo, tadOffsets, tadShapeInfoZ, tadOffsetsZ), LIBND4J_TYPES);
#endif

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execScalar B failed", res);
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execRandom(sd::LaunchContext  *lc,
						  int opNum,
                          Nd4jPointer stateHost,
                          void *hZ, Nd4jLong const* hZShapeInfo,
                          void *dZ, Nd4jLong const* dZShapeInfo,
                          void *extraArguments) {

    auto stream = lc->getCudaStream();
    auto sizeOf = sizeof(sd::graph::RandomGenerator);
    Nd4jPointer stateDevice;

    cudaError_t res = cudaMalloc(reinterpret_cast<void **>(&stateDevice), sizeOf);
    checkCudaErrors(cudaStreamSynchronize(*stream));
    checkCudaErrors(cudaMemcpyAsync(stateDevice, stateHost, sizeOf, cudaMemcpyHostToDevice, *stream));

    dim3 launchDims = dim3(512, 512, 32768);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    auto rng = reinterpret_cast<sd::graph::RandomGenerator*>(stateHost);

    // functions::random::RandomFunction<float>::executeCudaSingle(launchDims, extraPointers, opNum, stateHost, dZ, dZShapeInfo, extraArguments),
    BUILD_SINGLE_SELECTOR(zType, functions::random::RandomFunction, ::executeCudaSingle(launchDims, stream, opNum, stateDevice, dZ, dZShapeInfo, extraArguments), FLOAT_TYPES);

    res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execRandom X failed", res);

    cudaFree(stateDevice);

    rng->rewindH(shape::length(hZShapeInfo));
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execRandom(sd::LaunchContext  *lc,
							int opNum,
							Nd4jPointer stateHost,
						   	void const* hX, Nd4jLong const* hXShapeInfo,
						   	void const* dX, Nd4jLong const* dXShapeInfo,
						   	void *hZ, Nd4jLong const* hZShapeInfo,
						   	void *dZ, Nd4jLong const* dZShapeInfo,
						   	void *extraArguments) {

    auto stream = lc->getCudaStream();

    auto sizeOf = sizeof(sd::graph::RandomGenerator);
    Nd4jPointer stateDevice;

    cudaError_t res = cudaMalloc(reinterpret_cast<void **>(&stateDevice), sizeOf);
    checkCudaErrors(cudaStreamSynchronize(*stream));
    checkCudaErrors(cudaMemcpyAsync(stateDevice, stateHost, sizeOf, cudaMemcpyHostToDevice, *stream));

    auto rng = reinterpret_cast<sd::graph::RandomGenerator*>(stateHost);

    dim3 launchDims = dim3(512, 512, 32768);
    auto xType = sd::ArrayOptions::dataType(hZShapeInfo);
    // functions::random::RandomFunction<float>::executeCudaDouble(launchDims, extraPointers, opNum, stateHost, dX, dXShapeInfo, dZ, dZShapeInfo, extraArguments);
    BUILD_SINGLE_SELECTOR(xType, functions::random::RandomFunction, ::executeCudaDouble(launchDims, stream, opNum, stateDevice, dX, dXShapeInfo, dZ, dZShapeInfo, extraArguments), FLOAT_TYPES);

    res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execRandom XY failed", res);

    cudaFree(stateDevice);

    rng->rewindH(shape::length(hZShapeInfo));
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execRandom(sd::LaunchContext  *lc,
							int opNum,
							Nd4jPointer stateHost,
							void const* hX, Nd4jLong const* hXShapeInfo,
							void const* dX, Nd4jLong const* dXShapeInfo,
							void const* hY, Nd4jLong const* hYShapeInfo,
							void const* dY, Nd4jLong const* dYShapeInfo,
							void *hZ, Nd4jLong const* hZShapeInfo,
							void *dZ, Nd4jLong const* dZShapeInfo,
							void *extraArguments) {

    auto stream = lc->getCudaStream();
    auto sizeOf = sizeof(sd::graph::RandomGenerator);
    Nd4jPointer stateDevice;

    cudaError_t res = cudaMalloc(reinterpret_cast<void **>(&stateDevice), sizeOf);
    checkCudaErrors(cudaStreamSynchronize(*stream));
    checkCudaErrors(cudaMemcpyAsync(stateDevice, stateHost, sizeOf, cudaMemcpyHostToDevice, *stream));

    auto rng = reinterpret_cast<sd::graph::RandomGenerator*>(stateHost);

    dim3 launchDims = dim3(512, 512, 32768);
    auto xType = sd::ArrayOptions::dataType(hZShapeInfo);
    // functions::random::RandomFunction<float>::executeCudaTriple(launchDims, extraPointers, opNum, stateHost, dX, dXShapeInfo, dY, dYShapeInfo, dZ, dZShapeInfo, extraArguments);
    BUILD_SINGLE_SELECTOR(xType, functions::random::RandomFunction, ::executeCudaTriple(launchDims, stream, opNum, stateDevice, dX, dXShapeInfo, dY, dYShapeInfo, dZ, dZShapeInfo, extraArguments), FLOAT_TYPES);

    res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execRandom XYZ failed", res);

    cudaFree(stateDevice);

    rng->rewindH(shape::length(hZShapeInfo));
}

////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execReduce3All(sd::LaunchContext  *lc,
									int opNum,
									void const* hX, Nd4jLong const* hXShapeInfo,
                            		void const* dX, Nd4jLong const* dXShapeInfo,
                            		void *extraParamsVals,
									void const* hY, Nd4jLong const* hYShapeInfo,
                            		void const* dY, Nd4jLong const* dYShapeInfo,
                            		void *hZ, Nd4jLong const* hZShapeInfo,
                            		void *dZ, Nd4jLong const* dZShapeInfo,
									int *dimension, int dimensionLength,
									Nd4jLong const* xTadShapeInfo, Nd4jLong const* xOffsets,
									Nd4jLong const* yTadShapeInfo, Nd4jLong const* yOffsets) {

    auto stream = lc->getCudaStream();
    auto allocationPointer = lc->getAllocationPointer();
	auto reductionPointer  = lc->getReductionPointer();

    if (sd::Environment::getInstance().isDebugAndVerbose())
        printf("D119 opNum:[%i]\n", opNum);

    dim3 launchDims(shape::length(hZShapeInfo), CUDA_BLOCK_SIZE / 2, 1024);

    if (sd::Environment::getInstance().isVerbose() && launchDims.x == 1)
        printf("AD119 opNum:[%i]\n", opNum);

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto yType = sd::ArrayOptions::dataType(hYShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

    if (yType != xType)
        throw sd::datatype_exception::build("NativeOpExecutioner::execReduce3All both operands must have same data type", xType, yType);

    BUILD_DOUBLE_SELECTOR(xType, zType, functions::reduce3::Reduce3, ::execAll(launchDims, stream, opNum, dX, dXShapeInfo, dY, dYShapeInfo, extraParamsVals, dZ, dZShapeInfo, dimension, dimensionLength, 1, allocationPointer, xTadShapeInfo, xOffsets, yTadShapeInfo, yOffsets), LIBND4J_TYPES, FLOAT_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execReduce3All failed", res);
}


////////////////////////////////////////////////////////////////////////
void NativeOpExecutioner::execReduce3TAD(sd::LaunchContext  *lc,
                                            int opNum,
                                            void const* hX, Nd4jLong const* hXShapeInfo,
                                            void const* dX, Nd4jLong const* dXShapeInfo,
                                            void *extraParams,
                                            void const* hY, Nd4jLong const* hYShapeInfo,
                                            void const* dY, Nd4jLong const* dYShapeInfo,
                                            void *hZ, Nd4jLong const* hZShapeInfo,
                                            void *dZ, Nd4jLong const* dZShapeInfo,
                                            int *dimension, int dimensionLength,
                                            Nd4jLong const* tadShapeInfo,  Nd4jLong const* tadOffsets,
                                            Nd4jLong const* yTadShapeInfo, Nd4jLong const* yTadOffsets) {

    if(shape::isScalar(hZShapeInfo)) {
        NativeOpExecutioner::execReduce3(lc, opNum, hX, hXShapeInfo, dX, dXShapeInfo, extraParams, hY, hYShapeInfo, dY, dYShapeInfo, hZ, hZShapeInfo, dZ, dZShapeInfo);
        return;
    }

    auto stream = lc->getCudaStream();
    auto allocationPointer = lc->getAllocationPointer();

    auto xType = sd::ArrayOptions::dataType(hXShapeInfo);
    auto yType = sd::ArrayOptions::dataType(hYShapeInfo);
    auto zType = sd::ArrayOptions::dataType(hZShapeInfo);

     if (xType != yType)
        throw sd::datatype_exception::build("NativeOpExecutioner::execReduce3TAD requires Y operand to have X type", xType, yType);

    if (!DataTypeUtils::isR(zType))
        throw sd::datatype_exception::build("NativeOpExecutioner::execReduce3TAD requires Z operand to have floating point data type", zType);

    auto numBlocks = shape::length(hZShapeInfo);
    dim3 launchDims(numBlocks == 0 ? 1 : numBlocks, CUDA_BLOCK_SIZE, 1024);

    BUILD_DOUBLE_SELECTOR(xType, zType, functions::reduce3::Reduce3, ::exec(launchDims, stream, opNum, dX, dXShapeInfo, dY, dYShapeInfo, extraParams, dZ, dZShapeInfo, dimension, dimensionLength, 1, allocationPointer, tadShapeInfo, tadOffsets, yTadShapeInfo, yTadOffsets), LIBND4J_TYPES, FLOAT_TYPES);

    // TODO: remove after the release
    auto res = cudaStreamSynchronize(*stream);
    if (res != 0)
        throw cuda_exception::build("execReduce3TAD failed", res);
}

