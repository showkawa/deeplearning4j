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

#include <ops/declarable/helpers/unique.h>
#include <graph/Status.h>
#include <execution/Threads.h>
#include <graph/Variable.h>

namespace sd {
namespace ops {
namespace helpers {

    template <typename T>
    static Nd4jLong uniqueCount_(NDArray* input) {
        Nd4jLong count = 0;

        std::vector<T> values;

        for (Nd4jLong e = 0; e < input->lengthOf(); e++) {
            T v = input->e<T>(e);
            if (std::find(values.begin(), values.end(), v) == values.end()) {
                values.push_back(v);
                count++;
            }
        }
        return count;
    }

    Nd4jLong uniqueCount(sd::LaunchContext * context, NDArray* input) {
        BUILD_SINGLE_SELECTOR(input->dataType(), return uniqueCount_, (input), LIBND4J_TYPES);
    }

    BUILD_SINGLE_TEMPLATE(template Nd4jLong uniqueCount_, (NDArray* input), LIBND4J_TYPES);

    template <typename T>
    static Nd4jStatus uniqueFunctor_(NDArray* input, NDArray* values, NDArray* indices, NDArray* counts) {
    
        std::vector<T> valuesVector;
        MAP_IMPL<T, int> indicesMap;
        MAP_IMPL<T, int> countsMap;

        for (Nd4jLong e = 0; e < input->lengthOf(); e++) {
            T v = input->e<T>(e);
            if (std::find(valuesVector.begin(), valuesVector.end(), v) == valuesVector.end()) {
                valuesVector.push_back(v);
                indicesMap[v] = e;
                countsMap[v] = 1;
            }
            else {
                countsMap[v]++;
            }
        }

        auto func = PRAGMA_THREADS_FOR {
            for (auto e = start; e < stop; e++) {
                values->p(e, static_cast<T>(valuesVector[e]));
                if (counts != nullptr)
                    counts->p(e, countsMap[valuesVector[e]]);
            }
        };
        samediff::Threads::parallel_for(func, 0, values->lengthOf());

        for (Nd4jLong e = 0; e < indices->lengthOf(); e++) {
            auto posI = std::find(valuesVector.begin(), valuesVector.end(), input->e<T>(e));
            auto dist = std::distance(valuesVector.begin(), posI);
            indices->p(e, Nd4jLong(dist));//indicesMap[(*input)(e)];
        }

        return Status::OK();
    }

    Nd4jStatus uniqueFunctor(sd::LaunchContext * context, NDArray* input, NDArray* values, NDArray* indices, NDArray* counts) {
        input->syncToHost();
        values->syncToHost();
        indices->syncToHost();

        if (counts != nullptr)
            counts->syncToHost();

        BUILD_SINGLE_SELECTOR(input->dataType(), return uniqueFunctor_,(input, values, indices, counts), LIBND4J_TYPES);

        input->syncToDevice();
        values->syncToDevice();
        indices->syncToDevice();

        if (counts != nullptr)
            counts->syncToDevice();
    }

    BUILD_SINGLE_TEMPLATE(template Nd4jStatus uniqueFunctor_, (NDArray* input, NDArray* values, NDArray* indices, NDArray* counts), LIBND4J_TYPES);
}
}
}