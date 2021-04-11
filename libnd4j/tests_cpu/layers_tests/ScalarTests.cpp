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

#include "testlayers.h"
#include <ops/declarable/CustomOperations.h>
#include <array/NDArray.h>
#include <legacy/NativeOps.h>
#include <helpers/BitwiseUtils.h>

using namespace sd;
using namespace sd::graph;

class ScalarTests : public testing::Test {
public:

};

TEST_F(ScalarTests, Test_Create_1) {
    auto x = NDArrayFactory::create<float>(2.0f);

    ASSERT_EQ(0, x.rankOf());
    ASSERT_EQ(1, x.lengthOf());
    ASSERT_TRUE(x.isScalar());
    ASSERT_FALSE(x.isVector());
    ASSERT_FALSE(x.isRowVector());
    ASSERT_FALSE(x.isColumnVector());
    ASSERT_FALSE(x.isMatrix());
}

TEST_F(ScalarTests, Test_Add_1) {
    auto x = NDArrayFactory::create<float>(2.0f);
    auto exp = NDArrayFactory::create<float>(5.0f);

    x += 3.0f;

    ASSERT_NEAR(5.0f, x.e<float>(0), 1e-5f);
    ASSERT_TRUE(exp.isSameShape(&x));
    ASSERT_TRUE(exp.equalsTo(&x));
}

TEST_F(ScalarTests, Test_Add_2) {
    auto x = NDArrayFactory::create<float>(2.0f);
    auto y = NDArrayFactory::create<float>(3.0f);
    auto exp = NDArrayFactory::create<float>(5.0f);

    x += y;

    ASSERT_NEAR(5.0f, x.e<float>(0), 1e-5f);
    ASSERT_TRUE(exp.isSameShape(&x));
    ASSERT_TRUE(exp.equalsTo(&x));
}

TEST_F(ScalarTests, Test_Add_3) {
    auto x = NDArrayFactory::create<float>('c', {3}, {1, 2, 3});
    auto y = NDArrayFactory::create<float>(3.0f);
    auto exp = NDArrayFactory::create<float>('c', {3}, {4, 5, 6});

    x += y;

    ASSERT_TRUE(exp.isSameShape(&x));

    ASSERT_TRUE(exp.equalsTo(&x));
}

TEST_F(ScalarTests, Test_EQ_1) {
    auto x = NDArrayFactory::create<float>(2.0f);
    auto y = NDArrayFactory::create<float>(3.0f);

    ASSERT_TRUE(y.isSameShape(&x));
    ASSERT_FALSE(y.equalsTo(&x));
}

TEST_F(ScalarTests, Test_Concat_1) {
    auto t = NDArrayFactory::create<float>(1.0f);
    auto u = NDArrayFactory::create<float>(2.0f);
    auto v = NDArrayFactory::create<float>(3.0f);
    auto exp = NDArrayFactory::create<float>('c', {3}, {1, 2, 3});

    sd::ops::concat op;
    auto result = op.evaluate({&t, &u, &v}, {}, {0});

    ASSERT_EQ(ND4J_STATUS_OK, result.status());

    auto z = result.at(0);

    ASSERT_TRUE(exp.isSameShape(z));
    ASSERT_TRUE(exp.equalsTo(z));


}


TEST_F(ScalarTests, Test_Concat_2) {
    auto t = NDArrayFactory::create<float>(1.0f);
    auto u = NDArrayFactory::create<float>('c', {3}, {2, 3, 4});
    auto v = NDArrayFactory::create<float>(5.0f);
    auto exp = NDArrayFactory::create<float>('c', {5}, {1, 2, 3, 4, 5});

    sd::ops::concat op;
    auto result = op.evaluate({&t, &u, &v}, {}, {0});

    ASSERT_EQ(ND4J_STATUS_OK, result.status());

    auto z = result.at(0);
    // z->printIndexedBuffer();

    ASSERT_TRUE(exp.isSameShape(z));
    ASSERT_TRUE(exp.equalsTo(z));


}


