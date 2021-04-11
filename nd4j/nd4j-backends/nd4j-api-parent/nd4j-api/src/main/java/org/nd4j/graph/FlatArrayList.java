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
public final class FlatArrayList extends Table {
  public static FlatArrayList getRootAsFlatArrayList(ByteBuffer _bb) { return getRootAsFlatArrayList(_bb, new FlatArrayList()); }
  public static FlatArrayList getRootAsFlatArrayList(ByteBuffer _bb, FlatArrayList obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; }
  public FlatArrayList __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public FlatArray list(int j) { return list(new FlatArray(), j); }
  public FlatArray list(FlatArray obj, int j) { int o = __offset(4); return o != 0 ? obj.__assign(__indirect(__vector(o) + j * 4), bb) : null; }
  public int listLength() { int o = __offset(4); return o != 0 ? __vector_len(o) : 0; }

  public static int createFlatArrayList(FlatBufferBuilder builder,
      int listOffset) {
    builder.startObject(1);
    FlatArrayList.addList(builder, listOffset);
    return FlatArrayList.endFlatArrayList(builder);
  }

  public static void startFlatArrayList(FlatBufferBuilder builder) { builder.startObject(1); }
  public static void addList(FlatBufferBuilder builder, int listOffset) { builder.addOffset(0, listOffset, 0); }
  public static int createListVector(FlatBufferBuilder builder, int[] data) { builder.startVector(4, data.length, 4); for (int i = data.length - 1; i >= 0; i--) builder.addOffset(data[i]); return builder.endVector(); }
  public static void startListVector(FlatBufferBuilder builder, int numElems) { builder.startVector(4, numElems, 4); }
  public static int endFlatArrayList(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}

