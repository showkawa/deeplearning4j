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
// Created by raver119 on 16.10.2017.
//

#ifndef LIBND4J_LEGACYRANDOMOP_H
#define LIBND4J_LEGACYRANDOMOP_H


#include <helpers/helper_random.h>
#include <ops/declarable/LegacyOp.h>

namespace sd {
    namespace ops {
        /**
        *   This class provides wrapper for Random operations (i.e. linspace or Uniform)
        */
        class ND4J_EXPORT LegacyRandomOp : public LegacyOp {
        protected:
            Nd4jStatus validateAndExecute(Context& block) override;
        public:
            LegacyRandomOp();
            LegacyRandomOp(int opNum);
            ~LegacyRandomOp() = default;

            template <typename T>
            Nd4jStatus validateAndExecute_(Context &block);

            sd::ResultSet  execute(sd::graph::RandomGenerator& rng, std::initializer_list<NDArray*> inputs, std::initializer_list<double> tArgs, std::initializer_list<int> iArgs, bool isInplace = false);
            sd::ResultSet  execute(sd::graph::RandomGenerator& rng, std::vector<NDArray*>& inputs, std::vector<double>& tArgs, std::vector<int>& iArgs, bool isInplace = false);

            Nd4jStatus execute(Context* block) override;

            Nd4jStatus validateDataTypes(Context& block) override;
            ShapeList* calculateOutputShape(ShapeList* inputShape, sd::graph::Context& block) override;
            LegacyOp* clone() override;
        };
    }
}


#endif //LIBND4J_LEGACYTRANSFORMOP_H
