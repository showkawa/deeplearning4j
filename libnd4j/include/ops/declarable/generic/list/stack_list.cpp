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
//
// @author raver119@gmail.com
//

#include <system/op_boilerplate.h>
#if NOT_EXCLUDED(OP_stack_list)

#include <ops/declarable/CustomOperations.h>

namespace sd {
    namespace ops {
        LIST_OP_IMPL(stack_list, 1, 1, 0, 0) {
            auto list = INPUT_LIST(0);
            //auto z = OUTPUT_VARIABLE(0);

            // FIXME: this is obviously bad
            auto result = list->stack();

            //OVERWRITE_RESULT(result);
            setupResult(result, block);
            return Status::OK();
        }
        DECLARE_SYN(TensorArrayConcatV3, stack_list);
        DECLARE_SYN(tensorarrayconcatv3, stack_list);
    }
}

#endif