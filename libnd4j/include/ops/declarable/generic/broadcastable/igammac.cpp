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
// @author sgazeos@gmail.com
//

#include <system/op_boilerplate.h>
#if NOT_EXCLUDED(OP_igammac)

#include <ops/declarable/generic/helpers/BroadcastHelper.h>
#include <ops/declarable/CustomOperations.h>

namespace sd {
    namespace ops {
        BROADCASTABLE_OP_IMPL(igammac, 0, 0) {
            auto x = INPUT_VARIABLE(0);
            auto y = INPUT_VARIABLE(1);
            auto z = OUTPUT_VARIABLE(0);

            BROADCAST_CHECK_EMPTY(x,y,z);

            //REQUIRE_TRUE(!y->isB(), 0, "Pairwise OP: you can't divide by bool array!");

//            auto tZ = BroadcastHelper::broadcastApply({scalar::IGammac, pairwise::IGammac, broadcast::IGammac}, x, y, z);
            auto tZ = BroadcastHelper::broadcastApply(BroadcastOpsTuple::IGammac(), x, y, z);
            if (tZ == nullptr)
                return ND4J_STATUS_KERNEL_FAILURE;
            else if (tZ != z) {
                OVERWRITE_RESULT(tZ);
            }

            return Status::OK();
        }

        DECLARE_TYPES(igammac) {
            getOpDescriptor()
                ->setAllowedInputTypes(0, {ALL_FLOATS})
                ->setAllowedInputTypes(1, {ALL_FLOATS})
                ->setAllowedOutputTypes(0, {ALL_FLOATS});
        }
    }
}

#endif