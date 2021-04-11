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
// Created by Yurii Shyrma on 24.01.2019
//

#ifndef LIBND4J_SVD_HELPER_H
#define LIBND4J_SVD_HELPER_H

#include <ops/declarable/helpers/helpers.h>
#include "array/NDArray.h"

namespace sd    {
namespace ops     {
namespace helpers {

//////////////////////////////////////////////////////////////////////////
// svd operation, this function is not method of SVD class, it is standalone function
void svd(sd::LaunchContext* context, const NDArray* x, const std::vector<NDArray*>& outArrs, const bool fullUV, const bool calcUV, const int switchNum);


}
}
}

#endif //LIBND4J_SVD_HELPER_H
