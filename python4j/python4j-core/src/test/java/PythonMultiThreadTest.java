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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.python4j.*;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.bytedeco.cpython.global.python.PyGILState_Check;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@NotThreadSafe
@Tag(TagNames.FILE_IO)
@NativeTag
@Tag(TagNames.PYTHON)
@Tag(TagNames.MULTI_THREADED)
public class PythonMultiThreadTest {

    @Test
    public void testMultiThreading1()throws Throwable{
        final List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<Throwable>());
        Runnable runnable = () -> {
            try(PythonGIL gil = PythonGIL.lock()){
                try(PythonGC gc = PythonGC.watch()){
                    List<PythonVariable> inputs = new ArrayList<>();
                    inputs.add(new PythonVariable<>("x", PythonTypes.STR, "Hello "));
                    inputs.add(new PythonVariable<>("y", PythonTypes.STR, "World"));
                    PythonVariable out = new PythonVariable<>("z", PythonTypes.STR);
                    String code = "z = x + y";
                    PythonExecutioner.exec(code, inputs, Collections.singletonList(out));
                    assertEquals("Hello World", out.getValue());
                    System.out.println(out.getValue() + " From thread " + Thread.currentThread().getId());
                }
            }catch (Throwable e){
                exceptions.add(e);
            }
        };

        int numThreads = 10;
        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < threads.length; i++){
            threads[i] = new Thread(runnable);
        }
        for (int i = 0; i < threads.length; i++){
            threads[i].start();
        }
        Thread.sleep(100);
        for (int i = 0; i < threads.length; i++){
            threads[i].join();
        }
        if (!exceptions.isEmpty()){
            throw(exceptions.get(0));
        }

    }
    @Test
    public void testMultiThreading2()throws Throwable{
        final List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<Throwable>());
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try(PythonGIL gil = PythonGIL.lock()){
                    try(PythonGC gc = PythonGC.watch()){
                        PythonContextManager.reset();
                        PythonContextManager.reset();
                        List<PythonVariable> inputs = new ArrayList<>();
                        inputs.add(new PythonVariable<>("a", PythonTypes.INT, 5));
                        String code = "b = '10'\nc = 20.0 + a";
                        List<PythonVariable> vars = PythonExecutioner.execAndReturnAllVariables(code, inputs);

                        assertEquals("a", vars.get(0).getName());
                        assertEquals(PythonTypes.INT, vars.get(0).getType());
                        assertEquals(5L, (long)vars.get(0).getValue());

                        assertEquals("b", vars.get(1).getName());
                        assertEquals(PythonTypes.STR, vars.get(1).getType());
                        assertEquals("10", vars.get(1).getValue().toString());

                        assertEquals("c", vars.get(2).getName());
                        assertEquals(PythonTypes.FLOAT, vars.get(2).getType());
                        assertEquals(25.0, (double)vars.get(2).getValue(), 1e-5);
                    }
                }catch (Throwable e){
                    exceptions.add(e);
                }
            }
        };

        int numThreads = 10;
        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < threads.length; i++){
            threads[i] = new Thread(runnable);
        }
        for (int i = 0; i < threads.length; i++){
            threads[i].start();
        }
        Thread.sleep(100);
        for (int i = 0; i < threads.length; i++){
            threads[i].join();
        }
        if (!exceptions.isEmpty()){
            throw(exceptions.get(0));
        }
    }




    @Test
    public void testWorkerThreadLongRunning() throws Exception {
        int numThreads = 8;
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        new PythonExecutioner();
        final AtomicInteger finishedExecutionCount = new AtomicInteger(0);
        for(int i = 0; i < numThreads * 2; i++) {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try(PythonGIL pythonGIL = PythonGIL.lock()) {
                        System.out.println("Using thread " + Thread.currentThread().getId() + " to invoke python");
                        assertTrue(PyGILState_Check() > 0,"Thread " + Thread.currentThread().getId() + " does not hold the gil.");
                        PythonExecutioner.exec("import time; time.sleep(10)");
                        System.out.println("Finished execution on thread " + Thread.currentThread().getId());
                        finishedExecutionCount.incrementAndGet();
                    }
                }
            });

        }

        executorService.awaitTermination(3, TimeUnit.MINUTES);
        assertEquals(numThreads * 2,finishedExecutionCount.get());


    }


}
