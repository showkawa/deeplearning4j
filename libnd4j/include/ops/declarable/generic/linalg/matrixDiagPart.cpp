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
// Created to use with batched tensor by GS <sgazeos@gmail.com> 3/21/2018
//

#include <ops/declarable/CustomOperations.h>
#include <ops/declarable/helpers/matrix_diag_part.h>


namespace sd {
    namespace ops {
        CUSTOM_OP_IMPL(matrix_diag_part, 1, 1, false, 0, 0) {
            auto input  = INPUT_VARIABLE(0);
            auto output = OUTPUT_VARIABLE(0);
            const int inRank = input->rankOf();

            REQUIRE_TRUE(inRank >= 2, 0, "CUSTOM_OP matrix_diag_part: input array must have rank >= 2, but %i given!", inRank);

            output->nullify();
            return helpers::matrixDiagPart(block.launchContext(), input, output);
        }

        DECLARE_SHAPE_FN(matrix_diag_part) {
            Nd4jLong const* outShapeInfo = nullptr;
            auto in = inputShape->at(0);
            int inRank = shape::rank(in);

            REQUIRE_TRUE(inRank >= 2, 0, "CUSTOM_OP matrix_diag_part: input array must have rank >= 2, but %i given!", inRank);

            int outRank = inRank - 1;
            int lastDimension = sd::math::nd4j_min(shape::sizeAt(in, -1), shape::sizeAt(in, -2));
            if(outRank == 1) {
                //output shape is a vector with size min(sizeAt(0), sizeAt(1))
                outShapeInfo = ConstantShapeHelper::getInstance().vectorShapeInfo(lastDimension, ArrayOptions::dataType(in));
            }
            else {
                Nd4jLong* anShapeInfo;
                ALLOCATE(anShapeInfo, block.getWorkspace(), shape::shapeInfoLength(outRank), Nd4jLong);
                anShapeInfo[0] = outRank;
                for(int i = 0; i < outRank - 1; ++i)
                    anShapeInfo[i + 1] = shape::sizeAt(in, i);
                anShapeInfo[outRank] = lastDimension;

                ShapeUtils::updateStridesAndType(anShapeInfo, in, shape::order(in));
                outShapeInfo = CONSTANT(anShapeInfo);
            }
            return SHAPELIST(outShapeInfo);
    }

        DECLARE_TYPES(matrix_diag_part) {
            getOpDescriptor()
                    ->setAllowedInputTypes(sd::DataType::ANY)
                    ->setSameMode(true);
        }
}
}

