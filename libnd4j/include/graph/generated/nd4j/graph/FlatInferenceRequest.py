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

import flatbuffers

class FlatInferenceRequest(object):
    __slots__ = ['_tab']

    @classmethod
    def GetRootAsFlatInferenceRequest(cls, buf, offset):
        n = flatbuffers.encode.Get(flatbuffers.packer.uoffset, buf, offset)
        x = FlatInferenceRequest()
        x.Init(buf, n + offset)
        return x

    # FlatInferenceRequest
    def Init(self, buf, pos):
        self._tab = flatbuffers.table.Table(buf, pos)

    # FlatInferenceRequest
    def Id(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(4))
        if o != 0:
            return self._tab.Get(flatbuffers.number_types.Int64Flags, o + self._tab.Pos)
        return 0

    # FlatInferenceRequest
    def Variables(self, j):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(6))
        if o != 0:
            x = self._tab.Vector(o)
            x += flatbuffers.number_types.UOffsetTFlags.py_type(j) * 4
            x = self._tab.Indirect(x)
            from .FlatVariable import FlatVariable
            obj = FlatVariable()
            obj.Init(self._tab.Bytes, x)
            return obj
        return None

    # FlatInferenceRequest
    def VariablesLength(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(6))
        if o != 0:
            return self._tab.VectorLen(o)
        return 0

    # FlatInferenceRequest
    def Configuration(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(8))
        if o != 0:
            x = self._tab.Indirect(o + self._tab.Pos)
            from .FlatConfiguration import FlatConfiguration
            obj = FlatConfiguration()
            obj.Init(self._tab.Bytes, x)
            return obj
        return None

def FlatInferenceRequestStart(builder): builder.StartObject(3)
def FlatInferenceRequestAddId(builder, id): builder.PrependInt64Slot(0, id, 0)
def FlatInferenceRequestAddVariables(builder, variables): builder.PrependUOffsetTRelativeSlot(1, flatbuffers.number_types.UOffsetTFlags.py_type(variables), 0)
def FlatInferenceRequestStartVariablesVector(builder, numElems): return builder.StartVector(4, numElems, 4)
def FlatInferenceRequestAddConfiguration(builder, configuration): builder.PrependUOffsetTRelativeSlot(2, flatbuffers.number_types.UOffsetTFlags.py_type(configuration), 0)
def FlatInferenceRequestEnd(builder): return builder.EndObject()
