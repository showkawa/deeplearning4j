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
//  @author GS <sgazeos@gmail.com>
//

#include <ops/declarable/helpers/confusion.h>
#include <execution/Threads.h>


namespace sd {
namespace ops {
namespace helpers {

    template <typename T>
    void _confusionFunctor(NDArray* labels, NDArray* predictions, NDArray* weights, NDArray* output) {
        ResultSet arrs = output->allTensorsAlongDimension({1});
        int lLen = labels->lengthOf();

        auto func = PRAGMA_THREADS_FOR {
            for (int j = start; j < stop; j++) {
                auto label = labels->e<Nd4jLong>(j);
                auto pred = predictions->e<Nd4jLong>(j);
                T value = (weights == nullptr ? (T) 1.0f : weights->e<T>(j));
                arrs.at(label)->p<T>(pred, value);
            }
        };

        samediff::Threads::parallel_for(func, 0, lLen);
    }

    void confusionFunctor(sd::LaunchContext * context, NDArray* labels, NDArray* predictions, NDArray* weights, NDArray* output) {
        auto xType = output->dataType(); // weights can be null

        BUILD_SINGLE_SELECTOR(xType, _confusionFunctor, (labels, predictions, weights, output), NUMERIC_TYPES);
    }

    BUILD_SINGLE_TEMPLATE(template void _confusionFunctor, (NDArray* labels, NDArray* predictions, NDArray* weights, NDArray* output);, NUMERIC_TYPES);

}
}
}