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
// Based on PyTorch - https://github.com/pytorch/pytorch
//

#ifndef LIBND4J_CONVOLUTIONS_H
#define LIBND4J_CONVOLUTIONS_H

#include <array/NDArray.h>
#include <graph/Context.h>
#include <system/dll.h>

#include <execution/LaunchContext.h>

namespace sd {
    namespace ops {

        enum PoolingType {
            MAX_POOL = 0,
            AVG_POOL = 1,
            PNORM_POOL = 2,
        };

        class ND4J_EXPORT ConvolutionUtils {
        public:
            static inline void calcOutSizePool2D(int& oH, int& oW, const int kH, const int kW, const int sH, const int sW, const int pH, const int pW, const int dH, const int dW, const int iH, const int iW, const int paddingMode) {

                if(paddingMode == 0) {             // valid
                    // oH = (iH - (kH + (kH-1)*(dH-1)) + 2*pH)/sH + 1;
                    // oW = (iW - (kW + (kW-1)*(dW-1)) + 2*pW)/sW + 1;
                    oH = (iH - ((kH - 1) * dH + 1) + 2 * pH) / sH + 1;
                    oW = (iW - ((kW - 1) * dW + 1) + 2 * pW) / sW + 1;
                }
                else if (paddingMode == 1) {       // same
                    oH = (int) math::nd4j_ceil<double, double>(iH * 1. / sH);
                    oW = (int) math::nd4j_ceil<double, double>(iW * 1. / sW);
                }
                else {                      // causal
                    oH = (iH - 1) / sH + 1;     // 2*pH = (kH-1)*dH
                    oW = (iW - 1) / sW + 1;
                }
            }

            static inline void calcOutSizePool3D(int& oD, int& oH, int& oW, const int kD, const int kH, const int kW, const int sD, const int sH, const int sW, const int pD, const int pH, const int pW, const int dD, const int dH, const int dW, const int iD, const int iH, const int iW, const int paddingMode) {

                if(paddingMode == 0) {             // valid
                    oD = (iD - ((kD - 1) * dD + 1) + 2 * pD) / sD + 1;
                    oH = (iH - ((kH - 1) * dH + 1) + 2 * pH) / sH + 1;
                    oW = (iW - ((kW - 1) * dW + 1) + 2 * pW) / sW + 1;
                }
                else if(paddingMode == 1) {        // same
                    oD = (int) sd::math::nd4j_ceil<double, double>(iD * 1. / sD);
                    oH = (int) sd::math::nd4j_ceil<double, double>(iH * 1. / sH);
                    oW = (int) sd::math::nd4j_ceil<double, double>(iW * 1. / sW);

                }
                else {                      // causal
                    oD = (iD - 1) / sD + 1;
                    oH = (iH - 1) / sH + 1;     // 2*pH = (kH-1)*dH
                    oW = (iW - 1) / sW + 1;
                }
            }

            static inline void calcPadding2D(int& pH, int& pW, int oH, int oW, int iH, int iW, int kH, int kW, int sH, int sW, int dH, int dW, const int paddingMode = 1 /* default is same mode*/) {

                if(paddingMode == 0)        // valid
                    return;

                if(paddingMode == 1) {      // same

                    const int eKH = (kH - 1) * dH + 1;
                    const int eKW = (kW - 1) * dW + 1;

                    pH = ((oH - 1) * sH + eKH - iH) / 2; //Note that padBottom is 1 bigger than this if bracketed term is not divisible by 2
                    pW = ((oW - 1) * sW + eKW - iW) / 2;
                }
                else {                      // causal
                    pH = (kH - 1) * dH;
                    pW = (kW - 1) * dW;
                }
            }

            static inline void calcPadding3D(int& pD, int& pH, int& pW, const int oD, const int oH, const int oW, const int iD, const int iH, const int iW, const int kD, const int kH, const int kW, const int sD, const int sH, const int sW, const int dD, const int dH, const int dW, const int paddingMode = 1 /* default is same mode*/) {

                if(paddingMode == 0)        // valid
                    return;

                if(paddingMode == 1) {      // same

                    const int eKD = (kD - 1) * dD + 1;
                    const int eKH = (kH - 1) * dH + 1;
                    const int eKW = (kW - 1) * dW + 1;

                    pD = ((oD - 1) * sD + eKD - iD) / 2;
                    pH = ((oH - 1) * sH + eKH - iH) / 2; //Note that padBottom is 1 bigger than this if bracketed term is not divisible by 2
                    pW = ((oW - 1) * sW + eKW - iW) / 2;
                }
                else {                      // causal
                    pD = (kD - 1) * dD;
                    pH = (kH - 1) * dH;
                    pW = (kW - 1) * dW;
                }
            }

