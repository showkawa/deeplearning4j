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
package org.deeplearning4j.nn.layers;

import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.misc.RepeatVector;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.common.primitives.Pair;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("Repeat Vector Test")
@NativeTag
@Tag(TagNames.CUSTOM_FUNCTIONALITY)
@Tag(TagNames.DL4J_OLD_API)
class RepeatVectorTest extends BaseDL4JTest {

    private int REPEAT = 4;

    private Layer getRepeatVectorLayer() {
        NeuralNetConfiguration conf = new NeuralNetConfiguration.Builder().seed(123).dataType(DataType.DOUBLE).layer(new RepeatVector.Builder(REPEAT).build()).build();
        return conf.getLayer().instantiate(conf, null, 0, null, false, DataType.DOUBLE);
    }

    @Test
    @DisplayName("Test Repeat Vector")
    void testRepeatVector() {
        double[] arr = new double[] { 1., 2., 3., 1., 2., 3., 1., 2., 3., 1., 2., 3. };
        INDArray expectedOut = Nd4j.create(arr, new long[] { 1, 3, REPEAT }, 'f');
        INDArray input = Nd4j.create(new double[] { 1., 2., 3. }, new long[] { 1, 3 });
        Layer layer = getRepeatVectorLayer();
        INDArray output = layer.activate(input, false, LayerWorkspaceMgr.noWorkspaces());
        assertTrue(Arrays.equals(expectedOut.shape(), output.shape()));
        assertEquals(expectedOut, output);
        INDArray epsilon = Nd4j.ones(1, 3, 4);
        Pair<Gradient, INDArray> out = layer.backpropGradient(epsilon, LayerWorkspaceMgr.noWorkspaces());
        INDArray outEpsilon = out.getSecond();
        INDArray expectedEpsilon = Nd4j.create(new double[] { 4., 4., 4. }, new long[] { 1, 3 });
        assertEquals(expectedEpsilon, outEpsilon);
    }
}
