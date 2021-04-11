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
// @author Oleh Semeniv (oleg.semeniv@gmail.com)
// 

#include <ops/declarable/headers/images.h>
#include <ops/declarable/CustomOperations.h>  
#include <helpers/ConstantTadHelper.h>
#include <execution/Threads.h>

namespace sd {
namespace ops {

CONFIGURABLE_OP_IMPL(yuv_to_rgb, 1, 1, true, 0, 0) {

    auto input  = INPUT_VARIABLE(0);
    auto output = OUTPUT_VARIABLE(0);

    // just skip op if input is empty
    if (input->isEmpty())
        return Status::OK();

    const int rank = input->rankOf();
    const int argSize = block.getIArguments()->size();
    const int dimC = argSize > 0 ? (INT_ARG(0) >= 0 ? INT_ARG(0) : INT_ARG(0) + rank) : rank - 1;

    REQUIRE_TRUE(rank >= 1, 0, "YUVtoRGB: Fails to meet the rank requirement: %i >= 1 ", rank);
    if (argSize > 0) {
        REQUIRE_TRUE(dimC >= 0 && dimC < rank, 0, "Index of the Channel dimension out of range: %i not in [%i,%i) ", INT_ARG(0), -rank, rank);
    }
    REQUIRE_TRUE(input->sizeAt(dimC) == 3, 0, "YUVtoRGB: operation expects 3 channels (Y, U, V), but got %i instead", input->sizeAt(dimC));

    helpers::transformYuvRgb(block.launchContext(), *input, *output, dimC);

    return Status::OK();
}

DECLARE_TYPES(yuv_to_rgb) {
    getOpDescriptor()->setAllowedInputTypes({ ALL_FLOATS })
        ->setSameMode(true);
}


}
}