            // calculation of output height and width in 2D deconvolution procedure
            static inline void calcOutSizeDeconv2D(int& oH, int& oW, const int kH, const int kW, const int sH, const int sW, const int pH, const int pW, const int dH, const int dW, const int iH, const int iW, const int paddingMode) {

                if (paddingMode) {
                    oH = sH * iH;
                    oW = sW * iW;
                }
                else {
                    const int ekH = (kH - 1) * dH + 1;
                    const int ekW = (kW - 1) * dW + 1;

                    oH = sH * (iH - 1) + ekH - 2 * pH;
                    oW = sW * (iW - 1) + ekW - 2 * pW;
                }
            }

            // calculation of output height and width in 3D deconvolution procedure
            static inline void calcOutSizeDeconv3D(int& oD, int& oH, int& oW, const int kD, const int kH, const int kW, const int sD, const int sH, const int sW, const int pD, const int pH, const int pW, const int dD, const int dH, const int dW, const int iD, const int iH, const int iW, const int paddingMode) {

                if (paddingMode) {
                    oD = sD * iD;
                    oH = sH * iH;
                    oW = sW * iW;
                }
                else {

                    const int ekD = (kD - 1) * dD + 1;
                    const int ekH = (kH - 1) * dH + 1;
                    const int ekW = (kW - 1) * dW + 1;

                    oD = sD * (iD - 1) + ekD - 2 * pD;
                    oH = sH * (iH - 1) + ekH - 2 * pH;
                    oW = sW * (iW - 1) + ekW - 2 * pW;
                }
            }

            // evaluates sizes values and indexes using input and output arrays depending on data format
            static inline void getSizesAndIndexesConv2d(const bool isNCHW, const int wFormat, const NDArray& input, const NDArray& output, int& bS, int& iC, int& iH, int& iW, int& oC, int& oH, int& oW, int& indIOioC, int& indIiH, int& indWiC, int& indWoC, int& indWkH, int& indOoH) {
                getSizesAndIndexesConv2d(isNCHW, wFormat, input.shapeInfo(), output.shapeInfo(), bS, iC, iH, iW, oC, oH, oW, indIOioC, indIiH, indWiC, indWoC, indWkH, indOoH);
            }

            static inline void getSizesAndIndexesConv2d(const bool isNCHW, const int wFormat, const Nd4jLong* inShapeInfo, const Nd4jLong* outShapeInfo, int& bS, int& iC, int& iH, int& iW, int& oC, int& oH, int& oW, int& indIOioC, int& indIiH, int& indWiC, int& indWoC, int& indWkH, int& indOoH) {
                // input   [bS, iH, iW, iC] (NHWC) or [bS, iC, iH, iW] (NCHW)
                // weights [kH, kW, iC, oC] (wFormat = 0), [oC, iC, kH, kW] (wFormat = 1), [oC, kH, kW, iC] (wFormat = 2)
                // output  [bS, oH, oW, oC] (NHWC) or [bS, oC, oH, oW] (NCHW)

                if(0 == wFormat) {
                    indWkH = 0; indWiC = 2; indWoC = 3;
                }
                else if(1 == wFormat) {
                    indWkH = 2; indWiC = 1; indWoC = 0;
                }
                else {
                    indWkH = 1; indWiC = 3; indWoC = 0;
                }

                if(!isNCHW) {
                    indIOioC = 3; indIiH = 1; indOoH = 1;
                }
                else {
                    indIOioC = 1; indIiH = 2; indOoH = 2;
                }

                bS = inShapeInfo[1];                          // batch size
                iC = inShapeInfo[indIOioC+1];                 // input channels
                iH = inShapeInfo[indIiH+1];                   // input height
                iW = inShapeInfo[indIiH+2];                   // input width
                oC = outShapeInfo[indIOioC+1];                // output channels
                oH = outShapeInfo[indOoH+1];                  // output height
                oW = outShapeInfo[indOoH+2];                  // output width
            }

