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

#include "testlayers.h"
#include <NDArray.h>
#include <NDArrayFactory.h>
#include <Context.h>
#include <Node.h>
#include <graph/Variable.h>
#include <graph/VariableSpace.h>
#include <specials_cuda.h>
#include <TAD.h>

#include <cuda.h>
#include <cuda_launch_config.h>

using namespace nd4j;
using namespace nd4j::graph;

class CudaBasicsTests : public testing::Test {
public:

};


//////////////////////////////////////////////////////////////////////////
static cudaError_t allocateDeviceMem(LaunchContext& lc, std::vector<void*>& devicePtrs, const std::vector<std::pair<void*,size_t>>& hostData) { 

	if(devicePtrs.size() != hostData.size())
		throw std::invalid_argument("prepareDataForCuda: two input sts::vectors should same sizes !");

	cudaError_t cudaResult;

	void* reductionPointer;
    cudaResult = cudaMalloc(reinterpret_cast<void **>(&reductionPointer),  1024*1024);			if(cudaResult != 0) return cudaResult;
    int* allocationPointer;
	cudaResult = cudaMalloc(reinterpret_cast<void **>(&allocationPointer), 1024*1024);			if(cudaResult != 0) return cudaResult;

	lc.setReductionPointer(reductionPointer);
	lc.setAllocationPointer(allocationPointer);
	cudaStream_t stream = *lc.getCudaStream();

	for(int i = 0; i < devicePtrs.size(); ++i) {
		
		cudaResult = cudaMalloc(reinterpret_cast<void **>(&devicePtrs[i]), hostData[i].second); if(cudaResult != 0) return cudaResult;
		cudaMemcpyAsync(devicePtrs[i], hostData[i].first, hostData[i].second, cudaMemcpyHostToDevice, stream);
	}
	return cudaResult;
}

//////////////////////////////////////////////////////////////////////////
TEST_F(CudaBasicsTests, TestPairwise_1) {
	// allocating host-side arrays
	auto x = NDArrayFactory::create<double>('c', { 5 }, { 1, 2, 3, 4, 5});
	auto z = NDArrayFactory::create<double>('c', { 5 });

	auto exp = NDArrayFactory::create<double>('c', { 5 }, { 2, 4, 6, 8, 10 });

	// making raw buffers
	Nd4jPointer devBufferPtrX, devBufferPtrZ, devShapePtrX;
	cudaError_t res = cudaMalloc(reinterpret_cast<void **>(&devBufferPtrX), x.lengthOf() * x.sizeOfT());
	ASSERT_EQ(0, res);
	res = cudaMalloc(reinterpret_cast<void **>(&devBufferPtrZ), x.lengthOf() * x.sizeOfT());
	ASSERT_EQ(0, res);
	res = cudaMalloc(reinterpret_cast<void **>(&devShapePtrX), shape::shapeInfoByteLength(x.shapeInfo()));
	ASSERT_EQ(0, res);

	Nd4jPointer nativeStream = (Nd4jPointer)malloc(sizeof(cudaStream_t));
	CHECK_ALLOC(nativeStream, "Failed to allocate memory for new CUDA stream");
	cudaError_t dZ = cudaStreamCreate(reinterpret_cast<cudaStream_t *>(&nativeStream));
	auto stream = reinterpret_cast<cudaStream_t *>(&nativeStream);

	cudaMemcpyAsync(devBufferPtrX, x.buffer(), x.lengthOf() * x.sizeOfT(), cudaMemcpyHostToDevice, *stream);
	cudaMemcpyAsync(devShapePtrX, x.shapeInfo(), shape::shapeInfoByteLength(x.shapeInfo()), cudaMemcpyHostToDevice, *stream);
	
	LaunchContext lc(stream, nullptr, nullptr);
	NativeOpExecutioner::execPairwiseTransform(&lc, pairwise::Add, nullptr, x.shapeInfo(), devBufferPtrX, reinterpret_cast<Nd4jLong*>(devShapePtrX), nullptr, x.shapeInfo(), devBufferPtrX, reinterpret_cast<Nd4jLong*>(devShapePtrX), nullptr, z.shapeInfo(), devBufferPtrZ, reinterpret_cast<Nd4jLong*>(devShapePtrX), nullptr);
	res = cudaStreamSynchronize(*stream);
	ASSERT_EQ(0, res);

	cudaMemcpyAsync(z.buffer(), devBufferPtrZ, z.lengthOf() * x.sizeOfT(), cudaMemcpyDeviceToHost, *stream);
	res = cudaStreamSynchronize(*stream);
	ASSERT_EQ(0, res);

	cudaFree(devBufferPtrX);
	cudaFree(devBufferPtrZ);
	cudaFree(devShapePtrX);

	for (int e = 0; e < z.lengthOf(); e++) {
		ASSERT_NEAR(exp.e<double>(e), z.e<double>(e), 1e-5);
	}
}


