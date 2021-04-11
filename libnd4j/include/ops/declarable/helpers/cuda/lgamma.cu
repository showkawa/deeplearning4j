/*
 *  ******************************************************************************
 *  *
 *  *
 *  * This program and the accompanying materials are made available under the
 *  * terms of the Apache License, Version 2.0 which is available at
 *  * https://www.apache.org/licenses/LICENSE-2.0.
 *  *
 *  * See the NOTICE file distributed with this work for additional
 *  * information regarding copyright ownership.
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations
 *  * under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *  *****************************************************************************
 */

//
// @author George A. Shulinok <sgazeos@gmail.com>
//

#include<ops/declarable/helpers/lgamma.h>
//#include <execution/Threads.h>
//#include <helper_math.h>

namespace sd {
namespace ops {
namespace helpers {

//////////////////////////////////////////////////////////////////////////
// calculate digamma function for array elements
template <typename T>
void lgamma_(NDArray& x, NDArray& z) {
    //auto dtype = x.dataType();
    auto lgammaProc = LAMBDA_T(x_, dtype) {
        return T(DataTypeUtils::fromT<T>() == DataType::DOUBLE?::lgamma(x_): ::lgammaf(x_)); //math::nd4j_log<T,T>(math::nd4j_gamma<T,T>(x));
    };

    x.applyLambda(lgammaProc, z);
}

void lgamma(sd::LaunchContext* context, NDArray& x, NDArray& z) {

	BUILD_SINGLE_SELECTOR(x.dataType(), lgamma_, (x, z), FLOAT_TYPES);
}

BUILD_SINGLE_TEMPLATE(template void lgamma_, (NDArray& x, NDArray& z), FLOAT_TYPES);



}
}
}

