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

package org.nd4j.linalg.compression;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.BaseNd4jTestWithBackends;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NativeTag
@Tag(TagNames.COMPRESSION)
public class CompressionSerDeTests extends BaseNd4jTestWithBackends {


    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testAutoDecompression2(Nd4jBackend backend) throws Exception {
        INDArray array = Nd4j.linspace(1, 10, 11, DataType.DOUBLE);

        INDArray compressed = Nd4j.getCompressor().compress(array, "GZIP");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Nd4j.write(bos, compressed);

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());

        System.out.println("Restoring -------------------------");

        INDArray result = Nd4j.read(bis);

        System.out.println("Decomp -------------------------");

        INDArray decomp = Nd4j.getCompressor().decompress(result);

        assertEquals(array, decomp);
    }

    @Override
    public char ordering() {
        return 'c';
    }
}