////////////////////////////////////////////////////////////////////////////
TEST_F(CudaBasicsTests, execIndexReduceScalar_1) {

    NDArray x1('c', {2,2}, {0, 1, 2, 3}, nd4j::DataType::INT32);
    NDArray x2('c', {2,2}, {0.5, 1.5, -4.5, 3.5}, nd4j::DataType::BFLOAT16);    
    NDArray x3('c', {2,2}, {0, -1, 0, 1}, nd4j::DataType::BOOL);
    
    NDArray scalar(nd4j::DataType::INT64);

    NDArray exp1('c', {0}, {3}, nd4j::DataType::INT64);
    NDArray exp2('c', {0}, {2}, nd4j::DataType::INT64);
    NDArray exp3('c', {0}, {1}, nd4j::DataType::INT64);

    void *dX1, *dX2, *dX3, *dZ; 
    Nd4jLong *dX1ShapeInfo, *dX2ShapeInfo, *dX3ShapeInfo, *dZShapeInfo;

    cudaError_t cudaResult;

    cudaResult = cudaMalloc(reinterpret_cast<void **>(&dX1), x1.lengthOf() * x1.sizeOfT()); 		   		         	 ASSERT_EQ(0, cudaResult);
    cudaResult = cudaMalloc(reinterpret_cast<void **>(&dX2), x2.lengthOf() * x2.sizeOfT()); 		   		         	 ASSERT_EQ(0, cudaResult);    
    cudaResult = cudaMalloc(reinterpret_cast<void **>(&dX3), x3.lengthOf() * x3.sizeOfT()); 		   		         	 ASSERT_EQ(0, cudaResult);    
	cudaResult = cudaMalloc(reinterpret_cast<void **>(&dZ), scalar.lengthOf() * scalar.sizeOfT()); 				         ASSERT_EQ(0, cudaResult);
	cudaResult = cudaMalloc(reinterpret_cast<void **>(&dX1ShapeInfo), shape::shapeInfoByteLength(x1.getShapeInfo()));    ASSERT_EQ(0, cudaResult);
	cudaResult = cudaMalloc(reinterpret_cast<void **>(&dX2ShapeInfo), shape::shapeInfoByteLength(x2.getShapeInfo()));    ASSERT_EQ(0, cudaResult);
	cudaResult = cudaMalloc(reinterpret_cast<void **>(&dX3ShapeInfo), shape::shapeInfoByteLength(x3.getShapeInfo()));    ASSERT_EQ(0, cudaResult);
	cudaResult = cudaMalloc(reinterpret_cast<void **>(&dZShapeInfo), shape::shapeInfoByteLength(scalar.getShapeInfo())); ASSERT_EQ(0, cudaResult);	

    cudaStream_t stream;
	cudaResult = cudaStreamCreate(&stream); 
	ASSERT_EQ(0, cudaResult);
	
	cudaMemcpyAsync(dX1, x1.buffer(), x1.lengthOf() * x1.sizeOfT(), cudaMemcpyHostToDevice, stream);
	cudaMemcpyAsync(dX2, x2.buffer(), x2.lengthOf() * x2.sizeOfT(), cudaMemcpyHostToDevice, stream);
	cudaMemcpyAsync(dX3, x3.buffer(), x3.lengthOf() * x3.sizeOfT(), cudaMemcpyHostToDevice, stream);
	cudaMemcpyAsync(dX1ShapeInfo, x1.getShapeInfo(), shape::shapeInfoByteLength(x1.getShapeInfo()), cudaMemcpyHostToDevice, stream);
	cudaMemcpyAsync(dX2ShapeInfo, x2.getShapeInfo(), shape::shapeInfoByteLength(x2.getShapeInfo()), cudaMemcpyHostToDevice, stream);
	cudaMemcpyAsync(dX3ShapeInfo, x3.getShapeInfo(), shape::shapeInfoByteLength(x3.getShapeInfo()), cudaMemcpyHostToDevice, stream);
	cudaMemcpyAsync(dZShapeInfo, scalar.getShapeInfo(), shape::shapeInfoByteLength(scalar.getShapeInfo()), cudaMemcpyHostToDevice, stream);
	
	void* reductionPointer = nullptr;
	cudaResult = cudaMalloc(reinterpret_cast<void **>(&reductionPointer), 1024*1024);
	ASSERT_EQ(0, cudaResult);

	LaunchContext lc(&stream, reductionPointer);

	/***************************************/
	
    NativeOpExecutioner::execIndexReduceScalar(&lc, 
    											nd4j::indexreduce::IndexAbsoluteMax, 
    											x1.buffer(), x1.getShapeInfo(),
    	                                       	dX1, dX1ShapeInfo, 
    	                                       	nullptr, 
    	                                       	scalar.buffer(), scalar.getShapeInfo(),
    	                                       	dZ, dZShapeInfo);

    cudaResult = cudaStreamSynchronize(stream); 
    ASSERT_EQ(0, cudaResult);

    cudaMemcpyAsync(scalar.buffer(), dZ, scalar.lengthOf() * scalar.sizeOfT(), cudaMemcpyDeviceToHost, stream);

    cudaResult = cudaStreamSynchronize(stream); 
    ASSERT_EQ(0, cudaResult);

	ASSERT_NEAR(exp1.e<float>(0), scalar.e<float>(0), 1e-5);

    /***************************************/
    
    NativeOpExecutioner::execIndexReduceScalar(&lc,
    											nd4j::indexreduce::IndexAbsoluteMax, 
    											nullptr, x2.getShapeInfo(),
    	                                       	dX2, dX2ShapeInfo, 
    	                                       	nullptr, 
    	                                       	nullptr, scalar.getShapeInfo(),
    	                                       	dZ, dZShapeInfo);

    cudaResult = cudaStreamSynchronize(stream); 
    ASSERT_EQ(0, cudaResult);

    cudaMemcpyAsync(scalar.buffer(), dZ, scalar.lengthOf() * scalar.sizeOfT(), cudaMemcpyDeviceToHost, stream);

    cudaResult = cudaStreamSynchronize(stream); 
    ASSERT_EQ(0, cudaResult);

    ASSERT_NEAR(exp2.e<float>(0), scalar.e<float>(0), 1e-5);

    // *************************************

    NativeOpExecutioner::execIndexReduceScalar(&lc, 
    											nd4j::indexreduce::IndexAbsoluteMax, 
    											nullptr, x3.getShapeInfo(),
    	                                       	dX3, dX3ShapeInfo, 
    	                                       	nullptr, 
    	                                       	nullptr, scalar.getShapeInfo(),
    	                                       	dZ, dZShapeInfo);

    cudaResult = cudaStreamSynchronize(stream); 
    ASSERT_EQ(0, cudaResult);

    cudaMemcpyAsync(scalar.buffer(), dZ, scalar.lengthOf() * scalar.sizeOfT(), cudaMemcpyDeviceToHost, stream);

    cudaResult = cudaStreamSynchronize(stream); 
    ASSERT_EQ(0, cudaResult);

    ASSERT_NEAR(exp3.e<float>(0), scalar.e<float>(0), 1e-5);
    
	/***************************************/

	cudaFree(dX1); 			cudaFree(dX2); 			cudaFree(dX3); 			cudaFree(dZ);
	cudaFree(dX1ShapeInfo); cudaFree(dX2ShapeInfo); cudaFree(dX3ShapeInfo); cudaFree(dZShapeInfo); 

	/***************************************/	

	cudaResult = cudaStreamDestroy(stream); 
	ASSERT_EQ(0, cudaResult);
	
}

