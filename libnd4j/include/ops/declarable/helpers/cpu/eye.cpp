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


#include <ops/declarable/helpers/transforms.h>
#include <helpers/Loops.h>

namespace sd 	  {
namespace ops 	  {
namespace helpers {

//////////////////////////////////////////////////////////////////////////
void eye(sd::LaunchContext * context, NDArray& output) {

    const int rank = output.rankOf();
    auto arrs = output.allTensorsAlongDimension({rank-2, rank-1});

    auto func = PRAGMA_THREADS_FOR {
        for (auto i = start; i < stop; i++)
            arrs.at(i)->setIdentity();
    };

    samediff::Threads::parallel_tad(func, 0, arrs.size());
}

}
}
}
