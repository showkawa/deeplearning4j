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

package org.deeplearning4j.nn.conf.layers.samediff;

import org.deeplearning4j.nn.conf.graph.GraphVertex;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.common.base.Preconditions;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.*;


public abstract class SameDiffLambdaVertex extends SameDiffVertex {

    protected transient VertexInputs inputs;

    /**
     * The defineVertex method is used to define the foward pass for the vertex
     *
     * @param sameDiff SameDiff instance to use to define the vertex
     * @param inputs   Layer input variable
     * @return The output variable (orresponding to the output activations for the vertex)
     */
    public abstract SDVariable defineVertex(SameDiff sameDiff, VertexInputs inputs);

    @Override
    public SDVariable defineVertex(SameDiff sameDiff, Map<String, SDVariable> layerInput,
                                   Map<String, SDVariable> paramTable, Map<String, SDVariable> maskVars) {
        VertexInputs vi = getInputs(sameDiff);
        int i = 0;
        if (vi.map.size() == 0 && layerInput.size() > 0) {
            for (SDVariable v : layerInput.values()) {
                vi.map.put(i++, v);
            }
        }
        return defineVertex(sameDiff, getInputs(sameDiff));
    }

    @Override
    public void defineParametersAndInputs(SDVertexParams params) {
        //Parameters are no op, for lamda vertex - but inputs are NOT
        SameDiff temp = SameDiff.create();
        VertexInputs tempInputs = new VertexInputs(temp);
        defineVertex(temp, tempInputs);
        List<String> list = new ArrayList<>();
        for (Integer i : tempInputs.map.keySet()) {
            list.add(tempInputs.map.get(i).name());
        }
        params.defineInputs(list.toArray(new String[list.size()]));
    }

    @Override
    public void initializeParameters(Map<String, INDArray> params) {
        //No op, for lambda vertex
    }

    @Override
    public GraphVertex clone() {
        try {
            return getClass().getConstructor().newInstance();
        } catch (Exception e){
            throw new RuntimeException("Unable to create new instance of class " + getClass().getName() + " from no-arg constructor");
        }
    }

    protected VertexInputs getInputs(SameDiff sd) {
        if (inputs == null) {
            inputs = new VertexInputs(sd);
        }
        return inputs;
    }

    public class VertexInputs {

        private SameDiff sameDiff;
        private Map<Integer, SDVariable> map = new LinkedHashMap<>();

        protected VertexInputs(SameDiff sd) {
            this.sameDiff = sd;
        }

        public SDVariable getInput(int inputNum) {
            Preconditions.checkArgument(inputNum >= 0, "Input number must be >= 0." + "Got: %s", inputNum);

            if (!map.containsKey(inputNum)) {
                //Lazily define extra input variable as required
                SDVariable var = sameDiff.var("var_" + inputNum, dataType, -1); //TODO is this shape safe?
                map.put(inputNum, var);
            }

            return map.get(inputNum);
        }
    }
}
