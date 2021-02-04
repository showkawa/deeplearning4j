/*******************************************************************************
 * Copyright (c) 2015-2018 Skymind, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
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

#include <ops/declarable/LegacyReduceFloatOp.h>
#include <helpers/TAD.h>
#include <helpers/ShapeUtils.h>
#include <graph/Status.h>
#include <helpers/ConstantTadHelper.h>
#include <array/DataTypeUtils.h>

namespace sd {
    namespace ops {
        LegacyReduceFloatOp::LegacyReduceFloatOp() : LegacyOp::LegacyOp(1) {
            //
        }

        LegacyReduceFloatOp::LegacyReduceFloatOp(int opNum) : LegacyOp::LegacyOp(1, opNum) {
            //this->_opNum = opNum;
        }

        LegacyOp* LegacyReduceFloatOp::clone() {
            return new LegacyReduceFloatOp(this->_opNum);
        }

        Nd4jStatus LegacyReduceFloatOp::validateAndExecute(Context &block) {
            auto x = INPUT_VARIABLE(0);

            auto z = OUTPUT_VARIABLE(0);

            NDArray::prepareSpecialUse({z}, {x});

            int opNum = block.opNum() < 0 ? this->_opNum : block.opNum();
            nd4j_debug("Executing LegacyReduceFloatOp: [%i]\n", opNum);

            bool allAxes = false;
            auto axis = *block.getAxis();

            ExtraArguments extras(*block.getTArguments());
            PointersManager manager(block.launchContext(), "LegacyReduceFloatOp");

            if (block.width() == 1) {

                if (axis.size() == x->rankOf())
                    allAxes = true;

                // _axis.(block.getIArguments()->size() == 0) ||
                //                    (block.getIArguments()->size() == 1 && INT_ARG(0) == sd::DataTypeUtils::max<int>())
                if (block.getAxis()->empty() || allAxes) {
                    // scalar
                    NativeOpExecutioner::execReduceFloatScalar(block.launchContext(), opNum, x->buffer(), x->shapeInfo(), x->specialBuffer(), x->specialShapeInfo(),
                            extras.argumentsAsT(z->dataType()), z->buffer(), z->shapeInfo(), z->specialBuffer(), z->specialShapeInfo());
                } else {
                    // TAD
                    std::vector<int> dims(*block.getAxis());

                    for (int e = 0; e < dims.size(); e++)
                        if (dims[e] < 0)
                            dims[e] += x->rankOf();

                    REQUIRE_TRUE(dims.size() > 0, 0, "Some dimensions required for reduction!");

                    // auto packX = sd::ConstantTadHelper::getInstance().tadForDimensions(x->shapeInfo(), dims);

                    // auto pTadShape = Environment::getInstance().isCPU() ? packX.primaryShapeInfo() : packX.specialShapeInfo(); //manager.replicatePointer(tad.tadOnlyShapeInfo, shape::shapeInfoByteLength(tad.tadOnlyShapeInfo));
                    // auto pTadOffsets = Environment::getInstance().isCPU() ? packX.primaryOffsets() : packX.specialOffsets(); //manager.replicatePointer(tad.tadOffsets, tad.numTads * sizeof(Nd4jLong));

                    const Nd4jLong* zShapeInfoH = z->shapeInfo();
                    const Nd4jLong* zShapeInfoD = z->specialShapeInfo();

                    if(x->rankOf() == z->rankOf()) {
                        auto zPack = ConstantShapeHelper::getInstance().createShapeInfoWithNoUnitiesForReduce(z->shapeInfo(), dims, z->getContext()->getWorkspace());
                        zShapeInfoH = reinterpret_cast<Nd4jLong const*>(zPack.primary());
                        zShapeInfoD = reinterpret_cast<Nd4jLong const*>(zPack.special());
                    }

                    std::vector<int> dims2 = ShapeUtils::evalDimsForReduceOp(x->rankOf(), dims);

                    NativeOpExecutioner::execReduceFloat(block.launchContext(), opNum, x->buffer(), x->shapeInfo(), x->specialBuffer(), x->specialShapeInfo(),
                            extras.argumentsAsT(z->dataType()), z->buffer(), zShapeInfoH, z->specialBuffer(), zShapeInfoD,
                            dims2.data(), (int) dims2.size());

                }

                STORE_RESULT(*z);
            } else {
                auto indices = INPUT_VARIABLE(1);
                if (indices->lengthOf() == x->rankOf())
                    allAxes = true;

                //indices->printIndexedBuffer("indices");

                std::vector<int> dims(indices->lengthOf());
                for (int e = 0; e < indices->lengthOf(); e++) {
                    // lol otherwise we segfault on macOS
                    int f = indices->e<int>(e);
                    dims[e] = f >= 0 ? f : f += x->rankOf();
                }

                if ((block.getIArguments()->size() == 1 && INT_ARG(0) == sd::DataTypeUtils::max<int>()) || allAxes) {
                    // scalar
                    NativeOpExecutioner::execReduceFloatScalar(block.launchContext(), opNum, x->buffer(), x->shapeInfo(), x->specialBuffer(), x->specialShapeInfo(), extras.argumentsAsT(x->dataType()), z->buffer(), z->shapeInfo(), z->specialBuffer(), z->specialShapeInfo());
                } else {
                    // TAD
                    REQUIRE_TRUE(dims.size() > 0, 0, "Some dimensions required for reduction!");

                    // auto packX = sd::ConstantTadHelper::getInstance().tadForDimensions(x->shapeInfo(), dims);

                    // auto pTadShape = Environment::getInstance().isCPU() ? packX.primaryShapeInfo() : packX.specialShapeInfo(); //(Nd4jLong *) manager.replicatePointer(tad.tadOnlyShapeInfo, shape::shapeInfoByteLength(tad.tadOnlyShapeInfo));
                    // auto pTadOffsets = Environment::getInstance().isCPU() ? packX.primaryOffsets() : packX.specialOffsets(); //(Nd4jLong *) manager.replicatePointer(tad.tadOffsets, tad.numTads * sizeof(Nd4jLong));

                    const Nd4jLong* zShapeInfoH = z->shapeInfo();
                    const Nd4jLong* zShapeInfoD = z->specialShapeInfo();

                    if(x->rankOf() == z->rankOf()) {
                        auto zPack = ConstantShapeHelper::getInstance().createShapeInfoWithNoUnitiesForReduce(z->shapeInfo(), dims, z->getContext()->getWorkspace());
                        zShapeInfoH = reinterpret_cast<Nd4jLong const*>(zPack.primary());
                        zShapeInfoD = reinterpret_cast<Nd4jLong const*>(zPack.special());
                    }

                    std::vector<int> dims2 = ShapeUtils::evalDimsForReduceOp(x->rankOf(), dims);

                    NativeOpExecutioner::execReduceFloat(block.launchContext(), opNum, x->buffer(), x->shapeInfo(), x->specialBuffer(), x->specialShapeInfo(),
                            extras.argumentsAsT(z->dataType()), z->buffer(), zShapeInfoH, z->specialBuffer(), zShapeInfoD,
                            dims2.data(), (int) dims2.size());
                }
            }

            manager.synchronize();
            return Status::OK();
        }

        /**
        *   For all reductions rules are simple: either you return scalar, or you return reduced NDArray.
        *   It solely depends on input shape, and requested dimensions
        */
        ShapeList *LegacyReduceFloatOp::calculateOutputShape(ShapeList *inputShape, sd::graph::Context &block) {
            auto inShape = inputShape->at(0);

            bool allAxes = false;

            auto keepDims = block.numB() > 0 ? B_ARG(0) : false;
            auto newFormat = block.numB() > 1 ? B_ARG(1) : true;

            auto axis = block.width() > 1 ? INPUT_VARIABLE(1)->asVectorT<int>() : *block.getAxis();

            if (axis.size() == shape::rank(inShape))
                allAxes = true;

            // in this case we're building proper shape for reduction
            auto newShape = ShapeUtils::evalReduceShapeInfo(shape::order(inShape), axis, inShape, keepDims, !newFormat, block.workspace());

            return SHAPELIST(newShape);
        }
    }
}