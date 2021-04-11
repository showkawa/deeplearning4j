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

package org.nd4j.linalg.dataset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.BaseNd4jTestWithBackends;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.preprocessor.MultiNormalizerHybrid;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag(TagNames.NDARRAY_ETL)
@NativeTag
@Tag(TagNames.FILE_IO)
public class MultiNormalizerHybridTest extends BaseNd4jTestWithBackends {
    private MultiNormalizerHybrid SUT;
    private MultiDataSet data;
    private MultiDataSet dataCopy;

    @BeforeEach
    public void setUp() {
        SUT = new MultiNormalizerHybrid();
        data = new MultiDataSet(
                new INDArray[] {Nd4j.create(new float[][] {{1, 2}, {3, 4}}),
                        Nd4j.create(new float[][] {{3, 4}, {5, 6}}),},
                new INDArray[] {Nd4j.create(new float[][] {{10, 11}, {12, 13}}),
                        Nd4j.create(new float[][] {{14, 15}, {16, 17}}),});
        dataCopy = data.copy();
    }


    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testNoNormalizationByDefault(Nd4jBackend backend) {
        SUT.fit(data);
        SUT.preProcess(data);
        assertEquals(dataCopy, data);

        SUT.revert(data);
        assertEquals(dataCopy, data);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testGlobalNormalization(Nd4jBackend backend) {
        SUT.standardizeAllInputs().minMaxScaleAllOutputs(-10, 10).fit(data);
        SUT.preProcess(data);

        MultiDataSet expected = new MultiDataSet(
                new INDArray[] {Nd4j.create(new float[][] {{-1, -1}, {1, 1}}),
                        Nd4j.create(new float[][] {{-1, -1}, {1, 1}}),},
                new INDArray[] {Nd4j.create(new float[][] {{-10, -10}, {10, 10}}),
                        Nd4j.create(new float[][] {{-10, -10}, {10, 10}}),});

        assertEquals(expected, data);

        SUT.revert(data);
        assertEquals(dataCopy, data);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testSpecificInputOutputNormalization(Nd4jBackend backend) {
        SUT.minMaxScaleAllInputs().standardizeInput(1).standardizeOutput(0).fit(data);
        SUT.preProcess(data);

        MultiDataSet expected = new MultiDataSet(
                new INDArray[] {Nd4j.create(new float[][] {{0, 0}, {1, 1}}),
                        Nd4j.create(new float[][] {{-1, -1}, {1, 1}}),},
                new INDArray[] {Nd4j.create(new float[][] {{-1, -1}, {1, 1}}),
                        Nd4j.create(new float[][] {{14, 15}, {16, 17}}),});

        assertEquals(expected, data);

        SUT.revert(data);
        assertEquals(dataCopy, data);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testMasking(Nd4jBackend backend) {
        MultiDataSet timeSeries = new MultiDataSet(
                new INDArray[] {Nd4j.create(new float[] {1, 2, 3, 4, 5, 0, 7, 0}).reshape(2, 2, 2),},
                new INDArray[] {Nd4j.create(new float[] {0, 20, 0, 40, 50, 60, 70, 80}).reshape(2, 2, 2)},
                new INDArray[] {Nd4j.create(new float[][] {{1, 1}, {1, 0}})},
                new INDArray[] {Nd4j.create(new float[][] {{0, 1}, {1, 1}})});
        MultiDataSet timeSeriesCopy = timeSeries.copy();

        SUT.minMaxScaleAllInputs(-10, 10).minMaxScaleAllOutputs(-10, 10).fit(timeSeries);
        SUT.preProcess(timeSeries);

        MultiDataSet expected = new MultiDataSet(
                new INDArray[] {Nd4j.create(new float[] {-10, -5, -10, -5, 10, 0, 10, 0}).reshape(2, 2, 2),},
                new INDArray[] {Nd4j.create(new float[] {0, -10, 0, -10, 5, 10, 5, 10}).reshape(2, 2, 2),},
                new INDArray[] {Nd4j.create(new float[][] {{1, 1}, {1, 0}})},
                new INDArray[] {Nd4j.create(new float[][] {{0, 1}, {1, 1}})});

        assertEquals(expected, timeSeries);

        SUT.revert(timeSeries);

        assertEquals(timeSeriesCopy, timeSeries);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testDataSetWithoutLabels(Nd4jBackend backend) {
        SUT.standardizeAllInputs().standardizeAllOutputs().fit(data);

        data.setLabels(null);
        data.setLabelsMaskArray(null);

        SUT.preProcess(data);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testDataSetWithoutFeatures(Nd4jBackend backend) {
        SUT.standardizeAllInputs().standardizeAllOutputs().fit(data);

        data.setFeatures(null);
        data.setFeaturesMaskArrays(null);

        SUT.preProcess(data);
    }

    @Override
    public char ordering() {
        return 'c';
    }
}