TEST_F(ScalarTests, Test_Concat_3) {
    auto t = NDArrayFactory::create<float>('c', {3}, {1, 2, 3});
    auto u = NDArrayFactory::create<float>(4.0f);
    auto v = NDArrayFactory::create<float>(5.0f);
    auto exp = NDArrayFactory::create<float>('c', {5}, {1, 2, 3, 4, 5});

    sd::ops::concat op;
    auto result = op.evaluate({&t, &u, &v}, {}, {0});

    ASSERT_EQ(ND4J_STATUS_OK, result.status());

    auto z = result.at(0);

    //z->printShapeInfo("z");

    ASSERT_TRUE(exp.isSameShape(z));
    ASSERT_TRUE(exp.equalsTo(z));


}

TEST_F(ScalarTests, Test_ExpandDims_1) {
    auto x = NDArrayFactory::create<float>(2.0f);
    auto exp = NDArrayFactory::create<float>('c', {1}, {2.0f});

    sd::ops::expand_dims op;
    auto result = op.evaluate({&x}, {}, {0});

    ASSERT_EQ(ND4J_STATUS_OK, result.status());

    auto z = result.at(0);

    ASSERT_TRUE(exp.isSameShape(z));
    ASSERT_TRUE(exp.equalsTo(z));


}

TEST_F(ScalarTests, Test_Squeeze_1) {
    auto x = NDArrayFactory::create<float>(2.0f);
    auto exp = NDArrayFactory::create<float>(2.0f);

    sd::ops::squeeze op;
    auto result = op.evaluate({&x}, {}, {});
    ASSERT_EQ(ND4J_STATUS_OK, result.status());

    auto z = result.at(0);

    ASSERT_TRUE(exp.isSameShape(z));
    ASSERT_TRUE(exp.equalsTo(z));


}

TEST_F(ScalarTests, Test_Permute_1) {
    auto x = NDArrayFactory::create<float>(3.0f);
    auto exp = NDArrayFactory::create<float>(3.0f);

    sd::ops::permute op;
    auto result = op.evaluate({&x}, {}, {0});
    ASSERT_EQ(ND4J_STATUS_OK, result.status());

    auto z = result.at(0);

    ASSERT_TRUE(exp.isSameShape(z));
    ASSERT_TRUE(exp.equalsTo(z));


}

TEST_F(ScalarTests, Test_Concat_Scalar_1) {
    auto t = NDArrayFactory::create<float>('c', {1, 1}, {1.0f});
    auto u = NDArrayFactory::create<float>('c', {1, 1}, {2.0f});
    auto v = NDArrayFactory::create<float>('c', {1, 1}, {3.0f});
    auto w = NDArrayFactory::create<float>('c', {1, 1}, {4.0f});
    auto exp = NDArrayFactory::create<float>('c', {4, 1}, {1, 2, 3, 4});

    sd::ops::concat op;
    auto result = op.evaluate({&t, &u, &v, &w}, {}, {0});
    ASSERT_EQ(ND4J_STATUS_OK, result.status());

    auto z = result.at(0);

    ASSERT_TRUE(exp.isSameShape(z));
    ASSERT_TRUE(exp.equalsTo(z));

}


TEST_F(ScalarTests, Test_Concat_Scalar_2) {
    auto t = NDArrayFactory::create<float>('c', {1, 1}, {1.0f});
    auto u = NDArrayFactory::create<float>('c', {1, 1}, {2.0f});
    auto v = NDArrayFactory::create<float>('c', {1, 1}, {3.0f});
    auto w = NDArrayFactory::create<float>('c', {1, 1}, {4.0f});
    auto exp = NDArrayFactory::create<float>('c', {1, 4}, {1, 2, 3, 4});

    sd::ops::concat op;
    auto result = op.evaluate({&t, &u, &v, &w}, {}, {1});
    ASSERT_EQ(ND4J_STATUS_OK, result.status());

    auto z = result.at(0);

    ASSERT_TRUE(exp.isSameShape(z));
    ASSERT_TRUE(exp.equalsTo(z));

}