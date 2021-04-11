/* ******************************************************************************
 *
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 *  See the NOTICE file distributed with this work for additional
 *  information regarding copyright ownership.
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

//
// @author raver119@gmail.com
//

#ifndef LIBND4J_RANDOM_H
#define LIBND4J_RANDOM_H



#include <helpers/shape.h>
#include <helpers/helper_random.h>
#include <ops/random_ops.h>
#include <ops/special_random_ops.h>

#include <loops/legacy_ops.h>


namespace functions {
    namespace random {

        template<typename X>
        class RandomFunction {
        public:

#ifdef __CUDABLAS__
            template<typename OpClass>
            static _CUDA_D void execTransformCuda(Nd4jPointer state,
                                                  const void *x, const Nd4jLong *xShapeBuffer,
                                                  const void *y, const Nd4jLong *yShapeBuffer,
                                                  void *z, const Nd4jLong *zShapeBuffer,
                                                  void *extraArguments);

            template<typename OpClass>
            static _CUDA_D void execTransformCuda(Nd4jPointer state,
                                                  const void *x, const Nd4jLong *xShapeBuffer,
                                                  void *z, const Nd4jLong *zShapeBuffer,
                                                  void *extraArguments);

            template<typename OpClass>
            static _CUDA_D void execTransformCuda(Nd4jPointer state, void *z, const Nd4jLong *zShapeBuffer, void *extraArguments);


            static _CUDA_H void executeCudaSingle(dim3& launchDims, cudaStream_t* stream,
                                                  int opNum,
                                                  Nd4jPointer stateHost,
                                                  void *z, const Nd4jLong *zShapeBuffer,
                                                  void *extraArguments);


            static _CUDA_H void executeCudaDouble(dim3& launchDims, cudaStream_t* stream,
                                                  int opNum,
                                                  Nd4jPointer stateHost,
                                                  const void *x, const Nd4jLong *xShapeBuffer,
                                                  void *z, const Nd4jLong *zShapeBuffer,
                                                  void *extraArguments);


            static _CUDA_H void executeCudaTriple(dim3& launchDims, cudaStream_t* stream,
                                                  int opNum,
                                                  Nd4jPointer stateHost,
                                                  const void *x, const Nd4jLong *xShapeBuffer,
                                                  const void *y, const Nd4jLong *yShapeBuffer,
                                                  void *z, const Nd4jLong* zShapeBuffer,
                                                  void *extraArguments);
#else

            template<typename OpClass>
            static void execTransform(Nd4jPointer state, const void *x, const Nd4jLong *xShapeBuffer, const void *y, const Nd4jLong *yShapeBuffer, void *z, const Nd4jLong *zShapeBuffer, void *extraArguments);

            template<typename OpClass>
            static void execTransform(Nd4jPointer state, const void *x, const Nd4jLong *xShapeBuffer, void *z, const Nd4jLong *zShapeBuffer, void *extraArguments);

            template<typename OpClass>
            static void execTransform(Nd4jPointer state, void *z, const Nd4jLong *zShapeBuffer, void *extraArguments);

            static void execTransform(int opNum, Nd4jPointer state, const void *x, const Nd4jLong *xShapeBuffer, void *z, const Nd4jLong *zShapeBuffer, void *extraArguments);
            static void execTransform(int opNum, Nd4jPointer state, const void *x, const Nd4jLong *xShapeBuffer, const void *y, const Nd4jLong *yShapeBuffer, void *z, const Nd4jLong *zShapeBuffer, void *extraArguments);
            static void execTransform(int opNum, Nd4jPointer state, void *z, const Nd4jLong *zShapeBuffer, void *extraArguments);
#endif
        };
    }
}


#endif //LIBND4J_RANDOM_H
