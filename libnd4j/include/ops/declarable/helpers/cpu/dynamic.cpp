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
// Created by george on 05.04.18.
//
#include <ops/declarable/helpers/dynamic.h>
#include <execution/Threads.h>

namespace sd {
    namespace ops {
        namespace helpers {

            template <typename T>
            static void _dynamicPartitionFunctor(NDArray const* input, NDArray const* indices, std::vector<NDArray*>& outputList) {
                std::vector<std::pair<NDArray *, int>> outputs(outputList.size());
                int sourceDimsLen = input->rankOf() - indices->rankOf();
                if (sourceDimsLen) {
                    std::vector<int> sourceDims(sourceDimsLen);

                    for (int i = sourceDimsLen; i > 0; i--)
                        sourceDims[sourceDimsLen - i] = input->rankOf() - i;

                    ResultSet listOfTensors = input->allTensorsAlongDimension(sourceDims);

                    unsigned int outSize = outputList.size();

                    //PRAGMA_OMP_PARALLEL_FOR_IF(outSize > Environment::getInstance().tadThreshold())
                    for (unsigned int i = 0; i < outSize; i++) {
                        outputs[i].first = outputList[i];
                        std::vector<int> outDims(outputs[i].first->rankOf() - 1);

                        int r = outputs[i].first->rankOf();

                        for (int k = 1; k < r; k++)
                            outDims[k - 1] = k;

                        ResultSet listOutForCurrent = outputs[i].first->allTensorsAlongDimension(outDims);

                        outputs[i].second = 0;

                        //PRAGMA_OMP_PARALLEL_FOR_IF(indices->lengthOf() > Environment::getInstance().elementwiseThreshold())
                        for (Nd4jLong e = 0; e < indices->lengthOf(); ++e)
                            if ((*indices).e<Nd4jLong>(e) == i)
                                listOutForCurrent.at(outputs[i].second++)->assign(listOfTensors.at(e));
                    }

                } else {
                    unsigned int outSize = outputList.size();

                    auto func = PRAGMA_THREADS_FOR {
                        for (auto i = start; i < stop; i++) {
                            outputs[i].first = outputList[i];
                            outputs[i].second = 0;
                            for (Nd4jLong e = 0; e < indices->lengthOf(); ++e)
                                if (indices->e<Nd4jLong>(e) == i)
                                    outputs[i].first->p(outputs[i].second++, input->e<T>(e));
                        }
                    };

                    samediff::Threads::parallel_tad(func, 0, outSize);
                }
            }
            template <typename T>
            static int _dynamicStitchFunctor(std::vector<NDArray*> const& inputs, std::vector<NDArray*> const& indices, NDArray* output){

                int numOfData = inputs.size();

                if (output->isVector()) {
                    for (int e = 0; e < numOfData; e++) {
                        auto data = inputs[e];
                        auto index = indices[e];
                        for (Nd4jLong i = 0; i < index->lengthOf(); i++) {
                            Nd4jLong pos = index->e<Nd4jLong>(i);
                            if (pos < 0) {
                                nd4j_printf("dynamic_stitch: Index value should be non-negative. But %i was given", pos);
                                return ND4J_STATUS_VALIDATION;
                            }
                            if (pos >= output->lengthOf()) {
                                nd4j_printf("dynamic_stitch: Index should be less than %i. But %i was given",
                                            output->lengthOf(), pos);
                                return ND4J_STATUS_VALIDATION;
                            }
                            output->p<T>(pos, data->e<T>(i));
                        }
                    }
                }
                else {
                    std::vector<int> restDims(output->rankOf() - 1);
                    for (auto i = restDims.size(); i > 0;  i--)
                        restDims[restDims.size() - i] = output->rankOf() - i;

                    ResultSet listOfOutTensors = output->allTensorsAlongDimension(restDims);

                    for (int e = 0; e < numOfData; e++) {
                        auto data = inputs[e];
                        auto index = indices[e];
                        std::vector<int> sourceDims(data->rankOf() - index->rankOf());
                        for (auto i = sourceDims.size(); i > 0;  i--)
                            sourceDims[sourceDims.size() - i] = data->rankOf() - i;

                        ResultSet listOfTensors = data->allTensorsAlongDimension(sourceDims)    ;

                        for (Nd4jLong i = 0; i < index->lengthOf(); i++) {
                            auto pos = index->e<Nd4jLong>(i);
                            if (pos < 0) {
                                nd4j_printf("dynamic_stitch: Index value should be non-negative. But %i was given", pos);
                                return ND4J_STATUS_VALIDATION;
                            }
                            if (pos >= output->lengthOf()) {
                                nd4j_printf("dynamic_stitch: Index should be less than %i. But %i was given",
                                         output->lengthOf(), pos);
                                return ND4J_STATUS_VALIDATION;
                            }

                            listOfOutTensors.at(pos)->assign(listOfTensors.at(i));
                        }
                    }
                }
                return ND4J_STATUS_OK;
            }

