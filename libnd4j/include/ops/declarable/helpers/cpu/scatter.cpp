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

#include <ops/declarable/helpers/scatter.h>
#include <numeric>
#include <helpers/ShapeUtils.h>
#include <execution/Threads.h>

namespace sd    {
namespace ops     {
namespace helpers {

///////////////////////////////////////////////////////////////////
// x - indices, z - input/output
template<typename T>
Nd4jLong checkIndices_(const NDArray& indices, const NDArray& output, const int axis) {

    std::atomic<int64_t> numOfBadIndx{0};

    const auto x = indices.bufferAsT<T>();

    const auto xShapeInfo = indices.shapeInfo();
    const auto zShapeInfo = output.shapeInfo();

    const auto xRank = indices.rankOf();

    auto func = PRAGMA_THREADS_FOR {

        int xCoords[MAX_RANK];

        for (auto i = start; i < stop; i++) {

            shape::index2coordsCPU(start, i, xShapeInfo, xCoords);

            const Nd4jLong currentInd = x[shape::getOffset(xShapeInfo, xCoords)];

            if(currentInd >= shape::sizeAt(zShapeInfo, axis == -1 ? xCoords[xRank-1] : axis)) {
                printf("checkIndices: out of range element %lld at index %ld \n", currentInd,  i);
                ++numOfBadIndx;
            }
        }
    };

    samediff::Threads::parallel_for(func, 0, indices.lengthOf());

    return numOfBadIndx;
}

///////////////////////////////////////////////////////////////////
Nd4jLong checkIndices(sd::LaunchContext *context, const NDArray& indices, const NDArray& output, const int axis) {

    BUILD_SINGLE_SELECTOR(indices.dataType(), return checkIndices_, (indices, output, axis), INDEXING_TYPES);
}

///////////////////////////////////////////////////////////////////
void scatter(sd::LaunchContext  *context, pairwise::Ops op, const NDArray& indices, const NDArray& updates, NDArray& output, const bool lock) {

    const int outRank = output.rankOf();
    const int indRank = indices.rankOf();
    const int updRank = updates.rankOf();
    const Nd4jLong indLen = indices.lengthOf();

    if(outRank == 1) {
        auto func = PRAGMA_THREADS_FOR {
            for (auto i = start; i < stop; i++) {
                Nd4jLong idx = indices.e<Nd4jLong>(i);
                NDArray out = output({idx, idx + 1});

                out.applyPairwiseTransform(op, updates.e(i));
            }
        };

        samediff::Threads::parallel_tad(func, 0, indLen, 1, lock ? 1 : sd::Environment::getInstance().maxThreads());
    }
    else {      // outRank > 1

        int sizeOfDims = indRank;
        if(outRank == updRank && indices.isVector())
            sizeOfDims = 1;

        std::vector<int> dimsToExcludeUpd(sizeOfDims);
        std::iota(dimsToExcludeUpd.begin(), dimsToExcludeUpd.end(), 0);

        auto func = PRAGMA_THREADS_FOR {
            for (auto i = start; i < stop; i++) {
                NDArray outSubArr = output(indices.e<Nd4jLong>(i), std::vector<int>({0}));
                NDArray updSubArr = updates(i, dimsToExcludeUpd);

                outSubArr.applyPairwiseTransform(op, updSubArr);
            }
        };

        samediff::Threads::parallel_tad(func, 0, indLen, 1, lock ? 1 : sd::Environment::getInstance().maxThreads());
    }
}

///////////////////////////////////////////////////////////////////
void scatterND(sd::LaunchContext  *context, pairwise::Ops op, const NDArray& indices, const NDArray& updates, NDArray& output, const bool lock) {

    const Nd4jLong indLen = indices.lengthOf();
    const int outRank = output.rankOf();
    const int indRank = indices.rankOf();
    const Nd4jLong indLastDim = indices.sizeAt(-1);

    if(outRank == 1) {
        auto func = PRAGMA_THREADS_FOR {
            for (auto i = start; i < stop; i++) {
                Nd4jLong idx = indices.e<Nd4jLong>(i);
                NDArray out = output({idx, idx + 1});

                out.applyPairwiseTransform(op, updates.e(i), nullptr);
            }
        };

        samediff::Threads::parallel_tad(func, 0, indLen, 1, lock ? 1 : sd::Environment::getInstance().maxThreads());
    }
    else {
        std::vector<int> dimsToExcludeInd = ShapeUtils::evalDimsToExclude(indRank, {indRank-1});
        std::vector<int> dimsToExcludeUpd(indRank - 1);
        std::iota(dimsToExcludeUpd.begin(), dimsToExcludeUpd.end(), 0);

        auto func = PRAGMA_THREADS_FOR {
            std::vector<Nd4jLong> idxRangeOut(2*outRank, 0);

            for (auto i = start; i < stop; i++) {
                NDArray indSubArr = indices(i, dimsToExcludeInd);

                for (Nd4jLong j = 0; j < indLastDim; ++j) {
                    idxRangeOut[2 * j] = indSubArr.e<Nd4jLong>(j);
                    idxRangeOut[2 * j + 1] = idxRangeOut[2 * j] + 1;
                }

                NDArray outSubArr = output(idxRangeOut);
                NDArray updSubArr = updates(i, dimsToExcludeUpd);

                outSubArr.applyPairwiseTransform(op, updSubArr);
            }
        };

        samediff::Threads::parallel_tad(func, 0, indLen / indLastDim, 1, lock ? 1 : sd::Environment::getInstance().maxThreads());
    }
}

void scatterForLoss(sd::LaunchContext  *context, const NDArray& indices, NDArray& updates, NDArray& output, const bool calcGrad) {

    // shapes of indices and output must be the same
    // shape of indices should be the same as updates shape with last dimension excluded
    // for example if updates is {a,b,c} then indices should be {a,b}

    const Nd4jLong indicesLen = indices.lengthOf();

    std::vector<int> dimsToExclude = ShapeUtils::evalDimsToExclude(updates.rankOf(), {-1});

    if(!calcGrad) {
        auto func = PRAGMA_THREADS_FOR {
            for (auto i = start; i < stop; i++) {
                auto subArr = updates(i, dimsToExclude);
                output.p(i, subArr.e(indices.e<Nd4jLong>(i)));
            }
        };

        samediff::Threads::parallel_for(func, 0, indicesLen);
    } else {
        auto func = PRAGMA_THREADS_FOR {
            for (auto i = start; i < stop; i++) {
                auto subArr = updates(i, dimsToExclude);
                auto ind = indices.e<Nd4jLong>(i);
                subArr.p(ind, subArr.e(ind) - 1.);
            }
        };

        samediff::Threads::parallel_for(func, 0, indicesLen);
    }
}

}
}
}
