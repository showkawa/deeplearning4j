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

class UpdaterState(object):
    __slots__ = ['_tab']

    @classmethod
    def GetRootAsUpdaterState(cls, buf, offset):
        n = flatbuffers.encode.Get(flatbuffers.packer.uoffset, buf, offset)
        x = UpdaterState()
        x.Init(buf, n + offset)
        return x

    # UpdaterState
    def Init(self, buf, pos):
        self._tab = flatbuffers.table.Table(buf, pos)

    # UpdaterState
    def ParamName(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(4))
        if o != 0:
            return self._tab.String(o + self._tab.Pos)
        return None

    # UpdaterState
    def UpdaterStateKeys(self, j):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(6))
        if o != 0:
            a = self._tab.Vector(o)
            return self._tab.String(a + flatbuffers.number_types.UOffsetTFlags.py_type(j * 4))
        return ""

    # UpdaterState
    def UpdaterStateKeysLength(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(6))
        if o != 0:
            return self._tab.VectorLen(o)
        return 0

    # UpdaterState
    def UpdaterStateValues(self, j):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(8))
        if o != 0:
            x = self._tab.Vector(o)
            x += flatbuffers.number_types.UOffsetTFlags.py_type(j) * 4
            x = self._tab.Indirect(x)
            from .FlatArray import FlatArray
            obj = FlatArray()
            obj.Init(self._tab.Bytes, x)
            return obj
        return None

    # UpdaterState
    def UpdaterStateValuesLength(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(8))
        if o != 0:
            return self._tab.VectorLen(o)
        return 0

def UpdaterStateStart(builder): builder.StartObject(3)
def UpdaterStateAddParamName(builder, paramName): builder.PrependUOffsetTRelativeSlot(0, flatbuffers.number_types.UOffsetTFlags.py_type(paramName), 0)
def UpdaterStateAddUpdaterStateKeys(builder, updaterStateKeys): builder.PrependUOffsetTRelativeSlot(1, flatbuffers.number_types.UOffsetTFlags.py_type(updaterStateKeys), 0)
def UpdaterStateStartUpdaterStateKeysVector(builder, numElems): return builder.StartVector(4, numElems, 4)
def UpdaterStateAddUpdaterStateValues(builder, updaterStateValues): builder.PrependUOffsetTRelativeSlot(2, flatbuffers.number_types.UOffsetTFlags.py_type(updaterStateValues), 0)
def UpdaterStateStartUpdaterStateValuesVector(builder, numElems): return builder.StartVector(4, numElems, 4)
def UpdaterStateEnd(builder): return builder.EndObject()
