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
//  @author Yurii Shyrma (iuriish@yahoo.com)
//

#include <system/op_boilerplate.h>
#if NOT_EXCLUDED(OP_multiply)

#include <ops/declarable/CustomOperations.h>

namespace sd {
namespace ops {

    BROADCASTABLE_OP_IMPL(multiply, 0, 0) {
        auto x = INPUT_VARIABLE(0);
        auto y = INPUT_VARIABLE(1);
        auto z = OUTPUT_VARIABLE(0);

        BROADCAST_CHECK_EMPTY(x,y,z);

        const Nd4jLong* zShapeInfo = nullptr;
        const bool areShapesBroadcastable = ShapeUtils::evalBroadcastShapeInfo(x->shapeInfo(), y->shapeInfo(), true, zShapeInfo, block.getWorkspace());
        REQUIRE_TRUE(areShapesBroadcastable, 0, "MULTIPLY OP: the shapes of x %s and y %s are not suitable for broadcast !", ShapeUtils::shapeAsString(x).c_str(), ShapeUtils::shapeAsString(y).c_str());

        auto tZ = BroadcastHelper::broadcastApply(sd::BroadcastOpsTuple::Multiply(), x, y, z);
        if (tZ == nullptr)
            return ND4J_STATUS_KERNEL_FAILURE;
        else if (tZ != z)
            throw std::runtime_error("multiply: result was replaced");

        return Status::OK();
    }
    DECLARE_SYN(Mul, multiply);

    DECLARE_TYPES(multiply) {
        getOpDescriptor()
                ->setAllowedInputTypes(0, DataType::ANY)
                ->setAllowedInputTypes(1, DataType::ANY)
                ->setAllowedOutputTypes(0, DataType::INHERIT);
    }

