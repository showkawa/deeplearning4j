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

package org.nd4j.linalg.api.blas.params;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

@Getter
@EqualsAndHashCode
public class MMulTranspose implements Serializable {
    private static MMulTranspose allFalse = MMulTranspose.builder().build();
    private boolean transposeA;
    private boolean transposeB;
    private boolean transposeResult;


    @Builder
    public MMulTranspose(boolean transposeA, boolean transposeB, boolean transposeResult) {
        this.transposeA = transposeA;
        this.transposeB = transposeB;
        this.transposeResult = transposeResult;
    }

    /**
     * Returns the default transpose
     * where all are false
     *
     * @return
     */
    public static MMulTranspose allFalse() {
        return allFalse;
    }

    /**
     * Execute the matrix multiplication: A x B
     * Note that if a or b have transposeA/B == true, then this is done internally.
     * Also, if transposeResult == true, then this is also done internally - i.e., the result array - if present -
     * should not be transposed beforehand.
     * @param a      A array
     * @param b      B array
     * @param result Result array (pre resultArrayTranspose if required). May be null.
     * @return Result array
     */
    public INDArray exec(INDArray a, INDArray b, INDArray result) {
        a = transposeIfReq(transposeA, a);
        b = transposeIfReq(transposeB, b);
        if(result == null) {
            INDArray ret = a.mmul(b);
            return transposeIfReq(transposeResult, ret);
        } else {

            if(!transposeResult){
                return a.mmuli(b, result);
            } else {
                return a.mmuli(b, result).transpose();
            }
        }
    }

    private static INDArray transposeIfReq(boolean transpose, INDArray x){
        if (transpose) {
            if (x.rank() == 2)
                return x.transpose();
            if (x.rank() == 3)
                return x.permute(0, 2, 1);
        }
        return x;
    }

    public Object getValue(Field property) {
        try {
            return property.get(this);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Object> toProperties(){
        Map<String,Object> ret = new HashMap<>();
        ret.put("transposeA", transposeA);
        ret.put("transposeB", transposeB);
        ret.put("transposeResult", transposeResult);
        return ret;
    }

    public void setProperties(Map<String,Object> properties){
        if(properties.containsKey("transposeA"))
            transposeA = (Boolean)properties.get("transposeA");
        if(properties.containsKey("transposeB"))
            transposeB = (Boolean)properties.get("transposeB");
        if(properties.containsKey("transposeResult"))
            transposeResult = (Boolean)properties.get("transposeResult");
    }
}
