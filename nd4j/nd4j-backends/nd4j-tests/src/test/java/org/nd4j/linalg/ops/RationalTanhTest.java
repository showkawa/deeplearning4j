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

package org.nd4j.linalg.ops;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.linalg.BaseNd4jTestWithBackends;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.transforms.gradient.RationalTanhDerivative;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;

import static org.junit.jupiter.api.Assertions.assertTrue;

@NativeTag
public class RationalTanhTest extends BaseNd4jTestWithBackends {

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void gradientCheck(Nd4jBackend backend) {

        double eps = 1e-6;
        INDArray A = Nd4j.linspace(-3, 3, 10).reshape(2, 5);
        INDArray ADer = Nd4j.getExecutioner().exec(new RationalTanhDerivative(A.dup()));

        double[] a = A.data().asDouble();
        double[] aDer = ADer.data().asDouble();

        for (int i = 0; i < 10; i++) {
            double empirical = (f(a[i] + eps) - f(a[i] - eps)) / (2 * eps);
            double analytic = aDer[i];
            assertTrue(Math.abs(empirical - analytic) / (Math.abs(empirical) + Math.abs(analytic)) < 0.001);
        }

    }

    public static double f(double x) {
        return 1.7159 * tanhApprox(2.0 / 3 * x);
    }

    /*
    public static INDArray fDeriv(double x){
        //return C1 * 2.0/3 * tanhDeriv(2.0 / 3 * x);
    }
    */

    public static double tanhApprox(double y) {
        return Math.signum(y) * (1.0 - 1.0 / (1 + Math.abs(y) + y * y + 1.41645 * Math.pow(y, 4.0)));
    }

    /*
    public static double tanhDeriv(double y){
        double a = 1 + Math.abs(y) + y*y + C * Math.pow(y,4);
        return (1 + Math.signum(y) * (2*y + 4*C*Math.pow(y,3))) / (a * a);
    }
    */

    @Override
    public char ordering() {
        return 'f';
    }
}