    DECLARE_TYPES(multiply_bp) {
        getOpDescriptor()
                ->setAllowedInputTypes(DataType::ANY)
                ->setAllowedOutputTypes({ALL_FLOATS});
    }

///////////////////////////////////////////////////////////////////
CUSTOM_OP_IMPL(multiply_bp, 3, 2, false, 0, 0) {
    auto x    = INPUT_VARIABLE(0);
    auto y    = INPUT_VARIABLE(1);
    auto dLdz = INPUT_VARIABLE(2);

    auto dLdx = OUTPUT_VARIABLE(0);
    auto dLdy = OUTPUT_VARIABLE(1);

    const Nd4jLong* dLdzShapeInfo = nullptr;
    const bool areShapesBroadcastable = ShapeUtils::evalBroadcastShapeInfo(x->shapeInfo(), y->shapeInfo(), true, dLdzShapeInfo, block.getWorkspace());
    REQUIRE_TRUE(areShapesBroadcastable, 0, "MULTIPLY_BP OP: the shapes of x %s and y %s are not suitable for broadcast !", ShapeUtils::shapeAsString(x).c_str(), ShapeUtils::shapeAsString(y).c_str());
    REQUIRE_TRUE(shape::equalsSoft(dLdz->shapeInfo(), dLdzShapeInfo), 0, "MULTIPLY_BP OP: wrong shape of next epsilon array (dLdOut), expected is %s, but got %s instead !", ShapeUtils::shapeAsString(dLdzShapeInfo).c_str(), ShapeUtils::shapeAsString(dLdz).c_str());

    const Nd4jLong xLen = x->lengthOf();
    const Nd4jLong yLen = y->lengthOf();

    if(x->isScalar() && y->isScalar()) {    // both are scalars
        y->applyPairwiseTransform(pairwise::Multiply, *dLdz, *dLdx);
        x->applyPairwiseTransform(pairwise::Multiply, *dLdz, *dLdy);
        //dLdx->assign((*y) * (*dLdz));
        //dLdy->assign((*x) * (*dLdz));

    }
    else if(x->isScalar()) {            // x is scalar and y is not
        dLdx->assign((*y * *dLdz).reduceNumber(reduce::Sum));
        dLdz->applyScalarArr(scalar::Multiply, *x, *dLdy);
        //dLdz->applyTrueBroadcast(broadcast::Multiply, x, dLdy, true);
    }
    else if(y->isScalar()) {            // y is scalar and x is not
        dLdy->assign((*x * *dLdz).reduceNumber(reduce::Sum));
        dLdz->applyScalarArr(scalar::Multiply, *y, *dLdx);
    }
    else if(x->isSameShape(y)) {
        x->applyPairwiseTransform(pairwise::Multiply, *dLdz, *dLdy);
        y->applyPairwiseTransform(pairwise::Multiply, *dLdz, *dLdx);
    }
    else if (x->isSameShape(dLdz)) {

        auto yTiled = NDArray(dLdz, false, block.launchContext());
        y->tile(yTiled);
        std::vector<int> axesForY = ShapeUtils::evalBroadcastBackwardAxis(y->shapeInfo(), dLdz->shapeInfo());

        dLdy->assign( (*x * *dLdz).reduceAlongDimension(reduce::Sum, axesForY) );
        yTiled.applyPairwiseTransform(pairwise::Multiply, *dLdz, *dLdx);
    }
    else if (y->isSameShape(dLdz)) {

        auto xTiled = NDArray(dLdz, false, block.launchContext());
        x->tile(xTiled);
        std::vector<int> axesForX = ShapeUtils::evalBroadcastBackwardAxis(x->shapeInfo(), dLdz->shapeInfo());

        dLdx->assign( (*y * *dLdz).reduceAlongDimension(reduce::Sum, axesForX) );
        xTiled.applyPairwiseTransform(pairwise::Multiply, *dLdz, *dLdy);
    }
    else {

        auto xTiled = NDArray(dLdz, false, block.launchContext());
        auto yTiled = NDArray(dLdz, false, block.launchContext());
        x->tile(xTiled);
        y->tile(yTiled);
        std::vector<int> axesForX = ShapeUtils::evalBroadcastBackwardAxis(x->shapeInfo(), dLdz->shapeInfo());
        std::vector<int> axesForY = ShapeUtils::evalBroadcastBackwardAxis(y->shapeInfo(), dLdz->shapeInfo());

        dLdx->assign( (*y * *dLdz).reduceAlongDimension(reduce::Sum, axesForX) );
        dLdy->assign( (*x * *dLdz).reduceAlongDimension(reduce::Sum, axesForY) );
    }

    return Status::OK();
}

DECLARE_SHAPE_FN(multiply_bp) {

    auto xShapeInfo    = inputShape->at(0);
    auto yShapeInfo    = inputShape->at(1);

    Nd4jLong *dLdxShapeInfo = nullptr;
    Nd4jLong *dLdyShapeInfo = nullptr;

    COPY_SHAPE(xShapeInfo, dLdxShapeInfo);
    COPY_SHAPE(yShapeInfo, dLdyShapeInfo);

    return SHAPELIST(CONSTANT(dLdxShapeInfo), CONSTANT(dLdyShapeInfo));
}
/*
        CUSTOM_OP_IMPL(multiply_bp, 3, 2, false, 0, 0) {
            auto x = INPUT_VARIABLE(0);
            auto y = INPUT_VARIABLE(1);
            auto epsNext = INPUT_VARIABLE(2);

            auto gradX = OUTPUT_VARIABLE(0);
            auto gradY = OUTPUT_VARIABLE(1);

            auto lambdaX = LAMBDA_TT(_e, _y) {
                return _e * _y;
            };

            auto lambdaY = LAMBDA_TT(_e, _x) {
                return _e * _x;
            };


            if (x->isSameShape(y)) {
                // PWT case case

                // X gradient
                epsNext->applyPairwiseLambda(y, lambdaX, gradX);

                // Y gradient
                epsNext->applyPairwiseLambda(x, lambdaY, gradY);

            } else if (y->isScalar()) {
                // scalar case
                T _y = y->e(0);
                auto lambdaS = LAMBDA_T(_e, _y) {
                    return _e * _y;
                };

                T tmpX = x->template reduceNumber<simdOps::Sum<T>>();
                gradY->assign(tmpX);

                epsNext->applyLambda(lambdaS, *gradX);
            } else {
                // broadcast case

                auto preX = x->dup();
                auto preY = y->dup();

                auto targetShape = epsNext->getShapeAsVector();

                preX->tileToShape(targetShape);
                preY->tileToShape(targetShape);

                auto axisX = ShapeUtils::evalBroadcastBackwardAxis(x->shapeInfo(), epsNext->shapeInfo());
                auto axisY = ShapeUtils::evalBroadcastBackwardAxis(y->shapeInfo(), epsNext->shapeInfo());

                if (axisX.size() > 0) {
                    auto sum = preX->template reduceAlongDimension<simdOps::Sum<T>>(axisX);
                    gradX->assign(sum);
                    delete sum;
                } else
                    gradX->assign(preX);

                if (axisY.size() > 0) {
                    auto sum = preY->template reduceAlongDimension<simdOps::Sum<T>>(axisY);
                    gradY->assign(sum);
                    delete sum;
                } else
                    gradY->assign(preY);


                delete preX;
                delete preY;
            }

            return Status::OK();
        }
*/

}
}

#endif