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

package org.nd4j.autodiff.samediff.listeners;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.nd4j.autodiff.listeners.debugging.ExecDebuggingListener;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.autodiff.samediff.TrainingConfig;
import org.nd4j.linalg.BaseNd4jTestWithBackends;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.linalg.learning.config.Adam;

public class ExecDebuggingListenerTest extends BaseNd4jTestWithBackends {


    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testExecDebugListener(Nd4jBackend backend) {

        SameDiff sd = SameDiff.create();
        SDVariable in = sd.placeHolder("in", DataType.FLOAT, -1, 3);
        SDVariable label = sd.placeHolder("label", DataType.FLOAT, 1, 2);
        SDVariable w = sd.var("w", Nd4j.rand(DataType.FLOAT, 3, 2));
        SDVariable b = sd.var("b", Nd4j.rand(DataType.FLOAT, 1, 2));
        SDVariable sm = sd.nn.softmax("softmax", in.mmul(w).add(b));
        SDVariable loss = sd.loss.logLoss("loss", label, sm);

        INDArray i = Nd4j.rand(DataType.FLOAT, 1, 3);
        INDArray l = Nd4j.rand(DataType.FLOAT, 1, 2);

        sd.setTrainingConfig(TrainingConfig.builder()
                .dataSetFeatureMapping("in")
                .dataSetLabelMapping("label")
                .updater(new Adam(0.001))
                .build());

        for(ExecDebuggingListener.PrintMode pm : ExecDebuggingListener.PrintMode.values()){
            sd.setListeners(new ExecDebuggingListener(pm, -1, true));
//            sd.output(m, "softmax");
            sd.fit(new DataSet(i, l));

            System.out.println("\n\n\n");
        }

    }


    @Override
    public char ordering() {
        return 'c';
    }
}
