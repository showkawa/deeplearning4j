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
//  @author raver119@gmail.com
//

#ifndef LIBND4J_BROADCAST_HELPER_H
#define LIBND4J_BROADCAST_HELPER_H

#include <array/NDArray.h>
#include <helpers/ShapeUtils.h>
#include <ops/BroadcastOpsTuple.h>
#include <ops/BroadcastBoolOpsTuple.h>
#include <array/NDArrayFactory.h>

namespace sd {
    namespace ops {
        class BroadcastHelper {
        public:
            static FORCEINLINE NDArray* broadcastApply(sd::BroadcastOpsTuple op, NDArray* x, NDArray* y, NDArray* z, ExtraArguments *extraArgs = nullptr) {

                if(x->isEmpty() || y->isEmpty()) {
                    if(!z->isEmpty())
                        throw std::runtime_error("BroadcastHelper::broadcastApply: when some of input arrays (or both) is empty, output array must be empty as well !");
                    return z;
                }

                std::unique_ptr<NDArray> ptr;
                if (!Environment::getInstance().isExperimentalBuild()) {
                    if (y->dataType() != x->dataType()) {
                        y = new NDArray(y->cast(x->dataType()));
                        std::unique_ptr<NDArray> ptr2(y);
                        ptr.swap(ptr2);
                    }
                }

                if (!x->isScalar() && !y->isScalar() && x->isSameShape(y)) {
				    x->applyPairwiseTransform(op.p, *y, *z);
                } else if (!x->isScalar() && y->isScalar()) {
                    x->applyScalarArr(op.s, const_cast<const NDArray&>(*y), *z);
                } else if (x->isScalar() && !y->isScalar()) {
                    if (z->isSameShape(y)) {
                        if (op.s == scalar::Add || op.s == scalar::Multiply ) {
                            y->applyScalarArr(op.s, *x, *z);
                        } else if (op.s == scalar::SquaredSubtract) {
                            y->applyScalarArr(scalar::SquaredReverseSubtract, *x, *z);
                        } else if (op.s == scalar::Subtract) {
                            y->applyScalarArr(scalar::ReverseSubtract, *x, *z);
                        } else if (op.s == scalar::Divide) {
                            y->applyScalarArr(scalar::ReverseDivide, *x, *z);
                        } else if (op.s == scalar::Pow) {
                            y->applyScalarArr(scalar::ReversePow, *x, *z);
                        } else if (op.s == scalar::ReverseSubtract) {
                            y->applyScalarArr(scalar::Subtract, *x, *z);
                        } else if (op.s == scalar::ReverseDivide) {
                            y->applyScalarArr(scalar::Divide, *x, *z);
                        } else if (op.s == scalar::MaxPairwise || op.s == scalar::MinPairwise || op.s == scalar::AMaxPairwise || op.s == scalar::AMinPairwise) {
                            y->applyScalarArr(op.s, *x, *z);
                        } else if (op.s == scalar::CopyPws) {
                            z->assign(y);
                        } else {
                            z->assign(x);
                            z->applyPairwiseTransform(op.p, *y, extraArgs);
                        }
                        return z;
                    } else {
                        auto v = y->getShapeAsVector();
                        auto tZ = NDArrayFactory::valueOf(v, y, y->ordering());
                        tZ->applyPairwiseTransform(op.p, *y, extraArgs);
                        return tZ;
                    }
                } else if (x->isScalar() && y->isScalar()) { // x->isScalar() && y->isScalar()
				    x->applyScalarArr(op.s, const_cast<const NDArray&>(*y), *z);
			    } else if (ShapeUtils::areShapesBroadcastable(*x, *y)) {
                    x->applyTrueBroadcast(op, *y, *z, true, extraArgs);
                    return z;
                } else {
                    auto sx = ShapeUtils::shapeAsString(x);
                    auto sy = ShapeUtils::shapeAsString(y);
                    nd4j_printf("Broadcast: shapes should be equal, or broadcastable. But got %s vs %s instead\n", sx.c_str(), sy.c_str());
                    return nullptr;
                }

                return z;
            }

            static FORCEINLINE NDArray* broadcastApply(sd::BroadcastBoolOpsTuple op, NDArray* x, NDArray* y, NDArray* z, ExtraArguments *extraArgs = nullptr) {

                if(x->isEmpty() || y->isEmpty()) {
                    if(!z->isEmpty())
                        throw std::runtime_error("BroadcastHelper::broadcastApply: when some of input arrays (or both) is empty, output array must be empty as well !");
                    return z;
                }

                if (!x->isScalar() && !y->isScalar() && x->isSameShape(y)) {
                    x->applyPairwiseTransform(op.p, *y, *z);
                } else if (ShapeUtils::areShapesBroadcastable(*x, *y)) {
                    x->applyTrueBroadcast(op, *y, *z, true, extraArgs);
                    return z;
                } else if (!x->isScalar() && y->isScalar()) {
                    x->applyScalarArr(op.s, const_cast<const NDArray&>(*y), *z);
                } else if (x->isScalar() && !y->isScalar()) {
                    if (z->isSameShape(y)) {
                        //z->assign(x);
                        x->applyPairwiseTransform(op.p, *y, *z, extraArgs);
                        return z;
                    } else {
                        auto v = y->getShapeAsVector();
                        auto tZ = NDArrayFactory::valueOf(v, y, y->ordering());
                        //tZ->applyPairwiseTransform(op.p, *y, extraArgs);
                        return tZ;
                    }
                } else if (x->isScalar() && y->isScalar()) { // x->isScalar() && y->isScalar()
                    x->applyScalarArr(op.s, const_cast<const NDArray&>(*y), *z);
                } else if (ShapeUtils::areShapesBroadcastable(*x, *y)) {
                    x->applyTrueBroadcast(op, *y, *z, true, extraArgs);
                    return z;
                } else {
                    auto sx = ShapeUtils::shapeAsString(x);
                    auto sy = ShapeUtils::shapeAsString(y);
                    nd4j_printf("Broadcast: shapes should be equal, or broadcastable. But got %s vs %s instead\n", sx.c_str(), sy.c_str());
                    return nullptr;
                }

                return z;
            }
        };
    }
}

#endif