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

#include <exceptions/cuda_exception.h>
#include <helpers/StringUtils.h>

namespace sd {
    cuda_exception::cuda_exception(std::string message) : std::runtime_error(message){
        //
    }

    cuda_exception cuda_exception::build(std::string message, int errorCode) {
        auto ec = StringUtils::valueToString<int>(errorCode);
        message += "; Error code: [" + ec + "]";
        return cuda_exception(message);
    }
}