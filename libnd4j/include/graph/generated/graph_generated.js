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

/**
 * @const
 * @namespace
 */
var nd4j = nd4j || {};

/**
 * @const
 * @namespace
 */
nd4j.graph = nd4j.graph || {};

/**
 * @constructor
 */
nd4j.graph.UpdaterState = function() {
  /**
   * @type {flatbuffers.ByteBuffer}
   */
  this.bb = null;

  /**
   * @type {number}
   */
  this.bb_pos = 0;
};

/**
 * @param {number} i
 * @param {flatbuffers.ByteBuffer} bb
 * @returns {nd4j.graph.UpdaterState}
 */
nd4j.graph.UpdaterState.prototype.__init = function(i, bb) {
  this.bb_pos = i;
  this.bb = bb;
  return this;
};

/**
 * @param {flatbuffers.ByteBuffer} bb
 * @param {nd4j.graph.UpdaterState=} obj
 * @returns {nd4j.graph.UpdaterState}
 */
nd4j.graph.UpdaterState.getRootAsUpdaterState = function(bb, obj) {
  return (obj || new nd4j.graph.UpdaterState).__init(bb.readInt32(bb.position()) + bb.position(), bb);
};

/**
 * @param {flatbuffers.Encoding=} optionalEncoding
 * @returns {string|Uint8Array|null}
 */
nd4j.graph.UpdaterState.prototype.paramName = function(optionalEncoding) {
  var offset = this.bb.__offset(this.bb_pos, 4);
  return offset ? this.bb.__string(this.bb_pos + offset, optionalEncoding) : null;
};

/**
 * @param {number} index
 * @param {flatbuffers.Encoding=} optionalEncoding
 * @returns {string|Uint8Array}
 */
nd4j.graph.UpdaterState.prototype.updaterStateKeys = function(index, optionalEncoding) {
  var offset = this.bb.__offset(this.bb_pos, 6);
  return offset ? this.bb.__string(this.bb.__vector(this.bb_pos + offset) + index * 4, optionalEncoding) : null;
};

/**
 * @returns {number}
 */
nd4j.graph.UpdaterState.prototype.updaterStateKeysLength = function() {
  var offset = this.bb.__offset(this.bb_pos, 6);
  return offset ? this.bb.__vector_len(this.bb_pos + offset) : 0;
};

/**
 * @param {number} index
 * @param {nd4j.graph.FlatArray=} obj
 * @returns {nd4j.graph.FlatArray}
 */
nd4j.graph.UpdaterState.prototype.updaterStateValues = function(index, obj) {
  var offset = this.bb.__offset(this.bb_pos, 8);
  return offset ? (obj || new nd4j.graph.FlatArray).__init(this.bb.__indirect(this.bb.__vector(this.bb_pos + offset) + index * 4), this.bb) : null;
};

/**
 * @returns {number}
 */
nd4j.graph.UpdaterState.prototype.updaterStateValuesLength = function() {
  var offset = this.bb.__offset(this.bb_pos, 8);
  return offset ? this.bb.__vector_len(this.bb_pos + offset) : 0;
};

/**
 * @param {flatbuffers.Builder} builder
 */
