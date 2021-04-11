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

#include "../PlatformHelper.h"
#include <graph/Variable.h>

namespace sd {
    namespace ops {
        namespace platforms {
            PlatformHelper::PlatformHelper(const char *name, samediff::Engine engine) {
                // we just store name/hash of target operation
                _name = std::string(name);
                _hash = HashHelper::getInstance().getLongHash(_name);
                _engine = engine;
            }

            sd::NDArray* PlatformHelper::getNullifiedZ(graph::Context& block, int inputId) {
                auto result = getZ(block, inputId);
                if (result != nullptr && !block.isInplace())
                    result->nullify();

                return result;
            }

            sd::NDArray* PlatformHelper::getZ(graph::Context &ctx, int inputId) {
                NDArray *z = nullptr;

                if (ctx.isFastPath()) {
                    if (ctx.fastpath_out().size() <= inputId) {
                        if (ctx.isInplace()) {
                            z = ctx.fastpath_in()[inputId];
                        } else
                            throw std::runtime_error("fastpath_out: unresolved output array");
                    } else {
                        z = ctx.fastpath_out()[inputId];
                    }
                } else {
                    std::pair<int, int> pair(ctx.nodeId(), inputId);

                    if (ctx.isInplace()) {
                        z = ctx.variable(inputId)->getNDArray();

                        // hypothetically it's possible to have no variable. chances are low, but who knows. let's just create it for now
                        if (!ctx.getVariableSpace()->hasVariable(pair)) {
                            auto var = new graph::Variable();
                            ctx.getVariableSpace()->putVariable(pair, var);
                        }

                        // now we're saving input array as output array
                        auto var = ctx.getVariableSpace()->getVariable(pair);
                        var->markRemovable(false);
                        var->setNDArray(z);
                    } else if (!ctx.isInplace()) {
                        auto var = ctx.variable(pair);
                        if (var->getNDArray() != nullptr && var->getNDArray()->nonNull()) {
                            z = var->getNDArray();
                        } else {
                            nd4j_printf("Can't get Z variable for node_%i!\n", ctx.nodeId());
                        }
                    } else {
                        nd4j_printf("BOOM!\n", "");
                        throw std::runtime_error("Boom!");
                    }
                }

                return z;
            }

            samediff::Engine PlatformHelper::engine() {
                return _engine;
            }

            std::string PlatformHelper::name() {
                return _name;
            }

            Nd4jLong PlatformHelper::hash() {
                return _hash;
            }
        }
    }
}