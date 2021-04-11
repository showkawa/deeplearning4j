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
// Created by GS <sgazeos@gmail.com> at 12/10/2019
//

#include <system/op_boilerplate.h>
#if NOT_EXCLUDED(OP_matrix_inverse)

#include <ops/declarable/CustomOperations.h>
#include <ops/declarable/helpers/lup.h>
namespace sd {
    namespace ops {
        CUSTOM_OP_IMPL(lu, 1, 2, false, 0, 0) {
            auto input = INPUT_VARIABLE(0);
            auto z = OUTPUT_VARIABLE(0);

            auto p = OUTPUT_VARIABLE(1);
            if (block.getIArguments()->size()) {
                DataType dtype = (DataType)INT_ARG(0);
                REQUIRE_TRUE(dtype == sd::DataType::INT32 || dtype == sd::DataType::INT64, 0, "lu: Permutation data type should be 32bit or 64bit int only, but '%s' given.", DataTypeUtils::asString(dtype).c_str());            }

            REQUIRE_TRUE(input->rankOf() >=2, 0, "lu: The rank of input array should not less than 2, but %i is given", input->rankOf());
            REQUIRE_TRUE(input->sizeAt(-1) == input->sizeAt(-2), 0, "lu: The last two dimmensions should be equal, but %i and %i are given", input->sizeAt(-1), input->sizeAt(-2));

            helpers::lu(block.launchContext(), input, z, p);
            return Status::OK();
        }
        
        DECLARE_SHAPE_FN(lu) {
            auto in = inputShape->at(0);
            auto shapeVector = ShapeUtils::shapeAsVector(in);
            auto luShape = ShapeBuilders::copyShapeInfoAndType(in, in, true, block.workspace());
            auto dtype = sd::DataType::INT32;
            if (block.getIArguments()->size()) {
                dtype = (DataType)INT_ARG(0);
                REQUIRE_TRUE(dtype == sd::DataType::INT32 || dtype == sd::DataType::INT64, 0, "lu: Permutation data type should be 32bit or 64bit int only, but '%s' given.", DataTypeUtils::asString(dtype).c_str());
            }
            auto luP = ShapeBuilders::createShapeInfo(dtype, shape::order(in), shapeVector.size() - 1,
                    shapeVector.data(),  block.workspace());
            return SHAPELIST(CONSTANT(luShape), CONSTANT(luP));
        }

        DECLARE_TYPES(lu) {
            getOpDescriptor()
                    ->setAllowedInputTypes({ALL_FLOATS})
                    ->setAllowedOutputTypes(0, {ALL_FLOATS})
                    ->setAllowedOutputTypes(1, {sd::DataType::INT32, sd::DataType::INT64})
                    ->setSameMode(false);
        }
    }
}

#endif