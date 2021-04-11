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
package org.nd4j.samediff.frameworkimport.rule.attribute

import org.nd4j.ir.OpNamespace
import org.nd4j.samediff.frameworkimport.ArgDescriptor
import org.nd4j.samediff.frameworkimport.context.MappingContext
import org.nd4j.samediff.frameworkimport.lookupIndexForArgDescriptor
import org.nd4j.shade.protobuf.GeneratedMessageV3
import org.nd4j.shade.protobuf.ProtocolMessageEnum

/**
 * Need to implement tensor size extraction value at index
 */


abstract class NDArraySizeAtRule<
        GRAPH_DEF: GeneratedMessageV3,
        OP_DEF_TYPE: GeneratedMessageV3,
        NODE_TYPE: GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE>(mappingNamesToPerform: Map<String, String>,
                                                     transformerArgs: Map<String, List<OpNamespace.ArgDescriptor>>):
    BaseAttributeExtractionRule<GRAPH_DEF, OP_DEF_TYPE, NODE_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (name = "ndarraysizeat", mappingNamesToPerform = mappingNamesToPerform, transformerArgs = transformerArgs)
        where  DATA_TYPE: ProtocolMessageEnum {

    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.TENSOR
    }

    override fun outputsType(argDescriptorType: List<OpNamespace.ArgDescriptor.ArgType>): Boolean {
        return argDescriptorType.contains(OpNamespace.ArgDescriptor.ArgType.INT64)
    }

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF, NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, DATA_TYPE>): List<OpNamespace.ArgDescriptor> {
        val ret = ArrayList<OpNamespace.ArgDescriptor>()
        mappingNamesToPerform().forEach { (k, v) ->
            val transformArgsForAttribute = transformerArgs[k]
            //note that this finds a value for a named tensor within either the graph or the node
            //some frameworks may have a value node with a value attribute
            //others may have the actual tensor value
            val inputArr = mappingCtx.tensorInputFor(v)
            val sizeIndex = transformArgsForAttribute!![0].int64Value.toInt()
            val sizeAt = if(inputArr.shape().isEmpty()) -1 else  inputArr.shape()[sizeIndex]
            val argDescriptor = ArgDescriptor {
                name = v
                argType = OpNamespace.ArgDescriptor.ArgType.INT64
                int64Value = sizeAt
                argIndex = lookupIndexForArgDescriptor(
                    argDescriptorName = k,
                    opDescriptorName = mappingCtx.nd4jOpName(),
                    argDescriptorType = OpNamespace.ArgDescriptor.ArgType.INT64
                )
            }
            ret.add(argDescriptor)
        }

        return ret
    }
}