            // evaluates sizes values and indexes using input and output arrays depending on data format
            static inline void getSizesAndIndexesConv3d(const bool isNCDHW, const int wFormat, const NDArray& input, const NDArray& output, int& bS, int& iC, int& iD, int& iH, int& iW, int& oC, int& oD, int& oH, int& oW, int& indIOioC, int& indIOioD, int& indWiC, int& indWoC, int& indWkD) {
                // input   [bS, iD, iH, iW, iC] (NDHWC) or [bS, iC, iD, iH, iW] (NCDHW)
                // weights [kD, kH, kW, iC, oC] (wFormat = 0), [oC, iC, kD, kH, kW] (wFormat = 1), [oC, kD, kH, kW, iC] (wFormat = 2)
                // output  [bS, oD, oH, oW, oC] (NDHWC) or [bS, oC, oD, oH, oW] (NCDHW)

                if(0 == wFormat) {
                    indWkD = 0; indWiC = 3; indWoC = 4;
                }
                else if(1 == wFormat) {
                    indWkD = 2; indWiC = 1; indWoC = 0;
                }
                else {
                    indWkD = 1; indWiC = 4; indWoC = 0;
                }

                if(!isNCDHW) {
                    indIOioC = 4; indIOioD = 1;
                }
                else {
                    indIOioC = 1; indIOioD = 2;
                }

                bS = input.sizeAt(0);                          // batch size
                iC = input.sizeAt(indIOioC);                   // input channels
                iD = input.sizeAt(indIOioD);                   // input depth
                iH = input.sizeAt(indIOioD+1);                 // input height
                iW = input.sizeAt(indIOioD+2);                 // input width
                oC = output.sizeAt(indIOioC);                  // output channels
                oD = output.sizeAt(indIOioD);                  // output depth
                oH = output.sizeAt(indIOioD+1);                // output height
                oW = output.sizeAt(indIOioD+2);                // output width
            }

            // static inline void calcPaddingAndDilationForConv2DMKL(const int iH, const int iW, const int oH, const int oW, const int kH, const int kW, const int sH, const int sW, const int paddingMode, int& pH, int& pW, int& dH, int& dW) {

            //     if(kH != 1) {
            //         if(paddingMode) {
            //             pH = (oH - 1) * sH - iH + kH - pH;
            //             dH = dH - 1;
            //         }
            //         else
            //             dH = (iH + 2*pH - (oH - 1) * sH - kH) / (kH - 1);
            //     }
            //     if(kW != 1) {
            //         if(paddingMode) {
            //             pW = (oW - 1) * sW - iW + kW - pW;
            //             dW = dW - 1;
            //         }
            //         else
            //             dW = (iW + 2*pW - (oW - 1) * sW - kW) / (kW - 1);
            //     }
            // }

            // static inline void calcPaddingAndDilationForConv3DMKL(const int iD, const int iH, const int iW, const int oD, const int oH, const int oW, const int kD, const int kH, const int kW, const int sD, const int sH, const int sW, const int paddingMode, int& pD, int& pH, int& pW, int& dD, int& dH, int& dW) {

            //     if(kD != 1) {
            //         if(paddingMode) {
            //             pD = (oD - 1) * sD - iD + kD - pD;
            //             dD = dD - 1;
            //         }
            //         else
            //             dD = (iD + 2*pD - (oD - 1) * sD - kD) / (kD - 1);
            //     }
            //     if(kH != 1) {
            //         if(paddingMode) {
            //             pH = (oH - 1) * sH - iH + kH - pH;
            //             dH = dH - 1;
            //         }
            //         else
            //             dH = (iH + 2*pH - (oH - 1) * sH - kH) / (kH - 1);
            //     }
            //     if(kW != 1) {
            //         if(paddingMode) {
            //             pW = (oW - 1) * sW - iW + kW - pW;
            //             dW = dW - 1;
            //         }
            //         else
            //             dW = (iW + 2*pW - (oW - 1) * sW - kW) / (kW - 1);
            //     }
            // }

            static std::vector<Nd4jLong> expectWeightsShape(const int wFormat, const int kH, const int kW, const int iC, const int oC) {

                if(0 == wFormat)
                    return std::vector<Nd4jLong>({kH, kW, iC, oC});

                if(1 == wFormat)
                    return std::vector<Nd4jLong>({oC, iC, kH, kW});

                return std::vector<Nd4jLong>({oC, kH, kW, iC});
            }

