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

package org.deeplearning4j.nn.weights;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.shade.jackson.annotation.JsonProperty;

import java.util.Arrays;

@Data
@NoArgsConstructor
public class WeightInitIdentity implements IWeightInit {

    private Double scale;

    public WeightInitIdentity(@JsonProperty("scale") Double scale){
        this.scale = scale;
    }


    @Override
    public INDArray init(double fanIn, double fanOut, long[] shape, char order, INDArray paramView) {
        if (shape[0] != shape[1]) {
            throw new IllegalStateException("Cannot use IDENTITY init with parameters of shape "
                    + Arrays.toString(shape) + ": weights must be a square matrix for identity");
        }
        switch (shape.length) {
            case 2:
               return setIdentity2D(shape, order, paramView);
            case 3:
            case 4:
            case 5:
                return setIdentityConv(shape, order, paramView);
                default: throw new IllegalStateException("Identity mapping for " + shape.length +" dimensions not defined!");
        }
    }

    private INDArray setIdentity2D(long[] shape, char order, INDArray paramView) {
        INDArray ret;
        if (order == Nd4j.order()) {
            ret = Nd4j.eye(shape[0]);
        } else {
            ret = Nd4j.createUninitialized(shape, order).assign(Nd4j.eye(shape[0]));
        }

        if(scale != null){
            ret.muli(scale);
        }

        INDArray flat = Nd4j.toFlattened(order, ret);
        paramView.assign(flat);
        return paramView.reshape(order, shape);
    }

    /**
     * Set identity mapping for convolution layers. When viewed as an NxM matrix of kernel tensors,
     * identity mapping is when parameters is a diagonal matrix of identity kernels.
     * @param shape Shape of parameters
     * @param order Order of parameters
     * @param paramView View of parameters
     * @return A reshaped view of paramView which results in identity mapping when used in convolution layers
     */
    private INDArray setIdentityConv(long[] shape, char order, INDArray paramView) {
        final INDArrayIndex[] indArrayIndices = new INDArrayIndex[shape.length];
        for(int i = 2; i < shape.length; i++) {
            if(shape[i] % 2 == 0) {
                throw new IllegalStateException("Cannot use IDENTITY init with parameters of shape "
                        + Arrays.toString(shape) + "! Must have odd sized kernels!");
            }
            indArrayIndices[i] = NDArrayIndex.point(shape[i] / 2);
        }

        paramView.assign(0);
        final INDArray params =paramView.reshape(order, shape);
        for (int i = 0; i < shape[0]; i++) {
            indArrayIndices[0] = NDArrayIndex.point(i);
            indArrayIndices[1] = NDArrayIndex.point(i);
            params.put(indArrayIndices, Nd4j.ones(1));
        }
        if(scale != null){
            params.muli(scale);
        }
        return params;
    }
}
