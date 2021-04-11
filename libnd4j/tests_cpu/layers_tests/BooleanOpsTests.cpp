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
// Created by raver119 on 13.10.2017.
//

#include "testlayers.h"
#include <ops/declarable/CustomOperations.h>

using namespace sd;
using namespace sd::ops;
using namespace sd::graph;

class BooleanOpsTests : public testing::Test {
public:

};


TEST_F(BooleanOpsTests, LtTest_1) {
    auto x = NDArrayFactory::create_(1.0f);
    auto y = NDArrayFactory::create_(2.0f);

    sd::ops::lt_scalar op;


    ASSERT_TRUE(op.verify({x, y}));

    delete x;
    delete y;
}

TEST_F(BooleanOpsTests, LtTest_2) {
    auto x = NDArrayFactory::create_(2.0f);
    auto y = NDArrayFactory::create_(1.0f);

    sd::ops::lt_scalar op;


    ASSERT_FALSE(op.verify({x, y}));

    delete x;
    delete y;
}

TEST_F(BooleanOpsTests, Is_non_decreasing_1) {
    auto x = NDArrayFactory::create<double>('c', {2 , 2}, {1, 2, 4, 4});

    sd::ops::is_non_decreasing op;

    ASSERT_TRUE(op.verify({&x}));

}

TEST_F(BooleanOpsTests, Is_non_decreasing_2) {
    auto x = NDArrayFactory::create<double>('c', {2 , 2}, {1, 2, 4, 3});

    sd::ops::is_non_decreasing op;

    ASSERT_FALSE(op.verify({&x}));

}

TEST_F(BooleanOpsTests, Is_strictly_increasing_1) {
    auto x = NDArrayFactory::create<double>('c', {2 , 2}, {1, 2, 4, 5});

    sd::ops::is_strictly_increasing op;

    ASSERT_TRUE(op.verify({&x}));

}

TEST_F(BooleanOpsTests, Is_strictly_increasing_2) {
    auto x = NDArrayFactory::create<double>('c', {2 , 2}, {1, 2, 3, 3});

    sd::ops::is_strictly_increasing op;

    ASSERT_FALSE(op.verify({&x}));

}

TEST_F(BooleanOpsTests, Is_strictly_increasing_3) {
    auto x = NDArrayFactory::create<double>('c', {2 , 2}, {1, 2, 4, 3});

    sd::ops::is_strictly_increasing op;

    ASSERT_FALSE(op.verify({&x}));
}

TEST_F(BooleanOpsTests, Is_strictly_increasing_5) {
    auto x = NDArrayFactory::create<double>('c', {64, 512});
    x.linspace(1.0);

    sd::ops::is_strictly_increasing op;

    ASSERT_TRUE(op.verify({&x}));
}

TEST_F(BooleanOpsTests, Is_strictly_increasing_6) {
    auto x = NDArrayFactory::create<double>('c', {64, 512});
    x.linspace(1.0);

    x.p(18, 1000323.f);

    sd::ops::is_strictly_increasing op;

    ASSERT_FALSE(op.verify({&x}));
}

TEST_F(BooleanOpsTests, Is_numeric_tensor_1) {
    auto x = NDArrayFactory::create<float>('c', {2 , 2}, {1.f, 2.f, 4.f, 3.f});

    sd::ops::is_numeric_tensor op;

    ASSERT_TRUE(op.verify({&x}));
}

TEST_F(BooleanOpsTests, test_where_1) {
    auto x = NDArrayFactory::create<double>('c', {6}, { 1, -3, 4, 8, -2, 5 });
    auto y = NDArrayFactory::create<double>('c', {6}, { 2, -3, 1, 1, -2, 1 });
    auto e = NDArrayFactory::create<double>('c', {3}, { 4, 8, 5 });

    sd::ops::choose op;

    auto result = op.evaluate({&x, &y}, {3});
    ASSERT_EQ(Status::OK(), result.status());

    auto z = result.at(0);

    //z->printIndexedBuffer("z");

    ASSERT_EQ(e, *z);
}