////////////////////////////////////////////////////////////////////////////
TEST_F(CudaBasicsTests, execReduce3Scalar_1) {

    NDArray x1('c', {2,2}, {1,2,3,4}, nd4j::DataType::INT32);
    NDArray x2('c', {2,2}, {-1,-2,-3,-4}, nd4j::DataType::INT32);
    NDArray x3('c', {2,2}, {1.5,1.5,1.5,1.5}, nd4j::DataType::DOUBLE);
    NDArray x4('c', {2,2}, {1,2,3,4}, nd4j::DataType::DOUBLE);
    NDArray exp1('c', {0}, {-30}, nd4j::DataType::FLOAT32);
    NDArray exp2('c', {0}, {15}, nd4j::DataType::DOUBLE);
    
	NDArray scalar1('c', {0}, nd4j::DataType::FLOAT32);
    NDArray scalar2('c', {0}, nd4j::DataType::DOUBLE);

    void *dX1, *dX2, *dX3, *dX4, *dZ1, *dZ2; 
    Nd4jLong *dX1ShapeInfo, *dX3ShapeInfo, *dZ1ShapeInfo, *dZ2ShapeInfo;

    cudaError_t cudaResult;

    cudaResult = cudaMalloc(reinterpret_cast<void **>(&dX1), x1.lengthOf() * x1.sizeOfT()); 		   		         	 	ASSERT_EQ(0, cudaResult);
    cudaResult = cudaMalloc(reinterpret_cast<void **>(&dX2), x2.lengthOf() * x2.sizeOfT()); 		   		         	 	ASSERT_EQ(0, cudaResult);
    cudaResult = cudaMalloc(reinterpret_cast<void **>(&dX3), x3.lengthOf() * x3.sizeOfT()); 		   		         	 	ASSERT_EQ(0, cudaResult);
    cudaResult = cudaMalloc(reinterpret_cast<void **>(&dX4), x4.lengthOf() * x4.sizeOfT()); 		   		         	 	ASSERT_EQ(0, cudaResult);
	cudaResult = cudaMalloc(reinterpret_cast<void **>(&dZ1), scalar1.lengthOf() * scalar1.sizeOfT());			         	ASSERT_EQ(0, cudaResult);
	cudaResult = cudaMalloc(reinterpret_cast<void **>(&dZ2), scalar2.lengthOf() * scalar2.sizeOfT());			         	ASSERT_EQ(0, cudaResult);
	cudaResult = cudaMalloc(reinterpret_cast<void **>(&dX1ShapeInfo), shape::shapeInfoByteLength(x1.getShapeInfo()));    	ASSERT_EQ(0, cudaResult);
	cudaResult = cudaMalloc(reinterpret_cast<void **>(&dX3ShapeInfo), shape::shapeInfoByteLength(x3.getShapeInfo()));    	ASSERT_EQ(0, cudaResult);
	cudaResult = cudaMalloc(reinterpret_cast<void **>(&dZ1ShapeInfo), shape::shapeInfoByteLength(scalar1.getShapeInfo())); 	ASSERT_EQ(0, cudaResult);
	cudaResult = cudaMalloc(reinterpret_cast<void **>(&dZ2ShapeInfo), shape::shapeInfoByteLength(scalar2.getShapeInfo())); 	ASSERT_EQ(0, cudaResult);

    cudaStream_t stream;
	cudaResult = cudaStreamCreate(&stream); 
	ASSERT_EQ(0, cudaResult);
	
	cudaMemcpyAsync(dX1, x1.buffer(), x1.lengthOf() * x1.sizeOfT(), cudaMemcpyHostToDevice, stream);
	cudaMemcpyAsync(dX2, x2.buffer(), x2.lengthOf() * x2.sizeOfT(), cudaMemcpyHostToDevice, stream);
	cudaMemcpyAsync(dX3, x3.buffer(), x3.lengthOf() * x3.sizeOfT(), cudaMemcpyHostToDevice, stream);
	cudaMemcpyAsync(dX4, x4.buffer(), x4.lengthOf() * x4.sizeOfT(), cudaMemcpyHostToDevice, stream);
	cudaMemcpyAsync(dX1ShapeInfo, x1.getShapeInfo(), shape::shapeInfoByteLength(x1.getShapeInfo()), cudaMemcpyHostToDevice, stream);	
	cudaMemcpyAsync(dX3ShapeInfo, x3.getShapeInfo(), shape::shapeInfoByteLength(x3.getShapeInfo()), cudaMemcpyHostToDevice, stream);
	cudaMemcpyAsync(dZ1ShapeInfo, scalar1.getShapeInfo(), shape::shapeInfoByteLength(scalar1.getShapeInfo()), cudaMemcpyHostToDevice, stream);
	cudaMemcpyAsync(dZ2ShapeInfo, scalar2.getShapeInfo(), shape::shapeInfoByteLength(scalar2.getShapeInfo()), cudaMemcpyHostToDevice, stream);

	/***************************************/

	void* reductionPointer  = nullptr;
	int*  allocationPointer = nullptr;	

	cudaResult = cudaMalloc(reinterpret_cast<void **>(&reductionPointer),  1024*1024);		ASSERT_EQ(0, cudaResult);
	cudaResult = cudaMalloc(reinterpret_cast<void **>(&allocationPointer), 1024*1024);		ASSERT_EQ(0, cudaResult);

	LaunchContext lc(&stream, reductionPointer, nullptr, allocationPointer);

	/***************************************/
	
    NativeOpExecutioner::execReduce3Scalar(&lc, nd4j::reduce3::Dot,nullptr, x1.getShapeInfo(),dX1, dX1ShapeInfo, nullptr, nullptr, x2.getShapeInfo(),dX2, dX1ShapeInfo,nullptr, scalar1.getShapeInfo(),dZ1, dZ1ShapeInfo);

    cudaResult = cudaStreamSynchronize(stream);     
    ASSERT_EQ(0, cudaResult);

    cudaMemcpyAsync(scalar1.buffer(), dZ1, scalar1.lengthOf() * scalar1.sizeOfT(), cudaMemcpyDeviceToHost, stream);

    cudaResult = cudaStreamSynchronize(stream); 
    ASSERT_EQ(0, cudaResult);

	ASSERT_NEAR(exp1.e<float>(0), scalar1.e<float>(0), 1e-5);

    /***************************************/
    
    NativeOpExecutioner::execReduce3Scalar(&lc, nd4j::reduce3::Dot,nullptr, x3.getShapeInfo(),dX3, dX3ShapeInfo, nullptr, nullptr, x4.getShapeInfo(),dX4, dX3ShapeInfo,nullptr, scalar2.getShapeInfo(),dZ2, dZ2ShapeInfo);

    cudaResult = cudaStreamSynchronize(stream); 
    ASSERT_EQ(0, cudaResult);

    cudaMemcpyAsync(scalar2.buffer(), dZ2, scalar2.lengthOf() * scalar2.sizeOfT(), cudaMemcpyDeviceToHost, stream);

    cudaResult = cudaStreamSynchronize(stream); 
    ASSERT_EQ(0, cudaResult);

	ASSERT_NEAR(exp2.e<float>(0), scalar2.e<float>(0), 1e-5);
    
	/***************************************/

	cudaFree(dX1); 			cudaFree(dX2); cudaFree(dX3); 		   cudaFree(dX4); 	cudaFree(dZ1); 				cudaFree(dZ2);
	cudaFree(dX1ShapeInfo); 			   cudaFree(dX3ShapeInfo); 					cudaFree(dZ1ShapeInfo);		cudaFree(dZ2ShapeInfo);

	/***************************************/	

	cudaResult = cudaStreamDestroy(stream); 
	ASSERT_EQ(0, cudaResult);
}
 

////////////////////////////////////////////////////////////////////////////
TEST_F(CudaBasicsTests, execReduce3_1) {

    NDArray x('c', {2,2}, {1,2,3,4}, nd4j::DataType::INT32);
    NDArray y('c', {2,2}, {-1,-2,-3,-4}, nd4j::DataType::INT32);

    NDArray exp('c', {0}, {-30}, nd4j::DataType::FLOAT32);
    NDArray z('c', {0},   nd4j::DataType::FLOAT32);

    std::vector<int> dimensions = {0, 1};   

    // prepare input arrays for prepareDataForCuda function
    const int argsNum = 7;
    std::vector<void*> devicePtrs(argsNum, nullptr);
    std::vector<std::pair<void*,size_t>> hostData;
    
	hostData.emplace_back(x.buffer(), x.lengthOf() * x.sizeOfT());									// 0 -- dX
	hostData.emplace_back(x.getShapeInfo(), shape::shapeInfoByteLength(x.getShapeInfo()));			// 1 -- dXShapeInfo
	hostData.emplace_back(y.buffer(), y.lengthOf() * y.sizeOfT());									// 2 -- dY
	hostData.emplace_back(y.getShapeInfo(), shape::shapeInfoByteLength(y.getShapeInfo()));			// 3 -- dYShapeInfo
	hostData.emplace_back(z.buffer(), z.lengthOf() * z.sizeOfT());									// 4 -- dZ
	hostData.emplace_back(z.getShapeInfo(), shape::shapeInfoByteLength(z.getShapeInfo()));			// 5 -- dZShapeInfo
	hostData.emplace_back(dimensions.data(), dimensions.size() * sizeof(int));						// 6 -- dimensions
	
	// create cuda stream and LaunchContext
	cudaError_t cudaResult;
	cudaStream_t stream;
	cudaResult = cudaStreamCreate(&stream);	ASSERT_EQ(0, cudaResult);
	LaunchContext lc(&stream);

	// allocate required amount of global device memory and copy host data to it 		

	cudaResult = allocateDeviceMem(lc, devicePtrs, hostData);	ASSERT_EQ(0, cudaResult);
		
	// call cuda kernel which calculates result
	NativeOpExecutioner::execReduce3(&lc, nd4j::reduce3::Dot, nullptr, x.getShapeInfo(), devicePtrs[0], (Nd4jLong*)devicePtrs[1], nullptr, nullptr, y.getShapeInfo(), devicePtrs[2], (Nd4jLong*)devicePtrs[3], nullptr, z.getShapeInfo(), devicePtrs[4], (Nd4jLong*)devicePtrs[5], (int*)devicePtrs[6], dimensions.size(), nullptr, nullptr, nullptr, nullptr);

	cudaResult = cudaStreamSynchronize(stream); ASSERT_EQ(0, cudaResult);
    cudaMemcpyAsync(z.buffer(), devicePtrs[4], z.lengthOf() * z.sizeOfT(), cudaMemcpyDeviceToHost, stream);    
 	
 	// verify results
 	for (int e = 0; e < z.lengthOf(); e++) 
 		ASSERT_NEAR(exp.e<double>(e), z.e<double>(e), 1e-5);

	// free allocated global device memory
	for(int i = 0; i < devicePtrs.size(); ++i) cudaFree(devicePtrs[i]);	

	// delete cuda stream
	cudaResult = cudaStreamDestroy(stream); ASSERT_EQ(0, cudaResult);
}


