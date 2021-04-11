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

package org.datavec.api.transform.transform.string;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.datavec.api.writable.Text;
import org.datavec.api.writable.Writable;
import org.nd4j.shade.jackson.annotation.JsonProperty;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class StringMapTransform extends BaseStringTransform {

    private final Map<String, String> map;

    /**
     *
     * @param columnName Name of the column
     * @param map Key: From. Value: To
     */
    public StringMapTransform(@JsonProperty("columnName") String columnName,
                    @JsonProperty("map") Map<String, String> map) {
        super(columnName);
        this.map = map;
    }

    @Override
    public Text map(Writable writable) {
        String orig = writable.toString();
        if (map.containsKey(orig)) {
            return new Text(map.get(orig));
        }

        if (writable instanceof Text)
            return (Text) writable;
        else
            return new Text(writable.toString());
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
        String orig = input.toString();
        if (map.containsKey(orig)) {
            return map.get(orig);
        }

        if (input instanceof String)
            return input;
        else
            return orig;
    }
}
