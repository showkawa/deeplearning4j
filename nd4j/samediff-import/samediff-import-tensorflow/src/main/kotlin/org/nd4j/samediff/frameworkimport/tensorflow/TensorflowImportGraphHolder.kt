/*
 *  ******************************************************************************
 *  *
 *  *
 *  * This program and the accompanying materials are made available under the
 *  * terms of the Apache License, Version 2.0 which is available at
 *  * https://www.apache.org/licenses/LICENSE-2.0.
 *  *
 *  *  See the NOTICE file distributed with this work for additional
 *  *  information regarding copyright ownership.
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations
 *  * under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *  *****************************************************************************
 */
package org.nd4j.samediff.frameworkimport.tensorflow

import org.nd4j.samediff.frameworkimport.ImportGraph
import org.nd4j.samediff.frameworkimport.ImportGraphHolder
import org.nd4j.shade.protobuf.GeneratedMessageV3
import org.nd4j.shade.protobuf.ProtocolMessageEnum

class TensorflowImportGraphHolder: ImportGraphHolder {
    override fun frameworkName(): String {
        return "tensorflow"
    }

    override fun <GRAPH_TYPE : GeneratedMessageV3, NODE_TYPE : GeneratedMessageV3, OP_DEF_TYPE : GeneratedMessageV3, TENSOR_TYPE : GeneratedMessageV3, ATTR_DEF_TYPE : GeneratedMessageV3, ATTR_VALUE_TYPE : GeneratedMessageV3, DATA_TYPE : ProtocolMessageEnum> createImportGraph(
    ): ImportGraph<GRAPH_TYPE, NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF_TYPE, ATTR_VALUE_TYPE, DATA_TYPE> {
        return TensorflowImportGraph() as  ImportGraph<GRAPH_TYPE, NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF_TYPE, ATTR_VALUE_TYPE, DATA_TYPE>
    }
}