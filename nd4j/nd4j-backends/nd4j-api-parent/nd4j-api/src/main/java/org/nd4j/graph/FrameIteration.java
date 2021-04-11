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
public final class FrameIteration extends Table {
  public static FrameIteration getRootAsFrameIteration(ByteBuffer _bb) { return getRootAsFrameIteration(_bb, new FrameIteration()); }
  public static FrameIteration getRootAsFrameIteration(ByteBuffer _bb, FrameIteration obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; }
  public FrameIteration __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public String frame() { int o = __offset(4); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer frameAsByteBuffer() { return __vector_as_bytebuffer(4, 1); }
  public ByteBuffer frameInByteBuffer(ByteBuffer _bb) { return __vector_in_bytebuffer(_bb, 4, 1); }
  public int iteration() { int o = __offset(6); return o != 0 ? bb.getShort(o + bb_pos) & 0xFFFF : 0; }

  public static int createFrameIteration(FlatBufferBuilder builder,
      int frameOffset,
      int iteration) {
    builder.startObject(2);
    FrameIteration.addFrame(builder, frameOffset);
    FrameIteration.addIteration(builder, iteration);
    return FrameIteration.endFrameIteration(builder);
  }

  public static void startFrameIteration(FlatBufferBuilder builder) { builder.startObject(2); }
  public static void addFrame(FlatBufferBuilder builder, int frameOffset) { builder.addOffset(0, frameOffset, 0); }
  public static void addIteration(FlatBufferBuilder builder, int iteration) { builder.addShort(1, (short)iteration, (short)0); }
  public static int endFrameIteration(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}

