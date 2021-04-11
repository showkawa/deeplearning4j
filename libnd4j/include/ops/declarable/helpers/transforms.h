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
// @author Yurii Shyrma (iuriish@yahoo.com), created on 20.04.2018
//

#ifndef LIBND4J_TRANSFORMS_H
#define LIBND4J_TRANSFORMS_H

#include <ops/declarable/helpers/helpers.h>
#include <helpers/helper_random.h>
#include <graph/RandomGenerator.h>
#include <graph/Context.h>
namespace sd    {
namespace ops     {
namespace helpers {

	void triuBP(sd::LaunchContext * context, const NDArray& input, const NDArray& gradO, NDArray& gradI, const int diagonal);

	void trace(sd::LaunchContext * context, const NDArray& input, NDArray& output);

	void randomShuffle(sd::LaunchContext * context, NDArray& input, NDArray& output, sd::graph::RandomGenerator& rng, const bool isInplace);

    // auxiliary function which serves for recursion purpose and is used in pad operation
	// void recursiveLoopForPad(const int mode, NDArray& input, const NDArray& paddings, NDArray& output, std::vector<int> dimensions, int dim, int inIdx, int outIdx, NDArray& padValue);

	void pad(sd::LaunchContext * context, const int mode, const NDArray& input, const NDArray& paddings, NDArray& output, NDArray const& padValue);

	void invertPermutation(sd::LaunchContext * context, const NDArray& input, NDArray& output);

	void gatherND(sd::LaunchContext * context, NDArray& input, NDArray& indices, NDArray& output);

	void gather(sd::LaunchContext * context, NDArray* input, const NDArray* indices, NDArray* output, const std::vector<int>& intArgs);

	void eye(sd::LaunchContext * context, NDArray& output);

	void scatterUpdate(sd::LaunchContext * context, NDArray& operand, NDArray& updates, const std::vector<int>* intArgs);

	void scatterSimple(sd::LaunchContext * context, const int opId, NDArray& input, const NDArray& updates, const NDArray& indices, const std::vector<int>& dimensions);

	void mergeMaxIndex(sd::LaunchContext * context, const std::vector<const NDArray*>& inArrs, NDArray& output);

	void mergeMax(sd::LaunchContext * context, const std::vector<const NDArray*>& inArrs, NDArray& output);
	void mergeMaxBp(sd::LaunchContext* context, const std::vector<const NDArray*>& inArrs, std::vector<NDArray*>& outArrs);

	void mergeAvg(sd::LaunchContext * context, const std::vector<const NDArray*>& inArrs, NDArray& output);
	void mergeAvgBp(sd::LaunchContext* context, const NDArray& gradient, std::vector<NDArray*>& outArrs);

	void mergeAdd(sd::LaunchContext * context, const std::vector<const NDArray*>& inArrs, NDArray& output);
	void mergeAddBp(sd::LaunchContext* context, const NDArray& gradient, std::vector<NDArray*>& outArrs);

	void clipByNorm(sd::LaunchContext * context, NDArray& input, NDArray& output, const std::vector<int>& dimensions, const NDArray& clipNorm, const bool isInplace, const bool useAverage);

	void clipByGlobalNorm(sd::LaunchContext * context, std::vector<NDArray*> const& inputs, double clipNorm, sd::memory::Workspace* workspace, std::vector<NDArray*>& outputs, bool isInplace);

	void clipByNormBp(sd::LaunchContext * context, const NDArray& input, const NDArray& gradO, NDArray& gradI /*output*/, const std::vector<int>& dimensions, const NDArray& clipNorm, const bool useAverage);

	void clipByAveragedNorm(sd::LaunchContext * context, NDArray& input, NDArray& output, const std::vector<int>& dimensions, const NDArray& clipNorm, const bool isInplace);

	void mirrorPad(sd::LaunchContext * context, const NDArray& input, const NDArray& paddings, NDArray& output, const int mode);

	void clipByValue(sd::LaunchContext * context, NDArray& input, double leftBound, double rightBound, NDArray& output);

	void mirrorPad(sd::LaunchContext * context, const NDArray& input, const NDArray& paddings, NDArray& output, const int mode);

	void concat(sd::LaunchContext * context, const std::vector<const NDArray*>& inArrs, NDArray& output, const int axis);

	void tileBP(sd::LaunchContext * context, const NDArray& gradO /*input*/, NDArray& gradI /*output*/, const std::vector<Nd4jLong> reps);

	void split(sd::LaunchContext* context, const NDArray& input, std::vector<NDArray*>& outArrs, const int axis);

	void compareAndBitpack(graph::Context& block, const NDArray& input, const NDArray& threshold, NDArray& output);
}
}
}


#endif //LIBND4J_TRANSFORMS_H
