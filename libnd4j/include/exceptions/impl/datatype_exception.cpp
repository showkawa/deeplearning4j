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
// Created by raver on 11/26/2018.
//

#include <array/DataTypeUtils.h>
#include <exceptions/datatype_exception.h>

namespace sd {
    datatype_exception::datatype_exception(std::string message) : std::runtime_error(message){
        //
    }

    datatype_exception datatype_exception::build(std::string message, sd::DataType expected, sd::DataType actual) {
        auto exp = DataTypeUtils::asString(expected);
        auto act = DataTypeUtils::asString(actual);
        message += "; Expected: [" + exp + "]; Actual: [" + act + "]";
        return datatype_exception(message);
    }

    datatype_exception datatype_exception::build(std::string message, sd::DataType expected, sd::DataType actualX, sd::DataType actualY) {
        auto exp = DataTypeUtils::asString(expected);
        auto actX = DataTypeUtils::asString(actualX);
        auto actY = DataTypeUtils::asString(actualY);
        message += "; Expected: [" + exp + "]; Actual: [" + actX + ", " + actY + "]";
        return datatype_exception(message);
    }

    datatype_exception datatype_exception::build(std::string message, sd::DataType actual) {
        auto act = DataTypeUtils::asString(actual);
        message += "; Actual: [" + act + "]";
        return datatype_exception(message);
    }
}