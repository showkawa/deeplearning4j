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
// Created by raver119 on 17.10.2017.
//

#include <ops/declarable/LegacyStatsOp.h>
#include <helpers/ShapeUtils.h>
#include <helpers/TAD.h>
#include <helpers/ConstantTadHelper.h>
#include <array/DataTypeUtils.h>


namespace sd {
    namespace ops {
        Nd4jStatus LegacyStatsOp::validateAndExecute(Context &block) {
            auto x = INPUT_VARIABLE(0);
            auto z = OUTPUT_VARIABLE(0);

            NDArray::prepareSpecialUse({z}, {x});

            // we assume that opNuk is either stored in block, or was provided via op constructor
            int opNum = block.opNum() < 0 ? this->_opNum : block.opNum();

            // bias goes as first argument, unlike all other reductions
            bool biasCorrected = false;
            if (block.getIArguments()->size() > 0)
                biasCorrected = INT_ARG(0) > 0;

            ExtraArguments extras(*block.getTArguments());
            PointersManager manager(block.launchContext(),"LegacyStatsOp");

            if (block.getIArguments()->size() == 1 || (block.getIArguments()->size() == 2 && INT_ARG(1) == sd::DataTypeUtils::max<int>())) {
                // scalar
                NativeOpExecutioner::execSummaryStatsScalar(block.launchContext(), opNum, x->buffer(), x->shapeInfo(), x->specialBuffer(), x->specialShapeInfo(),
                        extras.argumentsAsT(z->dataType()), z->buffer(), z->shapeInfo(), z->specialBuffer(), z->specialShapeInfo(), biasCorrected);
            } else {
                // dimensions for TAD
                // we should skip first argument here, because it's addressing bias correction
                std::vector<int> dims(*block.getIArguments());
                for (int e = 0; e < dims.size(); e++)
                    if (dims[e] < 0)
                        dims[e] += x->rankOf();

                REQUIRE_TRUE(dims.size() > 0, 0, "Some dimensions requuired for reduction!");

                auto packX = sd::ConstantTadHelper::getInstance().tadForDimensions(x->shapeInfo(), dims);

                auto pTadShape = Environment::getInstance().isCPU() ? packX.primaryShapeInfo() : packX.specialShapeInfo(); //(Nd4jLong *) manager.replicatePointer(tad.tadOnlyShapeInfo, shape::shapeInfoByteLength(tad.tadOnlyShapeInfo));
                auto pTadOffsets = Environment::getInstance().isCPU() ? packX.primaryOffsets() : packX.specialOffsets(); //(Nd4jLong *) manager.replicatePointer(tad.tadOffsets, tad.numTads * sizeof(Nd4jLong));

                NativeOpExecutioner::execSummaryStats(block.launchContext(), opNum, x->buffer(), x->shapeInfo(), x->specialBuffer(), x->specialShapeInfo(), extras.argumentsAsT(z->dataType()),
                        z->buffer(), z->shapeInfo(), z->specialBuffer(), z->specialShapeInfo(), dims.data(), (int) dims.size(), pTadShape, pTadOffsets, biasCorrected);
            }

            manager.synchronize();
            STORE_RESULT(*z);

            return Status::OK();
        }

        LegacyStatsOp::LegacyStatsOp() : LegacyOp::LegacyOp(1) {
            //
        }

        LegacyStatsOp::LegacyStatsOp(int opNum) : LegacyOp::LegacyOp(1, opNum) {
            //
        }

        LegacyOp* LegacyStatsOp::clone() {
            return new LegacyStatsOp(this->_opNum);
        }

        /**
        *   For all reductions rules are simple: either you return scalar, or you return reduced NDArray.
        *   It solely depends on input shape, and requested dimensions
        */
        ShapeList *LegacyStatsOp::calculateOutputShape(ShapeList *inputShape, sd::graph::Context &block) {
            auto inShape = inputShape->at(0);

            Nd4jLong *newShape;
            if (block.getIArguments()->size() == 0 || (block.getIArguments()->size() == 1 && INT_ARG(0) == sd::DataTypeUtils::max<int>())) {
                // in this case we just return scalar
                ALLOCATE(newShape, block.getWorkspace(), shape::shapeInfoLength(2), Nd4jLong);
                newShape[0] = 2;
                newShape[1] = 1;
                newShape[2] = 1;
                newShape[3] = 1;
                newShape[4] = 1;
                newShape[5] = 0;
                newShape[6] = 1;
                newShape[7] = 99;
            } else {
                // in this case we're building proper shape for reduction
                auto array = new NDArray(nullptr, inShape, block.launchContext());

                auto newShape = ShapeUtils::evalReduceShapeInfo('c', *block.getIArguments(), *array, false, true);

                delete array;
                return SHAPELIST(newShape);
            }

            return SHAPELIST(CONSTANT(newShape));
        }
    }
}