            template <typename T>
            static void _dynamicPartitionFunctorBP(NDArray const* input, NDArray const* indices, std::vector<NDArray*> const& inputGradientList, std::vector<NDArray*>& outputList) {
                std::vector<std::pair<NDArray *, int>> outputs(inputGradientList.size());

                int sourceDimsLen = input->rankOf() - indices->rankOf();
                if (sourceDimsLen) { // multidimensional case
                    std::vector<int> sourceDims(sourceDimsLen);

                    for (int i = sourceDimsLen; i > 0; i--)
                        sourceDims[sourceDimsLen - i] = input->rankOf() - i;

                    ResultSet listOfTensors = outputList[0]->allTensorsAlongDimension(sourceDims);

                    for (auto i = 0; i < inputGradientList.size(); i++) {
                        outputs[i].first = inputGradientList[i];
                        if (outputs[i].first->rankOf() < 1) continue; // skip empty gradient outs
                        std::vector<int> outDims(outputs[i].first->rankOf() - 1);

                        for (int k = 1; k < outputs[i].first->rankOf(); k++)
                            outDims[k - 1] = k;

                        ResultSet listOutForCurrent = outputs[i].first->allTensorsAlongDimension(outDims);

                        outputs[i].second = 0;

                        for (Nd4jLong e = 0; e < indices->lengthOf(); ++e)
                            if (indices->e<Nd4jLong>(e) == i)
                                listOfTensors.at(e)->assign(listOutForCurrent.at(outputs[i].second++));
                    }
                }
                else { // one-dimensional case
                    auto output = outputList[0];
                    unsigned int gradsSize = inputGradientList.size();

                    auto func = PRAGMA_THREADS_FOR {
                        for (auto i = start; i < stop; i++) {
                            outputs[i].first = inputGradientList[i];
                            outputs[i].second = 0;
                            for (Nd4jLong e = 0; e < indices->lengthOf(); ++e)
                                if (indices->e<Nd4jLong>(e) == i)
                                    output->p<T>(e, outputs[i].first->e<T>(outputs[i].second++));
                        }
                    };

                    samediff::Threads::parallel_tad(func, 0, gradsSize);
                }

                outputList[1]->assign(indices);
            }

            void dynamicPartitionFunctor(sd::LaunchContext * context, NDArray const* input, NDArray const* indices, std::vector<NDArray*>& outputList) {
                auto xType = input->dataType();

                BUILD_SINGLE_SELECTOR(xType, _dynamicPartitionFunctor, (input, indices, outputList), LIBND4J_TYPES);
            }

            template <typename T>
            static int _dynamicStitchFunctorBP(std::vector<NDArray*> const& inputs, std::vector<NDArray*> const& indices, NDArray const* gradInput, std::vector<NDArray*>& outputList){
                throw std::runtime_error("Not umplemented yet");
            }

            int dynamicStitchFunctor(sd::LaunchContext * context, std::vector<NDArray*> const& inputs, std::vector<NDArray*> const& indices, NDArray* output){
                auto xType = inputs.at(0)->dataType();

                BUILD_SINGLE_SELECTOR(xType, return _dynamicStitchFunctor, (inputs, indices, output), LIBND4J_TYPES);
            }

            int dynamicStitchFunctorBP(sd::LaunchContext * context, std::vector<NDArray*> const& inputs, std::vector<NDArray*> const& indices, NDArray const* gradInput, std::vector<NDArray*>& outputList) {
                auto xType = inputs.at(0)->dataType();

                BUILD_SINGLE_SELECTOR(xType, return _dynamicStitchFunctorBP, (inputs, indices, gradInput, outputList), LIBND4J_TYPES);
            }

            void dynamicPartitionFunctorBP(sd::LaunchContext * context, NDArray const* input, NDArray const* indices, std::vector<NDArray*> const& inputGradientList, std::vector<NDArray*>& outputList) {
                auto xType = input->dataType();

                BUILD_SINGLE_SELECTOR(xType, _dynamicPartitionFunctorBP, (input, indices, inputGradientList, outputList), LIBND4J_TYPES);
            }

            BUILD_SINGLE_TEMPLATE(template void _dynamicPartitionFunctorBP, (NDArray const* input, NDArray const* indices, std::vector<NDArray*> const& inputGradientList, std::vector<NDArray*>& outputList);, LIBND4J_TYPES);
            BUILD_SINGLE_TEMPLATE(template int _dynamicStitchFunctorBP, (std::vector<NDArray*> const& inputs, std::vector<NDArray*> const& indices, NDArray const* gradInput, std::vector<NDArray*>& outputList);, LIBND4J_TYPES);

            BUILD_SINGLE_TEMPLATE(template void _dynamicPartitionFunctor, (NDArray const* input, NDArray const* indices, std::vector<NDArray*>& outputList);, LIBND4J_TYPES);
            BUILD_SINGLE_TEMPLATE(template int _dynamicStitchFunctor, (std::vector<NDArray*> const& inputs, std::vector<NDArray*> const& indices, NDArray* output);, LIBND4J_TYPES);


        }
    }
}

