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
// @author Yurii Shyrma (iuriish@yahoo.com), created on 01.11.2017
//

#include <system/op_boilerplate.h>
#if NOT_EXCLUDED(OP_parallel_stack)

#include <ops/declarable/CustomOperations.h>
#include<ops/declarable/helpers/stack.h>

namespace sd {
namespace ops  {


CUSTOM_OP_IMPL(parallel_stack, -1, 1, false, 0, 0) {
	auto input  = INPUT_VARIABLE(0);
	auto output = OUTPUT_VARIABLE(0);

	// check whether shapes of all input array are the same
	for (int i = 0; i < (int) block.width() - 1; ++i)
		REQUIRE_TRUE(shape::equalsSoft((INPUT_VARIABLE(i))->shapeInfo(), (INPUT_VARIABLE(i+1))->shapeInfo()), 0, "PARALLEL_STACK op: the shapes of all input arrays must be the same !");

 	std::vector<const NDArray*> inArrs(block.width());
 	for(int i = 0; i < block.width(); ++i)
		inArrs[i] = INPUT_VARIABLE(i);

	const int dim = 0;
	helpers::stack(block.launchContext(), inArrs, *output, dim);

  	return Status::OK();
}

	DECLARE_TYPES(parallel_stack) {
		getOpDescriptor()
				->setAllowedInputTypes(sd::DataType::ANY)
				->setAllowedOutputTypes({ALL_FLOATS});
	}

DECLARE_SHAPE_FN(parallel_stack) {

	auto inShapeInfo = inputShape->at(0);
	int rank = inShapeInfo[0];

	Nd4jLong* outShapeInfo = nullptr;
	ALLOCATE(outShapeInfo, block.getWorkspace(), shape::shapeInfoLength(rank+1), Nd4jLong);

	outShapeInfo[0] = rank + 1;
	outShapeInfo[1] = block.width();
	for(int i = 1; i <= rank; ++i)
		outShapeInfo[i+1] = inShapeInfo[i];

	ShapeUtils::updateStridesAndType(outShapeInfo, inShapeInfo, shape::order(inShapeInfo));

  	return SHAPELIST(CONSTANT(outShapeInfo));
}


}
}

#endif