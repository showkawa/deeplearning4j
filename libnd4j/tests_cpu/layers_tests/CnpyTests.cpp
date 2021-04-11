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
// Created by agibsonccc on 3/30/17.
//

#include "testinclude.h"
#include <string>
#include <legacy/NativeOps.h>

class FileTest : public testing::Test {

};

class LoadFromStringTest :  public testing::Test {

};

class HeaderTest :  public testing::Test {

};

TEST_F(HeaderTest, test_dataTypes_1) {
    std::string header("0NUMPY6789{'descr': '>f4");


    ASSERT_EQ(sd::DataType::FLOAT32, dataTypeFromNpyHeader(const_cast<char *>(header.data())));
}

TEST_F(HeaderTest, test_dataTypes_2) {
    std::string header("0NUMPY6789{'descr': '>f8");


    ASSERT_EQ(sd::DataType::DOUBLE, dataTypeFromNpyHeader(const_cast<char *>(header.data())));
}

TEST_F(HeaderTest, test_dataTypes_3) {
    std::string header("0NUMPY6789{'descr': '<i4");


    ASSERT_EQ(sd::DataType::INT32, dataTypeFromNpyHeader(const_cast<char *>(header.data())));
}

TEST_F(HeaderTest, test_dataTypes_4) {
    std::string header("0NUMPY6789{'descr': '>u2");


    ASSERT_EQ(sd::DataType::UINT16, dataTypeFromNpyHeader(const_cast<char *>(header.data())));
}

/*
TEST_F(FileTest,T) {
    cnpy::NpyArray npy = cnpy::npyLoad(std::string("/home/agibsonccc/code/libnd4j/test.npy"));
    ASSERT_FALSE(npy.fortranOrder);

    ASSERT_EQ(2,npy.shape[0]);
    ASSERT_EQ(2,npy.shape[1]);
}

TEST_F(LoadFromStringTest,PathTest) {
    char *loaded = cnpy::loadFile("/home/agibsonccc/code/libnd4j/test.npy");
    cnpy::NpyArray loadedArr = cnpy::loadNpyFromPointer(loaded);
    ASSERT_FALSE(loadedArr.fortranOrder);
    ASSERT_EQ(2,loadedArr.shape[0]);
    ASSERT_EQ(2,loadedArr.shape[1]);
    double *data = reinterpret_cast<double *>(loadedArr.data);
    ASSERT_EQ(1.0,data[0]);
    ASSERT_EQ(2.0,data[1]);
    ASSERT_EQ(3.0,data[2]);
    ASSERT_EQ(4.0,data[3]);
    Nd4jPointer  pointer = reinterpret_cast<Nd4jPointer >(&loadedArr);
    int *shapeBuffer = shape::shapeBufferOfNpy(loadedArr);
    Nd4jPointer  pointer1 = dataPointForNumpy(loaded);
    delete[] shapeBuffer;

    double *data2 = reinterpret_cast<double *>(pointer1);
    delete[] loaded;
}

*/
