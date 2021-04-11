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
//  @author raver119@gmail.com
//

#include <system/op_boilerplate.h>
#if NOT_EXCLUDED(OP_listdiff)

#include <ops/declarable/headers/parity_ops.h>
#include <ops/declarable/helpers/listdiff.h>

// this op will probably never become GPU-compatible
namespace sd {
    namespace ops {
        CUSTOM_OP_IMPL(listdiff, 2, 2, false, 0, 0) {
            auto values = INPUT_VARIABLE(0);
            auto keep = INPUT_VARIABLE(1);
            auto output1 = OUTPUT_VARIABLE(0);
            auto output2 = OUTPUT_VARIABLE(1);

            REQUIRE_TRUE(values->rankOf() == 1, 0, "ListDiff: rank of values should be 1D, but got %iD instead", values->rankOf());
            REQUIRE_TRUE(keep->rankOf() == 1, 0, "ListDiff: rank of keep should be 1D, but got %iD instead", keep->rankOf());
            REQUIRE_TRUE(keep->dataType() == values->dataType(), 0, "ListDiff: both inputs must have same data type");

            return helpers::listDiffFunctor(block.launchContext(), values, keep, output1, output2);
        };

        DECLARE_SHAPE_FN(listdiff) {
            auto values = INPUT_VARIABLE(0);
            auto keep = INPUT_VARIABLE(1);

            REQUIRE_TRUE(values->rankOf() == 1, 0, "ListDiff: rank of values should be 1D, but got %iD instead", values->rankOf());
            REQUIRE_TRUE(keep->rankOf() == 1, 0, "ListDiff: rank of keep should be 1D, but got %iD instead", keep->rankOf());
            auto v = values->dataType();
            auto k = keep->dataType();
            REQUIRE_TRUE(k == v, 0, "ListDiff: both inputs must have same data type");

            auto saved = helpers::listDiffCount(block.launchContext(), values, keep);

            REQUIRE_TRUE(saved > 0, 0, "ListDiff: no matches found");

            auto shapeX = ConstantShapeHelper::getInstance().vectorShapeInfo(saved, values->dataType());
            auto shapeY = ConstantShapeHelper::getInstance().vectorShapeInfo(saved, DataType::INT64);
            return SHAPELIST(shapeX, shapeY);
        }

        DECLARE_TYPES(listdiff) {
            getOpDescriptor()
                    ->setAllowedInputTypes({ALL_INTS, ALL_FLOATS})
                    ->setAllowedOutputTypes(0, DataType::INHERIT)
                    ->setAllowedOutputTypes(1, {ALL_INTS});
        }
    }
}

#endif