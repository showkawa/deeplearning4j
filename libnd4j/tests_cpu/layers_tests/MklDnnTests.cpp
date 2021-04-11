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

#ifdef HAVE_MKLDNN

#include "testlayers.h"
#include <initializer_list>
#include <ops/declarable/PlatformHelper.h>
#include <ops/declarable/platform/mkldnn/mkldnnUtils.h>
#include <array/NDArrayFactory.h>
#include <ops/declarable/CustomOperations.h>


using namespace sd;

class MklDnnTests : public testing::Test {
public:

};

static void printer(std::initializer_list<sd::ops::platforms::PlatformHelper*> helpers) {

    for (auto v:helpers) {
        nd4j_printf("Initialized [%s]\n", v->name().c_str());
    }
}


TEST_F(MklDnnTests, helpers_includer) {
    // we need this block, to make sure all helpers are still available within binary, and not optimized out by linker
    sd::ops::platforms::PLATFORM_conv2d_ENGINE_CPU conv2d;
    sd::ops::platforms::PLATFORM_conv2d_bp_ENGINE_CPU conv2d_bp;

    sd::ops::platforms::PLATFORM_conv2d_ENGINE_CPU conv3d;
    sd::ops::platforms::PLATFORM_conv2d_bp_ENGINE_CPU conv3d_bp;

    sd::ops::platforms::PLATFORM_avgpool2d_ENGINE_CPU avgpool2d;
    sd::ops::platforms::PLATFORM_avgpool2d_bp_ENGINE_CPU avgpool2d_bp;

    sd::ops::platforms::PLATFORM_maxpool2d_ENGINE_CPU maxpool2d;
    sd::ops::platforms::PLATFORM_maxpool2d_bp_ENGINE_CPU maxpool2d_bp;

    sd::ops::platforms::PLATFORM_avgpool3dnew_ENGINE_CPU avgpool3d;
    sd::ops::platforms::PLATFORM_avgpool3dnew_bp_ENGINE_CPU avgpool3d_bp;

    sd::ops::platforms::PLATFORM_maxpool3dnew_ENGINE_CPU maxpool3d;
    sd::ops::platforms::PLATFORM_maxpool3dnew_bp_ENGINE_CPU maxpool3d_bp;

    sd::ops::platforms::PLATFORM_lrn_ENGINE_CPU lrn;

    sd::ops::platforms::PLATFORM_batchnorm_ENGINE_CPU batchnorm;

    sd::ops::platforms::PLATFORM_matmul_ENGINE_CPU matmul;

    sd::ops::platforms::PLATFORM_softmax_ENGINE_CPU softmax;

    sd::ops::platforms::PLATFORM_softmax_bp_ENGINE_CPU softmax_bp;

    sd::ops::platforms::PLATFORM_tanh_ENGINE_CPU tanh;

    sd::ops::platforms::PLATFORM_tanh_ENGINE_CPU tanh_bp;
   
    sd::ops::platforms::PLATFORM_xw_plus_b_ENGINE_CPU xw_plus_b;

    sd::ops::platforms::PLATFORM_xw_plus_b_bp_ENGINE_CPU xw_plus_b_bp;


    printer({&conv2d, &conv2d_bp, &conv3d, &conv3d_bp, &avgpool2d, &avgpool2d_bp, &maxpool2d, &maxpool2d_bp, &avgpool3d, &avgpool3d_bp, &maxpool3d, &maxpool3d_bp, &lrn, &batchnorm, &matmul, &softmax, &softmax_bp, &tanh, &tanh_bp, &xw_plus_b, &xw_plus_b_bp });
}

TEST_F(MklDnnTests, test_tanh_1) {
    auto x = NDArrayFactory::create<float>(1.0f);
    auto z = NDArrayFactory::create<float>(0.0f);

    sd::ops::tanh op;
    auto status = op.execute({&x}, {&z});

    ASSERT_EQ(Status::OK(), status);
}

TEST_F(MklDnnTests, test_tanh_2) {
    auto x = NDArrayFactory::create<float>('c', {1}, {1.0f});
    auto z = NDArrayFactory::create<float>('c', {1}, {0.0f});

    sd::ops::tanh op;
    auto status = op.execute({&x}, {&z});

    ASSERT_EQ(Status::OK(), status);
}

#endif