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
// @author saudet
// @author Yurii Shyrma (iuriish@yahoo.com)
//

#include <dnnl_types.h>
#include <ops/declarable/helpers/convolutions.h>
#include "mkldnnUtils.h"

using namespace dnnl;

namespace sd        {
namespace mkldnnUtils {

//////////////////////////////////////////////////////////////////////
void getDims(const NDArray* array, const int rank, dnnl::memory::dims& mklDims){

    std::vector<int64_t> vDims(rank);
    for (auto i = 0; i < rank; i++) {
        vDims[i] = array->sizeAt(i);
    }
    mklDims = dnnl::memory::dims(vDims);
}
//////////////////////////////////////////////////////////////////////
dnnl::memory::format_tag getFormat(const NDArray& arr) {

    dnnl::memory::format_tag result;

    switch (arr.rankOf()) {
        case 1:
            result = dnnl::memory::format_tag::a;
            break;
        case 2:
            result = arr.ordering() == 'c' ? dnnl::memory::format_tag::ab : dnnl::memory::format_tag::ba;
            break;
        case 3:
            result = arr.ordering() == 'c' ? dnnl::memory::format_tag::abc : dnnl::memory::format_tag::cba;
            break;
        case 4:
            result = dnnl::memory::format_tag::abcd;
            break;
        case 5:
            result = dnnl::memory::format_tag::abcde;
            break;
        case 6:
            result = dnnl::memory::format_tag::abcdef;
            break;
        default:
            throw std::invalid_argument("MKLDNN getFormat: do we really want to use arras with rank > 6 ?");
    }

    return result;
}

//////////////////////////////////////////////////////////////////////
void setBlockStrides(const NDArray& array, dnnl::memory::desc& mklMd, const std::vector<int>& permut) {

    if (array.ews() != 1 || (array.rankOf() > 3 && array.ordering() == 'f') || !permut.empty()) {

        mklMd.data.format_kind = dnnl_blocked;                  // overrides format

        if(permut.empty())
            for (auto i = 0; i < array.rankOf(); ++i)
                mklMd.data.format_desc.blocking.strides[i] = array.strideAt(i);
        else {
            if(array.rankOf() != permut.size())
                throw std::invalid_argument("mkldnnUtils::setBlockStrides: size of permut vector is not equal to array rank !");
            for (auto i = 0; i < array.rankOf(); ++i)
                mklMd.data.format_desc.blocking.strides[i] = array.strideAt(permut[i]);
        }
    }
}
////////////////////////////////////////////////////////////////////////////////////////////////
dnnl::memory loadDataToMklStream(const NDArray& array, const dnnl::engine& engine, const dnnl::stream& stream,
                                 const dnnl::memory::desc& user_md, const dnnl::memory::desc& primitive_md, dnnl::memory& arg) {

    auto user_mem = dnnl::memory(user_md, engine, const_cast<NDArray&>(array).buffer());
    const bool bReorder = primitive_md != user_mem.get_desc();
    auto mkl_mem = bReorder ? dnnl::memory(primitive_md, engine) : user_mem;
    if (bReorder)
        dnnl::reorder(user_mem, mkl_mem).execute(stream, user_mem, mkl_mem);
    arg = mkl_mem;
    return user_mem;
}

//////////////////////////////////////////////////////////////////////
void poolingMKLDNN(const NDArray *input, NDArray *output,
                const int kD, const int kH, const int kW,
                const int sD, const int sH, const int sW,
                const int pD, const int pH, const int pW,
                const int isNCHW, const dnnl::algorithm mode) {

    // unfortunately mkl dnn doesn't support any format (dnnl::memory::format_tag::any) for input
    const int rank = input->rankOf();

    int bS, iC, iD, iH, iW, oC, oD, oH, oW, indIOioC, indIiH, indWoC, indWiC, indWkH, indOoH;
    dnnl::memory::dims strides, kernel, padding, padding_r, xDims, zDims;
    dnnl::memory::format_tag xzFrmat;

    const auto type = dnnl::memory::data_type::f32;

    if(rank == 4) {     // 2d

        ops::ConvolutionUtils::getSizesAndIndexesConv2d(isNCHW, 0, *input, *output, bS, iC, iH, iW, oC, oH, oW, indIOioC, indIiH, indWiC, indWoC, indWkH, indOoH);

        strides   = { sH, sW };
        kernel    = { kH, kW };
        padding   = { pH, pW };
        padding_r = { (oH - 1) * sH - iH + kH - pH, (oW - 1) * sW - iW + kW - pW };
        xDims     = {bS, iC, iH, iW};
        zDims     = {bS, oC, oH, oW};

        xzFrmat = isNCHW ? dnnl::memory::format_tag::nchw : dnnl::memory::format_tag::nhwc;
    }
    else {              // 3d

        ops::ConvolutionUtils::getSizesAndIndexesConv3d(isNCHW, 0, *input, *output, bS, iC, iD, iH, iW, oC, oD, oH, oW, indIOioC, indIiH, indWiC, indWoC, indWkH);

        strides   = { sD, sH, sW };
        kernel    = { kD, kH, kW };
        padding   = { pD, pH, pW };
        padding_r = { (oD - 1) * sD - iD + kD - pD, (oH - 1) * sH - iH + kH - pH, (oW - 1) * sW - iW + kW - pW };
        xDims     = {bS, iC, iD, iH, iW};
        zDims     = {bS, oC, oD, oH, oW};

        xzFrmat = isNCHW ? dnnl::memory::format_tag::ncdhw : dnnl::memory::format_tag::ndhwc;
    }

    std::vector<int> permut;
    if(!isNCHW)
        permut = rank == 4 ? std::vector<int>({0,3,1,2}) : std::vector<int>({0,4,1,2,3});

    // memory descriptors for arrays

    // input
    dnnl::memory::desc x_mkl_md  = dnnl::memory::desc(xDims, type, xzFrmat);
    dnnl::memory::desc x_user_md = dnnl::memory::desc(xDims, type, xzFrmat);
    mkldnnUtils::setBlockStrides(*input, x_user_md, permut);

    // output
    dnnl::memory::desc z_mkl_md  = dnnl::memory::desc(zDims, type, dnnl::memory::format_tag::any);
    dnnl::memory::desc z_user_md = dnnl::memory::desc(zDims, type, xzFrmat);
    mkldnnUtils::setBlockStrides(*output, z_user_md, permut);

    auto engine = mkldnnUtils::getEngine(LaunchContext::defaultContext()->engine());

    // operation primitive description
    dnnl::pooling_forward::desc op_desc(dnnl::prop_kind::forward_inference, mode, x_mkl_md, z_mkl_md, strides, kernel, padding, padding_r);
    dnnl::pooling_forward::primitive_desc op_prim_desc(op_desc, engine);

    // arguments (memory buffers) necessary for calculations
    std::unordered_map<int, dnnl::memory> args;

    dnnl::stream stream(engine);

    // provide memory buffers and check whether reorder is required

    // input
    mkldnnUtils::loadDataToMklStream(*input, engine, stream, x_user_md, op_prim_desc.src_desc(), args[DNNL_ARG_SRC]);

    // output
    auto z_user_mem = mkldnnUtils::loadDataToMklStream(*output, engine, stream, z_user_md, op_prim_desc.dst_desc(), args[DNNL_ARG_DST]);

    // run calculations
    dnnl::pooling_forward(op_prim_desc).execute(stream, args);

    // reorder outputs if necessary
    if (op_prim_desc.dst_desc() != z_user_mem.get_desc())
        dnnl::reorder(args[DNNL_ARG_DST], z_user_mem).execute(stream, args[DNNL_ARG_DST], z_user_mem);

    stream.wait();
}

//////////////////////////////////////////////////////////////////////
void poolingBpMKLDNN(const NDArray *input, const NDArray *gradO, NDArray *gradI,
                    const int kD, const int kH, const int kW,
                    const int sD, const int sH, const int sW,
                    const int pD, const int pH, const int pW,
                    const int isNCHW, const dnnl::algorithm mode) {

    // unfortunately mkl dnn doesn't support any format (dnnl::memory::format_tag::any) for input

    const int rank = input->rankOf();

    int bS, iC, iD, iH, iW, oC, oD, oH, oW, indIOioC, indIiH, indWoC, indWiC, indWkH, indOoH;
    dnnl::memory::dims strides, kernel, padding, padding_r, xDims, zDims;
    dnnl::memory::format_tag xzFrmat;

    const auto type = dnnl::memory::data_type::f32;

    if(rank == 4) {     // 2d

        ops::ConvolutionUtils::getSizesAndIndexesConv2d(isNCHW, 0, *input, *gradO, bS, iC, iH, iW, oC, oH, oW, indIOioC, indIiH, indWiC, indWoC, indWkH, indOoH);

        strides   = { sH, sW };
        kernel    = { kH, kW };
        padding   = { pH, pW };
        padding_r = { (oH - 1) * sH - iH + kH - pH, (oW - 1) * sW - iW + kW - pW };
        xDims     = {bS, iC, iH, iW};
        zDims     = {bS, oC, oH, oW};

        xzFrmat = isNCHW ? dnnl::memory::format_tag::nchw : dnnl::memory::format_tag::nhwc;
    }
    else {              // 3d

        ops::ConvolutionUtils::getSizesAndIndexesConv3d(isNCHW, 0, *input, *gradO, bS, iC, iD, iH, iW, oC, oD, oH, oW, indIOioC, indIiH, indWiC, indWoC, indWkH);

        strides   = { sD, sH, sW };
        kernel    = { kD, kH, kW };
        padding   = { pD, pH, pW };
        padding_r = { (oD - 1) * sD - iD + kD - pD, (oH - 1) * sH - iH + kH - pH, (oW - 1) * sW - iW + kW - pW };
        xDims     = {bS, iC, iD, iH, iW};
        zDims     = {bS, oC, oD, oH, oW};

        xzFrmat = isNCHW ? dnnl::memory::format_tag::ncdhw : dnnl::memory::format_tag::ndhwc;
    }

    std::vector<int> permut;
    if(!isNCHW)
        permut = rank == 4 ? std::vector<int>({0,3,1,2}) : std::vector<int>({0,4,1,2,3});


    // memory descriptors for arrays

    // input
    dnnl::memory::desc x_mkl_md  = dnnl::memory::desc(xDims, type, xzFrmat);
    dnnl::memory::desc x_user_md = dnnl::memory::desc(xDims, type, xzFrmat);
    mkldnnUtils::setBlockStrides(*input, x_user_md, permut);

    // gradO
    dnnl::memory::desc gradO_mkl_md  = dnnl::memory::desc(zDims, type, dnnl::memory::format_tag::any);
    dnnl::memory::desc gradO_user_md = dnnl::memory::desc(zDims, type, xzFrmat);
    mkldnnUtils::setBlockStrides(*gradO, gradO_user_md, permut);

    // gradI
    dnnl::memory::desc gradI_mkl_md  = dnnl::memory::desc(xDims, type, dnnl::memory::format_tag::any);
    dnnl::memory::desc gradI_user_md = dnnl::memory::desc(xDims, type, xzFrmat);
    mkldnnUtils::setBlockStrides(*gradI, gradI_user_md, permut);

    auto engine = mkldnnUtils::getEngine(LaunchContext::defaultContext()->engine());
    dnnl::stream stream(engine);

    // forward primitive description
    dnnl::pooling_forward::desc op_ff_desc(dnnl::prop_kind::forward, mode, x_mkl_md, gradO_mkl_md, strides, kernel, padding, padding_r);
    dnnl::pooling_forward::primitive_desc op_ff_prim_desc(op_ff_desc, engine);

    // backward primitive description
    dnnl::pooling_backward::desc op_bp_desc(mode, gradI_mkl_md, gradO_mkl_md, strides, kernel, padding, padding_r);
    dnnl::pooling_backward::primitive_desc op_bp_prim_desc(op_bp_desc, engine, op_ff_prim_desc);

    // arguments (memory buffers) necessary for calculations
    std::unordered_map<int, dnnl::memory> args;

    // gradO
    mkldnnUtils::loadDataToMklStream(*gradO, engine, stream, gradO_user_md, op_bp_prim_desc.diff_dst_desc(), args[DNNL_ARG_DIFF_DST]);

    // gradI
    auto gradI_user_mem = mkldnnUtils::loadDataToMklStream(*gradI, engine, stream, gradI_user_md, op_bp_prim_desc.diff_src_desc(), args[DNNL_ARG_DIFF_SRC]);

    if(mode == algorithm::pooling_max) {

        // input
        mkldnnUtils::loadDataToMklStream(*input, engine, stream, x_user_md, op_ff_prim_desc.src_desc(), args[DNNL_ARG_SRC]);

        // z
        auto z_mkl_mem = dnnl::memory(op_ff_prim_desc.dst_desc(), engine);
        args[DNNL_ARG_DST] = z_mkl_mem;

        // auxiliary memory allocation
        auto workspace = dnnl::memory(op_ff_prim_desc.workspace_desc(), engine);
        args[DNNL_ARG_WORKSPACE] = workspace;

        // run forward calculations
        dnnl::pooling_forward(op_ff_prim_desc).execute(stream, args);
    }

    // run backward calculations
    dnnl::pooling_backward(op_bp_prim_desc).execute(stream, args);

    // reorder gradI if necessary
    if (op_bp_prim_desc.diff_src_desc() != gradI_user_mem.get_desc())
        dnnl::reorder(args[DNNL_ARG_DIFF_SRC], gradI_user_mem).execute(stream, args[DNNL_ARG_DIFF_SRC], gradI_user_mem);

    stream.wait();
}

//////////////////////////////////////////////////////////////////////////
void getMKLDNNMemoryDescLrn(const NDArray* src, const NDArray* diff_src, const NDArray* dst,
                            dnnl::memory::desc* lrn_src_md, dnnl::memory::desc* lrn_diff_src_md, dnnl::memory::desc* lrn_dst_md,
                            dnnl::memory::desc* user_src_md, dnnl::memory::desc* user_diff_src_md, dnnl::memory::desc* user_dst_md, int axis) {
    const Nd4jLong* shape = src->shapeInfo();
    long rank = shape[0];
    long dim1 = axis; // MKL-DNN supports only 1 axis, which has to be the "channel" one
    long dim2 = axis >= 2 ? 1 : 2;
    long dim3 = axis >= 3 ? 2 : 3;
    dnnl::memory::dims lrn_src_tz = { (int)shape[1], (int)shape[dim1 + 1], rank > 2 ? (int)shape[dim2 + 1] : 1, rank > 3 ? (int)shape[dim3 + 1] : 1};

    auto type = dnnl::memory::data_type::f32;
    auto format = axis == 1 ? dnnl::memory::format_tag::nchw : dnnl::memory::format_tag::nhwc;
    auto supposed_to_be_any_format = format; // doesn't work with "any"

    if (src != nullptr && src->buffer() != nullptr && lrn_src_md != nullptr) {
        *lrn_src_md = dnnl::memory::desc({ lrn_src_tz }, type, supposed_to_be_any_format);
        *user_src_md = dnnl::memory::desc({ lrn_src_tz }, type, format);
        user_src_md->data.format_kind = dnnl_blocked;
        user_src_md->data.format_desc.blocking.strides[0] = src->stridesOf()[0];
        user_src_md->data.format_desc.blocking.strides[1] = src->stridesOf()[dim1];
        user_src_md->data.format_desc.blocking.strides[2] = rank > 2 ? src->stridesOf()[dim2] : 1;
        user_src_md->data.format_desc.blocking.strides[3] = rank > 3 ? src->stridesOf()[dim3] : 1;
    }

    if (diff_src != nullptr && diff_src->buffer() != nullptr && lrn_diff_src_md != nullptr) {
        *lrn_diff_src_md = dnnl::memory::desc({ lrn_src_tz }, type, supposed_to_be_any_format);
        *user_diff_src_md = dnnl::memory::desc({ lrn_src_tz }, type, format);
        user_diff_src_md->data.format_kind = dnnl_blocked;
        user_diff_src_md->data.format_desc.blocking.strides[0] = diff_src->stridesOf()[0];
        user_diff_src_md->data.format_desc.blocking.strides[1] = diff_src->stridesOf()[dim1];
        user_diff_src_md->data.format_desc.blocking.strides[2] = rank > 2 ? diff_src->stridesOf()[dim2] : 1;
        user_diff_src_md->data.format_desc.blocking.strides[3] = rank > 3 ? diff_src->stridesOf()[dim3] : 1;
    }

    if (dst != nullptr && dst->buffer() != nullptr && lrn_dst_md != nullptr) {
        *lrn_dst_md = dnnl::memory::desc({ lrn_src_tz }, type, supposed_to_be_any_format);
        *user_dst_md = dnnl::memory::desc({ lrn_src_tz }, type, format);
        user_dst_md->data.format_kind = dnnl_blocked;
        user_dst_md->data.format_desc.blocking.strides[0] = dst->stridesOf()[0];
        user_dst_md->data.format_desc.blocking.strides[1] = dst->stridesOf()[dim1];
        user_dst_md->data.format_desc.blocking.strides[2] = rank > 2 ? dst->stridesOf()[dim2] : 1;
        user_dst_md->data.format_desc.blocking.strides[3] = rank > 3 ? dst->stridesOf()[dim3] : 1;
    }
}

//////////////////////////////////////////////////////////////////////////
dnnl::engine& getEngine(void *ptr) {
    auto eng = reinterpret_cast<dnnl::engine*>(ptr);
    return *eng;
}


/*
//////////////////////////////////////////////////////////////////////////
void getMKLDNNMemoryDescPool2d(
        int kH, int kW, int sH, int sW, int pH, int pW, int dH, int dW, int poolingMode, int extraParam0, bool isNCHW,
        int bS, int iC, int iH, int iW, int oC, int oH, int oW,
        const NDArray* src, const NDArray* diff_src, const NDArray* dst, dnnl::algorithm& algorithm,
        dnnl::memory::desc* pool_src_md, dnnl::memory::desc* pool_diff_src_md, dnnl::memory::desc* pool_dst_md,
        dnnl::memory::desc* user_src_md, dnnl::memory::desc* user_diff_src_md, dnnl::memory::desc* user_dst_md,
        dnnl::memory::dims& pool_strides, dnnl::memory::dims& pool_kernel, dnnl::memory::dims& pool_padding, dnnl::memory::dims& pool_padding_r) {
    dnnl::memory::dims pool_src_tz = { bS, iC, iH, iW };
    dnnl::memory::dims pool_dst_tz = { bS, oC, oH, oW };

    pool_strides = { sH, sW };
    pool_kernel = { kH, kW };
    pool_padding = { pH, pW };
    pool_padding_r = { (oH - 1) * sH - iH + kH - pH,
                       (oW - 1) * sW - iW + kW - pW };

    algorithm = poolingMode == 0 ? algorithm::pooling_max
                                 : extraParam0 == 0 ? algorithm::pooling_avg_exclude_padding
                                                    : algorithm::pooling_avg_include_padding;
    auto type = dnnl::memory::data_type::f32;
    auto format = isNCHW ? dnnl::memory::format_tag::nchw : dnnl::memory::format_tag::nhwc;
    auto supposed_to_be_any_format = dnnl::memory::format_tag::nChw8c; // doesn't work with "any"

    if (src != nullptr && src->buffer() != nullptr && pool_src_md != nullptr) {
        *pool_src_md = dnnl::memory::desc({ pool_src_tz }, type, supposed_to_be_any_format);
        *user_src_md = dnnl::memory::desc({ pool_src_tz }, type, format);
        user_src_md->data.format_kind = dnnl_blocked; // overrides "format = isNCHW ? nchw : nhwc"
        user_src_md->data.format_desc.blocking.strides[0] = src->stridesOf()[isNCHW ? 0 : 0];
        user_src_md->data.format_desc.blocking.strides[1] = src->stridesOf()[isNCHW ? 1 : 3];
        user_src_md->data.format_desc.blocking.strides[2] = src->stridesOf()[isNCHW ? 2 : 1];
        user_src_md->data.format_desc.blocking.strides[3] = src->stridesOf()[isNCHW ? 3 : 2];
    }

    if (diff_src != nullptr && diff_src->buffer() != nullptr && pool_diff_src_md != nullptr) {
        *pool_diff_src_md = dnnl::memory::desc({ pool_src_tz }, type, supposed_to_be_any_format);
        *user_diff_src_md = dnnl::memory::desc({ pool_src_tz }, type, format);
        user_diff_src_md->data.format_kind = dnnl_blocked; // overrides "format = isNCHW ? nchw : nhwc"
        user_diff_src_md->data.format_desc.blocking.strides[0] = diff_src->stridesOf()[isNCHW ? 0 : 0];
        user_diff_src_md->data.format_desc.blocking.strides[1] = diff_src->stridesOf()[isNCHW ? 1 : 3];
        user_diff_src_md->data.format_desc.blocking.strides[2] = diff_src->stridesOf()[isNCHW ? 2 : 1];
        user_diff_src_md->data.format_desc.blocking.strides[3] = diff_src->stridesOf()[isNCHW ? 3 : 2];
    }

    if (dst != nullptr && dst->buffer() != nullptr && pool_dst_md != nullptr) {
        *pool_dst_md = dnnl::memory::desc({ pool_dst_tz }, type, supposed_to_be_any_format);
        *user_dst_md = dnnl::memory::desc({ pool_dst_tz }, type, format);
        user_dst_md->data.format_kind = dnnl_blocked; // overrides "format = isNCHW ? nchw : nhwc"
        user_dst_md->data.format_desc.blocking.strides[0] = dst->stridesOf()[isNCHW ? 0 : 0];
        user_dst_md->data.format_desc.blocking.strides[1] = dst->stridesOf()[isNCHW ? 1 : 3];
        user_dst_md->data.format_desc.blocking.strides[2] = dst->stridesOf()[isNCHW ? 2 : 1];
        user_dst_md->data.format_desc.blocking.strides[3] = dst->stridesOf()[isNCHW ? 3 : 2];
    }
};

//////////////////////////////////////////////////////////////////////////
void getMKLDNNMemoryDescPool3d(
        int kD, int kH, int kW, int sD, int sH, int sW, int pD, int pH, int pW, int dD, int dH, int dW, int poolingMode, int extraParam0, bool isNCDHW,
        int bS, int iC, int iD, int iH, int iW, int oC, int oD, int oH, int oW,
        const NDArray* src, const NDArray* diff_src, const NDArray* dst, dnnl::algorithm& algorithm,
        dnnl::memory::desc* pool_src_md, dnnl::memory::desc* pool_diff_src_md, dnnl::memory::desc* pool_dst_md,
        dnnl::memory::desc* user_src_md, dnnl::memory::desc* user_diff_src_md, dnnl::memory::desc* user_dst_md,
        dnnl::memory::dims& pool_strides, dnnl::memory::dims& pool_kernel, dnnl::memory::dims& pool_padding, dnnl::memory::dims& pool_padding_r) {
    dnnl::memory::dims pool_src_tz = { bS, iC, iD, iH, iW };
    dnnl::memory::dims pool_dst_tz = { bS, oC, oD, oH, oW };

    pool_strides = { sD, sH, sW };
    pool_kernel = { kD, kH, kW };
    pool_padding = { pD, pH, pW };
    pool_padding_r = { (oD - 1) * sD - iD + kD - pD,
                       (oH - 1) * sH - iH + kH - pH,
                       (oW - 1) * sW - iW + kW - pW };

    algorithm = poolingMode == 0 ? algorithm::pooling_max
                                 : extraParam0 == 0 ? algorithm::pooling_avg_exclude_padding
                                                    : algorithm::pooling_avg_include_padding;
    auto type = dnnl::memory::data_type::f32;
    auto format = isNCDHW ? dnnl::memory::format_tag::ncdhw : dnnl::memory::format_tag::ndhwc;
    auto supposed_to_be_any_format = dnnl::memory::format_tag::nCdhw8c; // doesn't work with "any"

    if (src != nullptr && src->buffer() != nullptr && pool_src_md != nullptr) {
        *pool_src_md = dnnl::memory::desc({ pool_src_tz }, type, supposed_to_be_any_format);
        *user_src_md = dnnl::memory::desc({ pool_src_tz }, type, format);
        user_src_md->data.format_kind = dnnl_blocked; // overrides "format = isNCDHW ? ncdhw : ndhwc"
        user_src_md->data.format_desc.blocking.strides[0] = src->stridesOf()[isNCDHW ? 0 : 0];
        user_src_md->data.format_desc.blocking.strides[1] = src->stridesOf()[isNCDHW ? 1 : 4];
        user_src_md->data.format_desc.blocking.strides[2] = src->stridesOf()[isNCDHW ? 2 : 1];
        user_src_md->data.format_desc.blocking.strides[3] = src->stridesOf()[isNCDHW ? 3 : 2];
        user_src_md->data.format_desc.blocking.strides[4] = src->stridesOf()[isNCDHW ? 4 : 3];
    }

    if (diff_src != nullptr && diff_src->buffer() != nullptr && pool_diff_src_md != nullptr) {
        *pool_diff_src_md = dnnl::memory::desc({ pool_src_tz }, type, supposed_to_be_any_format);
        *user_diff_src_md = dnnl::memory::desc({ pool_src_tz }, type, format);
        user_diff_src_md->data.format_kind = dnnl_blocked; // overrides "format = isNCDHW ? ncdhw : ndhwc"
        user_diff_src_md->data.format_desc.blocking.strides[0] = diff_src->stridesOf()[isNCDHW ? 0 : 0];
        user_diff_src_md->data.format_desc.blocking.strides[1] = diff_src->stridesOf()[isNCDHW ? 1 : 4];
        user_diff_src_md->data.format_desc.blocking.strides[2] = diff_src->stridesOf()[isNCDHW ? 2 : 1];
        user_diff_src_md->data.format_desc.blocking.strides[3] = diff_src->stridesOf()[isNCDHW ? 3 : 2];
        user_diff_src_md->data.format_desc.blocking.strides[4] = diff_src->stridesOf()[isNCDHW ? 4 : 3];
    }

    if (dst != nullptr && dst->buffer() != nullptr && pool_dst_md != nullptr) {
        *pool_dst_md = dnnl::memory::desc({ pool_dst_tz }, type, supposed_to_be_any_format);
        *user_dst_md = dnnl::memory::desc({ pool_dst_tz }, type, format);
        user_dst_md->data.format_kind = dnnl_blocked; // overrides "format = isNCDHW ? ncdhw : ndhwc"
        user_dst_md->data.format_desc.blocking.strides[0] = dst->stridesOf()[isNCDHW ? 0 : 0];
        user_dst_md->data.format_desc.blocking.strides[1] = dst->stridesOf()[isNCDHW ? 1 : 4];
        user_dst_md->data.format_desc.blocking.strides[2] = dst->stridesOf()[isNCDHW ? 2 : 1];
        user_dst_md->data.format_desc.blocking.strides[3] = dst->stridesOf()[isNCDHW ? 3 : 2];
        user_dst_md->data.format_desc.blocking.strides[4] = dst->stridesOf()[isNCDHW ? 4 : 3];
    }
};

//////////////////////////////////////////////////////////////////////////
void getMKLDNNMemoryDescConv2d(
        int kH, int kW, int sH, int sW, int pH, int pW, int dH, int dW, const int paddingMode, bool isNCHW,
        int bS, int iC, int iH, int iW, int oC, int oH, int oW, const NDArray* src, const NDArray* diff_src,
        const NDArray* weights, const NDArray* diff_weights, const NDArray* bias, const NDArray* dst,
        dnnl::memory::desc* conv_src_md, dnnl::memory::desc* conv_diff_src_md, dnnl::memory::desc* conv_weights_md,
        dnnl::memory::desc* conv_diff_weights_md, dnnl::memory::desc* conv_bias_md, dnnl::memory::desc* conv_dst_md,
        dnnl::memory::desc* user_src_md, dnnl::memory::desc* user_diff_src_md, dnnl::memory::desc* user_weights_md,
        dnnl::memory::desc* user_diff_weights_md, dnnl::memory::desc* user_bias_md, dnnl::memory::desc* user_dst_md,
        dnnl::memory::dims& conv_strides, dnnl::memory::dims& conv_padding, dnnl::memory::dims& conv_padding_r, dnnl::memory::dims& conv_dilation) {
    dnnl::memory::dims conv_src_tz = { bS, iC, iH, iW };
    dnnl::memory::dims conv_weights_tz = { oC, iC, kH, kW };
    dnnl::memory::dims conv_bias_tz = { oC };
    dnnl::memory::dims conv_dst_tz = { bS, oC, oH, oW };

    const int pWSame = (paddingMode == 2 && dW > 1) ? ((oW - 1) * sW + (kW - 1) * dW + 1 - iW) / 2 : pW;       // dH == 1 for causal mode in conv1d

    conv_strides   = { sH, sW };
    conv_padding   = { pH, pW };
    conv_padding_r = { (oH - 1) * sH - iH + kH - pH, (oW - 1) * sW - iW + kW - pWSame };
    conv_dilation  = { dH-1, dW-1};

    auto type = dnnl::memory::data_type::f32;
    auto format = isNCHW ? dnnl::memory::format_tag::nchw : dnnl::memory::format_tag::nhwc;
    auto formatw = dnnl::memory::format_tag::hwio;

    if (src != nullptr && conv_src_md != nullptr) {
        *conv_src_md = dnnl::memory::desc({ conv_src_tz }, type, dnnl::memory::format_tag::any);
        *user_src_md = dnnl::memory::desc({ conv_src_tz }, type, format);
        user_src_md->data.format_kind = dnnl_blocked; // overrides "format = isNCHW ? nchw : nhwc"
        user_src_md->data.format_desc.blocking.strides[0] = src->stridesOf()[isNCHW ? 0 : 0];
        user_src_md->data.format_desc.blocking.strides[1] = src->stridesOf()[isNCHW ? 1 : 3];
        user_src_md->data.format_desc.blocking.strides[2] = src->stridesOf()[isNCHW ? 2 : 1];
        user_src_md->data.format_desc.blocking.strides[3] = src->stridesOf()[isNCHW ? 3 : 2];
    }

    if (diff_src != nullptr && conv_diff_src_md != nullptr) {
        *conv_diff_src_md = dnnl::memory::desc({ conv_src_tz }, type, dnnl::memory::format_tag::any);
        *user_diff_src_md = dnnl::memory::desc({ conv_src_tz }, type, format);
        user_diff_src_md->data.format_kind = dnnl_blocked; // overrides "format = isNCHW ? nchw : nhwc"
        user_diff_src_md->data.format_desc.blocking.strides[0] = diff_src->stridesOf()[isNCHW ? 0 : 0];
        user_diff_src_md->data.format_desc.blocking.strides[1] = diff_src->stridesOf()[isNCHW ? 1 : 3];
        user_diff_src_md->data.format_desc.blocking.strides[2] = diff_src->stridesOf()[isNCHW ? 2 : 1];
        user_diff_src_md->data.format_desc.blocking.strides[3] = diff_src->stridesOf()[isNCHW ? 3 : 2];
    }

    if (weights != nullptr && conv_weights_md != nullptr) {
        *conv_weights_md = dnnl::memory::desc({ conv_weights_tz }, type, dnnl::memory::format_tag::any);
        *user_weights_md = dnnl::memory::desc({ conv_weights_tz }, type, formatw);
        user_weights_md->data.format_kind = dnnl_blocked; // overrides "formatw = hwio"
        user_weights_md->data.format_desc.blocking.strides[0] = weights->stridesOf()[3];
        user_weights_md->data.format_desc.blocking.strides[1] = weights->stridesOf()[2];
        user_weights_md->data.format_desc.blocking.strides[2] = weights->stridesOf()[0];
        user_weights_md->data.format_desc.blocking.strides[3] = weights->stridesOf()[1];
    }

    if (diff_weights != nullptr && conv_diff_weights_md != nullptr) {
        *conv_diff_weights_md = dnnl::memory::desc({ conv_weights_tz }, type, dnnl::memory::format_tag::any);
        *user_diff_weights_md = dnnl::memory::desc({ conv_weights_tz }, type, formatw);
        user_diff_weights_md->data.format_kind = dnnl_blocked; // overrides "formatw = hwio"
        user_diff_weights_md->data.format_desc.blocking.strides[0] = diff_weights->stridesOf()[3];
        user_diff_weights_md->data.format_desc.blocking.strides[1] = diff_weights->stridesOf()[2];
        user_diff_weights_md->data.format_desc.blocking.strides[2] = diff_weights->stridesOf()[0];
        user_diff_weights_md->data.format_desc.blocking.strides[3] = diff_weights->stridesOf()[1];
    }

    if (bias != nullptr && conv_bias_md != nullptr) {
        *conv_bias_md = dnnl::memory::desc({ conv_bias_tz }, type, dnnl::memory::format_tag::any);
        *user_bias_md = dnnl::memory::desc({ conv_bias_tz }, type, dnnl::memory::format_tag::x);
    }

    if (dst != nullptr && conv_dst_md != nullptr) {
        *conv_dst_md = dnnl::memory::desc({ conv_dst_tz }, type, dnnl::memory::format_tag::any);
        *user_dst_md = dnnl::memory::desc({ conv_dst_tz }, type, format);
        user_dst_md->data.format_kind = dnnl_blocked; // overrides "format = isNCHW ? nchw : nhwc"
        user_dst_md->data.format_desc.blocking.strides[0] = dst->stridesOf()[isNCHW ? 0 : 0];
        user_dst_md->data.format_desc.blocking.strides[1] = dst->stridesOf()[isNCHW ? 1 : 3];
        user_dst_md->data.format_desc.blocking.strides[2] = dst->stridesOf()[isNCHW ? 2 : 1];
        user_dst_md->data.format_desc.blocking.strides[3] = dst->stridesOf()[isNCHW ? 3 : 2];
    }
}

//////////////////////////////////////////////////////////////////////////
void getMKLDNNMemoryDescConv3d(
        int kD, int kH, int kW, int sD, int sH, int sW, int pD, int pH, int pW, int dD, int dH, int dW, bool paddingMode, bool isNCDHW,
        int bS, int iC, int iD, int iH, int iW, int oC, int oD, int oH, int oW, const NDArray* src, const NDArray* diff_src,
        const NDArray* weights, const NDArray* diff_weights, const NDArray* bias, const NDArray* dst,
        dnnl::memory::desc* conv_src_md, dnnl::memory::desc* conv_diff_src_md, dnnl::memory::desc* conv_weights_md,
        dnnl::memory::desc* conv_diff_weights_md, dnnl::memory::desc* conv_bias_md, dnnl::memory::desc* conv_dst_md,
        dnnl::memory::desc* user_src_md, dnnl::memory::desc* user_diff_src_md, dnnl::memory::desc* user_weights_md,
        dnnl::memory::desc* user_diff_weights_md, dnnl::memory::desc* user_bias_md, dnnl::memory::desc* user_dst_md,
        dnnl::memory::dims& conv_strides, dnnl::memory::dims& conv_padding, dnnl::memory::dims& conv_padding_r, dnnl::memory::dims& conv_dilation) {
    dnnl::memory::dims conv_src_tz = { bS, iC, iD, iH, iW };
    dnnl::memory::dims conv_weights_tz = { oC, iC, kD, kH, kW };
    dnnl::memory::dims conv_bias_tz = { oC };
    dnnl::memory::dims conv_dst_tz = { bS, oC, oD, oH, oW };

    conv_strides   = { sD, sH, sW };
    conv_padding   = { pD, pH, pW };
    conv_padding_r = { (oD - 1) * sD - iD + kD - pD, (oH - 1) * sH - iH + kH - pH, (oW - 1) * sW - iW + kW - pW };
    conv_dilation  = { dD-1, dH-1, dW-1};

    auto type = dnnl::memory::data_type::f32;
    auto format = isNCDHW ? dnnl::memory::format_tag::ncdhw : dnnl::memory::format_tag::ndhwc;
    auto formatw = dnnl::memory::format_tag::dhwio;

    if (src != nullptr && conv_src_md != nullptr) {
        *conv_src_md = dnnl::memory::desc({ conv_src_tz }, type, dnnl::memory::format_tag::any);
        *user_src_md = dnnl::memory::desc({ conv_src_tz }, type, format);
        user_src_md->data.format_kind = dnnl_blocked; // overrides "format = isNCDHW ? ncdhw : ndhwc"
        user_src_md->data.format_desc.blocking.strides[0] = src->stridesOf()[isNCDHW ? 0 : 0];
        user_src_md->data.format_desc.blocking.strides[1] = src->stridesOf()[isNCDHW ? 1 : 4];
        user_src_md->data.format_desc.blocking.strides[2] = src->stridesOf()[isNCDHW ? 2 : 1];
        user_src_md->data.format_desc.blocking.strides[3] = src->stridesOf()[isNCDHW ? 3 : 2];
        user_src_md->data.format_desc.blocking.strides[4] = src->stridesOf()[isNCDHW ? 4 : 3];
    }

    if (diff_src != nullptr && conv_diff_src_md != nullptr) {
        *conv_diff_src_md = dnnl::memory::desc({ conv_src_tz }, type, dnnl::memory::format_tag::any);
        *user_diff_src_md = dnnl::memory::desc({ conv_src_tz }, type, format);
        user_diff_src_md->data.format_kind = dnnl_blocked; // overrides "format = isNCDHW ? ncdhw : ndhwc"
        user_diff_src_md->data.format_desc.blocking.strides[0] = diff_src->stridesOf()[isNCDHW ? 0 : 0];
        user_diff_src_md->data.format_desc.blocking.strides[1] = diff_src->stridesOf()[isNCDHW ? 1 : 4];
        user_diff_src_md->data.format_desc.blocking.strides[2] = diff_src->stridesOf()[isNCDHW ? 2 : 1];
        user_diff_src_md->data.format_desc.blocking.strides[3] = diff_src->stridesOf()[isNCDHW ? 3 : 2];
        user_diff_src_md->data.format_desc.blocking.strides[4] = diff_src->stridesOf()[isNCDHW ? 4 : 3];
    }

    if (weights != nullptr && conv_weights_md != nullptr) {
        *conv_weights_md = dnnl::memory::desc({ conv_weights_tz }, type, dnnl::memory::format_tag::any);
        *user_weights_md = dnnl::memory::desc({ conv_weights_tz }, type, formatw);
        user_weights_md->data.format_kind = dnnl_blocked; // overrides "formatw = dhwio"
        user_weights_md->data.format_desc.blocking.strides[0] = weights->stridesOf()[4];
        user_weights_md->data.format_desc.blocking.strides[1] = weights->stridesOf()[3];
        user_weights_md->data.format_desc.blocking.strides[2] = weights->stridesOf()[0];
        user_weights_md->data.format_desc.blocking.strides[3] = weights->stridesOf()[1];
        user_weights_md->data.format_desc.blocking.strides[4] = weights->stridesOf()[2];
    }

    if (diff_weights != nullptr && conv_diff_weights_md != nullptr) {
        *conv_diff_weights_md = dnnl::memory::desc({ conv_weights_tz }, type, dnnl::memory::format_tag::any);
        *user_diff_weights_md = dnnl::memory::desc({ conv_weights_tz }, type, formatw);
        user_diff_weights_md->data.format_kind = dnnl_blocked; // overrides "formatw = dhwio"
        user_diff_weights_md->data.format_desc.blocking.strides[0] = diff_weights->stridesOf()[4];
        user_diff_weights_md->data.format_desc.blocking.strides[1] = diff_weights->stridesOf()[3];
        user_diff_weights_md->data.format_desc.blocking.strides[2] = diff_weights->stridesOf()[0];
        user_diff_weights_md->data.format_desc.blocking.strides[3] = diff_weights->stridesOf()[1];
        user_diff_weights_md->data.format_desc.blocking.strides[4] = diff_weights->stridesOf()[2];
    }

    if (bias != nullptr && conv_bias_md != nullptr) {
        *conv_bias_md = dnnl::memory::desc({ conv_bias_tz }, type, dnnl::memory::format_tag::any);
        *user_bias_md = dnnl::memory::desc({ conv_bias_tz }, type, dnnl::memory::format_tag::x);
    }

    if (dst != nullptr && conv_dst_md != nullptr) {
        *conv_dst_md = dnnl::memory::desc({ conv_dst_tz }, type, dnnl::memory::format_tag::any);
        *user_dst_md = dnnl::memory::desc({ conv_dst_tz }, type, format);
        user_dst_md->data.format_kind = dnnl_blocked; // overrides "format = isNCDHW ? ncdhw : ndhwc"
        user_dst_md->data.format_desc.blocking.strides[0] = dst->stridesOf()[isNCDHW ? 0 : 0];
        user_dst_md->data.format_desc.blocking.strides[1] = dst->stridesOf()[isNCDHW ? 1 : 4];
        user_dst_md->data.format_desc.blocking.strides[2] = dst->stridesOf()[isNCDHW ? 2 : 1];
        user_dst_md->data.format_desc.blocking.strides[3] = dst->stridesOf()[isNCDHW ? 3 : 2];
        user_dst_md->data.format_desc.blocking.strides[4] = dst->stridesOf()[isNCDHW ? 4 : 3];
    }
};

void getMKLDNNMemoryDescBatchNorm(const NDArray* src, const NDArray* diff_src, const NDArray* dst,
                                  dnnl::memory::desc* batchnorm_src_md, dnnl::memory::desc* batchnorm_diff_src_md, dnnl::memory::desc* batchnorm_dst_md,
                                  dnnl::memory::desc* user_src_md, dnnl::memory::desc* user_diff_src_md, dnnl::memory::desc* user_dst_md, int axis) {
    const Nd4jLong* shape = src->shapeInfo();
    Nd4jLong rank = shape[0];
    Nd4jLong dim1 = axis; // MKL-DNN supports only 1 axis, which has to be the "channel" one
    Nd4jLong dim2 = axis >= 2 ? 1 : 2;
    Nd4jLong dim3 = axis >= 3 ? 2 : 3;
    dnnl::memory::dims batchnorm_src_tz = { (int)shape[1], (int)shape[dim1 + 1], rank > 2 ? (int)shape[dim2 + 1] : 1, rank > 3 ? (int)shape[dim3 + 1] : 1};

    auto type = dnnl::memory::data_type::f32;
    auto format = dnnl::memory::format_tag::nchw;
    auto supposed_to_be_any_format = dnnl::memory::format_tag::nChw8c; // doesn't work with "any"

    if (src != nullptr && src->buffer() != nullptr && batchnorm_src_md != nullptr) {
        *batchnorm_src_md = dnnl::memory::desc({ batchnorm_src_tz }, type, supposed_to_be_any_format);
        *user_src_md = dnnl::memory::desc({ batchnorm_src_tz }, type, format);
        user_src_md->data.format_kind = dnnl_blocked; // overrides format
        user_src_md->data.format_desc.blocking.strides[0] = src->stridesOf()[0];
        user_src_md->data.format_desc.blocking.strides[1] = src->stridesOf()[dim1];
        user_src_md->data.format_desc.blocking.strides[2] = rank > 2 ? src->stridesOf()[dim2] : 1;
        user_src_md->data.format_desc.blocking.strides[3] = rank > 3 ? src->stridesOf()[dim3] : 1;
    }

    if (diff_src != nullptr && diff_src->buffer() != nullptr && batchnorm_diff_src_md != nullptr) {
        *batchnorm_diff_src_md = dnnl::memory::desc({ batchnorm_src_tz }, type, supposed_to_be_any_format);
        *user_diff_src_md = dnnl::memory::desc({ batchnorm_src_tz }, type, format);
        user_diff_src_md->data.format_kind = dnnl_blocked; // overrides format
        user_diff_src_md->data.format_desc.blocking.strides[0] = diff_src->stridesOf()[0];
        user_diff_src_md->data.format_desc.blocking.strides[1] = diff_src->stridesOf()[dim1];
        user_diff_src_md->data.format_desc.blocking.strides[2] = rank > 2 ? diff_src->stridesOf()[dim2] : 1;
        user_diff_src_md->data.format_desc.blocking.strides[3] = rank > 3 ? diff_src->stridesOf()[dim3] : 1;
    }

    if (dst != nullptr && dst->buffer() != nullptr && batchnorm_dst_md != nullptr) {
        *batchnorm_dst_md = dnnl::memory::desc({ batchnorm_src_tz }, type, supposed_to_be_any_format);
        *user_dst_md = dnnl::memory::desc({ batchnorm_src_tz }, type, format);
        user_dst_md->data.format_kind = dnnl_blocked; // overrides format
        user_dst_md->data.format_desc.blocking.strides[0] = dst->stridesOf()[0];
        user_dst_md->data.format_desc.blocking.strides[1] = dst->stridesOf()[dim1];
        user_dst_md->data.format_desc.blocking.strides[2] = rank > 2 ? dst->stridesOf()[dim2] : 1;
        user_dst_md->data.format_desc.blocking.strides[3] = rank > 3 ? dst->stridesOf()[dim3] : 1;
    }
};
*/

}
}