////////////////////////////////////////////////////////////////////////////
TEST_F(CudaBasicsTests, execReduce3_2) {
    
	NDArray x('c', {2,2}, {1.5,1.5,1.5,1.5}, nd4j::DataType::DOUBLE);
    NDArray y('c', {2,2}, {1,2,3,4}, nd4j::DataType::DOUBLE);

    NDArray exp('c', {0}, {15}, nd4j::DataType::DOUBLE);
    NDArray z('c', {0},   nd4j::DataType::DOUBLE);
   
    std::vector<int> dimensions = {0, 1};   

    // prepare input arrays for prepareDataForCuda function
    const int argsNum = 7;
    std::vector<void*> devicePtrs(argsNum, nullptr);
    std::vector<std::pair<void*,size_t>> hostData;
    
	hostData.emplace_back(x.buffer(), x.lengthOf() * x.sizeOfT());									// 0 -- dX
	hostData.emplace_back(x.getShapeInfo(), shape::shapeInfoByteLength(x.getShapeInfo()));			// 1 -- dXShapeInfo
	hostData.emplace_back(y.buffer(), y.lengthOf() * y.sizeOfT());									// 2 -- dY
	hostData.emplace_back(y.getShapeInfo(), shape::shapeInfoByteLength(y.getShapeInfo()));			// 3 -- dYShapeInfo
	hostData.emplace_back(z.buffer(), z.lengthOf() * z.sizeOfT());									// 4 -- dZ
	hostData.emplace_back(z.getShapeInfo(), shape::shapeInfoByteLength(z.getShapeInfo()));			// 5 -- dZShapeInfo
	hostData.emplace_back(dimensions.data(), dimensions.size() * sizeof(int));						// 6 -- dimensions	

	// create cuda stream and LaunchContext
	cudaError_t cudaResult;
	cudaStream_t stream;
	cudaResult = cudaStreamCreate(&stream);	ASSERT_EQ(0, cudaResult);
	LaunchContext lc(&stream);

	// allocate required amount of global device memory and copy host data to it 		

	cudaResult = allocateDeviceMem(lc, devicePtrs, hostData);	ASSERT_EQ(0, cudaResult);
		
	// call cuda kernel which calculates result
	NativeOpExecutioner::execReduce3(&lc, nd4j::reduce3::Dot, nullptr, x.getShapeInfo(), devicePtrs[0], (Nd4jLong*)devicePtrs[1], nullptr, nullptr, y.getShapeInfo(), devicePtrs[2], (Nd4jLong*)devicePtrs[3], nullptr, z.getShapeInfo(), devicePtrs[4], (Nd4jLong*)devicePtrs[5], (int*)devicePtrs[6], dimensions.size(), nullptr, nullptr, nullptr, nullptr);

	cudaResult = cudaStreamSynchronize(stream); ASSERT_EQ(0, cudaResult);
    cudaMemcpyAsync(z.buffer(), devicePtrs[4], z.lengthOf() * z.sizeOfT(), cudaMemcpyDeviceToHost, stream);    
 	
 	// verify results
 	for (int e = 0; e < z.lengthOf(); e++) 
 		ASSERT_NEAR(exp.e<double>(e), z.e<double>(e), 1e-5);

	// free allocated global device memory
	for(int i = 0; i < devicePtrs.size(); ++i) cudaFree(devicePtrs[i]);	

	// delete cuda stream
	cudaResult = cudaStreamDestroy(stream); ASSERT_EQ(0, cudaResult);
}

////////////////////////////////////////////////////////////////////////////
TEST_F(CudaBasicsTests, execReduce3_3) {
    
	NDArray x('c', {2,3}, {1,2,3,4,5,6}, nd4j::DataType::INT32);
    NDArray y('c', {2,3}, {-6,-5,-4,-3,-2,-1}, nd4j::DataType::INT32);        

    NDArray exp('c', {3}, {-18,-20,-18}, nd4j::DataType::FLOAT32);
    NDArray z('c', {3}, nd4j::DataType::FLOAT32);
   
    std::vector<int> dimensions = {0};

    // evaluate xTad data 
    shape::TAD xTad(x.getShapeInfo(), dimensions.data(), dimensions.size());
    xTad.createTadOnlyShapeInfo();
    xTad.createOffsets();

    // evaluate yTad data
    shape::TAD yTad(y.getShapeInfo(), dimensions.data(), dimensions.size());    	    
    yTad.createTadOnlyShapeInfo();
    yTad.createOffsets();

    // prepare input arrays for prepareDataForCuda function
    const int argsNum = 11;
    std::vector<void*> devicePtrs(argsNum, nullptr);
    std::vector<std::pair<void*,size_t>> hostData;
    
	hostData.emplace_back(x.buffer(), x.lengthOf() * x.sizeOfT());									// 0 -- dX
	hostData.emplace_back(x.getShapeInfo(), shape::shapeInfoByteLength(x.getShapeInfo()));			// 1 -- dXShapeInfo
	hostData.emplace_back(y.buffer(), y.lengthOf() * y.sizeOfT());									// 2 -- dY
	hostData.emplace_back(y.getShapeInfo(), shape::shapeInfoByteLength(y.getShapeInfo()));			// 3 -- dYShapeInfo
	hostData.emplace_back(z.buffer(), z.lengthOf() * z.sizeOfT());									// 4 -- dZ
	hostData.emplace_back(z.getShapeInfo(), shape::shapeInfoByteLength(z.getShapeInfo()));			// 5 -- dZShapeInfo
	hostData.emplace_back(dimensions.data(), dimensions.size() * sizeof(int));						// 6 -- dimensions
	hostData.emplace_back(xTad.tadOnlyShapeInfo, shape::shapeInfoByteLength(xTad.tadOnlyShapeInfo));// 7 -- xTadShapeInfo
	hostData.emplace_back(xTad.tadOffsets, xTad.numTads * sizeof(Nd4jLong));						// 8 -- xTadOffsets
	hostData.emplace_back(yTad.tadOnlyShapeInfo, shape::shapeInfoByteLength(yTad.tadOnlyShapeInfo));// 9 -- yTadShapeInfo
	hostData.emplace_back(yTad.tadOffsets, yTad.numTads * sizeof(Nd4jLong));						// 10-- yTadOffsets	

	// create cuda stream and LaunchContext
	cudaError_t cudaResult;
	cudaStream_t stream;
	cudaResult = cudaStreamCreate(&stream);	ASSERT_EQ(0, cudaResult);
	LaunchContext lc(&stream);

	// allocate required amount of global device memory and copy host data to it 		

	cudaResult = allocateDeviceMem(lc, devicePtrs, hostData);	ASSERT_EQ(0, cudaResult);
		
	// call cuda kernel which calculates result
	NativeOpExecutioner::execReduce3(&lc, nd4j::reduce3::Dot, nullptr, x.getShapeInfo(), devicePtrs[0], (Nd4jLong*)devicePtrs[1], nullptr, nullptr, y.getShapeInfo(), devicePtrs[2], (Nd4jLong*)devicePtrs[3], nullptr, z.getShapeInfo(), devicePtrs[4], (Nd4jLong*)devicePtrs[5], (int*)devicePtrs[6], dimensions.size(), (Nd4jLong*)devicePtrs[7], (Nd4jLong*)devicePtrs[8], (Nd4jLong*)devicePtrs[9], (Nd4jLong*)devicePtrs[10]);

	cudaResult = cudaStreamSynchronize(stream); ASSERT_EQ(0, cudaResult);
    cudaMemcpyAsync(z.buffer(), devicePtrs[4], z.lengthOf() * z.sizeOfT(), cudaMemcpyDeviceToHost, stream);    
 	
 	// verify results
 	for (int e = 0; e < z.lengthOf(); e++) 
 		ASSERT_NEAR(exp.e<double>(e), z.e<double>(e), 1e-5);

	// free allocated global device memory
	for(int i = 0; i < devicePtrs.size(); ++i) cudaFree(devicePtrs[i]);	

	// delete cuda stream
	cudaResult = cudaStreamDestroy(stream); ASSERT_EQ(0, cudaResult);
}

