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

class UIStaticInfoRecord(object):
    __slots__ = ['_tab']

    @classmethod
    def GetRootAsUIStaticInfoRecord(cls, buf, offset):
        n = flatbuffers.encode.Get(flatbuffers.packer.uoffset, buf, offset)
        x = UIStaticInfoRecord()
        x.Init(buf, n + offset)
        return x

    # UIStaticInfoRecord
    def Init(self, buf, pos):
        self._tab = flatbuffers.table.Table(buf, pos)

    # UIStaticInfoRecord
    def InfoType(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(4))
        if o != 0:
            return self._tab.Get(flatbuffers.number_types.Int8Flags, o + self._tab.Pos)
        return 0

def UIStaticInfoRecordStart(builder): builder.StartObject(1)
def UIStaticInfoRecordAddInfoType(builder, infoType): builder.PrependInt8Slot(0, infoType, 0)
def UIStaticInfoRecordEnd(builder): return builder.EndObject()