nd4j.graph.UpdaterState.startUpdaterState = function(builder) {
  builder.startObject(3);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {flatbuffers.Offset} paramNameOffset
 */
nd4j.graph.UpdaterState.addParamName = function(builder, paramNameOffset) {
  builder.addFieldOffset(0, paramNameOffset, 0);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {flatbuffers.Offset} updaterStateKeysOffset
 */
nd4j.graph.UpdaterState.addUpdaterStateKeys = function(builder, updaterStateKeysOffset) {
  builder.addFieldOffset(1, updaterStateKeysOffset, 0);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {Array.<flatbuffers.Offset>} data
 * @returns {flatbuffers.Offset}
 */
nd4j.graph.UpdaterState.createUpdaterStateKeysVector = function(builder, data) {
  builder.startVector(4, data.length, 4);
  for (var i = data.length - 1; i >= 0; i--) {
    builder.addOffset(data[i]);
  }
  return builder.endVector();
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {number} numElems
 */
nd4j.graph.UpdaterState.startUpdaterStateKeysVector = function(builder, numElems) {
  builder.startVector(4, numElems, 4);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {flatbuffers.Offset} updaterStateValuesOffset
 */
nd4j.graph.UpdaterState.addUpdaterStateValues = function(builder, updaterStateValuesOffset) {
  builder.addFieldOffset(2, updaterStateValuesOffset, 0);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {Array.<flatbuffers.Offset>} data
 * @returns {flatbuffers.Offset}
 */
nd4j.graph.UpdaterState.createUpdaterStateValuesVector = function(builder, data) {
  builder.startVector(4, data.length, 4);
  for (var i = data.length - 1; i >= 0; i--) {
    builder.addOffset(data[i]);
  }
  return builder.endVector();
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {number} numElems
 */
nd4j.graph.UpdaterState.startUpdaterStateValuesVector = function(builder, numElems) {
  builder.startVector(4, numElems, 4);
};

/**
 * @param {flatbuffers.Builder} builder
 * @returns {flatbuffers.Offset}
 */
nd4j.graph.UpdaterState.endUpdaterState = function(builder) {
  var offset = builder.endObject();
  return offset;
};

/**
 * @constructor
 */
nd4j.graph.FlatGraph = function() {
  /**
   * @type {flatbuffers.ByteBuffer}
   */
  this.bb = null;

  /**
   * @type {number}
   */
  this.bb_pos = 0;
};

/**
 * @param {number} i
 * @param {flatbuffers.ByteBuffer} bb
 * @returns {nd4j.graph.FlatGraph}
 */
nd4j.graph.FlatGraph.prototype.__init = function(i, bb) {
  this.bb_pos = i;
  this.bb = bb;
  return this;
};

/**
 * @param {flatbuffers.ByteBuffer} bb
 * @param {nd4j.graph.FlatGraph=} obj
 * @returns {nd4j.graph.FlatGraph}
 */
nd4j.graph.FlatGraph.getRootAsFlatGraph = function(bb, obj) {
  return (obj || new nd4j.graph.FlatGraph).__init(bb.readInt32(bb.position()) + bb.position(), bb);
};

/**
 * @returns {flatbuffers.Long}
 */
nd4j.graph.FlatGraph.prototype.id = function() {
  var offset = this.bb.__offset(this.bb_pos, 4);
  return offset ? this.bb.readInt64(this.bb_pos + offset) : this.bb.createLong(0, 0);
};

/**
 * @param {number} index
 * @param {nd4j.graph.FlatVariable=} obj
 * @returns {nd4j.graph.FlatVariable}
 */
nd4j.graph.FlatGraph.prototype.variables = function(index, obj) {
  var offset = this.bb.__offset(this.bb_pos, 6);
  return offset ? (obj || new nd4j.graph.FlatVariable).__init(this.bb.__indirect(this.bb.__vector(this.bb_pos + offset) + index * 4), this.bb) : null;
};

/**
 * @returns {number}
 */
nd4j.graph.FlatGraph.prototype.variablesLength = function() {
  var offset = this.bb.__offset(this.bb_pos, 6);
  return offset ? this.bb.__vector_len(this.bb_pos + offset) : 0;
};

/**
 * @param {number} index
 * @param {nd4j.graph.FlatNode=} obj
 * @returns {nd4j.graph.FlatNode}
 */
nd4j.graph.FlatGraph.prototype.nodes = function(index, obj) {
  var offset = this.bb.__offset(this.bb_pos, 8);
  return offset ? (obj || new nd4j.graph.FlatNode).__init(this.bb.__indirect(this.bb.__vector(this.bb_pos + offset) + index * 4), this.bb) : null;
};

/**
 * @returns {number}
 */
nd4j.graph.FlatGraph.prototype.nodesLength = function() {
  var offset = this.bb.__offset(this.bb_pos, 8);
  return offset ? this.bb.__vector_len(this.bb_pos + offset) : 0;
};

/**
 * @param {number} index
 * @param {nd4j.graph.IntPair=} obj
 * @returns {nd4j.graph.IntPair}
 */
nd4j.graph.FlatGraph.prototype.outputs = function(index, obj) {
  var offset = this.bb.__offset(this.bb_pos, 10);
  return offset ? (obj || new nd4j.graph.IntPair).__init(this.bb.__indirect(this.bb.__vector(this.bb_pos + offset) + index * 4), this.bb) : null;
};

/**
 * @returns {number}
 */
nd4j.graph.FlatGraph.prototype.outputsLength = function() {
  var offset = this.bb.__offset(this.bb_pos, 10);
  return offset ? this.bb.__vector_len(this.bb_pos + offset) : 0;
};

/**
 * @param {nd4j.graph.FlatConfiguration=} obj
 * @returns {nd4j.graph.FlatConfiguration|null}
 */
nd4j.graph.FlatGraph.prototype.configuration = function(obj) {
  var offset = this.bb.__offset(this.bb_pos, 12);
  return offset ? (obj || new nd4j.graph.FlatConfiguration).__init(this.bb.__indirect(this.bb_pos + offset), this.bb) : null;
};

/**
 * @param {number} index
 * @param {flatbuffers.Encoding=} optionalEncoding
 * @returns {string|Uint8Array}
 */
nd4j.graph.FlatGraph.prototype.placeholders = function(index, optionalEncoding) {
  var offset = this.bb.__offset(this.bb_pos, 14);
  return offset ? this.bb.__string(this.bb.__vector(this.bb_pos + offset) + index * 4, optionalEncoding) : null;
};

/**
 * @returns {number}
 */
nd4j.graph.FlatGraph.prototype.placeholdersLength = function() {
  var offset = this.bb.__offset(this.bb_pos, 14);
  return offset ? this.bb.__vector_len(this.bb_pos + offset) : 0;
};

/**
 * @param {number} index
 * @param {flatbuffers.Encoding=} optionalEncoding
 * @returns {string|Uint8Array}
 */
nd4j.graph.FlatGraph.prototype.lossVariables = function(index, optionalEncoding) {
  var offset = this.bb.__offset(this.bb_pos, 16);
  return offset ? this.bb.__string(this.bb.__vector(this.bb_pos + offset) + index * 4, optionalEncoding) : null;
};

/**
 * @returns {number}
 */
nd4j.graph.FlatGraph.prototype.lossVariablesLength = function() {
  var offset = this.bb.__offset(this.bb_pos, 16);
  return offset ? this.bb.__vector_len(this.bb_pos + offset) : 0;
};

/**
 * @param {flatbuffers.Encoding=} optionalEncoding
 * @returns {string|Uint8Array|null}
 */
nd4j.graph.FlatGraph.prototype.trainingConfig = function(optionalEncoding) {
  var offset = this.bb.__offset(this.bb_pos, 18);
  return offset ? this.bb.__string(this.bb_pos + offset, optionalEncoding) : null;
};

/**
 * @param {number} index
 * @param {nd4j.graph.UpdaterState=} obj
 * @returns {nd4j.graph.UpdaterState}
 */
nd4j.graph.FlatGraph.prototype.updaterState = function(index, obj) {
  var offset = this.bb.__offset(this.bb_pos, 20);
  return offset ? (obj || new nd4j.graph.UpdaterState).__init(this.bb.__indirect(this.bb.__vector(this.bb_pos + offset) + index * 4), this.bb) : null;
};

/**
 * @returns {number}
 */
nd4j.graph.FlatGraph.prototype.updaterStateLength = function() {
  var offset = this.bb.__offset(this.bb_pos, 20);
  return offset ? this.bb.__vector_len(this.bb_pos + offset) : 0;
};

/**
 * @param {flatbuffers.Builder} builder
 */
nd4j.graph.FlatGraph.startFlatGraph = function(builder) {
  builder.startObject(9);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {flatbuffers.Long} id
 */
nd4j.graph.FlatGraph.addId = function(builder, id) {
  builder.addFieldInt64(0, id, builder.createLong(0, 0));
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {flatbuffers.Offset} variablesOffset
 */
nd4j.graph.FlatGraph.addVariables = function(builder, variablesOffset) {
  builder.addFieldOffset(1, variablesOffset, 0);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {Array.<flatbuffers.Offset>} data
 * @returns {flatbuffers.Offset}
 */
nd4j.graph.FlatGraph.createVariablesVector = function(builder, data) {
  builder.startVector(4, data.length, 4);
  for (var i = data.length - 1; i >= 0; i--) {
    builder.addOffset(data[i]);
  }
  return builder.endVector();
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {number} numElems
 */
nd4j.graph.FlatGraph.startVariablesVector = function(builder, numElems) {
  builder.startVector(4, numElems, 4);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {flatbuffers.Offset} nodesOffset
 */
nd4j.graph.FlatGraph.addNodes = function(builder, nodesOffset) {
  builder.addFieldOffset(2, nodesOffset, 0);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {Array.<flatbuffers.Offset>} data
 * @returns {flatbuffers.Offset}
 */
nd4j.graph.FlatGraph.createNodesVector = function(builder, data) {
  builder.startVector(4, data.length, 4);
  for (var i = data.length - 1; i >= 0; i--) {
    builder.addOffset(data[i]);
  }
  return builder.endVector();
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {number} numElems
 */
nd4j.graph.FlatGraph.startNodesVector = function(builder, numElems) {
  builder.startVector(4, numElems, 4);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {flatbuffers.Offset} outputsOffset
 */
nd4j.graph.FlatGraph.addOutputs = function(builder, outputsOffset) {
  builder.addFieldOffset(3, outputsOffset, 0);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {Array.<flatbuffers.Offset>} data
 * @returns {flatbuffers.Offset}
 */
nd4j.graph.FlatGraph.createOutputsVector = function(builder, data) {
  builder.startVector(4, data.length, 4);
  for (var i = data.length - 1; i >= 0; i--) {
    builder.addOffset(data[i]);
  }
  return builder.endVector();
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {number} numElems
 */
nd4j.graph.FlatGraph.startOutputsVector = function(builder, numElems) {
  builder.startVector(4, numElems, 4);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {flatbuffers.Offset} configurationOffset
 */
nd4j.graph.FlatGraph.addConfiguration = function(builder, configurationOffset) {
  builder.addFieldOffset(4, configurationOffset, 0);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {flatbuffers.Offset} placeholdersOffset
 */
nd4j.graph.FlatGraph.addPlaceholders = function(builder, placeholdersOffset) {
  builder.addFieldOffset(5, placeholdersOffset, 0);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {Array.<flatbuffers.Offset>} data
 * @returns {flatbuffers.Offset}
 */
nd4j.graph.FlatGraph.createPlaceholdersVector = function(builder, data) {
  builder.startVector(4, data.length, 4);
  for (var i = data.length - 1; i >= 0; i--) {
    builder.addOffset(data[i]);
  }
  return builder.endVector();
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {number} numElems
 */
nd4j.graph.FlatGraph.startPlaceholdersVector = function(builder, numElems) {
  builder.startVector(4, numElems, 4);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {flatbuffers.Offset} lossVariablesOffset
 */
nd4j.graph.FlatGraph.addLossVariables = function(builder, lossVariablesOffset) {
  builder.addFieldOffset(6, lossVariablesOffset, 0);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {Array.<flatbuffers.Offset>} data
 * @returns {flatbuffers.Offset}
 */
nd4j.graph.FlatGraph.createLossVariablesVector = function(builder, data) {
  builder.startVector(4, data.length, 4);
  for (var i = data.length - 1; i >= 0; i--) {
    builder.addOffset(data[i]);
  }
  return builder.endVector();
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {number} numElems
 */
nd4j.graph.FlatGraph.startLossVariablesVector = function(builder, numElems) {
  builder.startVector(4, numElems, 4);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {flatbuffers.Offset} trainingConfigOffset
 */
nd4j.graph.FlatGraph.addTrainingConfig = function(builder, trainingConfigOffset) {
  builder.addFieldOffset(7, trainingConfigOffset, 0);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {flatbuffers.Offset} updaterStateOffset
 */
nd4j.graph.FlatGraph.addUpdaterState = function(builder, updaterStateOffset) {
  builder.addFieldOffset(8, updaterStateOffset, 0);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {Array.<flatbuffers.Offset>} data
 * @returns {flatbuffers.Offset}
 */
nd4j.graph.FlatGraph.createUpdaterStateVector = function(builder, data) {
  builder.startVector(4, data.length, 4);
  for (var i = data.length - 1; i >= 0; i--) {
    builder.addOffset(data[i]);
  }
  return builder.endVector();
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {number} numElems
 */
nd4j.graph.FlatGraph.startUpdaterStateVector = function(builder, numElems) {
  builder.startVector(4, numElems, 4);
};

/**
 * @param {flatbuffers.Builder} builder
 * @returns {flatbuffers.Offset}
 */
nd4j.graph.FlatGraph.endFlatGraph = function(builder) {
  var offset = builder.endObject();
  return offset;
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {flatbuffers.Offset} offset
 */
nd4j.graph.FlatGraph.finishFlatGraphBuffer = function(builder, offset) {
  builder.finish(offset);
};

/**
 * @constructor
 */
nd4j.graph.FlatDropRequest = function() {
  /**
   * @type {flatbuffers.ByteBuffer}
   */
  this.bb = null;

  /**
   * @type {number}
   */
  this.bb_pos = 0;
};

/**
 * @param {number} i
 * @param {flatbuffers.ByteBuffer} bb
 * @returns {nd4j.graph.FlatDropRequest}
 */
nd4j.graph.FlatDropRequest.prototype.__init = function(i, bb) {
  this.bb_pos = i;
  this.bb = bb;
  return this;
};

/**
 * @param {flatbuffers.ByteBuffer} bb
 * @param {nd4j.graph.FlatDropRequest=} obj
 * @returns {nd4j.graph.FlatDropRequest}
 */
nd4j.graph.FlatDropRequest.getRootAsFlatDropRequest = function(bb, obj) {
  return (obj || new nd4j.graph.FlatDropRequest).__init(bb.readInt32(bb.position()) + bb.position(), bb);
};

/**
 * @returns {flatbuffers.Long}
 */
nd4j.graph.FlatDropRequest.prototype.id = function() {
  var offset = this.bb.__offset(this.bb_pos, 4);
  return offset ? this.bb.readInt64(this.bb_pos + offset) : this.bb.createLong(0, 0);
};

/**
 * @param {flatbuffers.Builder} builder
 */
nd4j.graph.FlatDropRequest.startFlatDropRequest = function(builder) {
  builder.startObject(1);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {flatbuffers.Long} id
 */
nd4j.graph.FlatDropRequest.addId = function(builder, id) {
  builder.addFieldInt64(0, id, builder.createLong(0, 0));
};

/**
 * @param {flatbuffers.Builder} builder
 * @returns {flatbuffers.Offset}
 */
nd4j.graph.FlatDropRequest.endFlatDropRequest = function(builder) {
  var offset = builder.endObject();
  return offset;
};

/**
 * @constructor
 */
nd4j.graph.FlatResponse = function() {
  /**
   * @type {flatbuffers.ByteBuffer}
   */
  this.bb = null;

  /**
   * @type {number}
   */
  this.bb_pos = 0;
};

/**
 * @param {number} i
 * @param {flatbuffers.ByteBuffer} bb
 * @returns {nd4j.graph.FlatResponse}
 */
nd4j.graph.FlatResponse.prototype.__init = function(i, bb) {
  this.bb_pos = i;
  this.bb = bb;
  return this;
};

/**
 * @param {flatbuffers.ByteBuffer} bb
 * @param {nd4j.graph.FlatResponse=} obj
 * @returns {nd4j.graph.FlatResponse}
 */
nd4j.graph.FlatResponse.getRootAsFlatResponse = function(bb, obj) {
  return (obj || new nd4j.graph.FlatResponse).__init(bb.readInt32(bb.position()) + bb.position(), bb);
};

/**
 * @returns {number}
 */
nd4j.graph.FlatResponse.prototype.status = function() {
  var offset = this.bb.__offset(this.bb_pos, 4);
  return offset ? this.bb.readInt32(this.bb_pos + offset) : 0;
};

/**
 * @param {flatbuffers.Builder} builder
 */
nd4j.graph.FlatResponse.startFlatResponse = function(builder) {
  builder.startObject(1);
};

/**
 * @param {flatbuffers.Builder} builder
 * @param {number} status
 */
nd4j.graph.FlatResponse.addStatus = function(builder, status) {
  builder.addFieldInt32(0, status, 0);
};

/**
 * @param {flatbuffers.Builder} builder
 * @returns {flatbuffers.Offset}
 */
nd4j.graph.FlatResponse.endFlatResponse = function(builder) {
  var offset = builder.endObject();
  return offset;
};

// Exports for Node.js and RequireJS
this.nd4j = nd4j;
