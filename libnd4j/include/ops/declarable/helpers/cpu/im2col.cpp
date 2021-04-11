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
// @author Yurii Shyrma (iuriish@yahoo.com), created on 19.09.2018
//

#include <ops/declarable/helpers/im2col.h>
#include <execution/Threads.h>


namespace sd    {
namespace ops     {
namespace helpers {

//////////////////////////////////////////////////////////////////////////
template <typename T>
static void im2col_(sd::LaunchContext & context, const NDArray& input,  NDArray& output, const int kH, const int kW, const int sH, const int sW, const int pH, const int pW, const int dH, const int dW, const NDArray& arrZeroPadVal) {

    // input [bS, iC, iH, iW] is convoluted to output [bS, iC, kH, kW, oH, oW]

	auto imBuff         = static_cast<T const*>(input.buffer());
	auto colBuff        = static_cast<T*>(output.buffer());
	auto imShapeBuffer  = input.shapeInfo();
	auto colShapeBuffer = output.shapeInfo();
    auto colShape       = shape::shapeOf(colShapeBuffer);
    auto colStride      = shape::stride(colShapeBuffer);
    auto imShape        = shape::shapeOf(imShapeBuffer);
    auto imStride       = shape::stride(imShapeBuffer);

    const T zeroPadVal =  arrZeroPadVal.e<T>(0);

    const int bS = imShape[0];
    const int iC = imShape[1];
    const int iH = imShape[2];
    const int iW = imShape[3];
    const int oH = colShape[4];
    const int oW = colShape[5];
    const Nd4jLong colStride0 = colStride[0];
    const Nd4jLong colStride1 = colStride[1];
    const Nd4jLong colStride2 = colStride[2];
    const Nd4jLong colStride3 = colStride[3];
    const Nd4jLong colStride4 = colStride[4];
    const Nd4jLong colStride5 = colStride[5];
    const Nd4jLong imStride0  = imStride[0];
    const Nd4jLong imStride1  = imStride[1];
    const Nd4jLong imStride2  = imStride[2];
    const Nd4jLong imStride3  = imStride[3];


    if (shape::order(imShapeBuffer) == 'c' &&  shape::order(colShapeBuffer) == 'c' && shape::strideDescendingCAscendingF(imShapeBuffer) && shape::strideDescendingCAscendingF(colShapeBuffer)) {

        auto func = PRAGMA_THREADS_FOR_2D {
            for (auto b = start_x; b < stop_x; b++) {
                for (auto c = start_y; c < stop_y; c++) {
                    for (int kRow = 0; kRow < kH; ++kRow) {
                        for (int kCol = 0; kCol < kW; ++kCol) {
                            for (int colH = 0; colH < oH; ++colH) {
                                for (int colW = 0; colW < oW; ++colW) {

                                    int imRow = (-pH + kRow * dH) + colH * sH;
                                    int imCol = (-pW + kCol * dW) + colW * sW;

                                    auto col = colBuff + b * colStride0 + c * colStride1 + kRow * colStride2 + kCol * colStride3 + colH * colStride4 + colW * colStride5;

                                    if (static_cast<unsigned>(imRow) >= static_cast<unsigned>(iH) || static_cast<unsigned>(imCol) >= static_cast<unsigned>(iW))
                                        *col = zeroPadVal;
                                    else {
                                        auto im = imBuff + b * imStride0 + c * imStride1 + imRow * imStride2 + imCol * imStride3;
                                        *col = *im;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        };

        samediff::Threads::parallel_for(func, 0, bS, 1, 0, iC, 1);
    }
    else {

        auto func = PRAGMA_THREADS_FOR_2D {
            T *col;
            T const* im;
            int imRow, imCol;

            for (auto b = start_x; b < stop_x; b += inc_x) {
                for (auto colH = start_y; colH < stop_y; colH += inc_y) {
                    for (int colW = 0; colW < oW; ++colW) {
                        for (int c = 0; c < iC; ++c) {
                            for (int kRow = 0; kRow < kH; ++kRow) {
                                for (int kCol = 0; kCol < kW; ++kCol) {

                                    imRow = (-pH + kRow * dH) + colH * sH;
                                    imCol = (-pW + kCol * dW) + colW * sW;

                                    col = colBuff + b * colStride0 + c * colStride1 + kRow * colStride2 + kCol * colStride3 + colH * colStride4 + colW * colStride5;

                                    if (static_cast<unsigned>(imRow) >= static_cast<unsigned>(iH) || static_cast<unsigned>(imCol) >= static_cast<unsigned>(iW))
                                        *col = zeroPadVal;
                                    else {
                                        im = imBuff + b * imStride0 + c * imStride1 + imRow * imStride2 + imCol * imStride3;
                                        *col = *im;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        };

        samediff::Threads::parallel_for(func, 0, bS, 1, 0, oH, 1);
    }
}


void im2col(sd::LaunchContext & context, const NDArray& im,  NDArray& col, const int kH, const int kW, const int sH, const int sW, const int pH, const int pW, const int dH, const int dW, const NDArray& arrZeroPadVal) {
	BUILD_SINGLE_SELECTOR(im.dataType(), im2col_, (context, im, col, kH, kW, sH, sW, pH, pW, dH, dW, arrZeroPadVal), FLOAT_TYPES);
}


}
}
}