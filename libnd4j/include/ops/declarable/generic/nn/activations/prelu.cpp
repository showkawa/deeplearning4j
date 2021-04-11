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
// @author Yurii Shyrma (iuriish@yahoo.com), created on 24.07.2018
//


#include <system/op_boilerplate.h>
#if NOT_EXCLUDED(OP_prelu)

#include <ops/declarable/CustomOperations.h>
#include <ops/declarable/helpers/activations.h>
#include <numeric>

namespace sd {
namespace ops  {


////////////////////////////////////////////////////////////////////////
CONFIGURABLE_OP_IMPL(prelu, 2, 1, true, 0, 0) {
    auto input  = INPUT_VARIABLE(0);
    auto alpha  = INPUT_VARIABLE(1);
    auto output = OUTPUT_VARIABLE(0);

    std::vector<int> sharedAxes = *block.getIArguments();

    const int inputRank     = input->rankOf();
    const int numSharedAxes = sharedAxes.size();            // can be zero as well
    const Nd4jLong inputLen = input->lengthOf();
    const Nd4jLong alphaLen = alpha->lengthOf();
    const std::vector<Nd4jLong> inputShape = input->getShapeAsVector();
    const std::vector<Nd4jLong> alphaShape = alpha->getShapeAsVector();

    //***** input validation *****//
    std::vector<Nd4jLong> expectedAlphaShape(&inputShape[1], &inputShape[inputRank]);

    REQUIRE_TRUE(inputRank > 1, 0, "PRELU OP: wrong rank of input array, expected rank should be > 1, but got %i instead !", inputRank);

    for(int i = 0; i < numSharedAxes; ++i) {
        if(sharedAxes[i] <= 0)
            sharedAxes[i] += inputRank - 1;
        REQUIRE_TRUE(1 <= sharedAxes[i] && sharedAxes[i] <= inputRank - 1, 0, "PRELU OP: wrong axis value %i in sharedAxes at position %i, axis value must be within range [1, input_rank-1] !", sharedAxes[i], i);
        expectedAlphaShape[sharedAxes[i] - 1] = 1;
    }

    Nd4jLong product = 1;
    for(const auto& item : expectedAlphaShape)
        product *= item;

    REQUIRE_TRUE(product == alphaLen, 0, "PRELU OP: wrong shape of alpha array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(expectedAlphaShape).c_str(), ShapeUtils::shapeAsString(alphaShape).c_str());
    // ***** end of validation ***** //

    helpers::prelu(block.launchContext(), *input,  alphaShape != expectedAlphaShape ? alpha->reshape(alpha->ordering(), expectedAlphaShape) : *alpha, *output);

    return Status::OK();
}


        DECLARE_TYPES(prelu) {
            getOpDescriptor()
                    ->setAllowedInputTypes(0, DataType::ANY)
                    ->setAllowedInputTypes(1, {ALL_FLOATS})
                    ->setAllowedOutputTypes(0, {ALL_FLOATS});
        }


////////////////////////////////////////////////////////////////////////
CONFIGURABLE_OP_IMPL(prelu_bp, 3, 2, true, 0, 0) {
    auto input = INPUT_VARIABLE(0);
    auto alpha = INPUT_VARIABLE(1);
    auto dLdO  = INPUT_VARIABLE(2);

    auto dLdI = OUTPUT_VARIABLE(0);
    auto dLdA = OUTPUT_VARIABLE(1);

    std::vector<int> sharedAxes = *block.getIArguments();

    const int inputRank     = input->rankOf();
    const int numSharedAxes = sharedAxes.size();            // can be zero as well
    const Nd4jLong inputLen = input->lengthOf();
    const Nd4jLong alphaLen = alpha->lengthOf();
    const std::vector<Nd4jLong> inputShape = input->getShapeAsVector();
    const std::vector<Nd4jLong> alphaShape = alpha->getShapeAsVector();

    //***** input validation *****//

    // temporary limitation imposed by Yurii
    REQUIRE_TRUE(inputRank <= MAX_RANK/2, 0, "rank of input array should be <= MAX_RANK/2, but got %i instead!", inputRank);
    REQUIRE_TRUE(input->lengthOf() / alpha->lengthOf() <= MAX_RANK*2, 0, "the length of input array should be no more than MAX_RANK*2 times the alpha array length, but got %lld and %lld correspondingly!", input->lengthOf(), alpha->lengthOf());

    std::vector<Nd4jLong> expectedAlphaShape(&inputShape[1], &inputShape[inputRank]);

    REQUIRE_TRUE(inputRank > 1, 0, "PRELU_BP OP: wrong rank of input array, expected rank should be > 1, but got %i instead !", inputRank);

    for(int i = 0; i < numSharedAxes; ++i) {
        if(sharedAxes[i] <= 0)
            sharedAxes[i] += inputRank - 1;
        REQUIRE_TRUE(1 <= sharedAxes[i] && sharedAxes[i] <= inputRank - 1, 0, "PRELU_BP OP: wrong axis value %i in sharedAxes at position %i, axis value must be within range [1, input_rank-1] !", sharedAxes[i], i);
        expectedAlphaShape[sharedAxes[i] - 1] = 1;
    }

    Nd4jLong product = 1;
    for(const auto& item : expectedAlphaShape)
        product *= item;

    REQUIRE_TRUE(product == alphaLen, 0, "PRELU_BP OP: wrong shape of alpha array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(expectedAlphaShape).c_str(), ShapeUtils::shapeAsString(alphaShape).c_str());
    // ***** end of validation ***** //


    if(alphaShape != expectedAlphaShape) {
        alpha = new NDArray(alpha->reshape(alpha->ordering(), expectedAlphaShape));
        dLdA  = new NDArray(dLdA->reshape(dLdA->ordering(), expectedAlphaShape));
    }

    helpers::preluBP(block.launchContext(), *input, *alpha, *dLdO, *dLdI, *dLdA);

    if(alphaShape != expectedAlphaShape) {
        delete alpha;
        delete dLdA;
    }

    return Status::OK();
}

        DECLARE_TYPES(prelu_bp) {
            getOpDescriptor()
                    ->setAllowedInputTypes(0, DataType::ANY)
                    ->setAllowedInputTypes(1, {DataType::FLOAT32, DataType ::DOUBLE, DataType::HALF})
                    ->setAllowedInputTypes(2, {DataType::FLOAT32, DataType ::DOUBLE, DataType::HALF})
                    ->setAllowedOutputTypes(0, {DataType::FLOAT32, DataType ::DOUBLE, DataType::HALF})
                    ->setAllowedOutputTypes(1, {DataType::FLOAT32, DataType ::DOUBLE, DataType::HALF});
        }


}
}

#endif