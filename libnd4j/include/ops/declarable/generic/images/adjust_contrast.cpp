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
//  @author George A. Shulinok <sgazeos@gmail.com>
//

#include <system/op_boilerplate.h>
#if NOT_EXCLUDED(OP_adjust_contrast)

#include <ops/declarable/headers/parity_ops.h>
#include <array/NDArrayFactory.h>

namespace sd {
namespace ops {

////////////////////////////////////////////////////////////////////
CONFIGURABLE_OP_IMPL(adjust_contrast, 1, 1, true, 0, 0) {

    auto input  = INPUT_VARIABLE(0);
    auto output = OUTPUT_VARIABLE(0);

    // just skip op if input is empty
    if (input->isEmpty())
        return Status::OK();

    REQUIRE_TRUE(block.numT() > 0 || block.width() > 1, 0, "ADJUST_CONTRAST: Scale factor required");
    REQUIRE_TRUE(input->rankOf() > 2, 0, "ADJUST_CONTRAST: op expects rank of input array to be >= 3, but got %i instead", input->rankOf());
//    REQUIRE_TRUE(input->sizeAt(-1) == 3, 0, "ADJUST_CONTRAST: operation expects image with 3 channels (R, G, B), but got %i instead", input->sizeAt(-1));

    NDArray* factor = nullptr;

    if(block.width() > 1)
        factor = INPUT_VARIABLE(1);
    else {
        factor = new NDArray(output->dataType(), block.launchContext());
        factor->p(0, T_ARG(0));
    }

    // fill up axes vector first
    std::vector<int> axes(input->rankOf() - 1);
    for (auto i = 0; i < axes.size(); ++i)
        axes[i] = i;

    // mean as reduction for last dimension set
    auto mean = input->reduceAlongDimension(reduce::Mean, axes);

    // this is contrast calculation
    output->assign((*input - mean) * (*factor) + mean);

    if(block.width() == 1)
        delete factor;

    return Status::OK();
}

DECLARE_TYPES(adjust_contrast) {
    getOpDescriptor()->setAllowedInputTypes(sd::DataType::ANY)
                     ->setAllowedOutputTypes({ALL_FLOATS})
                     ->setSameMode(true);
}

////////////////////////////////////////////////////////////////////
CONFIGURABLE_OP_IMPL(adjust_contrast_v2, 1, 1, true, 0, 0) {

    auto input  = INPUT_VARIABLE(0);
    auto output = OUTPUT_VARIABLE(0);

    // just skip op if input is empty
    if (input->isEmpty())
        return Status::OK();

    REQUIRE_TRUE(input->rankOf() > 2, 0, "ADJUST_CONTRAST_V2: op expects rank of input array to be >= 3, but got %i instead", input->rankOf());
//    REQUIRE_TRUE(input->sizeAt(-1) == 3, 0, "ADJUST_CONTRAST_V2: operation expects image with 3 channels (R, G, B), but got %i instead", input->sizeAt(-1));
    REQUIRE_TRUE(block.numT() > 0 || block.width() > 1, 0, "ADJUST_CONTRAST_V2: Scale factor required");

    NDArray* factor = nullptr;
    auto size = input->sizeAt(-2) * input->sizeAt(-3);
    auto channels = input->sizeAt(-1);
    auto batch = input->lengthOf() / (size * channels);
    auto input3D = input->reshape(input->ordering(), {batch, size, channels});
    auto output3D = input->reshape(input->ordering(), {batch, size, channels});

    if(block.width() > 1)
        factor = INPUT_VARIABLE(1);
    else {
        factor = new NDArray(output->dataType(), block.launchContext());
        factor->p(0, T_ARG(0));
    }

    std::vector<int> axes({1}); // dim 1 of pseudoresult

// mean as reduction for last dimension set over size (dim 1) of result3D
    auto mean = input3D.reduceAlongDimension(reduce::Mean, axes);

    // result as (x - mean) * factor + mean
    auto temp = input3D.ulike();
    input3D.applyBroadcast(broadcast::Subtract, {0, 2}, mean, temp);
    temp.applyScalarArr(scalar::Multiply, *factor, temp);
    temp.applyBroadcast(broadcast::Add, {0, 2},  mean, output3D);
    output->assign(output3D);
    if(block.width() == 1)
        delete factor;

    return Status::OK();
}

DECLARE_TYPES(adjust_contrast_v2) {
    getOpDescriptor()->setAllowedInputTypes(sd::DataType::ANY)
            ->setAllowedOutputTypes({ALL_FLOATS})
            ->setSameMode(true);
}

}
}

#endif