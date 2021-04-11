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

#include <system/op_boilerplate.h>
#if NOT_EXCLUDED(OP_evaluate_reduction_shape)

#include <ops/declarable/CustomOperations.h>

namespace sd {
    namespace ops {
        CUSTOM_OP_IMPL(evaluate_reduction_shape, 2, 1, false, 0, 0) {
            auto inputShape = INPUT_VARIABLE(0);
            auto axis = INPUT_VARIABLE(1)->asVectorT<int>();
            auto keepDims = block.numB() > 0 ? B_ARG(0) : false;
            auto oldFormat = block.numB() > 1 ? B_ARG(1) : false;
            auto output = OUTPUT_VARIABLE(0);

            auto shape = inputShape->asVectorT<Nd4jLong>();

            auto tempShapeInfo = ConstantShapeHelper::getInstance().createShapeInfo(sd::DataType::INT64, 'c', shape);
            auto tempReductionShapeInfo = ShapeUtils::evalReduceShapeInfo('c', axis, tempShapeInfo, keepDims, oldFormat, block.workspace());

            REQUIRE_TRUE(output->lengthOf() == shape::rank(tempReductionShapeInfo), 0, "evaluate_reduction_shape: output length should be %i, but got %i instead", shape::rank(tempReductionShapeInfo), output->lengthOf());

            for (int e = 0; e < shape::rank(tempReductionShapeInfo); e++)
                output->p(e, tempReductionShapeInfo[e+1]);

            return Status::OK();
        }

        DECLARE_TYPES(evaluate_reduction_shape) {
            getOpDescriptor()
                    ->setAllowedInputTypes(0, {ALL_INTS})
                    ->setAllowedInputTypes(1, {ALL_INTS})
                    ->setAllowedOutputTypes(0, sd::DataType::INT64);
        }

        DECLARE_SHAPE_FN(evaluate_reduction_shape) {
            auto input = INPUT_VARIABLE(0);
            auto axis = INPUT_VARIABLE(1)->asVectorT<int>();

            auto keepDims = block.numB() > 0 ? B_ARG(0) : false;
            auto oldFormat = block.numB() > 1 ? B_ARG(1) : false;

            Nd4jLong length = input->lengthOf();

            if (keepDims) {
                if (oldFormat) {
                    // for oldFormat we can't go below rank 2
                    length = sd::math::nd4j_max<int>(2, length);
                }
            } else {
                length -= axis.size();
                if (oldFormat) {
                    length = sd::math::nd4j_max<int>(2, length);
                }
            }

            return SHAPELIST(ConstantShapeHelper::getInstance().vectorShapeInfo(length, sd::DataType::INT64));
        }
    }
}
#endif