////////////////////////////////////////////////////////////////////////////
TEST_F(CudaBasicsTests, execReduce3_4) {
    	
    NDArray x('c', {2,3}, {1,2,3,4,5,6}, nd4j::DataType::DOUBLE);
    NDArray y('c', {2,3}, {1.5,1.5,1.5,1.5,1.5,1.5}, nd4j::DataType::DOUBLE);

    NDArray exp('c', {2}, {9,22.5}, nd4j::DataType::DOUBLE);
    NDArray z('c', {2}, nd4j::DataType::DOUBLE);
   
    std::vector<int> dimensions = {1};

    // evaluate xTad data 
    shape::TAD xTad(x.getShapeInfo(), dimensions.data(), dimensions.size());
    xTad.createTadOnlyShapeInfo();
    xTad.createOffsets();

    // evaluate yTad data
    shape::TAD yTad(y.getShapeInfo(), dimensions.data(), dimensions.size());    	    
    yTad.createTadOnlyShapeInfo();
    yTad.createOffsets();

    // prepare input arrays for prepareDataForCuda function
    const int argsNum = 11;
    std::vector<void*> devicePtrs(argsNum, nullptr);
    std::vector<std::pair<void*,size_t>> hostData;
    
	hostData.emplace_back(x.buffer(), x.lengthOf() * x.sizeOfT());									// 0 -- dX
	hostData.emplace_back(x.getShapeInfo(), shape::shapeInfoByteLength(x.getShapeInfo()));			// 1 -- dXShapeInfo
	hostData.emplace_back(y.buffer(), y.lengthOf() * y.sizeOfT());									// 2 -- dY
	hostData.emplace_back(y.getShapeInfo(), shape::shapeInfoByteLength(y.getShapeInfo()));			// 3 -- dYShapeInfo
	hostData.emplace_back(z.buffer(), z.lengthOf() * z.sizeOfT());									// 4 -- dZ
	hostData.emplace_back(z.getShapeInfo(), shape::shapeInfoByteLength(z.getShapeInfo()));			// 5 -- dZShapeInfo
	hostData.emplace_back(dimensions.data(), dimensions.size() * sizeof(int));						// 6 -- dimensions
	hostData.emplace_back(xTad.tadOnlyShapeInfo, shape::shapeInfoByteLength(xTad.tadOnlyShapeInfo));// 7 -- xTadShapeInfo
	hostData.emplace_back(xTad.tadOffsets, xTad.numTads * sizeof(Nd4jLong));						// 8 -- xTadOffsets
	hostData.emplace_back(yTad.tadOnlyShapeInfo, shape::shapeInfoByteLength(yTad.tadOnlyShapeInfo));// 9 -- yTadShapeInfo
	hostData.emplace_back(yTad.tadOffsets, yTad.numTads * sizeof(Nd4jLong));						// 10-- yTadOffsets	

	// create cuda stream and LaunchContext
	cudaError_t cudaResult;
	cudaStream_t stream;
	cudaResult = cudaStreamCreate(&stream);	ASSERT_EQ(0, cudaResult);
	LaunchContext lc(&stream);

	// allocate required amount of global device memory and copy host data to it 		

	cudaResult = allocateDeviceMem(lc, devicePtrs, hostData);	ASSERT_EQ(0, cudaResult);
		
	// call cuda kernel which calculates result
	NativeOpExecutioner::execReduce3(&lc, nd4j::reduce3::Dot, nullptr, x.getShapeInfo(), devicePtrs[0], (Nd4jLong*)devicePtrs[1], nullptr, nullptr, y.getShapeInfo(), devicePtrs[2], (Nd4jLong*)devicePtrs[3], nullptr, z.getShapeInfo(), devicePtrs[4], (Nd4jLong*)devicePtrs[5], (int*)devicePtrs[6], dimensions.size(), (Nd4jLong*)devicePtrs[7], (Nd4jLong*)devicePtrs[8], (Nd4jLong*)devicePtrs[9], (Nd4jLong*)devicePtrs[10]);

	cudaResult = cudaStreamSynchronize(stream); ASSERT_EQ(0, cudaResult);
    cudaMemcpyAsync(z.buffer(), devicePtrs[4], z.lengthOf() * z.sizeOfT(), cudaMemcpyDeviceToHost, stream);    
 	
 	// verify results
 	for (int e = 0; e < z.lengthOf(); e++) 
 		ASSERT_NEAR(exp.e<double>(e), z.e<double>(e), 1e-5);

	// free allocated global device memory
	for(int i = 0; i < devicePtrs.size(); ++i) cudaFree(devicePtrs[i]);	

	// delete cuda stream
	cudaResult = cudaStreamDestroy(stream); ASSERT_EQ(0, cudaResult);
}

////////////////////////////////////////////////////////////////////////////
TEST_F(CudaBasicsTests, execReduce3_5) {
    	
    NDArray x('c', {2,2,3}, {1.5,1.5,1.5,1.5,1.5,1.5,1.5,1.5,1.5,1.5,1.5,1.5}, nd4j::DataType::FLOAT32);
    NDArray y('c', {2,2,3}, {1,2,3,4,5,6,7,8,9,10,11,12}, nd4j::DataType::FLOAT32);

    NDArray exp('c', {2,3}, {7.5, 10.5, 13.5, 25.5, 28.5, 31.5}, nd4j::DataType::FLOAT32);
    NDArray z('c', {2,3}, nd4j::DataType::FLOAT32);
   
    std::vector<int> dimensions = {1};

    // evaluate xTad data 
    shape::TAD xTad(x.getShapeInfo(), dimensions.data(), dimensions.size());
    xTad.createTadOnlyShapeInfo();
    xTad.createOffsets();

    // evaluate yTad data
    shape::TAD yTad(y.getShapeInfo(), dimensions.data(), dimensions.size());    	    
    yTad.createTadOnlyShapeInfo();
    yTad.createOffsets();

    // prepare input arrays for prepareDataForCuda function
    const int argsNum = 11;
    std::vector<void*> devicePtrs(argsNum, nullptr);
    std::vector<std::pair<void*,size_t>> hostData;
    
	hostData.emplace_back(x.buffer(), x.lengthOf() * x.sizeOfT());									// 0 -- dX
	hostData.emplace_back(x.getShapeInfo(), shape::shapeInfoByteLength(x.getShapeInfo()));			// 1 -- dXShapeInfo
	hostData.emplace_back(y.buffer(), y.lengthOf() * y.sizeOfT());									// 2 -- dY
	hostData.emplace_back(y.getShapeInfo(), shape::shapeInfoByteLength(y.getShapeInfo()));			// 3 -- dYShapeInfo
	hostData.emplace_back(z.buffer(), z.lengthOf() * z.sizeOfT());									// 4 -- dZ
	hostData.emplace_back(z.getShapeInfo(), shape::shapeInfoByteLength(z.getShapeInfo()));			// 5 -- dZShapeInfo
	hostData.emplace_back(dimensions.data(), dimensions.size() * sizeof(int));						// 6 -- dimensions
	hostData.emplace_back(xTad.tadOnlyShapeInfo, shape::shapeInfoByteLength(xTad.tadOnlyShapeInfo));// 7 -- xTadShapeInfo
	hostData.emplace_back(xTad.tadOffsets, xTad.numTads * sizeof(Nd4jLong));						// 8 -- xTadOffsets
	hostData.emplace_back(yTad.tadOnlyShapeInfo, shape::shapeInfoByteLength(yTad.tadOnlyShapeInfo));// 9 -- yTadShapeInfo
	hostData.emplace_back(yTad.tadOffsets, yTad.numTads * sizeof(Nd4jLong));						// 10-- yTadOffsets	

	// create cuda stream and LaunchContext
	cudaError_t cudaResult;
	cudaStream_t stream;
	cudaResult = cudaStreamCreate(&stream);	ASSERT_EQ(0, cudaResult);
	LaunchContext lc(&stream);

	// allocate required amount of global device memory and copy host data to it 		

	cudaResult = allocateDeviceMem(lc, devicePtrs, hostData);	ASSERT_EQ(0, cudaResult);
		
	// call cuda kernel which calculates result
	NativeOpExecutioner::execReduce3(&lc, nd4j::reduce3::Dot, nullptr, x.getShapeInfo(), devicePtrs[0], (Nd4jLong*)devicePtrs[1], nullptr, nullptr, y.getShapeInfo(), devicePtrs[2], (Nd4jLong*)devicePtrs[3], nullptr, z.getShapeInfo(), devicePtrs[4], (Nd4jLong*)devicePtrs[5], (int*)devicePtrs[6], dimensions.size(), (Nd4jLong*)devicePtrs[7], (Nd4jLong*)devicePtrs[8], (Nd4jLong*)devicePtrs[9], (Nd4jLong*)devicePtrs[10]);

	cudaResult = cudaStreamSynchronize(stream); ASSERT_EQ(0, cudaResult);
    cudaMemcpyAsync(z.buffer(), devicePtrs[4], z.lengthOf() * z.sizeOfT(), cudaMemcpyDeviceToHost, stream);    
 	
 	// verify results
 	for (int e = 0; e < z.lengthOf(); e++) 
 		ASSERT_NEAR(exp.e<double>(e), z.e<double>(e), 1e-5);

	// free allocated global device memory
	for(int i = 0; i < devicePtrs.size(); ++i) cudaFree(devicePtrs[i]);	

	// delete cuda stream
	cudaResult = cudaStreamDestroy(stream); ASSERT_EQ(0, cudaResult);
}

