#  /* ******************************************************************************
#   *
#   *
#   * This program and the accompanying materials are made available under the
#   * terms of the Apache License, Version 2.0 which is available at
#   * https://www.apache.org/licenses/LICENSE-2.0.
#   *
#   *  See the NOTICE file distributed with this work for additional
#   *  information regarding copyright ownership.
#   * Unless required by applicable law or agreed to in writing, software
#   * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
#   * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
#   * License for the specific language governing permissions and limitations
#   * under the License.
#   *
#   * SPDX-License-Identifier: Apache-2.0
#   ******************************************************************************/

class OpType(object):
    TRANSFORM_FLOAT = 0
    TRANSFORM_SAME = 1
    TRANSFORM_BOOL = 2
    TRANSFORM_STRICT = 3
    TRANSFORM_ANY = 4
    REDUCE_FLOAT = 5
    REDUCE_SAME = 6
    REDUCE_LONG = 7
    REDUCE_BOOL = 8
    INDEX_REDUCE = 9
    SCALAR = 10
    SCALAR_BOOL = 11
    BROADCAST = 12
    BROADCAST_BOOL = 13
    PAIRWISE = 14
    PAIRWISE_BOOL = 15
    REDUCE_3 = 16
    SUMMARYSTATS = 17
    SHAPE = 18
    AGGREGATION = 19
    RANDOM = 20
    CUSTOM = 21
    GRAPH = 22
    VARIABLE = 40
    BOOLEAN = 60
    LOGIC = 119

