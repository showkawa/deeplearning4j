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

package org.nd4j.linalg.api;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.Pointer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.BaseNd4jTestWithBackends;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.common.io.ClassPathResource;
import org.nd4j.nativeblas.NativeOpsHolder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@NativeTag
public class TestNDArrayCreation extends BaseNd4jTestWithBackends {

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testBufferCreation(Nd4jBackend backend) {
        DataBuffer dataBuffer = Nd4j.createBuffer(new float[] {1, 2});
        Pointer pointer = dataBuffer.pointer();
        FloatPointer floatPointer = new FloatPointer(pointer);
        DataBuffer dataBuffer1 = Nd4j.createBuffer(floatPointer, 2, DataType.FLOAT);

        assertEquals(2, dataBuffer.length());
        assertEquals(1.0, dataBuffer.getDouble(0), 1e-1);
        assertEquals(2.0, dataBuffer.getDouble(1), 1e-1);

        assertEquals(2, dataBuffer1.length());
        assertEquals(1.0, dataBuffer1.getDouble(0), 1e-1);
        assertEquals(2.0, dataBuffer1.getDouble(1), 1e-1);
        INDArray arr = Nd4j.create(dataBuffer1);
        System.out.println(arr);
    }


    @Disabled
    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testCreateNpy() throws Exception {
        INDArray arrCreate = Nd4j.createFromNpyFile(new ClassPathResource("nd4j-tests/test.npy").getFile());
        assertEquals(2, arrCreate.size(0));
        assertEquals(2, arrCreate.size(1));
        assertEquals(1.0, arrCreate.getDouble(0, 0), 1e-1);
        assertEquals(2.0, arrCreate.getDouble(0, 1), 1e-1);
        assertEquals(3.0, arrCreate.getDouble(1, 0), 1e-1);
        assertEquals(4.0, arrCreate.getDouble(1, 1), 1e-1);

    }

    @Disabled
    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testCreateNpz(Nd4jBackend backend) throws Exception {
        Map<String, INDArray> map = Nd4j.createFromNpzFile(new ClassPathResource("nd4j-tests/test.npz").getFile());
        assertEquals(true, map.containsKey("x"));
        assertEquals(true, map.containsKey("y"));
        INDArray arrX = map.get("x");
        INDArray arrY = map.get("y");
        assertEquals(1.0, arrX.getDouble(0), 1e-1);
        assertEquals(2.0, arrX.getDouble(1), 1e-1);
        assertEquals(3.0, arrX.getDouble(2), 1e-1);
        assertEquals(4.0, arrX.getDouble(3), 1e-1);
        assertEquals(5.0, arrY.getDouble(0), 1e-1);
        assertEquals(6.0, arrY.getDouble(1), 1e-1);
        assertEquals(7.0, arrY.getDouble(2), 1e-1);
        assertEquals(8.0, arrY.getDouble(3), 1e-1);

    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testCreateNpy3(Nd4jBackend backend) throws Exception {
        INDArray arrCreate = Nd4j.createFromNpyFile(new ClassPathResource("nd4j-tests/rank3.npy").getFile());
        assertEquals(8, arrCreate.length());
        assertEquals(3, arrCreate.rank());

        Pointer pointer = NativeOpsHolder.getInstance().getDeviceNativeOps()
                        .pointerForAddress(arrCreate.data().address());
        assertEquals(arrCreate.data().address(), pointer.address());
    }

    @Disabled // this is endless test
    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testEndlessAllocation(Nd4jBackend backend) {
        Nd4j.getEnvironment().setMaxSpecialMemory(1);
        while (true) {
            val arr = Nd4j.createUninitialized(DataType.FLOAT, 100000000);
            arr.assign(1.0f);
        }
    }

    @Disabled("This test is designed to run in isolation. With parallel gc it makes no real sense since allocated amount changes at any time")
    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testAllocationLimits(Nd4jBackend backend) throws Exception {
        Nd4j.create(1);

        val origDeviceLimit = Nd4j.getEnvironment().getDeviceLimit(0);
        val origDeviceCount = Nd4j.getEnvironment().getDeviceCouner(0);

        val limit = origDeviceCount + 10000;

        Nd4j.getEnvironment().setDeviceLimit(0, limit);

        val array = Nd4j.createUninitialized(DataType.DOUBLE, 1024);
        assertNotNull(array);

        try {
            Nd4j.createUninitialized(DataType.DOUBLE, 1024);
            assertTrue(false);
        } catch (Exception e) {
            //
        }

        // we want to be sure there's nothing left after exception
        assertEquals(0, NativeOpsHolder.getInstance().getDeviceNativeOps().lastErrorCode());

        Nd4j.getEnvironment().setDeviceLimit(0, origDeviceLimit);

    }

    @Override
    public char ordering() {
        return 'c';
    }
}
