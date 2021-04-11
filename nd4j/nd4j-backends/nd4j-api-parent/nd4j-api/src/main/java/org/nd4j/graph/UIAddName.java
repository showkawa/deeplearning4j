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

package org.nd4j.graph;

import java.nio.*;
import java.lang.*;
import java.nio.ByteOrder;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class UIAddName extends Table {
  public static UIAddName getRootAsUIAddName(ByteBuffer _bb) { return getRootAsUIAddName(_bb, new UIAddName()); }
  public static UIAddName getRootAsUIAddName(ByteBuffer _bb, UIAddName obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; }
  public UIAddName __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public int nameIdx() { int o = __offset(4); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public String name() { int o = __offset(6); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer nameAsByteBuffer() { return __vector_as_bytebuffer(6, 1); }
  public ByteBuffer nameInByteBuffer(ByteBuffer _bb) { return __vector_in_bytebuffer(_bb, 6, 1); }

  public static int createUIAddName(FlatBufferBuilder builder,
      int nameIdx,
      int nameOffset) {
    builder.startObject(2);
    UIAddName.addName(builder, nameOffset);
    UIAddName.addNameIdx(builder, nameIdx);
    return UIAddName.endUIAddName(builder);
  }

  public static void startUIAddName(FlatBufferBuilder builder) { builder.startObject(2); }
  public static void addNameIdx(FlatBufferBuilder builder, int nameIdx) { builder.addInt(0, nameIdx, 0); }
  public static void addName(FlatBufferBuilder builder, int nameOffset) { builder.addOffset(1, nameOffset, 0); }
  public static int endUIAddName(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}

