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
// implementation of operations for Simple Recurrent Unit: arXiv:1709.02755v2 [cs.CL] 12 Sep 2017
//
//@author Yurii Shyrma
//

#include <system/op_boilerplate.h>
#if NOT_EXCLUDED(OP_sru)

#include <ops/declarable/CustomOperations.h>
#include <ops/declarable/helpers/sru.h>
#include <helpers/MmulHelper.h>
#include <helpers/PointersManager.h>

namespace sd {
namespace ops  {

//////////////////////////////////////////////////////////////////////////
CUSTOM_OP_IMPL(sru, 5, 2, false, 0, 0) {
    auto x    = INPUT_VARIABLE(0);                                   // X, input 3d tensor [bS x inSize x time], time - number of time steps, bS - batch size, inSize - number of features
    auto w    = INPUT_VARIABLE(1);                                   // W, 2d tensor of weights [3*inSize x inSize]
    auto b    = INPUT_VARIABLE(2);                                   // B, row of biases with twice length [2*inSize]
    auto c0   = INPUT_VARIABLE(3);                                   // C_{0}, 2d tensor of initial state [bS x inSize] at time t=0
    auto mask = block.width() > 4 ? INPUT_VARIABLE(4) : nullptr;     // optional,  2d tensor of dropout mask [bS x inSize]

    auto h = OUTPUT_VARIABLE(0);                                     // cell outputs, [bS x inSize x time]
    auto c = OUTPUT_VARIABLE(1);                                     // cell states,  [bS x inSize x time]

    const int rank   = x->rankOf();              // = 3
    const auto bS     = x->sizeAt(0);
    const auto inSize = x->sizeAt(1);
    const auto time   = x->sizeAt(2);

    // input shapes validation
    REQUIRE_TRUE(w->rankOf()  == rank-1, 0, "SRU operation: wrong rank of weights array, expected is %i, but got %i instead !", rank-1, w->rankOf());
    REQUIRE_TRUE(b->rankOf()  == 1,      0, "SRU operation: wrong rank of biases  array, expected is %i, but got %i instead !", 1, b->rankOf());
    REQUIRE_TRUE(c0->rankOf() == rank-1, 0, "SRU operation: wrong rank of initial state array, expected is %i, but got %i instead !", rank-1, c0->rankOf());
    if(mask)
        REQUIRE_TRUE(mask->rankOf() == rank-1, 0, "SRU operation: wrong rank of mask array, expected is %i, but got %i instead !", rank-1, mask->rankOf());

    const std::vector<Nd4jLong> wCorrectShape  = {3*inSize, inSize};
    const std::vector<Nd4jLong> bCorrectShape  = {2*inSize};
    const std::vector<Nd4jLong> c0CorrectShape = {bS, inSize};

    REQUIRE_TRUE(w->isSameShape(wCorrectShape),  0, "SRU operation: wrong shape of weights array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(wCorrectShape).c_str(), ShapeUtils::shapeAsString(w).c_str());
    REQUIRE_TRUE(b->isSameShape(bCorrectShape),  0, "SRU operation: wrong shape of biases  array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(bCorrectShape).c_str(), ShapeUtils::shapeAsString(b).c_str());
    REQUIRE_TRUE(c0->isSameShape(c0CorrectShape), 0, "SRU operation: wrong shape of initial state array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(c0CorrectShape).c_str(), ShapeUtils::shapeAsString(c0).c_str());
    if(mask)
        REQUIRE_TRUE(mask->isSameShape(c0CorrectShape), 0, "SRU operation: wrong shape of mask array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(c0CorrectShape).c_str(), ShapeUtils::shapeAsString(mask).c_str());

    //  xm = x * mask
    auto xm = x;
    if(mask) {
        xm = new NDArray(x->shapeInfo(), true, block.launchContext());
        x->applyBroadcast(broadcast::Multiply, {0, 1}, *mask, *xm);
    }

    // time loop
    helpers::sruTimeLoop(block.launchContext(), xm, c0, w, b, h, c);

    if(mask)
        delete xm;

    return Status::OK();
}

        DECLARE_TYPES(sru) {
            getOpDescriptor()
                    ->setAllowedInputTypes(sd::DataType::ANY)
                    ->setAllowedOutputTypes({ALL_FLOATS});
        }

DECLARE_SHAPE_FN(sru) {

    auto xShapeInfo    = inputShape->at(0);                                   // X, input 3d tensor [bS x inSize x time], time - number of time steps, bS - batch size, inSize - number of features
    auto wShapeInfo    = inputShape->at(1);                                   // W, 2d tensor of weights [3*inSize x inSize]
    auto bShapeInfo    = inputShape->at(2);                                   // B, row of biases with twice length [2*inSize]
    auto c0ShapeInfo   = inputShape->at(3);                                   // C_{0}, 2d tensor of initial state [bS x inSize] at time t=0
    auto maskShapeInfo = block.width() > 4 ? inputShape->at(4) : nullptr;     // optional,  2d tensor of dropout mask [bS x inSize]

    const int rank   = xShapeInfo[0];              // = 3
    const int bS     = xShapeInfo[1];
    const int inSize = xShapeInfo[2];
    const int time   = xShapeInfo[3];

    // input shapes validation
    REQUIRE_TRUE(wShapeInfo[0]  == rank-1, 0, "SRU operation: wrong rank of weights array, expected is %i, but got %i instead !", rank-1, wShapeInfo[0]);
    REQUIRE_TRUE(bShapeInfo[0]  == 1,      0, "SRU operation: wrong rank of biases  array, expected is %i, but got %i instead !", 1, bShapeInfo[0]);
    REQUIRE_TRUE(c0ShapeInfo[0] == rank-1, 0, "SRU operation: wrong rank of initial state array, expected is %i, but got %i instead !", rank-1, c0ShapeInfo[0]);
    if(maskShapeInfo)
        REQUIRE_TRUE(maskShapeInfo[0] == rank-1, 0, "SRU operation: wrong rank of mask array, expected is %i, but got %i instead !", rank-1, maskShapeInfo[0]);

    const std::vector<Nd4jLong> wCorrectShape  = {3*inSize, inSize};
    const std::vector<Nd4jLong> bCorrectShape  = {2*inSize};
    const std::vector<Nd4jLong> c0CorrectShape = {bS, inSize};

    REQUIRE_TRUE(ShapeUtils::areShapesEqual(wShapeInfo,  wCorrectShape),  0, "SRU operation: wrong shape of weights array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(wCorrectShape).c_str(), ShapeUtils::shapeAsString(wShapeInfo).c_str());
    REQUIRE_TRUE(ShapeUtils::areShapesEqual(bShapeInfo,  bCorrectShape),  0, "SRU operation: wrong shape of biases  array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(bCorrectShape).c_str(), ShapeUtils::shapeAsString(bShapeInfo).c_str());
    REQUIRE_TRUE(ShapeUtils::areShapesEqual(c0ShapeInfo, c0CorrectShape), 0, "SRU operation: wrong shape of initial state array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(c0CorrectShape).c_str(), ShapeUtils::shapeAsString(c0ShapeInfo).c_str());
    if(maskShapeInfo)
        REQUIRE_TRUE(ShapeUtils::areShapesEqual(maskShapeInfo, c0CorrectShape), 0, "SRU operation: wrong shape of mask array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(c0CorrectShape).c_str(), ShapeUtils::shapeAsString(maskShapeInfo).c_str());

    Nd4jLong* newShapeInfo1 = nullptr;
    ALLOCATE(newShapeInfo1, block.getWorkspace(), shape::shapeInfoLength(rank), Nd4jLong);       // [bS x inSize x time]

    newShapeInfo1[0] = rank;
    newShapeInfo1[1] = bS;
    newShapeInfo1[2] = inSize;
    newShapeInfo1[3] = time;

    ShapeUtils::updateStridesAndType(newShapeInfo1, xShapeInfo, shape::order(xShapeInfo));
    ShapeDescriptor descriptor(newShapeInfo1);
    RELEASE(newShapeInfo1, block.getWorkspace());
    auto result = ConstantShapeHelper::getInstance().createShapeInfo(descriptor);
    return SHAPELIST(result, result);
}

//////////////////////////////////////////////////////////////////////////
CUSTOM_OP_IMPL(sru_bp, 8, 4, true, 0, 0) {
    auto x        = INPUT_VARIABLE(0);                // X, input 3d tensor [bS x K x N], N - number of time steps, bS - batch size, K - number of features
    auto w        = INPUT_VARIABLE(1);                // W, 2d tensor of weights [3K x K]
    auto b        = INPUT_VARIABLE(2);                // B, row of biases with twice length [1 x 2*K]
    auto c0       = INPUT_VARIABLE(3);                // C_{0}, 2d tensor of initial state [bS x K] at time t=0
    auto c        = INPUT_VARIABLE(4);                // C, [bS x K x N]
    auto inGradCt = INPUT_VARIABLE(5);                // [bS x K]
    auto inGradH  = INPUT_VARIABLE(6);                // [bS x K x N]
    NDArray* mask     = nullptr;                      // optional,  2d tensor of dropout mask [bS x K]

    bool applyMask = false;
    if (block.width() > 7) {
        mask = INPUT_VARIABLE(7);
        applyMask = true;
    }

    auto gradX    = OUTPUT_VARIABLE(0);              // [bS x K x N]
    auto gradW    = OUTPUT_VARIABLE(1);              // [bS x 3K x K]
    auto gradB    = OUTPUT_VARIABLE(2);              // [1 x 2K]
    auto gradInit = OUTPUT_VARIABLE(3);              // [bS x K]

    const int bS      = x->shapeOf()[0];
    const int K       = x->shapeOf()[1];
    const int N       = x->shapeOf()[2];                     // N - number of time steps

    auto gradBias = NDArrayFactory::create_(x->ordering(), {bS, 2*K, N}, gradX->dataType(), block.launchContext());
    auto gradU    = NDArrayFactory::create_(x->ordering(), {bS, 3*K, N}, gradX->dataType(), block.launchContext());
    auto gradHX   = NDArrayFactory::create_(x->ordering(), {bS, K, N}, gradX->dataType(), block.launchContext());
    auto gct      = NDArrayFactory::create_(c->ordering(), {bS, K}, gradX->dataType(), block.launchContext());
    auto gradTanh = NDArrayFactory::create_(c->ordering(), {bS, K}, gradX->dataType(), block.launchContext());
    auto gradCt   = NDArrayFactory::create_(c->ordering(), {bS, K}, gradX->dataType(), block.launchContext());
    auto ftMinus  = NDArrayFactory::create_(c->ordering(), {bS, K}, gradX->dataType(), block.launchContext());
    auto rtMinus  = NDArrayFactory::create_(c->ordering(), {bS, K}, gradX->dataType(), block.launchContext());
    auto temp1    = NDArrayFactory::create_(c->ordering(), {bS, K}, gradX->dataType(), block.launchContext());
    auto temp2    = NDArrayFactory::create_(c->ordering(), {bS, K}, gradX->dataType(), block.launchContext());

    //  x = x * mask
    if(applyMask)
        x->applyBroadcast(broadcast::Multiply, {0, 1}, *mask, *x);            // apply mask
    // multiplication matrix wi = matmul(w,x), U = WX
    auto wi = MmulHelper::mmul(w, x, nullptr, 1., 0.);      // U [bS x 3K x N]

    auto wiZ = (*wi)({0,0,  0,K,     0,0}, true);           // [bS x K x N]
    auto wiF = (*wi)({0,0,  K,2*K,   0,0}, true);           // forget gate [bS x K x N]
    auto wiR = (*wi)({0,0,  2*K,3*K, 0,0}, true);           // reset gate [bS x K x N]
    auto bF  = (*b) ({0,0,  0,K  }, true);                  // biases for forget gate [1 x K]
    auto bR  = (*b) ({0,0,  K,2*K}, true);                  // biases for reset gate [1 x K]
    auto gradBF = (*gradBias)({0,0,  0,K,     0,0}, true);  // [bS x K x N]
    auto gradBR = (*gradBias)({0,0,  K,2*K,   0,0}, true);  // [bS x K x N]
    auto gradUZ = (*gradU)   ({0,0,  0,K,     0,0}, true ); // [bS x K x N]
    auto gradUF = (*gradU)   ({0,0,  K,2*K,   0,0}, true ); // [bS x K x N]
    auto gradUR = (*gradU)   ({0,0,  2*K,3*K, 0,0}, true ); // [bS x K x N]

    NDArray*  ct_1 = nullptr;

    std::vector<Nd4jLong> idx = {0,0, 0,0, 0,0};

    for (int t = N-1; t >=0 ; --t) {
        // initialization
        idx[4] = t;
        idx[5] = t + 1;
        auto xt = (*x)(idx);                // [bS x K x N] -> [bS x K x 1] -> [bS x K]
        auto zt = wiZ(idx);                 // [bS x K x N] -> [bS x K x 1] -> [bS x K]
        auto ft = wiF(idx);                 // [bS x K x N] -> [bS x K x 1] -> [bS x K]
        auto rt = wiR(idx);                 // [bS x K x N] -> [bS x K x 1] -> [bS x K]
        auto ct = (*c)(idx);                // [bS x K x N] -> [bS x K x 1] -> [bS x K]
        auto inGradHt = (*inGradH)(idx);    // [bS x K x N] -> [bS x K x 1] -> [bS x K]
        auto gradBRt  = gradBR(idx);        // [bS x K x N] -> [bS x K x 1] -> [bS x K]
        auto gradBFt  = gradBF(idx);        // [bS x K x N] -> [bS x K x 1] -> [bS x K]
        auto gradHXt  = (*gradHX)(idx);     // [bS x K x N] -> [bS x K x 1] -> [bS x K]
        auto gradUZt  = gradUZ(idx);        // [bS x K x N] -> [bS x K x 1] -> [bS x K]
        auto gradUFt  = gradUF(idx);        // [bS x K x N] -> [bS x K x 1] -> [bS x K]
        auto gradURt  = gradUR(idx);        // [bS x K x N] -> [bS x K x 1] -> [bS x K]

        if(t != 0) {
            idx[4] = t - 1;
            idx[5] = t;
            ct_1  = new NDArray((*c)(idx));        // previous c_{t-1}
        }
        else
            ct_1 = c0;

        ///////////////// forward
        // ft = sigmoid(ft + bf), rt = sigmoid(rt + bR)
        ft.addRowVector(bF, ft);
        rt.addRowVector(bR, rt);
        ft.applyTransform(transform::Sigmoid, ft);
        rt.applyTransform(transform::Sigmoid, rt);

        // TODO T val = (activation_type == 1) ? tanh(cur) : ((activation_type == 2) ? reluf(cur) : cur );
        ct.applyTransform(transform::Tanh, *gct);
        // ftMinus = 1-ft,  rtMinus = 1-rt
        ft.applyTransform(transform::OneMinus, *ftMinus);
        rt.applyTransform(transform::OneMinus, *rtMinus);

        ///////////////// backward
        // bR, *grad_brt_ptr = inGradHt * (g_ct - xt) * (1.0f - rt) * rt;
        gct->applyPairwiseTransform(pairwise::Subtract, xt, *temp1);                 // temp1 = (g_ct - xt)
        rtMinus->applyPairwiseTransform(pairwise::Multiply, rt, *temp2);             // temp2 = (1.0f - rt) * rt;
        temp1->applyPairwiseTransform(pairwise::Multiply, *temp2);                   // temp1 = (g_ct - xt) * (1.0f - rt) * rt;
        inGradHt.applyPairwiseTransform(pairwise::Multiply, *temp1, gradBRt);       // = inGradHt * (g_ct - xt) * (1.0f - rt) * rt;

        // bF, TODO - tanh
        // gradTanh = (1.0f - g_ct * g_ct);
        gct->applyPairwiseTransform(pairwise::Multiply, *gct, *gradTanh);             // gradTanh = g_ct * g_ct
        gradTanh->applyTransform(transform::OneMinus, *gradTanh);                            // gradTanh = (1.0f - g_ct * g_ct)
        // gradCt  = inGradHt * rt * gradTanh
        rt.applyPairwiseTransform(pairwise::Multiply, *gradTanh, *gradCt);           // gradCt = rt * gradTanh
        inGradHt.applyPairwiseTransform(pairwise::Multiply, *gradCt, *gradCt);       // gradCt = inGradHt * rt * gradTanh
        // gradBFt = (gradCt + inGradCt) * (ct_1 - zt) * (1 - ft) * ft;
        gradCt->applyPairwiseTransform(pairwise::Add, *inGradCt, *temp1);              // temp1 = (gradCt + inGradCt)
        ct_1->applyPairwiseTransform(pairwise::Subtract, zt, *temp2);                // temp2 = (ct_1 - zt)
        temp1->applyPairwiseTransform(pairwise::Multiply, *ftMinus, *temp1);          // temp1 = (gradCt + inGradCt)*(1-ft)
        temp1->applyPairwiseTransform(pairwise::Multiply, ft, *temp1);               // temp1 = (gradCt + inGradCt)*(1-ft)*ft
        temp1->applyPairwiseTransform(pairwise::Multiply, *temp2, gradBFt);          // gradBFt = (gradCt + inGradCt) * (ct_1 - zt) * (1 - ft) * ft;

        // x_t (highway connection), gradHXt = inGradHt * (1.0f - rt);
        inGradHt.applyPairwiseTransform(pairwise::Multiply, *rtMinus, gradHXt);

        // U_t, gradUZt = (inGradHt * rt * grad_tanh + inGradCt) * (1.0f - ft);
        rt.applyPairwiseTransform(pairwise::Multiply, *gradTanh, *temp1);        // temp1 = rt * grad_tanh
        inGradHt.applyPairwiseTransform(pairwise::Multiply, *temp1, *temp1);     // temp1 = inGradHt * rt * grad_tanh
        temp1->applyPairwiseTransform(pairwise::Add, *inGradCt, *temp1);         // temp1 = inGradHt * rt * grad_tanh + inGradCt
        temp1->applyPairwiseTransform(pairwise::Multiply, *ftMinus, gradUZt);    // gradUZt = (inGradHt * rt * grad_tanh + inGradCt) * (1.0f - ft);
        gradUFt.assign(&gradBFt);
        gradURt.assign(&gradBRt);

        // c_{t-1}, inGradCt = (gradCt + inGradCt) * ft;
        gradCt->applyPairwiseTransform(pairwise::Add, *inGradCt, *temp1);         // temp1 = (gradCt + inGradCt)
        temp1->applyPairwiseTransform(pairwise::Multiply, ft, *inGradCt);       // inGradCt = (gradCt + inGradCt) * ft;

        if(t != 0)
            delete ct_1;
    }

    // gradInit
    gradInit->assign(inGradCt);

    // gradX
    auto weightsT = w->transpose();                                            // [K x 3K]
    MmulHelper::mmul(&weightsT, gradU, gradX, 1., 0.);                    // [bS x K x N]
    gradX->applyPairwiseTransform(pairwise::Add, *gradHX, *gradX);        // + grad_highway_x
    if(applyMask)
        gradX->applyBroadcast(broadcast::Multiply, {0,1}, *mask, *gradX);  // apply mask

    // gradB
    auto gradB2 = gradB->reshape(gradB->ordering(), {2*K});
    gradBias->reduceAlongDimension(reduce::Sum, gradB2, {0,2});    // [1 x 2K]

    // gradW [bS x 3K x K]
    x->permutei({0, 2, 1});                                               // [bS x N x K]
    MmulHelper::mmul(gradU, x, gradW, 1., 0.);          // [bS x 3K x K]

    delete gct;   delete gradU; delete gradHX;
    delete temp1; delete temp2; delete gradCt; delete wi;
    delete gradTanh; delete ftMinus; delete rtMinus; delete gradBias;

    return Status::OK();
}

        DECLARE_TYPES(sru_bp) {
            getOpDescriptor()
                    ->setAllowedInputTypes(sd::DataType::ANY)
                    ->setAllowedOutputTypes({ALL_FLOATS});
        }

DECLARE_SHAPE_FN(sru_bp) {

    auto inShape = inputShape->at(0);   // [bS x inSize x time]
    auto bS   = inShape[1];
    auto inSize    = inShape[2];
    auto time    = inShape[3];
    char order = (char)(inShape[9]);

    ShapeDescriptor descriptor1(ArrayOptions::dataType(inShape), order, {bS, inSize, time});
    ShapeDescriptor descriptor2(ArrayOptions::dataType(inShape), order, {bS, 3 * inSize, inSize});
    ShapeDescriptor descriptor3(ArrayOptions::dataType(inShape), order, {1, 2 * inSize});
    ShapeDescriptor descriptor4(ArrayOptions::dataType(inShape), order, {bS, inSize});

    return SHAPELIST(ConstantShapeHelper::getInstance().createShapeInfo(descriptor1), ConstantShapeHelper::getInstance().createShapeInfo(descriptor2), ConstantShapeHelper::getInstance().createShapeInfo(descriptor3), ConstantShapeHelper::getInstance().createShapeInfo(descriptor4));
}



//////////////////////////////////////////////////////////////////////////
CUSTOM_OP_IMPL(sru_bi, 5, 2, true, 0, 0) {

    auto x  = INPUT_VARIABLE(0);                                      // X, input 3d tensor [time x bS x 2*inSize], time - number of time steps, bS - batch size, inSize - number of features
    auto w  = INPUT_VARIABLE(1);                                      // W, 2d tensor of weights [2*inSize x 6*inSize]
    auto b  = INPUT_VARIABLE(2);                                      // B, row of biases with twice length [1 x 4*inSize]
    auto c0 = INPUT_VARIABLE(3);                                      // C_{0}, 2d tensor of initial state [bS x 2*inSize] at time t=0
    NDArray* mask = block.width() > 4 ? INPUT_VARIABLE(4) : nullptr;  // optional, 2d tensor of dropout mask [bS x 2*inSize]

    auto ht = OUTPUT_VARIABLE(0);             // h_t, [time x bS x 2*inSize]
    auto ct = OUTPUT_VARIABLE(1);             // c_t, [time x bS x 2*inSize]

    // input shapes validation
    const int rank = x->rankOf();
    const Nd4jLong bS     = x->sizeAt(1);
    const Nd4jLong inSize = x->sizeAt(2) / 2;

    REQUIRE_TRUE(x->rankOf()  == rank,   0, "SRU_BI operation: wrong rank of input array, expected is %i, but got %i instead !", rank, x->rankOf());
    REQUIRE_TRUE(w->rankOf()  == rank-1, 0, "SRU_BI operation: wrong rank of weights array, expected is %i, but got %i instead !", rank-1, w->rankOf());
    REQUIRE_TRUE(b->rankOf()  == 1,      0, "SRU_BI operation: wrong rank of biases array, expected is 1, but got %i instead !", b->rankOf());
    REQUIRE_TRUE(c0->rankOf() == rank-1, 0, "SRU_BI operation: wrong rank of initial state array, expected is %i, but got %i instead !", rank-1, c0->rankOf());
    if(mask)
        REQUIRE_TRUE(mask->rankOf() == rank-1, 0, "SRU_BI operation: wrong rank of mask array, expected is %i, but got %i instead !", rank-1, mask->rankOf());

    const std::vector<Nd4jLong> wCorrectShape  = {2*inSize, 6*inSize};
    const std::vector<Nd4jLong> bCorrectShape  = {4*inSize};
    const std::vector<Nd4jLong> c0CorrectShape = {bS, 2*inSize};

    REQUIRE_TRUE(w->isSameShape(wCorrectShape),  0, "SRU_BI operation: wrong shape of weights array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(wCorrectShape).c_str(), ShapeUtils::shapeAsString(w).c_str());
    REQUIRE_TRUE(b->isSameShape(bCorrectShape),  0, "SRU_BI operation: wrong shape of biases array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(bCorrectShape).c_str(), ShapeUtils::shapeAsString(b).c_str());
    REQUIRE_TRUE(c0->isSameShape(c0CorrectShape), 0, "SRU_BI operation: wrong shape of initial state array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(c0CorrectShape).c_str(), ShapeUtils::shapeAsString(c0).c_str());
    if(mask)
        REQUIRE_TRUE(mask->isSameShape(c0CorrectShape), 0, "SRU_BI operation: wrong shape of mask array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(c0CorrectShape).c_str(), ShapeUtils::shapeAsString(mask).c_str());

    helpers::sruBI(block.launchContext(), x, w, b, c0, mask, ht, ct);

    return Status::OK();
}

        DECLARE_TYPES(sru_bi) {
            getOpDescriptor()
                    ->setAllowedInputTypes(sd::DataType::ANY)
                    ->setAllowedOutputTypes({ALL_FLOATS});
        }

DECLARE_SHAPE_FN(sru_bi) {

    auto xShapeInfo    = inputShape->at(0);         // [time x bS x 2K ]
    auto wShapeInfo    = inputShape->at(1);
    auto bShapeInfo    = inputShape->at(2);
    auto c0ShapeInfo   = inputShape->at(3);
    auto maskShapeInfo = block.width() > 4 ? inputShape->at(4) : nullptr;     // optional,  2d tensor of dropout mask [bS x inSize]

    const int      rank   = xShapeInfo[0];              // = 3
    const Nd4jLong time   = xShapeInfo[1];
    const Nd4jLong bS     = xShapeInfo[2];
    const Nd4jLong inSize = xShapeInfo[3] / 2;


      // input shapes validation
    REQUIRE_TRUE(wShapeInfo[0]  == rank-1, 0, "SRU_BI operation: wrong rank of weights array, expected is %i, but got %i instead !", rank-1, wShapeInfo[0]);
    REQUIRE_TRUE(bShapeInfo[0]  == 1,      0, "SRU_BI operation: wrong rank of biases  array, expected is 1, but got %i instead !", bShapeInfo[0]);
    REQUIRE_TRUE(c0ShapeInfo[0] == rank-1, 0, "SRU_BI operation: wrong rank of initial state array, expected is %i, but got %i instead !", rank-1, c0ShapeInfo[0]);
    if(maskShapeInfo)
        REQUIRE_TRUE(maskShapeInfo[0] == rank-1, 0, "SRU_BI operation: wrong rank of mask array, expected is %i, but got %i instead !", rank-1, maskShapeInfo[0]);

    const std::vector<Nd4jLong> wCorrectShape  = {2*inSize, 6*inSize};
    const std::vector<Nd4jLong> bCorrectShape  = {4*inSize};
    const std::vector<Nd4jLong> c0CorrectShape = {bS, 2*inSize};

    REQUIRE_TRUE(ShapeUtils::areShapesEqual(wShapeInfo, wCorrectShape),  0, "SRU_BI operation: wrong shape of weights array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(wCorrectShape).c_str(), ShapeUtils::shapeAsString(wShapeInfo).c_str());
    REQUIRE_TRUE(ShapeUtils::areShapesEqual(bShapeInfo, bCorrectShape),  0, "SRU_BI operation: wrong shape of biases array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(bCorrectShape).c_str(), ShapeUtils::shapeAsString(bShapeInfo).c_str());
    REQUIRE_TRUE(ShapeUtils::areShapesEqual(c0ShapeInfo, c0CorrectShape), 0, "SRU_BI operation: wrong shape of initial state array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(c0CorrectShape).c_str(), ShapeUtils::shapeAsString(c0ShapeInfo).c_str());
    if(maskShapeInfo)
        REQUIRE_TRUE(ShapeUtils::areShapesEqual(maskShapeInfo, c0CorrectShape), 0, "SRU_BI operation: wrong shape of mask array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(c0CorrectShape).c_str(), ShapeUtils::shapeAsString(maskShapeInfo).c_str());


    char order = shape::order(xShapeInfo);

    ShapeDescriptor descriptor(ArrayOptions::dataType(xShapeInfo), order, {time, bS, 2 * inSize});
    auto result = ConstantShapeHelper::getInstance().createShapeInfo(descriptor);
    return SHAPELIST(result, result);
}


        DECLARE_TYPES(sru_bi_bp) {
            getOpDescriptor()
                    ->setAllowedInputTypes(sd::DataType::ANY)
                    ->setAllowedOutputTypes({ALL_FLOATS});
        }

//////////////////////////////////////////////////////////////////////////
CUSTOM_OP_IMPL(sru_bi_bp, 8, 4, true, 0, 0) {

    auto x        = INPUT_VARIABLE(0);                // X, input 3d tensor [time x bS x 2*inSize], time - number of time steps, bS - batch size, inSize - number of features
    auto w        = INPUT_VARIABLE(1);                // W, 2d tensor of weights [2*inSize x 6*inSize]
    auto b        = INPUT_VARIABLE(2);                // B, row of biases with twice length [4*inSize]
    auto c0       = INPUT_VARIABLE(3);                // C_{0}, 2d tensor of initial state [bS x 2*inSize] at time t=0
    auto ct       = INPUT_VARIABLE(4);                // C, [time x bS x 2*inSize]
    auto inGradC0 = INPUT_VARIABLE(5);                // [bS x 2*inSize]
    auto inGradHt = INPUT_VARIABLE(6);                // [time x bS x 2*inSize]
    NDArray* mask = block.width() > 7 ? INPUT_VARIABLE(7) : nullptr;  // optional,  2d tensor of dropout mask [bS x 2*inSize]

    // input shapes validation
    const int rank = x->rankOf();
    const Nd4jLong time   = x->sizeAt(0);
    const Nd4jLong bS     = x->sizeAt(1);
    const Nd4jLong inSize = x->sizeAt(2) / 2;

    REQUIRE_TRUE(w->rankOf()        == rank-1, 0, "SRU_BI_BP operation: wrong rank of weights array, expected is %i, but got %i instead !", rank-1, w->rankOf());
    REQUIRE_TRUE(b->rankOf()        == 1,      0, "SRU_BI_BP operation: wrong rank of biases array, expected is 1, but got %i instead !", b->rankOf());
    REQUIRE_TRUE(c0->rankOf()       == rank-1, 0, "SRU_BI_BP operation: wrong rank of initial state array, expected is %i, but got %i instead !", rank-1, c0->rankOf());
    REQUIRE_TRUE(ct->rankOf()       == rank,   0, "SRU_BI_BP operation: wrong rank of state array, expected is %i, but got %i instead !", rank, ct->rankOf());
    REQUIRE_TRUE(inGradC0->rankOf() == rank-1, 0, "SRU_BI_BP operation: wrong rank of gradient c0, expected is %i, but got %i instead !", rank-1, inGradC0->rankOf());
    REQUIRE_TRUE(inGradHt->rankOf() == rank,   0, "SRU_BI_BP operation: wrong rank of gradient ht, expected is %i, but got %i instead !", rank, inGradHt->rankOf());
    if(mask)
        REQUIRE_TRUE(mask->rankOf() == rank-1, 0, "SRU_BI_BP operation: wrong rank of mask array, expected is %i, but got %i instead !", rank-1, mask->rankOf());

    const std::vector<Nd4jLong> wCorrectShape  = {2*inSize, 6*inSize};
    const std::vector<Nd4jLong> bCorrectShape  = {4*inSize};
    const std::vector<Nd4jLong> c0CorrectShape = {bS, 2*inSize};
    const std::vector<Nd4jLong> ctCorrectShape = {time, bS, 2*inSize};

    REQUIRE_TRUE(w->isSameShape(wCorrectShape),  0, "SRU_BI operation: wrong shape of weights array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(wCorrectShape).c_str(), ShapeUtils::shapeAsString(w).c_str());
    REQUIRE_TRUE(b->isSameShape(bCorrectShape),  0, "SRU_BI operation: wrong shape of biases  array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(bCorrectShape).c_str(), ShapeUtils::shapeAsString(b).c_str());
    REQUIRE_TRUE(c0->isSameShape(c0CorrectShape), 0, "SRU_BI operation: wrong shape of initial state array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(c0CorrectShape).c_str(), ShapeUtils::shapeAsString(c0).c_str());
    REQUIRE_TRUE(ct->isSameShape(ctCorrectShape), 0, "SRU_BI operation: wrong shape of state array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(ctCorrectShape).c_str(), ShapeUtils::shapeAsString(ct).c_str());
    if(mask)
        REQUIRE_TRUE(mask->isSameShape(c0CorrectShape), 0, "SRU_BI operation: wrong shape of mask array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(c0CorrectShape).c_str(), ShapeUtils::shapeAsString(mask).c_str());

    auto gradI  = OUTPUT_VARIABLE(0);              // [time x bS x 2*inSize]
    auto gradW  = OUTPUT_VARIABLE(1);              // [time x 2*inSize x 6*inSize]
    auto gradB  = OUTPUT_VARIABLE(2);              // [1 x 4*inSize]
    auto gradC0 = OUTPUT_VARIABLE(3);              // [bS x 2*inSize]

    helpers::sruBIBP(block.launchContext(), x, w, b, c0, ct, inGradC0, inGradHt, mask, gradI, gradW, gradB, gradC0);

    return Status::OK();
}

DECLARE_SHAPE_FN(sru_bi_bp) {

    auto xShapeInfo        = inputShape->at(0);         // [time x bS x 2K ]
    auto wShapeInfo        = inputShape->at(1);
    auto bShapeInfo        = inputShape->at(2);
    auto c0ShapeInfo       = inputShape->at(3);
    auto ctShapeInfo       = inputShape->at(4);
    auto inGradC0ShapeInfo = inputShape->at(5);
    auto inGradHtShapeInfo = inputShape->at(6);
    auto maskShapeInfo = block.width() > 7 ? inputShape->at(7) : nullptr;     // optional,  2d tensor of dropout mask [bS x inSize]

    // input shapes validation
    const int rank        = xShapeInfo[0];
    const Nd4jLong time   = xShapeInfo[1];
    const Nd4jLong bS     = xShapeInfo[2];
    const Nd4jLong inSize = xShapeInfo[3] / 2;

    REQUIRE_TRUE(wShapeInfo[0]        == rank-1, 0, "SRU_BI_BP operation: wrong rank of weights array, expected is %i, but got %i instead !", rank-1, wShapeInfo[0]);
    REQUIRE_TRUE(bShapeInfo[0]        == 1,      0, "SRU_BI_BP operation: wrong rank of biases  array, expected is 1, but got %i instead !", bShapeInfo[0]);
    REQUIRE_TRUE(c0ShapeInfo[0]       == rank-1, 0, "SRU_BI_BP operation: wrong rank of initial state array, expected is %i, but got %i instead !", rank-1, c0ShapeInfo);
    REQUIRE_TRUE(ctShapeInfo[0]       == rank,   0, "SRU_BI_BP operation: wrong rank of state array, expected is %i, but got %i instead !", rank, ctShapeInfo);
    REQUIRE_TRUE(inGradC0ShapeInfo[0] == rank-1, 0, "SRU_BI_BP operation: wrong rank of gradient c0, expected is %i, but got %i instead !", rank-1, inGradC0ShapeInfo[0]);
    REQUIRE_TRUE(inGradHtShapeInfo[0] == rank,   0, "SRU_BI_BP operation: wrong rank of gradient ht, expected is %i, but got %i instead !", rank, inGradHtShapeInfo[0]);
    if(maskShapeInfo)
        REQUIRE_TRUE(maskShapeInfo[0] == rank-1, 0, "SRU_BI_BP operation: wrong rank of mask array, expected is %i, but got %i instead !", rank-1, maskShapeInfo[0]);

    const std::vector<Nd4jLong> wCorrectShape        = {2*inSize, 6*inSize};
    const std::vector<Nd4jLong> bCorrectShape        = {4*inSize};
    const std::vector<Nd4jLong> c0CorrectShape       = {bS, 2*inSize};
    const std::vector<Nd4jLong> ctCorrectShape       = {time, bS, 2*inSize};
    const std::vector<Nd4jLong> inGradC0CorrectShape = {bS, 2*inSize};
    const std::vector<Nd4jLong> inGradHtCorrectShape = {time, bS, 2*inSize};

    REQUIRE_TRUE(ShapeUtils::areShapesEqual(wShapeInfo, wCorrectShape), 0, "SRU_BI operation: wrong shape of weights array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(wCorrectShape).c_str(), ShapeUtils::shapeAsString(wShapeInfo).c_str());
    REQUIRE_TRUE(ShapeUtils::areShapesEqual(bShapeInfo, bCorrectShape), 0, "SRU_BI operation: wrong shape of biases  array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(bCorrectShape).c_str(), ShapeUtils::shapeAsString(bShapeInfo).c_str());
    REQUIRE_TRUE(ShapeUtils::areShapesEqual(c0ShapeInfo, c0CorrectShape), 0, "SRU_BI operation: wrong shape of initial state array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(c0CorrectShape).c_str(), ShapeUtils::shapeAsString(c0ShapeInfo).c_str());
    REQUIRE_TRUE(ShapeUtils::areShapesEqual(ctShapeInfo, ctCorrectShape), 0, "SRU_BI operation: wrong shape of state array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(ctCorrectShape).c_str(), ShapeUtils::shapeAsString(ctShapeInfo).c_str());
    REQUIRE_TRUE(ShapeUtils::areShapesEqual(inGradC0ShapeInfo, inGradC0CorrectShape), 0, "SRU_BI operation: wrong shape of gradient c0 array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(inGradC0CorrectShape).c_str(), ShapeUtils::shapeAsString(inGradC0ShapeInfo).c_str());
    REQUIRE_TRUE(ShapeUtils::areShapesEqual(inGradHtShapeInfo, inGradHtCorrectShape), 0, "SRU_BI operation: wrong shape of gradient ht array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(inGradHtCorrectShape).c_str(), ShapeUtils::shapeAsString(inGradHtShapeInfo).c_str());
    if(maskShapeInfo)
        REQUIRE_TRUE(ShapeUtils::areShapesEqual(maskShapeInfo, c0CorrectShape), 0, "SRU_BI operation: wrong shape of mask array, expected is %s, but got %s instead !", ShapeUtils::shapeAsString(c0CorrectShape).c_str(), ShapeUtils::shapeAsString(maskShapeInfo).c_str());

    const char order = shape::order(xShapeInfo);

    ShapeDescriptor descriptor1(ArrayOptions::dataType(xShapeInfo), order, {time, bS, 2 * inSize});
    ShapeDescriptor descriptor2(ArrayOptions::dataType(xShapeInfo), order, {time, 2 * inSize, 6 * inSize});
    ShapeDescriptor descriptor3(ArrayOptions::dataType(xShapeInfo), order, {4 * inSize});
    ShapeDescriptor descriptor4(ArrayOptions::dataType(xShapeInfo), order, {bS, 2 * inSize});

    return SHAPELIST(ConstantShapeHelper::getInstance().createShapeInfo(descriptor1), ConstantShapeHelper::getInstance().createShapeInfo(descriptor2), ConstantShapeHelper::getInstance().createShapeInfo(descriptor3), ConstantShapeHelper::getInstance().createShapeInfo(descriptor4));
}

}
}

#endif

//////////////////////////////////////////////////////////////////////////
    /**
       * Implementation of operations for Simple Recurrent Unit: "Training RNNs as Fast as CNNs" Tao Lei, Yu Zhang, Yoav Artzi
       *
       * Input arrays:
       *    0: input 3d tensor with shape [bS x K x N], N - number of time steps, bS - batch size, K - number of features
       *    1: 2d tensor of weights [3K x K]
       *    2: row of biases with twice length [1 x 2K]
       *    3: 2d tensor of previous cell state [bS x K]
       *    4: optional, 2d tensor of dropout mask [bS x K]
       *
       * Output arrays:
       *    0: 3d tensor of cell output [bS x K x N]
       *    1: 3d tensor of cell state [bS x K x N]
       */
        // #if NOT_EXCLUDED(OP_sru)
        // DECLARE_CUSTOM_OP(sru_old,       5, 2, false, 0, 0);


    //////////////////////////////////////////////////////////////////////////
    /**
       * Implementation of operation for Simple Recurrent Unit: "Training RNNs as Fast as CNNs" Tao Lei, Yu Zhang, Yoav Artzi
       *
       * Input arrays:
       *    0: input 3d tensor with shape [bS x K x N], N - number of time steps, bS - batch size, K - number of features
       *    1: 2d tensor of weights [3K x K]
       *    2: row of biases with twice length [1 x 2K]
       *    3: 2d tensor of previous cell state [bS x K]
       *    4: optional, 2d tensor of dropout mask [bS x K]
       *
       * Output arrays:
       *    0: 3d tensor of cell output [bS x K x N]
       *    1: 3d tensor of cell state [bS x K x N]
       */
        // #if NOT_EXCLUDED(OP_sru_logic)
        // DECLARE_CUSTOM_OP(sru_logic,   5, 2, false, 0, 0);
        // #endif


//////////////////////////////////////////////////////////////////////////
    /**
       * Implementation of operation for back propagation in Simple Recurrent Unit: "Training RNNs as Fast as CNNs" Tao Lei, Yu Zhang, Yoav Artzi
       *
       * Input arrays:
       *    0: input 3d tensor with shape [bS x K x N], N - number of time steps, bS - batch size, K - number of features
       *    1: 2d tensor of weights [3K x K]
       *    2: row of biases with twice length [1 x 2K]
       *    3: 2d tensor of previous cell state [bS x K]
       *    4: 3d tensor of cell state [bS x K x N]
       *    5: 2d tensor of cell state gradients [bS x K]
       *    6: 3d tensor of state output gradients [bS x K x N]
       *    7: optional, 2d tensor of dropout mask [bS x K]
       *
       * Output arrays:
       *    0: 3d tensor of input gradients [bS x K x N]
       *    1: 3d tensor of weights gradients [bS x 3K x K]
       *    2: 2d, row of biases gradients [1 x 2K]
       *    3: 2d, tensor of state gradients [bS x K]
       */
        // #if NOT_EXCLUDED(OP_sru_logic)
        // DECLARE_CUSTOM_OP(sru_bp_logic,8, 4, true,  0, 0);
        // #endif

// return 2d array evaluated though last dimension interval t1-t2
// static NDArray* timestep(const NDArray* arr, const int t1, const int t2) {
//         NDArray* result = new NDArray((*arr)({0,0, 0,0, t1,t2}, true));
//         result->reshapei(result->ordering(), {arr->shapeOf()[0], arr->shapeOf()[1]} );

//         return result;
// }

/////////////////////////////////////////////////////////////////////////
// CUSTOM_OP_IMPL(sru_logic, 5, 2, false, 0, 0) {

//     auto input   = INPUT_VARIABLE(0);                // X, input 3d tensor [bS x K x N], N - number of time steps, bS - batch size, K - number of features
//     auto weights = INPUT_VARIABLE(1);                // W, 2d tensor of weights [3K x K]
//     auto bias    = INPUT_VARIABLE(2);                // B, row of biases with twice length [1 x 2*K]
//     auto init    = INPUT_VARIABLE(3);                // C_{0}, 2d tensor of initial state [bS x K] at time t=0
//     NDArray* mask    = nullptr;                          // optional,  2d tensor of dropout mask [bS x K]

//     bool applyMask = false;
//     if (block.width() > 4) {
//         mask = INPUT_VARIABLE(4);
//         applyMask = true;
//     }

//     auto output = OUTPUT_VARIABLE(0);                // h_t, [bS x K x N]
//     auto state  = OUTPUT_VARIABLE(1);                // c_t, [bS x K x N]

//     const int bS     = input->shapeOf()[0];                     // bS - batch size
//     const int K      = input->shapeOf()[1];                     // K - number of features
//     const int N      = input->shapeOf()[2];                     // N - number of time steps

//     const auto wi = mmul(*weights, *input);                    //  U [bS x 3K x N]
//     const auto bF = (*bias)({0,0,  0,  K});                       // biases for forget gate [1 x K]
//     const auto bR = (*bias)({0,0,  K,2*K});                       // biases for reset  gate [1 x K]

//     NDArray xt(input->dataType(), block.launchContext());
//     NDArray zt(input->dataType(), block.launchContext());
//     NDArray ft(input->dataType(), block.launchContext());
//     NDArray rt(input->dataType(), block.launchContext());
//     NDArray ht(input->dataType(), block.launchContext());
//     NDArray ct = *init;
//     NDArray gct(state->ordering(), {bS, K}, input->dataType(), block.launchContext());
//     NDArray xmt = *input;
//     //  input = input * mask
//     if(applyMask)
//         xmt.applyBroadcast(broadcast::Multiply, {0, 1}, mask, &xmt, nullptr);

//     for (int t = 0; t < N; ++t) {
//         xt = xmt({0,0, 0,0,     t,t+1}); xt.reshapei(xt.ordering(), {bS, K});       // [bS x  K x N] -> [bS x K x 1] -> [bS x K]
//         zt =  wi({0,0, 0,    K, t,t+1}); zt.reshapei(zt.ordering(), {bS, K});       // [bS x 3K x N] -> [bS x K x 1] -> [bS x K]
//         ft =  wi({0,0, K,  2*K, t,t+1}); ft.reshapei(ft.ordering(), {bS, K});       // [bS x 3K x N] -> [bS x K x 1] -> [bS x K]
//         rt =  wi({0,0, 2*K,3*K, t,t+1}); rt.reshapei(rt.ordering(), {bS, K});       // [bS x 3K x N] -> [bS x K x 1] -> [bS x K]

//         ft = sigmoid_(ft + bF);
//         rt = sigmoid_(rt + bR);

//         ct = ft * (ct - zt) + zt;
//         // TODO T val = (activation_type == 1) ? tanh(cur) : ((activation_type == 2) ? reluf(cur) : cur );
//         ct.applyTransform(transform::Tanh, &gct);
//         ht = rt * (gct - xt) + xt;

//         // save results
//         (*output)({0,0, 0,0, t,t+1}, true).assign(ht);
//         (*state)({0,0, 0,0, t,t+1}, true).assign(ct);
//     }

//     return Status::OK();
// }

//         DECLARE_TYPES(sru_logic) {
//             getOpDescriptor()
//                     ->setAllowedInputTypes(sd::DataType::ANY)
//                     ->setAllowedOutputTypes({ALL_FLOATS});
//         }

// DECLARE_SHAPE_FN(sru_logic) {
//     auto inShape = inputShape->at(0);   // [bS x K x N]
//     int rank = inShape[0];              // = 3
//     int size = rank*2 + 4;
//     int bS   = inShape[1];
//     int K    = inShape[2];
//     int N    = inShape[3];
//     char order = (char)(inShape[size-1]);

//     Nd4jLong* newShapeInfo1 = nullptr;
//     ALLOCATE(newShapeInfo1, block.getWorkspace(), size, Nd4jLong);

//     newShapeInfo1[0] = rank;
//     newShapeInfo1[1] = bS;
//     newShapeInfo1[2] = K;
//     newShapeInfo1[3] = N;

//     ShapeUtils::updateStridesAndType(newShapeInfo1, inShape, order);
//     auto result = CONSTANT(newShapeInfo1);
//     return SHAPELIST(result, result);
// }


// //////////////////////////////////////////////////////////////////////////
// CUSTOM_OP_IMPL(sru_old, 5, 2, false, 0, 0) {
//     auto x   = INPUT_VARIABLE(0);                // X, input 3d tensor [bS x inSize x time], time - number of time steps, bS - batch size, inSize - number of features
//     auto w = INPUT_VARIABLE(1);                // W, 2d tensor of weights [3K x inSize]
//     auto b    = INPUT_VARIABLE(2);                // B, row of biases with twice length [1 x 2*inSize]
//     auto c0    = INPUT_VARIABLE(3);                // C_{0}, 2d tensor of initial state [bS x inSize] at time t=0
//     NDArray* mask    = nullptr;                          // optional,  2d tensor of dropout mask [bS x inSize]

//     bool applyMask = false;
//     if (block.width() > 4) {
//         mask = INPUT_VARIABLE(4);
//         applyMask = true;
//     }

//     auto h = OUTPUT_VARIABLE(0);                // h_t, [bS x inSize x time]
//     auto state  = OUTPUT_VARIABLE(1);                // c_t, [bS x inSize x time]

//     const int bS     = x->shapeOf()[0];                     // bS - batch size
//     const int inSize      = x->shapeOf()[1];                     // inSize - number of features
//     const int time      = x->shapeOf()[2];                     // time - number of time steps

//       // multiplication matrix = matmul(w,x)
//     auto wi = MmulHelper::mmul(w, x, nullptr, 1., 0.);            // U [bS x 3K x time]
//     auto wiZ = (*wi)({0,0,  0,inSize,          0,0}, true);       // [bS x inSize x time]
//     auto wiF = (*wi)({0,0,  inSize,2*inSize,   0,0}, true);       // forget gate [bS x inSize x time]
//     auto wiR = (*wi)({0,0,  2*inSize,3*inSize, 0,0}, true);       // reset gate [bS x inSize x time]
//     auto bF  = (*b) ({0,0,  0,inSize       }, true);              // biases for forget gate [1 x inSize]
//     auto bR  = (*b) ({0,0,  inSize,2*inSize}, true);              // biases for reset gate [1 x inSize]

//     NDArray* xt(nullptr), *zt(nullptr), *ft(nullptr), *rt(nullptr), *ct(nullptr), *ht(nullptr);
//     auto ct_1 = c0->dup(c0->ordering());
//     auto gct  = NDArrayFactory::create_(state->ordering(), {bS, inSize}, state->dataType(), state->getContext());
//     auto xmt  = x->dup(x->ordering());
//     //  x = x * mask
//     if(applyMask)
//         xmt->applyBroadcast(broadcast::Multiply, {0, 1}, mask, xmt, nullptr);            // apply mask

//     for (int t = 0; t < time; ++t) {
//         xt = timestep(xmt, t, t+1);         // [bS x inSize x time] -> [bS x inSize x 1] -> [bS x inSize]
//         zt = timestep(&wiZ, t, t+1);        // [bS x inSize x time] -> [bS x inSize x 1] -> [bS x inSize]
//         ft = timestep(&wiF, t, t+1);        // [bS x inSize x time] -> [bS x inSize x 1] -> [bS x inSize]
//         rt = timestep(&wiR, t, t+1);        // [bS x inSize x time] -> [bS x inSize x 1] -> [bS x inSize]
//         ct = timestep(state, t, t+1);       // [bS x inSize x time] -> [bS x inSize x 1] -> [bS x inSize]
//         ht = timestep(h, t, t+1);           // [bS x inSize x time] -> [bS x inSize x 1] -> [bS x inSize]

//         // ft = sigmoid(ft + bf), rt = sigmoid(rt + bR)
//         ft->addRowVector(&bF, ft);
//         rt->addRowVector(&bR, rt);
//         ft->applyTransform(transform::Sigmoid, ft, nullptr);
//         rt->applyTransform(transform::Sigmoid, rt, nullptr);
//         // ct = ft * c_t-1 + (1 - ft) * zt,
//         ft->applyPairwiseTransform(pairwise::Multiply, ct_1, ct, nullptr);
//         ft->applyTransform(transform::OneMinus, ft);
//         ft->applyPairwiseTransform(pairwise::Multiply, *zt, nullptr);
//         ct->applyPairwiseTransform(pairwise::Add, *ft, nullptr);
//         // TODO T val = (activation_type == 1) ? tanh(cur) : ((activation_type == 2) ? reluf(cur) : cur );
//         ct->applyTransform(transform::Tanh, gct);

//         // ht = rt * gct + (1 - rt) * xt
//         rt->applyPairwiseTransform(pairwise::Multiply, gct, ht, nullptr);
//         rt->applyTransform(transform::OneMinus, rt);
//         rt->applyPairwiseTransform(pairwise::Multiply, *xt, nullptr);
//         ht->applyPairwiseTransform(pairwise::Add, *rt, nullptr);

//         delete xt; delete zt; delete ft; delete rt; delete ht; delete ct_1;
//         ct_1 = ct;
//     }

//     delete wi; delete ct_1; delete gct; delete xmt;

//     return Status::OK();
// }

//         DECLARE_TYPES(sru_old) {
//             getOpDescriptor()
//                     ->setAllowedInputTypes(sd::DataType::ANY)
//                     ->setAllowedOutputTypes({ALL_FLOATS});
//         }

// DECLARE_SHAPE_FN(sru_old) {
//     auto inShape = inputShape->at(0);   // [bS x inSize x time]
//     int rank = inShape[0];              // = 3
//     int size = rank*2 + 4;
//     auto bS   = inShape[1];
//     auto inSize    = inShape[2];
//     int time    = inShape[3];
//     char order = (char)(inShape[size-1]);

//     Nd4jLong *newShapeInfo1 = nullptr;
//     ALLOCATE(newShapeInfo1, block.getWorkspace(), size, Nd4jLong);

//     newShapeInfo1[0] = rank;
//     newShapeInfo1[1] = bS;
//     newShapeInfo1[2] = inSize;
//     newShapeInfo1[3] = time;

//     ShapeUtils::updateStridesAndType(newShapeInfo1, inShape, order);

//     auto result = ConstantShapeHelper::getInstance().createShapeInfo(ShapeDescriptor(newShapeInfo1));
//     RELEASE(newShapeInfo1, block.getWorkspace());
//     return SHAPELIST(result, result);
// }

// static NDArray sigmoid_(const NDArray& arr) {
//     NDArray result(arr.shapeInfo(), false, arr.getContext());
//     (const_cast<NDArray&>(arr)).applyTransform(transform::Sigmoid, &result);
//     return result;
// }

//////////////////////////////////////////////////////////////////////////
// CUSTOM_OP_IMPL(sru_bp_logic, 8, 4, true, 0, 0) {

//     auto x        = INPUT_VARIABLE(0);                                   // X, input 3d tensor [bS x inSize x time], time - number of time steps, bS - batch size, inSize - number of features
//     auto w        = INPUT_VARIABLE(1);                                   // W, 2d tensor of weights [3*inSize x inSize]
//     auto b        = INPUT_VARIABLE(2);                                   // B, row of biases with twice length [1 x 2*inSize]
//     auto c0       = INPUT_VARIABLE(3);                                   // C_{0}, 2d tensor of initial state [bS x inSize] at time t=0
//     auto c        = INPUT_VARIABLE(4);                                   // C, [bS x inSize x time]
//     auto inGradCt = INPUT_VARIABLE(5);                                   // [bS x inSize]
//     auto inGradH  = INPUT_VARIABLE(6);                                   // [bS x inSize x time]
//     auto mask     = block.width() > 7 ? INPUT_VARIABLE(7) : nullptr;     // optional,  2d tensor of dropout mask [bS x inSize]

//     auto gradX    = OUTPUT_VARIABLE(0);              // [bS x inSize x time]
//     auto gradW    = OUTPUT_VARIABLE(1);              // [bS x 3*inSize x inSize]
//     auto gradB    = OUTPUT_VARIABLE(2);              // [2*inSize]
//     auto gradInit = OUTPUT_VARIABLE(3);              // [bS x inSize]

//     // input shapes validation
//     const int rank = 3;
//     REQUIRE_TRUE(x->rankOf()  == rank,   0, "SRU_BP operation: wrong rank of input array, expected is %i, but got %i instead !", rank, x->rankOf());
//     REQUIRE_TRUE(w->rankOf()  == rank-1, 0, "SRU_BP operation: wrong rank of weights array, expected is %i, but got %i instead !", rank-1, w->rankOf());
//     REQUIRE_TRUE(b->rankOf()  <= 2,      0, "SRU_BP operation: wrong rank of biases  array, expected is <=2, but got %i instead !", b->rankOf());
//     REQUIRE_TRUE(c0->rankOf() == rank-1, 0, "SRU_BP operation: wrong rank of initial state array, expected is %i, but got %i instead !", rank-1, c0->rankOf());
//     REQUIRE_TRUE(c->rankOf()  == rank,   0, "SRU_BP operation: wrong rank of cell states array, expected is %i, but got %i instead !", rank, c->rankOf());
//     REQUIRE_TRUE(inGradCt->rankOf() == rank-1, 0, "SRU_BP operation: wrong rank of array of cell state gradient, expected is %i, but got %i instead !", rank-1, inGradCt->rankOf());
//     REQUIRE_TRUE(inGradH->rankOf()  == rank,   0, "SRU_BP operation: wrong rank of array of cell outputs gradients, expected is %i, but got %i instead !", rank, inGradH->rankOf());
//     if(mask)
//         REQUIRE_TRUE(mask->rankOf() == rank-1, 0, "SRU_BP operation: wrong rank of mask array, expected is %i, but got %i instead !", rank-1, mask->rankOf());

//     const int bS      = x->shapeOf()[0];
//     const int inSize  = x->shapeOf()[1];
//     const int time    = x->shapeOf()[2];                     // time - number of time steps

//     const std::string wShape               = ShapeUtils::shapeAsString(w);
//     const std::string wCorrectShape        = ShapeUtils::shapeAsString({3*inSize, inSize});
//     // const std::string bShape               = ShapeUtils::shapeAsString(b);
//     // const std::string bCorrectShape        = ShapeUtils::shapeAsString({2*inSize});
//     const std::string c0Shape              = ShapeUtils::shapeAsString(c0);
//     const std::string c0CorrectShape       = ShapeUtils::shapeAsString({bS, inSize});
//     const std::string cShape               = ShapeUtils::shapeAsString(c);
//     const std::string cCorrectShape        = ShapeUtils::shapeAsString({bS, inSize, time});
//     const std::string inGradCtShape        = ShapeUtils::shapeAsString(inGradCt);
//     const std::string inGradCtCorrectShape = ShapeUtils::shapeAsString({bS, inSize});
//     const std::string inGradHShape         = ShapeUtils::shapeAsString(inGradH);
//     const std::string inGradHCorrectShape  = ShapeUtils::shapeAsString({bS, inSize, time});

//     REQUIRE_TRUE(wShape  == wCorrectShape,  0, "SRU_BP operation: wrong shape of weights array, expected is %s, but got %s instead !", wCorrectShape.c_str(), wShape.c_str());
//     // REQUIRE_TRUE(bShape  == bCorrectShape,  0, "SRU_BP operation: wrong shape of biases  array, expected is %s, but got %s instead !", bCorrectShape.c_str(), bShape.c_str());
//     REQUIRE_TRUE(c0Shape == c0CorrectShape, 0, "SRU_BP operation: wrong shape of initial state array, expected is %s, but got %s instead !", c0CorrectShape.c_str(), c0Shape.c_str());
//     REQUIRE_TRUE(cShape == cCorrectShape, 0, "SRU_BP operation: wrong shape of cell states array, expected is %s, but got %s instead !", cCorrectShape.c_str(), cShape.c_str());
//     REQUIRE_TRUE(inGradCtShape == inGradCtCorrectShape, 0, "SRU_BP operation: wrong shape of array of cell state gradient, expected is %s, but got %s instead !", inGradCtCorrectShape.c_str(), inGradCtShape.c_str());
//     REQUIRE_TRUE(inGradHShape == inGradHCorrectShape, 0, "SRU_BP operation: wrong shape of array of cell outputs gradients, expected is %s, but got %s instead !", inGradHCorrectShape.c_str(), inGradHShape.c_str());
//     if(mask) {
//         const std::string maskShape = ShapeUtils::shapeAsString(mask);
//         REQUIRE_TRUE(maskShape == c0CorrectShape, 0, "SRU_BP operation: wrong shape of mask array, expected is %s, but got %s instead !", c0CorrectShape.c_str(), maskShape.c_str());
//     }


//     const auto bF = (*b)({0,0,  0,       inSize});                                 // biases for forget gate [1 x inSize]
//     const auto bR = (*b)({0,0,  inSize,2*inSize});                                 // biases for reset  gate [1 x inSize]
//     NDArray gradBias(x->ordering(),   {bS, 2*inSize, time}, x->dataType(), block.launchContext());
//     NDArray gradU   (x->ordering(),   {bS, 3*inSize, time}, x->dataType(), block.launchContext());
//     NDArray gradHX  (x->ordering(),   {bS,   inSize, time}, x->dataType(), block.launchContext());
//     NDArray gct     (c->ordering(),   {bS, inSize},         x->dataType(), block.launchContext());

//     //  x = x * mask
//     if(mask)
//         x->applyBroadcast(broadcast::Multiply, {0, 1}, mask, x, nullptr);             // apply mask
//     // multiplication matrix wi = matmul(w,x), U = WX
//     const auto wi = mmul(*w, *x);                                                   //  U [bS x 3K x time]

//     for (int t = time-1; t >=0 ; --t) {
//         // initialization
//         auto xt =         (*x)({0,0, 0,0,                   t,t+1});    // [bS x inSize  x time] -> [bS x inSize]
//         auto zt =               wi({0,0, 0,         inSize, t,t+1});    // [bS x 3K x time] -> [bS x inSize]
//         auto ft =               wi({0,0, inSize,  2*inSize, t,t+1});    // [bS x 3K x time] -> [bS x inSize]
//         auto rt =               wi({0,0, 2*inSize,3*inSize, t,t+1});    // [bS x 3K x time] -> [bS x inSize]
//         auto ct =         (*c)({0,0, 0,0,                   t,t+1});    // [bS x inSize  x time] -> [bS x inSize]
//         auto inGradHt = (*inGradH)({ 0,0, 0,0,              t,t+1});    // [bS x inSize  x time] -> [bS x inSize]

//         auto ct_1 = t ? (*c)({ 0,0, 0,0, t-1,t}) : *c0;                                                // previous c_{t-1}

//         ///////////////// forward
//         // ft = sigmoid(ft + bf), rt = sigmoid(rt + bR)
//         ft = sigmoid_(ft + bF);
//         rt = sigmoid_(rt + bR);
//         // TODO T val = (activation_type == 1) ? tanh(cur) : ((activation_type == 2) ? reluf(cur) : cur );
//         ct.applyTransform(transform::Tanh, &gct);

//         ///////////////// backward
//         // bR, *grad_brt_ptr = inGradHt * (g_ct - xt) * (1.0f - rt) * rt;
//         // ftMinus = -ft + (T)1.;
//         NDArray ftMinus = 1. - ft;
//         NDArray rtMinus = 1. - rt;
//         NDArray gradBRt = inGradHt * (gct - xt) * rtMinus * rt;
//         // bF, TODO - tanh
//         NDArray gradTanh = 1. - gct * gct;
//         NDArray gradCt = inGradHt * rt * gradTanh;
//         NDArray gradBFt = (gradCt + *inGradCt) * (ct_1 - zt) * ftMinus * ft;
//         // x_t (highway connection), gradHXt = inGradHt * (1.0f - rt);
//         NDArray gradHXt = inGradHt * rtMinus;

//         // U_t, gradUZt = (inGradHt * rt * grad_tanh + inGradCt) * (1.0f - ft);
//         NDArray gradUZt = (inGradHt * rt * gradTanh + *inGradCt) * ftMinus;

//         // c_{t-1}, inGradCt = (gradCt + inGradCt) * ft;
//         *inGradCt = (gradCt + *inGradCt) * ft;

//         // save results
//         gradBias({0,0, 0,inSize, t,t+1}, true).assign(gradBFt);
//         gradBias({0,0, inSize,2*inSize, t,t+1}, true).assign(gradBRt);
//         gradU({0,0, 0,inSize, t,t+1}, true).assign(gradUZt);
//         gradU({0,0, inSize,2*inSize, t,t+1}, true).assign(gradBFt);
//         gradU({0,0, 2*inSize, 3*inSize, t,t+1}, true).assign(gradBRt);
//         gradHX({0,0, 0,0, t,t+1}, true).assign(gradHXt);
//     }

//     // gradInit
//     gradInit->assign(inGradCt);
//     // gradX
//     w->transposei();                                                               // [inSize x 3K]
//     gradX->assign( mmul(*w, gradU) + gradHX);
//     if(mask)
//         gradX->applyBroadcast(broadcast::Multiply, {0,1}, mask, gradX, nullptr);       // apply mask

//     // gradB
//     gradBias.reduceAlongDimension(reduce::Sum, *gradB, {0,2}, false, true);    // [1 x 2K]

//     // gradW [bS x 3K x inSize]
//     x->permutei({0, 2, 1});                                               // [bS x time x inSize]
//     gradW->assign( mmul(gradU, *x) );

//     return Status::OK();
// }

//         DECLARE_TYPES(sru_bp_logic) {
//             getOpDescriptor()
//                     ->setAllowedInputTypes(sd::DataType::ANY)
//                     ->setAllowedOutputTypes({ALL_FLOATS});
//         }

// DECLARE_SHAPE_FN(sru_bp_logic) {
//     auto inShape = inputShape->at(0);   // [bS x inSize x time]
//     auto bS   = inShape[1];
//     auto inSize    = inShape[2];
//     auto time    = inShape[3];
//     char order = shape::order(inShape);

//     ShapeDescriptor descriptor1(ArrayOptions::dataType(inShape), order, {bS, inSize, time});
//     ShapeDescriptor descriptor2(ArrayOptions::dataType(inShape), order, {bS, 3 * inSize, inSize});
//     ShapeDescriptor descriptor3(ArrayOptions::dataType(inShape), order, {1, 2 * inSize});
//     ShapeDescriptor descriptor4(ArrayOptions::dataType(inShape), order, {bS, inSize});

//     return SHAPELIST(ConstantShapeHelper::getInstance().createShapeInfo(descriptor1), ConstantShapeHelper::getInstance().createShapeInfo(descriptor2), ConstantShapeHelper::getInstance().createShapeInfo(descriptor3), ConstantShapeHelper::getInstance().createShapeInfo(descriptor4));
// }
