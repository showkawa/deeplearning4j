/*
 *  ******************************************************************************
 *  *
 *  *
 *  * This program and the accompanying materials are made available under the
 *  * terms of the Apache License, Version 2.0 which is available at
 *  * https://www.apache.org/licenses/LICENSE-2.0.
 *  *
 *  * See the NOTICE file distributed with this work for additional
 *  * information regarding copyright ownership.
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations
 *  * under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *  *****************************************************************************
 */

 //
 //  @author Oleh Semeniv (oleg.semeniv@gmail.com)
 //

#include <ops/declarable/helpers/transforms.h>
#include <helpers/Loops.h>

namespace sd {
namespace ops {
namespace helpers {


            //////////////////////////////////////////////////////////////////////////
            template <typename T>
            static void split_(const NDArray& input, const std::vector<NDArray*>& outArrs, const int axis) {
                uint numSplits = outArrs.size();

                const auto sizeofT = input.sizeOfT();

                auto xBuff = input.bufferAsT<T>();

                bool luckCase1 = ((axis == 0 && input.ordering() == 'c') || (axis == input.rankOf() - 1 && input.ordering() == 'f')) && input.ews() == 1;

                if (luckCase1) {
                    for (uint i = 0; i < numSplits; ++i) {
                        luckCase1 &= outArrs[i]->ordering() == input.ordering() && outArrs[i]->ews() == 1;
                        if (!luckCase1)
                            break;
                    }
                }

                if (luckCase1) {

                    T* x = const_cast<T*>(xBuff);
                    for (uint i = 0; i < numSplits; ++i) {
                        const auto memAmountToCopy = outArrs[i]->lengthOf();
                        memcpy(outArrs[i]->bufferAsT<T>(), x, memAmountToCopy * sizeofT);
                        x += memAmountToCopy;
                    }
                    return;
                }

                const bool isXcontin = input.strideAt(axis) == 1 && input.ordering() == 'c';
                bool areOutsContin = true;
                bool allSameOrder = true;

                if (isXcontin) {
                    for (uint i = 0; i < numSplits; ++i) {
                        areOutsContin &= outArrs[i]->strideAt(axis) == 1;
                        allSameOrder &= outArrs[i]->ordering() == input.ordering();
                        if (!areOutsContin || !allSameOrder)
                            break;
                    }
                }

                const bool luckCase2 = isXcontin && areOutsContin && allSameOrder;

                if (luckCase2) {

                    const auto xDim = input.sizeAt(axis);

                    for (Nd4jLong i = 0; i < input.lengthOf() / xDim; ++i) {

                        auto x = xBuff + xDim * i;

                        for (uint j = 0; j < numSplits; ++j) {
                            const auto zDim = outArrs[j]->sizeAt(axis);
                            T* z = outArrs[j]->bufferAsT<T>() + zDim * i;
                            memcpy(z, x, zDim * sizeofT);
                            z += zDim;
                            x += zDim;
                        }
                    }

                    return;
                }

                uint zDim = outArrs[0]->sizeAt(axis);
                // general case

                auto func = PRAGMA_THREADS_FOR{

                    int coords[MAX_RANK], temp;

                    for (auto i = start; i < stop; i += increment) {

                        shape::index2coordsCPU(start, i, input.shapeInfo(), coords);
                        const auto xOffset = shape::getOffset(input.shapeInfo(), coords);

                        uint outArrIdx = 0;

                        temp = coords[axis];

                        while (coords[axis] >= zDim) {
                            coords[axis] -= zDim;
                            ++outArrIdx;
                        }

                        T* z = outArrs[outArrIdx]->bufferAsT<T>();
                        const auto zOffset = shape::getOffset(outArrs[outArrIdx]->shapeInfo(), coords);
                        z[zOffset] = xBuff[xOffset];

                        coords[axis] = temp;
                    }
                };

                samediff::Threads::parallel_for(func, 0, input.lengthOf());
            }

            void split(sd::LaunchContext* context, const NDArray& input, std::vector<NDArray*>& outArrs, const int axis) {
                BUILD_SINGLE_SELECTOR(input.dataType(), split_, (input, outArrs, axis), LIBND4J_TYPES);
            }
      }
    }
}
