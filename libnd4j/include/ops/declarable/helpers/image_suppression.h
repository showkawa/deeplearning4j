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
//  @author sgazeos@gmail.com
//
#ifndef __IMAGE_SUPPRESSION_H_HELPERS__
#define __IMAGE_SUPPRESSION_H_HELPERS__
#include <system/op_boilerplate.h>
#include <array/NDArray.h>

namespace sd {
namespace ops {
namespace helpers {

    void nonMaxSuppression(sd::LaunchContext * context, NDArray* boxes, NDArray* scales, int maxSize,
            double overlapThreshold, double scoreThreshold, NDArray* output);
    Nd4jLong nonMaxSuppressionV3(sd::LaunchContext * context, NDArray* boxes, NDArray* scales, int maxSize,
                           double overlapThreshold, double scoreThreshold, NDArray* output);
    Nd4jLong nonMaxSuppressionGeneric(sd::LaunchContext* context, NDArray* boxes, NDArray* scores, int maxSize,
                             double overlapThreshold, double scoreThreshold, NDArray* output);

}
}
}
#endif