////////////////////////////////////////////////////////////////////////////
TEST_F(CudaBasicsTests, execReduce3All_1) {
    	
    NDArray x('c', {2,2}, {1,2,3,4}, nd4j::DataType::INT32);
    NDArray y('c', {2,3}, {-1,1,-1,1,-1,1}, nd4j::DataType::INT32);

    NDArray exp('c', {2,3}, {2,-2,2,2,-2,2}, nd4j::DataType::FLOAT32);
    NDArray z('c', {2,3}, nd4j::DataType::FLOAT32);
   
    std::vector<int> dimensions = {0};

    // evaluate xTad data 
    shape::TAD xTad(x.getShapeInfo(), dimensions.data(), dimensions.size());
    xTad.createTadOnlyShapeInfo();
    xTad.createOffsets();

    // evaluate yTad data
    shape::TAD yTad(y.getShapeInfo(), dimensions.data(), dimensions.size());    	    
    yTad.createTadOnlyShapeInfo();
    yTad.createOffsets();

    // prepare input arrays for prepareDataForCuda function
    const int argsNum = 11;
    std::vector<void*> devicePtrs(argsNum, nullptr);
    std::vector<std::pair<void*,size_t>> hostData;
    
	hostData.emplace_back(x.buffer(), x.lengthOf() * x.sizeOfT());									// 0 -- dX
	hostData.emplace_back(x.getShapeInfo(), shape::shapeInfoByteLength(x.getShapeInfo()));			// 1 -- dXShapeInfo
	hostData.emplace_back(y.buffer(), y.lengthOf() * y.sizeOfT());									// 2 -- dY
	hostData.emplace_back(y.getShapeInfo(), shape::shapeInfoByteLength(y.getShapeInfo()));			// 3 -- dYShapeInfo
	hostData.emplace_back(z.buffer(), z.lengthOf() * z.sizeOfT());									// 4 -- dZ
	hostData.emplace_back(z.getShapeInfo(), shape::shapeInfoByteLength(z.getShapeInfo()));			// 5 -- dZShapeInfo
	hostData.emplace_back(dimensions.data(), dimensions.size() * sizeof(int));						// 6 -- dimensions
	hostData.emplace_back(xTad.tadOnlyShapeInfo, shape::shapeInfoByteLength(xTad.tadOnlyShapeInfo));// 7 -- xTadShapeInfo
	hostData.emplace_back(xTad.tadOffsets, xTad.numTads * sizeof(Nd4jLong));						// 8 -- xTadOffsets
	hostData.emplace_back(yTad.tadOnlyShapeInfo, shape::shapeInfoByteLength(yTad.tadOnlyShapeInfo));// 9 -- yTadShapeInfo
	hostData.emplace_back(yTad.tadOffsets, yTad.numTads * sizeof(Nd4jLong));						// 10-- yTadOffsets	

	// create cuda stream and LaunchContext
	cudaError_t cudaResult;
	cudaStream_t stream;
	cudaResult = cudaStreamCreate(&stream);	ASSERT_EQ(0, cudaResult);
	LaunchContext lc(&stream);

	// allocate required amount of global device memory and copy host data to it 		

	cudaResult = allocateDeviceMem(lc, devicePtrs, hostData);	ASSERT_EQ(0, cudaResult);
		
	// call cuda kernel which calculates result
	NativeOpExecutioner::execReduce3All(&lc, nd4j::reduce3::Dot, nullptr, x.getShapeInfo(), devicePtrs[0], (Nd4jLong*)devicePtrs[1], nullptr, nullptr, y.getShapeInfo(), devicePtrs[2], (Nd4jLong*)devicePtrs[3], nullptr, z.getShapeInfo(), devicePtrs[4], (Nd4jLong*)devicePtrs[5], (int*)devicePtrs[6], dimensions.size(), (Nd4jLong*)devicePtrs[7], (Nd4jLong*)devicePtrs[8], (Nd4jLong*)devicePtrs[9], (Nd4jLong*)devicePtrs[10]);

	cudaResult = cudaStreamSynchronize(stream); ASSERT_EQ(0, cudaResult);
    cudaMemcpyAsync(z.buffer(), devicePtrs[4], z.lengthOf() * z.sizeOfT(), cudaMemcpyDeviceToHost, stream);    
 	
 	// verify results
 	for (int e = 0; e < z.lengthOf(); e++) 
 		ASSERT_NEAR(exp.e<double>(e), z.e<double>(e), 1e-5);

	// free allocated global device memory
	for(int i = 0; i < devicePtrs.size(); ++i) cudaFree(devicePtrs[i]);	

	// delete cuda stream
	cudaResult = cudaStreamDestroy(stream); ASSERT_EQ(0, cudaResult);
}

