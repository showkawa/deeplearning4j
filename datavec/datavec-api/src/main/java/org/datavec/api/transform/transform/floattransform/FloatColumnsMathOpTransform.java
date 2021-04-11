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

package org.datavec.api.transform.transform.floattransform;

import lombok.Data;
import org.datavec.api.transform.MathOp;
import org.datavec.api.transform.metadata.ColumnMetaData;
import org.datavec.api.transform.metadata.FloatMetaData;
import org.datavec.api.transform.schema.Schema;
import org.datavec.api.transform.transform.BaseColumnsMathOpTransform;
import org.datavec.api.transform.transform.floattransform.FloatMathOpTransform;
import org.datavec.api.writable.FloatWritable;
import org.datavec.api.writable.Writable;
import org.nd4j.shade.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
public class FloatColumnsMathOpTransform extends BaseColumnsMathOpTransform {

    public FloatColumnsMathOpTransform(@JsonProperty("newColumnName") String newColumnName,
                                       @JsonProperty("mathOp") MathOp mathOp, @JsonProperty("columns") List<String> columns) {
        this(newColumnName, mathOp, columns.toArray(new String[columns.size()]));
    }

    public FloatColumnsMathOpTransform(String newColumnName, MathOp mathOp, String... columns) {
        super(newColumnName, mathOp, columns);
    }

    @Override
    protected ColumnMetaData derivedColumnMetaData(String newColumnName, Schema inputSchema) {
        return new FloatMetaData(newColumnName);
    }

    @Override
    protected Writable doOp(Writable... input) {
        switch (mathOp) {
            case Add:
                Float sum = 0f;
                for (Writable w : input)
                    sum += w.toFloat();
                return new FloatWritable(sum);
            case Subtract:
                return new FloatWritable(input[0].toFloat() - input[1].toFloat());
            case Multiply:
                float product = 1.0f;
                for (Writable w : input)
                    product *= w.toFloat();
                return new FloatWritable(product);
            case Divide:
                return new FloatWritable(input[0].toFloat() / input[1].toFloat());
            case Modulus:
                return new FloatWritable(input[0].toFloat() % input[1].toFloat());
            case ReverseSubtract:
            case ReverseDivide:
            case ScalarMin:
            case ScalarMax:
            default:
                throw new RuntimeException("Invalid mathOp: " + mathOp); //Should never happen
        }
    }

    @Override
    public String toString() {
        return "FloatColumnsMathOpTransform(newColumnName=\"" + newColumnName + "\",mathOp=" + mathOp + ",columns="
                        + Arrays.toString(columns) + ")";
    }

    /**
     * Transform an object
     * in to another object
     *
     * @param input the record to transform
     * @return the transformed writable
     */
    @Override
    public Object map(Object input) {
        List<Float> row = (List<Float>) input;
        switch (mathOp) {
            case Add:
                float sum = 0f;
                for (Float w : row)
                    sum += w;
                return sum;
            case Subtract:
                return row.get(0) - row.get(1);
            case Multiply:
                float product = 1.0f;
                for (Float w : row)
                    product *= w;
                return product;
            case Divide:
                return row.get(0) / row.get(1);
            case Modulus:
                return row.get(0) % row.get(1);
            case ReverseSubtract:
            case ReverseDivide:
            case ScalarMin:
            case ScalarMax:
            default:
                throw new RuntimeException("Invalid mathOp: " + mathOp); //Should never happen
        }
    }

    /**
     * Transform a sequence
     *
     * @param sequence
     */
    @Override
    public Object mapSequence(Object sequence) {
        List<List<Float>> seq = (List<List<Float>>) sequence;
        List<Float> ret = new ArrayList<>();
        for (List<Float> step : seq)
            ret.add((Float) map(step));
        return ret;
    }
}
