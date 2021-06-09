/*
 *
 *  *  ******************************************************************************
 *  *  *
 *  *  *
 *  *  * This program and the accompanying materials are made available under the
 *  *  * terms of the Apache License, Version 2.0 which is available at
 *  *  * https://www.apache.org/licenses/LICENSE-2.0.
 *  *  *
 *  *  *  See the NOTICE file distributed with this work for additional
 *  *  *  information regarding copyright ownership.
 *  *  * Unless required by applicable law or agreed to in writing, software
 *  *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  *  * License for the specific language governing permissions and limitations
 *  *  * under the License.
 *  *  *
 *  *  * SPDX-License-Identifier: Apache-2.0
 *  *  *****************************************************************************
 *
 *
 */

package org.nd4j.python4j;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.nativeblas.OpaqueDataBuffer;

import javax.annotation.concurrent.NotThreadSafe;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NotThreadSafe
@Tag(TagNames.FILE_IO)
@NativeTag
@Tag(TagNames.PYTHON)
public class PythonNumpyBasicTest {


    @BeforeAll
    public static void init() {
        new NumpyArray().init();
    }

    public static Stream<Arguments> params() {
        DataType[] types = new DataType[] {
                DataType.BOOL,
                DataType.FLOAT16,
                DataType.BFLOAT16,
                DataType.FLOAT,
                DataType.DOUBLE,
                DataType.INT8,
                DataType.INT16,
                DataType.INT32,
                DataType.INT64,
                DataType.UINT8,
                DataType.UINT16,
                DataType.UINT32,
                DataType.UINT64
        };

        long[][] shapes = new long[][]{
                new long[]{2, 3},
                new long[]{3},
                new long[]{1},
                new long[]{} // scalar
        };


        List<Object[]> ret = new ArrayList<>();
        for (DataType type: types){
            for (long[] shape: shapes){
                ret.add(new Object[]{type, shape, Arrays.toString(shape)});
            }
        }
        return ret.stream().map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.python4j.PythonNumpyBasicTest#params")
    public void testConversion(DataType dataType,long[] shape) {
        try(PythonGIL pythonGIL = PythonGIL.lock()) {
            INDArray arr = Nd4j.zeros(dataType, shape);
            PythonObject npArr = PythonTypes.convert(arr);
            INDArray arr2 = PythonTypes.<INDArray>getPythonTypeForPythonObject(npArr).toJava(npArr);
            if (dataType == DataType.BFLOAT16){
                arr = arr.castTo(DataType.FLOAT);
            }
            assertEquals(arr,arr2);
        }

    }


    @ParameterizedTest
    @MethodSource("org.nd4j.python4j.PythonNumpyBasicTest#params")
    public void testExecution(DataType dataType,long[] shape) {
        try(PythonGIL pythonGIL = PythonGIL.lock()) {
            List<PythonVariable> inputs = new ArrayList<>();
            INDArray x = Nd4j.ones(dataType, shape);
            INDArray y = Nd4j.zeros(dataType, shape);
            INDArray z = (dataType == DataType.BOOL)?x:x.mul(y.add(2));
            z = (dataType == DataType.BFLOAT16)? z.castTo(DataType.FLOAT): z;
            PythonType<INDArray> arrType = PythonTypes.get("numpy.ndarray");
            inputs.add(new PythonVariable<>("x", arrType, x));
            inputs.add(new PythonVariable<>("y", arrType, y));
            List<PythonVariable> outputs = new ArrayList<>();
            PythonVariable<INDArray> output = new PythonVariable<>("z", arrType);
            outputs.add(output);
            String code = (dataType == DataType.BOOL)?"z = x":"z = x * (y + 2)";
            if (shape.length == 0){ // scalar special case
                code += "\nimport numpy as np\nz = np.asarray(float(z), dtype=x.dtype)";
            }
            PythonExecutioner.exec(code, inputs, outputs);
            INDArray z2 = output.getValue();

            assertEquals(z.dataType(), z2.dataType());
            assertEquals(z, z2);
        }


    }


    @ParameterizedTest
    @MethodSource("org.nd4j.python4j.PythonNumpyBasicTest#params")
    public void testInplaceExecution(DataType dataType,long[] shape) {
        try(PythonGIL pythonGIL = PythonGIL.lock()) {
            if (dataType == DataType.BOOL || dataType == DataType.BFLOAT16)return;
            if (shape.length == 0) return;
            List<PythonVariable> inputs = new ArrayList<>();
            INDArray x = Nd4j.ones(dataType, shape);
            INDArray y = Nd4j.zeros(dataType, shape);
            INDArray z = x.mul(y.add(2));
            // Nd4j.getAffinityManager().ensureLocation(z, AffinityManager.Location.HOST);
            PythonType<INDArray> arrType = PythonTypes.get("numpy.ndarray");
            inputs.add(new PythonVariable<>("x", arrType, x));
            inputs.add(new PythonVariable<>("y", arrType, y));
            List<PythonVariable> outputs = new ArrayList<>();
            PythonVariable<INDArray> output = new PythonVariable<>("x", arrType);
            outputs.add(output);
            String code = "x *= y + 2";
            PythonExecutioner.exec(code, inputs, outputs);
            INDArray z2 = output.getValue();
            assertEquals(x.dataType(), z2.dataType());
            assertEquals(z.dataType(), z2.dataType());
            assertEquals(x, z2);
            assertEquals(z, z2);
            assertEquals(x.data().pointer().address(), z2.data().pointer().address());
            if("CUDA".equalsIgnoreCase(Nd4j.getExecutioner().getEnvironmentInformation().getProperty("backend"))){
                assertEquals(getDeviceAddress(x), getDeviceAddress(z2));
            }

        }


    }


    private static long getDeviceAddress(INDArray array) {
        if(!"CUDA".equalsIgnoreCase(Nd4j.getExecutioner().getEnvironmentInformation().getProperty("backend"))){
            throw new IllegalStateException("Cannot ge device pointer for non-CUDA device");
        }

        //Use reflection here as OpaqueDataBuffer is only available on BaseCudaDataBuffer and BaseCpuDataBuffer - not DataBuffer/BaseDataBuffer
        // due to it being defined in nd4j-native-api, not nd4j-api
        try {
            Class<?> c = Class.forName("org.nd4j.linalg.jcublas.buffer.BaseCudaDataBuffer");
            Method m = c.getMethod("getOpaqueDataBuffer");
            OpaqueDataBuffer db = (OpaqueDataBuffer) m.invoke(array.data());
            long address = db.specialBuffer().address();
            return address;
        } catch (Throwable t){
            throw new RuntimeException("Error getting OpaqueDataBuffer", t);
        }
    }




}
