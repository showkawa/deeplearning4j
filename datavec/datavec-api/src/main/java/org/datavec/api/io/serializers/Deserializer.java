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

package org.datavec.api.io.serializers;

import java.io.IOException;
import java.io.InputStream;

public interface Deserializer<T> {
    /**
     * <p>Prepare the deserializer for reading.</p>
     */
    void open(InputStream in) throws IOException;

    /**
     * <p>
     * Deserialize the next object from the underlying input stream.
     * If the object <code>t</code> is non-null then this deserializer
     * <i>may</i> set its internal state to the next object read from the input
     * stream. Otherwise, if the object <code>t</code> is null a new
     * deserialized object will be created.
     * </p>
     * @return the deserialized object
     */
    T deserialize(T t) throws IOException;

    /**
     * <p>Close the underlying input stream and clear up any resources.</p>
     */
    void close() throws IOException;
}
