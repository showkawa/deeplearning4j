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
public final class UpdaterState extends Table {
  public static UpdaterState getRootAsUpdaterState(ByteBuffer _bb) { return getRootAsUpdaterState(_bb, new UpdaterState()); }
  public static UpdaterState getRootAsUpdaterState(ByteBuffer _bb, UpdaterState obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; }
  public UpdaterState __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public String paramName() { int o = __offset(4); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer paramNameAsByteBuffer() { return __vector_as_bytebuffer(4, 1); }
  public ByteBuffer paramNameInByteBuffer(ByteBuffer _bb) { return __vector_in_bytebuffer(_bb, 4, 1); }
  public String updaterStateKeys(int j) { int o = __offset(6); return o != 0 ? __string(__vector(o) + j * 4) : null; }
  public int updaterStateKeysLength() { int o = __offset(6); return o != 0 ? __vector_len(o) : 0; }
  public FlatArray updaterStateValues(int j) { return updaterStateValues(new FlatArray(), j); }
  public FlatArray updaterStateValues(FlatArray obj, int j) { int o = __offset(8); return o != 0 ? obj.__assign(__indirect(__vector(o) + j * 4), bb) : null; }
  public int updaterStateValuesLength() { int o = __offset(8); return o != 0 ? __vector_len(o) : 0; }

  public static int createUpdaterState(FlatBufferBuilder builder,
      int paramNameOffset,
      int updaterStateKeysOffset,
      int updaterStateValuesOffset) {
    builder.startObject(3);
    UpdaterState.addUpdaterStateValues(builder, updaterStateValuesOffset);
    UpdaterState.addUpdaterStateKeys(builder, updaterStateKeysOffset);
    UpdaterState.addParamName(builder, paramNameOffset);
    return UpdaterState.endUpdaterState(builder);
  }

  public static void startUpdaterState(FlatBufferBuilder builder) { builder.startObject(3); }
  public static void addParamName(FlatBufferBuilder builder, int paramNameOffset) { builder.addOffset(0, paramNameOffset, 0); }
  public static void addUpdaterStateKeys(FlatBufferBuilder builder, int updaterStateKeysOffset) { builder.addOffset(1, updaterStateKeysOffset, 0); }
  public static int createUpdaterStateKeysVector(FlatBufferBuilder builder, int[] data) { builder.startVector(4, data.length, 4); for (int i = data.length - 1; i >= 0; i--) builder.addOffset(data[i]); return builder.endVector(); }
  public static void startUpdaterStateKeysVector(FlatBufferBuilder builder, int numElems) { builder.startVector(4, numElems, 4); }
  public static void addUpdaterStateValues(FlatBufferBuilder builder, int updaterStateValuesOffset) { builder.addOffset(2, updaterStateValuesOffset, 0); }
  public static int createUpdaterStateValuesVector(FlatBufferBuilder builder, int[] data) { builder.startVector(4, data.length, 4); for (int i = data.length - 1; i >= 0; i--) builder.addOffset(data[i]); return builder.endVector(); }
  public static void startUpdaterStateValuesVector(FlatBufferBuilder builder, int numElems) { builder.startVector(4, numElems, 4); }
  public static int endUpdaterState(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}

