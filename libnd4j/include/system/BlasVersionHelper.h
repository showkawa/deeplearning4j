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

#ifndef SAMEDIFF_BLASVERSIONHELPER_H
#define SAMEDIFF_BLASVERSIONHELPER_H

#include <system/dll.h>
#include <cuda.h>
#include <cuda_runtime.h>

namespace sd {
    class ND4J_EXPORT BlasVersionHelper {
    public:
        int _blasMajorVersion = 0;
        int _blasMinorVersion = 0;
        int _blasPatchVersion = 0;

        BlasVersionHelper();
        ~BlasVersionHelper() = default;
    };
}

#endif //DEV_TESTS_BLASVERSIONHELPER_H
