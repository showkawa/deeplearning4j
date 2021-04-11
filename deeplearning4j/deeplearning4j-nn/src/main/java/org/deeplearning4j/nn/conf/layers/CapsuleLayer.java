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

package org.deeplearning4j.nn.conf.layers;

import lombok.*;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.inputs.InputType.InputTypeRecurrent;
import org.deeplearning4j.nn.conf.inputs.InputType.Type;
import org.deeplearning4j.nn.conf.layers.samediff.SDLayerParams;
import org.deeplearning4j.nn.conf.layers.samediff.SameDiffLayer;
import org.deeplearning4j.nn.weights.WeightInitUtil;
import org.deeplearning4j.util.CapsuleUtils;
import org.deeplearning4j.util.ValidationUtils;
import org.nd4j.autodiff.samediff.SDIndex;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.linalg.api.memory.MemoryWorkspace;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Map;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CapsuleLayer extends SameDiffLayer {

    private static final String WEIGHT_PARAM = "weight";
    private static final String BIAS_PARAM = "bias";

    private boolean hasBias = false;
    private long inputCapsules = 0;
    private long inputCapsuleDimensions = 0;
    private int capsules;
    private int capsuleDimensions;
    private int routings = 3;

    public CapsuleLayer(Builder builder){
        super(builder);
        this.hasBias = builder.hasBias;
        this.inputCapsules = builder.inputCapsules;
        this.inputCapsuleDimensions = builder.inputCapsuleDimensions;
        this.capsules = builder.capsules;
        this.capsuleDimensions = builder.capsuleDimensions;
        this.routings = builder.routings;

        if(capsules <= 0 || capsuleDimensions <= 0 || routings <= 0){
            throw new IllegalArgumentException("Invalid configuration for Capsule Layer (layer name = \""
                    + layerName + "\"):"
                    + " capsules, capsuleDimensions, and routings must be > 0.  Got: "
                    + capsules + ", " + capsuleDimensions + ", " + routings);
        }

        if(inputCapsules < 0 || inputCapsuleDimensions < 0){
            throw new IllegalArgumentException("Invalid configuration for Capsule Layer (layer name = \""
                    + layerName + "\"):"
                    + " inputCapsules and inputCapsuleDimensions must be >= 0 if set.  Got: "
                    + inputCapsules + ", " + inputCapsuleDimensions);
        }

    }

    @Override
    public void setNIn(InputType inputType, boolean override) {
        if(inputType == null || inputType.getType() != Type.RNN) {
            throw new IllegalStateException("Invalid input for Capsule layer (layer name = \""
                    + layerName + "\"): expect RNN input.  Got: " + inputType);
        }

        if(inputCapsules <= 0 || inputCapsuleDimensions <= 0){
            InputType.InputTypeRecurrent ir = (InputTypeRecurrent) inputType;
            inputCapsules = ir.getSize();
            inputCapsuleDimensions = ir.getTimeSeriesLength();
        }

    }

    @Override
    public SDVariable defineLayer(SameDiff sd, SDVariable input, Map<String, SDVariable> paramTable, SDVariable mask) {

        // input: [mb, inputCapsules, inputCapsuleDimensions]

        // [mb, inputCapsules, 1, inputCapsuleDimensions, 1]
        SDVariable expanded = sd.expandDims(sd.expandDims(input, 2), 4);

        // [mb, inputCapsules, capsules  * capsuleDimensions, inputCapsuleDimensions, 1]
        SDVariable tiled = sd.tile(expanded, 1, 1, capsules * capsuleDimensions, 1, 1);

        // [1, inputCapsules, capsules * capsuleDimensions, inputCapsuleDimensions]
        SDVariable weights = paramTable.get(WEIGHT_PARAM);

        // uHat is the matrix of prediction vectors between two capsules
        // [mb, inputCapsules, capsules, capsuleDimensions, 1]
        SDVariable uHat = weights.times(tiled).sum(true, 3)
                .reshape(-1, inputCapsules, capsules, capsuleDimensions, 1);

        // b is the logits of the routing procedure
        // [mb, inputCapsules, capsules, 1, 1]
        SDVariable b = sd.zerosLike(uHat).get(SDIndex.all(), SDIndex.all(), SDIndex.all(), SDIndex.interval(0, 1), SDIndex.interval(0, 1));

        for(int i = 0 ; i < routings ; i++){

            // c is the coupling coefficient, i.e. the edge weight between the 2 capsules
            // [mb, inputCapsules, capsules, 1, 1]
            SDVariable c = sd.nn.softmax(b, 2);

            // [mb, 1, capsules, capsuleDimensions, 1]
            SDVariable s = c.times(uHat).sum(true, 1);
            if(hasBias){
                s = s.plus(paramTable.get(BIAS_PARAM));
            }

            // v is the per capsule activations.  On the last routing iteration, this is output
            // [mb, 1, capsules, capsuleDimensions, 1]
            SDVariable v = CapsuleUtils.squash(sd, s, 3);

            if(i == routings - 1){
                return sd.squeeze(sd.squeeze(v, 1), 3);
            }

            // [mb, inputCapsules, capsules, capsuleDimensions, 1]
            SDVariable vTiled = sd.tile(v, 1, (int) inputCapsules, 1, 1, 1);

            // [mb, inputCapsules, capsules, 1, 1]
            b = b.plus(uHat.times(vTiled).sum(true, 3));
        }

        return null; // will always return in the loop
    }

    @Override
    public void defineParameters(SDLayerParams params) {
        params.clear();
        params.addWeightParam(WEIGHT_PARAM,
                1, inputCapsules, capsules * capsuleDimensions, inputCapsuleDimensions, 1);

        if(hasBias){
            params.addBiasParam(BIAS_PARAM,
                    1, 1, capsules, capsuleDimensions, 1);
        }
    }

    @Override
    public void initializeParameters(Map<String, INDArray> params) {
        try (MemoryWorkspace ws = Nd4j.getWorkspaceManager().scopeOutOfWorkspaces()) {
            for (Map.Entry<String, INDArray> e : params.entrySet()) {
                if (BIAS_PARAM.equals(e.getKey())) {
                    e.getValue().assign(0);
                } else if(WEIGHT_PARAM.equals(e.getKey())){
                    WeightInitUtil.initWeights(
                            inputCapsules * inputCapsuleDimensions,
                            capsules * capsuleDimensions,
                            new long[]{1, inputCapsules, capsules * capsuleDimensions,
                                    inputCapsuleDimensions, 1},
                            this.weightInit,
                            null,
                            'c',
                            e.getValue()
                    );
                }
            }
        }
    }

    @Override
    public InputType getOutputType(int layerIndex, InputType inputType) {
        return InputType.recurrent(capsules, capsuleDimensions);
    }

    @Getter
    @Setter
    public static class Builder extends SameDiffLayer.Builder<Builder>{

        private int capsules;
        private int capsuleDimensions;

        private int routings = 3;

        private boolean hasBias = false;

        private int inputCapsules = 0;
        private int inputCapsuleDimensions = 0;

        public Builder(int capsules, int capsuleDimensions){
            this(capsules, capsuleDimensions, 3);
        }

        public Builder(int capsules, int capsuleDimensions, int routings){
            super();
            this.setCapsules(capsules);
            this.setCapsuleDimensions(capsuleDimensions);
            this.setRoutings(routings);
        }

        @Override
        public <E extends Layer> E build() {
            return (E) new CapsuleLayer(this);
        }

        /**
         * Set the number of capsules to use.
         * @param capsules
         * @return
         */
        public Builder capsules(int capsules){
            this.setCapsules(capsules);
            return this;
        }

        /**
         * Set the number dimensions of each capsule
         * @param capsuleDimensions
         * @return
         */
        public Builder capsuleDimensions(int capsuleDimensions){
            this.setCapsuleDimensions(capsuleDimensions);
            return this;
        }

        /**
         * Set the number of dynamic routing iterations to use.
         * The default is 3 (recommendedded in Dynamic Routing Between Capsules)
         * @param routings
         * @return
         */
        public Builder routings(int routings){
            this.setRoutings(routings);
            return this;
        }

        /**
         * Usually inferred automatically.
         * @param inputCapsules
         * @return
         */
        public Builder inputCapsules(int inputCapsules){
            this.setInputCapsules(inputCapsules);
            return this;
        }

        /**
         * Usually inferred automatically.
         * @param inputCapsuleDimensions
         * @return
         */
        public Builder inputCapsuleDimensions(int inputCapsuleDimensions){
            this.setInputCapsuleDimensions(inputCapsuleDimensions);
            return this;
        }

        /**
         * Usually inferred automatically.
         * @param inputShape
         * @return
         */
        public Builder inputShape(int... inputShape){
            int[] input = ValidationUtils.validate2NonNegative(inputShape, false, "inputShape");
            this.setInputCapsules(input[0]);
            this.setInputCapsuleDimensions(input[1]);
            return this;
        }

        /**
         * Sets whether to use bias.  False by default.
         * @param hasBias
         * @return
         */
        public Builder hasBias(boolean hasBias){
            this.setHasBias(hasBias);
            return this;
        }

    }
}