            static std::vector<Nd4jLong> expectWeightsShape(const int wFormat, const int kD, const int kH, const int kW, const int iC, const int oC) {

                if(0 == wFormat)
                    return std::vector<Nd4jLong>({kD, kH, kW, iC, oC});

                if(1 == wFormat)
                    return std::vector<Nd4jLong>({oC, iC, kD, kH, kW});

                return std::vector<Nd4jLong>({oC, kD, kH, kW, iC});
            }

            static void conv2d(sd::graph::Context  &context, const NDArray* input, const NDArray* weights, const NDArray* bias, NDArray* output, const int kH, const int kW, const int sH, const int sW, int pH, int pW, const int dH, const int dW, const int paddingMode, const int isNCHW, const int wFormat);

            // static void conv2d(sd::graph::Context & block, const std::vector<NDArray*>& inArrs, NDArray* output, const std::vector<int>& intArgs);

            // static void conv2dBP(sd::graph::Context & block, const std::vector<NDArray*>& inArrs, const std::vector<NDArray*>& outArrs, const std::vector<int>& intArgs);

            static void conv2dBP(sd::graph::Context & block, const NDArray* input, const NDArray* weights, const NDArray* bias, const NDArray* gradO, NDArray* gradI, NDArray* gradW, NDArray* gradB, const int kH, const int kW, const int sH, const int sW, int pH, int pW, const int dH, const int dW, const int paddingMode, const int isNCHW, const int wFormat);

            static void depthwiseConv2d(sd::graph::Context & block, const NDArray* input, const NDArray* weights, const NDArray* bias, NDArray* output, const int kH, const int kW, const int sH, const int sW, int pH, int pW, const int dH, const int dW, const int paddingMode, const int isNCHW, const int wFormat);

            static void depthwiseConv2dBP(sd::graph::Context & block, const NDArray* input, const NDArray* weights, const NDArray* bias, const NDArray* gradO, NDArray* gradI, NDArray* gradW, NDArray* gradB, const int kH, const int kW, const int sH, const int sW, int pH, int pW, const int dH, const int dW, const int paddingMode, const int isNCHW, const int wFormat);

            static void sconv2d(sd::graph::Context & block, const NDArray* input, const NDArray* weightsDepth, const NDArray* weightsPoint, const NDArray* bias,  NDArray* output, const int kH, const int kW, const int sH, const int sW, int pH, int pW, const int dH, const int dW, const int paddingMode, const int isNCHW, const int wFormat);

            static void vol2col(sd::graph::Context & block, const NDArray& vol, NDArray& col, const int sD, const int sH, const int sW, const int pD, const int pH, const int pW, const int dD, const int dH, const int dW);

            static void col2vol(sd::graph::Context & block, const NDArray& col, NDArray& vol, const int sD, const int sH, const int sW, const int pD, const int pH, const int pW, const int dD, const int dH, const int dW);

            static void upsampling2d(sd::graph::Context & block, const NDArray& input, NDArray& output, const int factorH, const int factorW, const bool isNCHW);

            static void upsampling3d(sd::graph::Context & block, const NDArray& input, NDArray& output, const int factorD, const int factorH, const int factorW, const bool isNCDHW);

            static void upsampling2dBP(sd::graph::Context & block, const NDArray& gradO, NDArray& gradI, const bool isNCHW);

            static void upsampling3dBP(sd::graph::Context & block, const NDArray& gradO, NDArray& gradI, const bool isNCDHW);

            static void pooling2d(sd::graph::Context & block, const NDArray& input, NDArray& output, const int kH, const int kW, const int sH, const int sW, const int pH, const int pW, const int dH, const int dW, const PoolingType poolingMode, const int extraParam0);

            static void pooling3d(sd::graph::Context & block, const NDArray& input, NDArray& output, const int kD, const int kH, const int kW, const int sD, const int sH, const int sW, const int pD, const int pH, const int pW, const int dD, const int dH, const int dW, const int poolingMode, const int extraParam0);

            static void pooling2dBP(sd::graph::Context & block, const NDArray& input, const NDArray& gradO, NDArray& gradI, const int kH, const int kW, const int sH, const int sW, const int pH, const int pW, const int dH, const int dW, const int poolingMode, const int extraParam0);

            static void pooling3dBP(sd::graph::Context & block, const NDArray& input, const NDArray& gradO, NDArray& gradI, const int kD, const int kH, const int kW, const int sD, const int sH, const int sW, const int pD, const int pH, const int pW, const int dD, const int dH, const int dW, const int poolingMode, const int extraParam0);
    };

}
}
#endif //LIBND4J_CONVOLUTIONS_H