////////////////////////////////////////////////////////////////////////////
TEST_F(CudaBasicsTests, execReduce3All_2) {
    	
    NDArray x('c', {2,2}, {1,2,3,4}, nd4j::DataType::DOUBLE);
    NDArray y('c', {2,3}, {1.5,1.5,1.5,1.5,1.5,1.5}, nd4j::DataType::DOUBLE);    

    NDArray exp('c', {2,3}, {6,6,6,9,9,9}, nd4j::DataType::DOUBLE);    
    NDArray z('c', {2,3}, nd4j::DataType::DOUBLE);    
   
    std::vector<int> dimensions = {0};

    // evaluate xTad data 
    shape::TAD xTad(x.getShapeInfo(), dimensions.data(), dimensions.size());
    xTad.createTadOnlyShapeInfo();
    xTad.createOffsets();

    // evaluate yTad data
    shape::TAD yTad(y.getShapeInfo(), dimensions.data(), dimensions.size());    	    
    yTad.createTadOnlyShapeInfo();
    yTad.createOffsets();

    // prepare input arrays for prepareDataForCuda function
    const int argsNum = 11;
    std::vector<void*> devicePtrs(argsNum, nullptr);
    std::vector<std::pair<void*,size_t>> hostData;
    
	hostData.emplace_back(x.buffer(), x.lengthOf() * x.sizeOfT());									// 0 -- dX
	hostData.emplace_back(x.getShapeInfo(), shape::shapeInfoByteLength(x.getShapeInfo()));			// 1 -- dXShapeInfo
	hostData.emplace_back(y.buffer(), y.lengthOf() * y.sizeOfT());									// 2 -- dY
	hostData.emplace_back(y.getShapeInfo(), shape::shapeInfoByteLength(y.getShapeInfo()));			// 3 -- dYShapeInfo
	hostData.emplace_back(z.buffer(), z.lengthOf() * z.sizeOfT());									// 4 -- dZ
	hostData.emplace_back(z.getShapeInfo(), shape::shapeInfoByteLength(z.getShapeInfo()));			// 5 -- dZShapeInfo
	hostData.emplace_back(dimensions.data(), dimensions.size() * sizeof(int));						// 6 -- dimensions
	hostData.emplace_back(xTad.tadOnlyShapeInfo, shape::shapeInfoByteLength(xTad.tadOnlyShapeInfo));// 7 -- xTadShapeInfo
	hostData.emplace_back(xTad.tadOffsets, xTad.numTads * sizeof(Nd4jLong));						// 8 -- xTadOffsets
	hostData.emplace_back(yTad.tadOnlyShapeInfo, shape::shapeInfoByteLength(yTad.tadOnlyShapeInfo));// 9 -- yTadShapeInfo
	hostData.emplace_back(yTad.tadOffsets, yTad.numTads * sizeof(Nd4jLong));						// 10-- yTadOffsets	

	// create cuda stream and LaunchContext
	cudaError_t cudaResult;
	cudaStream_t stream;
	cudaResult = cudaStreamCreate(&stream);	ASSERT_EQ(0, cudaResult);
	LaunchContext lc(&stream);

	// allocate required amount of global device memory and copy host data to it 		

	cudaResult = allocateDeviceMem(lc, devicePtrs, hostData);	ASSERT_EQ(0, cudaResult);
		
	// call cuda kernel which calculates result
	NativeOpExecutioner::execReduce3All(&lc, nd4j::reduce3::Dot, nullptr, x.getShapeInfo(), devicePtrs[0], (Nd4jLong*)devicePtrs[1], nullptr, nullptr, y.getShapeInfo(), devicePtrs[2], (Nd4jLong*)devicePtrs[3], nullptr, z.getShapeInfo(), devicePtrs[4], (Nd4jLong*)devicePtrs[5], (int*)devicePtrs[6], dimensions.size(), (Nd4jLong*)devicePtrs[7], (Nd4jLong*)devicePtrs[8], (Nd4jLong*)devicePtrs[9], (Nd4jLong*)devicePtrs[10]);

	cudaResult = cudaStreamSynchronize(stream); ASSERT_EQ(0, cudaResult);
    cudaMemcpyAsync(z.buffer(), devicePtrs[4], z.lengthOf() * z.sizeOfT(), cudaMemcpyDeviceToHost, stream);    
 	
 	// verify results
 	for (int e = 0; e < z.lengthOf(); e++) 
 		ASSERT_NEAR(exp.e<double>(e), z.e<double>(e), 1e-5);

	// free allocated global device memory
	for(int i = 0; i < devicePtrs.size(); ++i) cudaFree(devicePtrs[i]);	

	// delete cuda stream
	cudaResult = cudaStreamDestroy(stream); ASSERT_EQ(0, cudaResult);
}

////////////////////////////////////////////////////////////////////////////
TEST_F(CudaBasicsTests, execIndexReduce_1) {
    	
    NDArray x('c', {2,3}, nd4j::DataType::DOUBLE);
    x.linspace(-2.);
    NDArray exp('c', {2}, {2, 2}, nd4j::DataType::INT64);
    NDArray z('c', {2}, nd4j::DataType::INT64);
    
    std::vector<int> dimensions = {1};          

    // evaluate xTad data 
    shape::TAD xTad(x.getShapeInfo(), dimensions.data(), dimensions.size());
    xTad.createTadOnlyShapeInfo();
    xTad.createOffsets();

    // prepare input arrays for prepareDataForCuda function
    const int argsNum = 7;
    std::vector<void*> devicePtrs(argsNum, nullptr);
    std::vector<std::pair<void*,size_t>> hostData;
    
	hostData.emplace_back(x.buffer(), x.lengthOf() * x.sizeOfT());									// 0 -- dX
	hostData.emplace_back(x.getShapeInfo(), shape::shapeInfoByteLength(x.getShapeInfo()));			// 1 -- dXShapeInfo
	hostData.emplace_back(z.buffer(), z.lengthOf() * z.sizeOfT());									// 2 -- dZ
	hostData.emplace_back(z.getShapeInfo(), shape::shapeInfoByteLength(z.getShapeInfo()));			// 3 -- dZShapeInfo
	hostData.emplace_back(dimensions.data(), dimensions.size() * sizeof(int));						// 4 -- dimensions
	hostData.emplace_back(xTad.tadOnlyShapeInfo, shape::shapeInfoByteLength(xTad.tadOnlyShapeInfo));// 5 -- xTadShapeInfo
	hostData.emplace_back(xTad.tadOffsets, xTad.numTads * sizeof(Nd4jLong));						// 6 -- xTadOffsets
	
	// create cuda stream and LaunchContext
	cudaError_t cudaResult;
	cudaStream_t stream;
	cudaResult = cudaStreamCreate(&stream);	ASSERT_EQ(0, cudaResult);
	LaunchContext lc(&stream);

	// allocate required amount of global device memory and copy host data to it 		

	cudaResult = allocateDeviceMem(lc, devicePtrs, hostData);	ASSERT_EQ(0, cudaResult);
		
	// call cuda kernel which calculates result
	NativeOpExecutioner::execIndexReduce(&lc, nd4j::indexreduce::IndexMax, 
		nullptr, x.getShapeInfo(), devicePtrs[0], (Nd4jLong*)devicePtrs[1], 
		nullptr, 
		nullptr, z.getShapeInfo(), devicePtrs[2], (Nd4jLong*)devicePtrs[3], 
		(int*)devicePtrs[4], dimensions.size(), 
		(Nd4jLong*)devicePtrs[5], (Nd4jLong*)devicePtrs[6]);

	cudaResult = cudaStreamSynchronize(stream); ASSERT_EQ(0, cudaResult);
    cudaMemcpyAsync(z.buffer(), devicePtrs[2], z.lengthOf() * z.sizeOfT(), cudaMemcpyDeviceToHost, stream);    
 	
 	// verify results
 	for (int e = 0; e < z.lengthOf(); e++) 
 		ASSERT_NEAR(exp.e<double>(e), z.e<double>(e), 1e-5);

	// free allocated global device memory
	for(int i = 0; i < devicePtrs.size(); ++i) 
		cudaFree(devicePtrs[i]);	

	// delete cuda stream
	cudaResult = cudaStreamDestroy(stream); ASSERT_EQ(0, cudaResult);
}

