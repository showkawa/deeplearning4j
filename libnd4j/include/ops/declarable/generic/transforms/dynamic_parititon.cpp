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
//  @author GS <sgazeos@gmail.com>
//

#include <system/op_boilerplate.h>
#if NOT_EXCLUDED(OP_dynamic_partition)

#include <ops/declarable/CustomOperations.h>
#include <array>
#include <ops/declarable/helpers/dynamic.h>

namespace sd {
namespace ops {
    CUSTOM_OP_IMPL(dynamic_partition, 2, 1, false, 0, 1) {
        auto input = INPUT_VARIABLE(0);
        auto indices = INPUT_VARIABLE(1);

        // input->printShapeInfo("input");
        // indices->printShapeInfo("indices");

        REQUIRE_TRUE(input->rankOf() >= indices->rankOf(), 0,
                     "dynamic_partition: data tensor rank should be non-lesser than indices\' tensor, but %i < %i given,",
                     input->rankOf(), indices->rankOf());
        for (int dim = 0; dim < indices->rankOf(); dim++) {
            REQUIRE_TRUE(input->sizeAt(dim) == indices->sizeAt(dim), 0,
                         "dynamic_partition: dimensions should be equals for data and indices tensors, but at axis[%i] %i != %i given",
                         dim, input->sizeAt(dim), indices->sizeAt(dim));
        }

        auto numPartition = INT_ARG(0);
        std::vector<NDArray *> outputList(numPartition);
        for (int o = 0; o < numPartition; ++o) {
            outputList[o] = OUTPUT_VARIABLE(o);
        }
        helpers::dynamicPartitionFunctor(block.launchContext(), input, indices, outputList);

        return Status::OK();
    }

    DECLARE_SHAPE_FN(dynamic_partition) {
        auto numPartition = INT_ARG(0);
        auto indices = INPUT_VARIABLE(1);
        std::vector<int> partitionSizes(numPartition, 0);
        auto in = inputShape->at(0);
        auto idx = inputShape->at(1);
        for (int i = 0; i < numPartition; i++) {
            for (int e = 0; e < indices->lengthOf(); ++e)
                if (indices->e<Nd4jLong>(e) == i)
                    partitionSizes[i]++;
        }

        auto shapes = SHAPELIST();
        int outRank = shape::rank(in) - shape::rank(idx) + 1;
        for (int e = 0; e < numPartition; e++) {
            Nd4jLong *newShape;
            ALLOCATE(newShape, block.getWorkspace(), shape::shapeInfoLength(outRank), Nd4jLong);
            //shape::shapeVector(partitionSizes[e], newShape);
            newShape[0] = outRank;
            newShape[1] = partitionSizes[e];
            for (int i = 1; i < outRank; ++i)
                newShape[i + 1] = shape::sizeAt(in, outRank + i - 1);

            shape::updateStrides(newShape, shape::order(in));
            ArrayOptions::setDataType(newShape, ArrayOptions::dataType(in));
            shapes->push_back(CONSTANT(newShape));
        }

        return shapes;
    }

    DECLARE_TYPES(dynamic_partition) {
        getOpDescriptor()
                ->setAllowedInputTypes(sd::DataType::ANY)
                ->setAllowedOutputTypes({ALL_FLOATS, ALL_INTS});
    }

    DECLARE_TYPES(dynamic_partition_bp) {
        getOpDescriptor()
                ->setAllowedInputTypes(sd::DataType::ANY)
                ->setSameMode(true);
    }

    CUSTOM_OP_IMPL(dynamic_partition_bp, 3, 2, false, 0, 1) {
        auto input = INPUT_VARIABLE(0);
        auto indices = INPUT_VARIABLE(1);
        //auto gradOut = ;
        auto numPartition = INT_ARG(0);

        std::vector<NDArray*> outputList(2); // only for output
        std::vector<NDArray*> gradOutList(numPartition);
        for (Nd4jLong e = 0; e < numPartition; e++) {
            gradOutList[e] = INPUT_VARIABLE(e + 2);
        }
        outputList[0] = OUTPUT_VARIABLE(0);
        outputList[1] = OUTPUT_VARIABLE(1);
        NDArray originalIndices(*indices); //->ordering(), indices->shapeInfo(), indices->dataType());
        originalIndices.linspace(0);
        ops::dynamic_partition op;
        auto res = op.evaluate({&originalIndices, indices}, {numPartition});
        REQUIRE_TRUE(res.status() == ND4J_STATUS_OK, 0, "dynamic_partition_bp: Error with dynamic partitioning.");
        ops::dynamic_stitch stichOp;
        std::vector<NDArray*> partitions(numPartition * 2);
        for (size_t i = 0; i < res.size(); i++) {
            partitions[i] = res.at(i);
            partitions[i + numPartition] = gradOutList[i];
        }

        auto result = stichOp.evaluate(partitions, {numPartition});
        REQUIRE_TRUE(result.status() == ND4J_STATUS_OK, 0, "dynamic_partition_bp: Error with dynamic partitioning.");
        result.at(0)->reshapei(outputList[0]->getShapeAsVector());
        outputList[1]->assign(indices);
        outputList[0]->assign(result.at(0));

//        helpers::dynamicPartitionFunctorBP(block.launchContext(), input, indices, gradOutList, outputList);
        return ND4J_STATUS_OK;
    }

    DECLARE_SHAPE_FN(dynamic_partition_bp) {
        auto numPartition = INT_ARG(0);
        auto indices = INPUT_VARIABLE(1);
        std::vector<int> partitionSizes(numPartition, 0);

        auto shapes = SHAPELIST();
        // just copy shape info from input and indices to output
        for (Nd4jLong i = 0; i < 2; i++) {
            Nd4jLong *newShape;
            COPY_SHAPE(inputShape->at(i), newShape);
            shapes->push_back(CONSTANT(newShape));
        }

        return shapes;
    }
}
}

#endif