////////////////////////////////////////////////////////////////////////////
TEST_F(CudaBasicsTests, execIndexReduce_2) {
    	
    NDArray x('c', {2,3,4,5}, nd4j::DataType::FLOAT32);
    x.linspace(-2.f);
    NDArray exp('c', {2,5}, {11,11,11,11,11,11,11,11,11,11}, nd4j::DataType::INT64);    
    NDArray z('c', {2,5}, nd4j::DataType::INT64);
    
    std::vector<int> dimensions = {1,2};     

    // evaluate xTad data 
    shape::TAD xTad(x.getShapeInfo(), dimensions.data(), dimensions.size());
    xTad.createTadOnlyShapeInfo();
    xTad.createOffsets();

    // prepare input arrays for prepareDataForCuda function
    const int argsNum = 7;
    std::vector<void*> devicePtrs(argsNum, nullptr);
    std::vector<std::pair<void*,size_t>> hostData;
    
	hostData.emplace_back(x.buffer(), x.lengthOf() * x.sizeOfT());									// 0 -- dX
	hostData.emplace_back(x.getShapeInfo(), shape::shapeInfoByteLength(x.getShapeInfo()));			// 1 -- dXShapeInfo
	hostData.emplace_back(z.buffer(), z.lengthOf() * z.sizeOfT());									// 2 -- dZ
	hostData.emplace_back(z.getShapeInfo(), shape::shapeInfoByteLength(z.getShapeInfo()));			// 3 -- dZShapeInfo
	hostData.emplace_back(dimensions.data(), dimensions.size() * sizeof(int));						// 4 -- dimensions
	hostData.emplace_back(xTad.tadOnlyShapeInfo, shape::shapeInfoByteLength(xTad.tadOnlyShapeInfo));// 5 -- xTadShapeInfo
	hostData.emplace_back(xTad.tadOffsets, xTad.numTads * sizeof(Nd4jLong));						// 6 -- xTadOffsets
	
	// create cuda stream and LaunchContext
	cudaError_t cudaResult;
	cudaStream_t stream;
	cudaResult = cudaStreamCreate(&stream);	ASSERT_EQ(0, cudaResult);
	LaunchContext lc(&stream);

	// allocate required amount of global device memory and copy host data to it 		

	cudaResult = allocateDeviceMem(lc, devicePtrs, hostData);	ASSERT_EQ(0, cudaResult);
		
	// call cuda kernel which calculates result
	NativeOpExecutioner::execIndexReduce(&lc, nd4j::indexreduce::IndexMax, 
		nullptr, x.getShapeInfo(), devicePtrs[0], (Nd4jLong*)devicePtrs[1], 
		nullptr, 
		nullptr, z.getShapeInfo(), devicePtrs[2], (Nd4jLong*)devicePtrs[3], 
		(int*)devicePtrs[4], dimensions.size(), 
		(Nd4jLong*)devicePtrs[5], (Nd4jLong*)devicePtrs[6]);

	cudaResult = cudaStreamSynchronize(stream); ASSERT_EQ(0, cudaResult);
    cudaMemcpyAsync(z.buffer(), devicePtrs[2], z.lengthOf() * z.sizeOfT(), cudaMemcpyDeviceToHost, stream);    
 	
 	// verify results
 	for (int e = 0; e < z.lengthOf(); e++) 
 		ASSERT_NEAR(exp.e<double>(e), z.e<double>(e), 1e-5);

	// free allocated global device memory
	for(int i = 0; i < devicePtrs.size(); ++i) 
		cudaFree(devicePtrs[i]);	

	// delete cuda stream
	cudaResult = cudaStreamDestroy(stream); ASSERT_EQ(0, cudaResult);
}

////////////////////////////////////////////////////////////////////////////
TEST_F(CudaBasicsTests, execIndexReduce_3) {
    	
    NDArray x('c', {2,3,4,5}, nd4j::DataType::DOUBLE);
    x.linspace(-2.);
    NDArray exp('c', {3}, {39, 39, 39}, nd4j::DataType::INT64);    
    NDArray z('c', {3}, nd4j::DataType::INT64);
    
    std::vector<int> dimensions = {0,2,3};

    // evaluate xTad data 
    shape::TAD xTad(x.getShapeInfo(), dimensions.data(), dimensions.size());
    xTad.createTadOnlyShapeInfo();
    xTad.createOffsets();

    // prepare input arrays for prepareDataForCuda function
    const int argsNum = 7;
    std::vector<void*> devicePtrs(argsNum, nullptr);
    std::vector<std::pair<void*,size_t>> hostData;
    
	hostData.emplace_back(x.buffer(), x.lengthOf() * x.sizeOfT());									// 0 -- dX
	hostData.emplace_back(x.getShapeInfo(), shape::shapeInfoByteLength(x.getShapeInfo()));			// 1 -- dXShapeInfo
	hostData.emplace_back(z.buffer(), z.lengthOf() * z.sizeOfT());									// 2 -- dZ
	hostData.emplace_back(z.getShapeInfo(), shape::shapeInfoByteLength(z.getShapeInfo()));			// 3 -- dZShapeInfo
	hostData.emplace_back(dimensions.data(), dimensions.size() * sizeof(int));						// 4 -- dimensions
	hostData.emplace_back(xTad.tadOnlyShapeInfo, shape::shapeInfoByteLength(xTad.tadOnlyShapeInfo));// 5 -- xTadShapeInfo
	hostData.emplace_back(xTad.tadOffsets, xTad.numTads * sizeof(Nd4jLong));						// 6 -- xTadOffsets
	
	// create cuda stream and LaunchContext
	cudaError_t cudaResult;
	cudaStream_t stream;
	cudaResult = cudaStreamCreate(&stream);	ASSERT_EQ(0, cudaResult);
	LaunchContext lc(&stream);

	// allocate required amount of global device memory and copy host data to it 		

	cudaResult = allocateDeviceMem(lc, devicePtrs, hostData);	ASSERT_EQ(0, cudaResult);
		
	// call cuda kernel which calculates result
	NativeOpExecutioner::execIndexReduce(&lc, nd4j::indexreduce::IndexMax, 
		nullptr, x.getShapeInfo(), devicePtrs[0], (Nd4jLong*)devicePtrs[1], 
		nullptr, 
		nullptr, z.getShapeInfo(), devicePtrs[2], (Nd4jLong*)devicePtrs[3], 
		(int*)devicePtrs[4], dimensions.size(), 
		(Nd4jLong*)devicePtrs[5], (Nd4jLong*)devicePtrs[6]);

	cudaResult = cudaStreamSynchronize(stream); ASSERT_EQ(0, cudaResult);
    cudaMemcpyAsync(z.buffer(), devicePtrs[2], z.lengthOf() * z.sizeOfT(), cudaMemcpyDeviceToHost, stream);    
 	
 	// verify results
 	for (int e = 0; e < z.lengthOf(); e++) 
 		ASSERT_NEAR(exp.e<double>(e), z.e<double>(e), 1e-5);

	// free allocated global device memory
	for(int i = 0; i < devicePtrs.size(); ++i) 
		cudaFree(devicePtrs[i]);	

	// delete cuda stream
	cudaResult = cudaStreamDestroy(stream); ASSERT_EQ(0, cudaResult);
}


// printCudaGlobal<double><<<1,1,0,*stream>>>(dX, 6);
//     printCudaGlobal<Nd4jLong><<<1,1,0,*stream>>>(dXShapeInfo, 8);
//     printCudaGlobal<double><<<1,1,0,*stream>>>(dZ, 2);
//     printCudaGlobal<Nd4jLong><<<1,1,0,*stream>>>(dZShapeInfo, 6);
//     printCudaGlobal<int><<<1,1,0,*stream>>>(dimension, 1);
//     printCudaGlobal<Nd4jLong><<<1,1,0,*stream>>>(tadShapeInfo, 6);
//     printCudaGlobal<Nd4jLong><<<1,1,0,*stream>>>(tadOffsets, 2);
//     cudaStreamSynchronize(*stream);