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

package org.nd4j.nativeblas;

import org.bytedeco.javacpp.annotation.Platform;
import org.bytedeco.javacpp.annotation.Properties;
import org.bytedeco.javacpp.tools.*;
import org.bytedeco.openblas.global.openblas;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 *
 * @author saudet
 */
@Properties(inherit = openblas.class, target = "org.nd4j.nativeblas.Nd4jCpu", helper = "org.nd4j.nativeblas.Nd4jCpuHelper",
                value = {@Platform(define = "LIBND4J_ALL_OPS", include = {
                                              "memory/MemoryType.h",
                                              "array/DataType.h",
                                              "array/DataBuffer.h",
                                              "array/PointerDeallocator.h",
                                              "array/PointerWrapper.h",
                                              "array/ConstantDataBuffer.h",
                                              "array/ConstantShapeBuffer.h",
                                              "array/ConstantOffsetsBuffer.h",
                                              "array/ConstantDescriptor.h",
                                              "array/TadPack.h",
                                              "execution/ErrorReference.h",
                                              "execution/Engine.h",
                                              "execution/ExecutionMode.h",
                                              "system/Environment.h",
                                              "types/utf8string.h",
                                              "legacy/NativeOps.h",
                                              "build_info.h",
                                              "memory/ExternalWorkspace.h",
                                              "memory/Workspace.h",
                                              "indexing/NDIndex.h",
                                              "indexing/IndicesList.h",
                                              "graph/VariableType.h",
                                              "graph/ArgumentsList.h",
                                              "types/pair.h",
                                              "array/NDArray.h",
                                              "array/NDArrayList.h",
                                              "array/ResultSet.h",
                                              "types/pair.h",
                                              "graph/RandomGenerator.h",
                                              "graph/Variable.h",
                                              "graph/VariablesSet.h",
                                              "graph/FlowPath.h",
                                              "graph/Intervals.h",
                                              "graph/Stash.h",
                                              "graph/GraphState.h",
                                              "graph/VariableSpace.h",
                                              "helpers/helper_generator.h",
                                              "graph/profiling/GraphProfile.h",
                                              "graph/profiling/NodeProfile.h",
                                              "graph/Context.h",
                                              "graph/ContextPrototype.h",
                                              "graph/ResultWrapper.h",
                                              "helpers/shape.h",
                                              "helpers/OpArgsHolder.h",
                                              "array/ShapeList.h",
                                              "system/type_boilerplate.h",
                                              "system/op_boilerplate.h",
                                              //"enum_boilerplate.h",
                                              //"op_enums.h",
                                              "ops/InputType.h",
                                              "ops/declarable/OpDescriptor.h",
                                              "ops/declarable/PlatformHelper.h",
                                              "ops/declarable/BroadcastableOp.h",
                                              "ops/declarable/BroadcastableBoolOp.h",
                                              "ops/declarable/DeclarableOp.h",
                                              "ops/declarable/DeclarableListOp.h",
                                              "ops/declarable/DeclarableReductionOp.h",
                                              "ops/declarable/DeclarableCustomOp.h",
                                              "ops/declarable/BooleanOp.h",
                                              "ops/declarable/LogicOp.h",
                                              "ops/declarable/OpRegistrator.h",
                                              "ops/declarable/CustomOperations.h",
                                              "ops/declarable/headers/activations.h",
                                              "ops/declarable/headers/boolean.h",
                                              "ops/declarable/headers/broadcastable.h",
                                              "ops/declarable/headers/convo.h",
                                              "ops/declarable/headers/list.h",
                                              "ops/declarable/headers/recurrent.h",
                                              "ops/declarable/headers/transforms.h",
                                              "ops/declarable/headers/parity_ops.h",
                                              "ops/declarable/headers/shape.h",
                                              "ops/declarable/headers/random.h",
                                              "ops/declarable/headers/nn.h",
                                              "ops/declarable/headers/blas.h",
                                              "ops/declarable/headers/tests.h",
                                              "ops/declarable/headers/bitwise.h",
                                              "ops/declarable/headers/loss.h",
                                              "ops/declarable/headers/datatypes.h",
                                              "execution/ContextBuffers.h",
                                              "execution/LaunchContext.h",
                                              "array/ShapeDescriptor.h",
                                              "array/TadDescriptor.h",
                                              "helpers/DebugInfo.h",
                                              "ops/declarable/headers/third_party.h"},
                                   exclude = {"ops/declarable/headers/activations.h",
                                              "ops/declarable/headers/boolean.h",
                                              "ops/declarable/headers/broadcastable.h",
                                              "ops/declarable/headers/convo.h",
                                              "ops/declarable/headers/list.h",
                                              "ops/declarable/headers/recurrent.h",
                                              "ops/declarable/headers/transforms.h",
                                              "ops/declarable/headers/parity_ops.h",
                                              "ops/declarable/headers/shape.h",
                                              "ops/declarable/headers/random.h",
                                              "ops/declarable/headers/nn.h",
                                              "ops/declarable/headers/blas.h",
                                              "ops/declarable/headers/bitwise.h",
                                              "ops/declarable/headers/tests.h",
                                              "ops/declarable/headers/loss.h",
                                              "ops/declarable/headers/datatypes.h",
                                              "ops/declarable/headers/third_party.h",
                                              "openblas_config.h",
                                              "cblas.h",
                                              "lapacke_config.h",
                                              "lapacke_mangling.h",
                                              "lapack.h",
                                              "lapacke.h",
                                              "lapacke_utils.h",
                                              "cnpy/cnpy.h"
                                   },
                                compiler = {"cpp11", "nowarnings"},
                                library = "jnind4jcpu", link = "nd4jcpu", preload = "libnd4jcpu"),
                @Platform(value = "linux", preload = "gomp@.1", preloadpath = {"/lib64/", "/lib/", "/usr/lib64/", "/usr/lib/"}),
                @Platform(value = "linux-armhf", preloadpath = {"/usr/arm-linux-gnueabihf/lib/", "/usr/lib/arm-linux-gnueabihf/"}),
                @Platform(value = "linux-arm64", preloadpath = {"/usr/aarch64-linux-gnu/lib/", "/usr/lib/aarch64-linux-gnu/"}),
                @Platform(value = "linux-ppc64", preloadpath = {"/usr/powerpc64-linux-gnu/lib/", "/usr/powerpc64le-linux-gnu/lib/", "/usr/lib/powerpc64-linux-gnu/", "/usr/lib/powerpc64le-linux-gnu/"}),
                @Platform(value = "windows", preload = {"libwinpthread-1", "libgcc_s_seh-1", "libgomp-1", "libstdc++-6", "libnd4jcpu"}),
                @Platform(extension = {"-onednn", "-onednn-avx512","-onednn-avx2","-","-avx2","-avx512","-compat"}) })
public class Nd4jCpuPresets implements InfoMapper, BuildEnabled {

    private Logger logger;
    private java.util.Properties properties;
    private String encoding;

    @Override
    public void init(Logger logger, java.util.Properties properties, String encoding) {
        this.logger = logger;
        this.properties = properties;
        this.encoding = encoding;
    }

    @Override
    public void map(InfoMap infoMap) {
        infoMap.put(new Info("thread_local", "ND4J_EXPORT", "INLINEDEF", "CUBLASWINAPI", "FORCEINLINE",
                             "_CUDA_H", "_CUDA_D", "_CUDA_G", "_CUDA_HD", "LIBND4J_ALL_OPS", "NOT_EXCLUDED").cppTypes().annotations())
               .put(new Info("openblas_config.h", "cblas.h", "lapacke_config.h", "lapacke_mangling.h", "lapack.h", "lapacke.h", "lapacke_utils.h").skip())
                        .put(new Info("NativeOps.h", "build_info.h").objectify())
                        .put(new Info("OpaqueTadPack").pointerTypes("OpaqueTadPack"))
                        .put(new Info("OpaqueResultWrapper").pointerTypes("OpaqueResultWrapper"))
                        .put(new Info("OpaqueShapeList").pointerTypes("OpaqueShapeList"))
                        .put(new Info("OpaqueVariablesSet").pointerTypes("OpaqueVariablesSet"))
                        .put(new Info("OpaqueVariable").pointerTypes("OpaqueVariable"))
                        .put(new Info("OpaqueConstantDataBuffer").pointerTypes("OpaqueConstantDataBuffer"))
                        .put(new Info("OpaqueConstantShapeBuffer").pointerTypes("OpaqueConstantShapeBuffer"))
                        .put(new Info("OpaqueConstantOffsetsBuffer").pointerTypes("OpaqueConstantOffsetsBuffer"))
                        .put(new Info("OpaqueDataBuffer").pointerTypes("OpaqueDataBuffer"))
                        .put(new Info("OpaqueContext").pointerTypes("OpaqueContext"))
                        .put(new Info("OpaqueRandomGenerator").pointerTypes("OpaqueRandomGenerator"))
                        .put(new Info("OpaqueLaunchContext").pointerTypes("OpaqueLaunchContext"))
                        .put(new Info("const char").valueTypes("byte").pointerTypes("@Cast(\"char*\") String",
                                        "@Cast(\"char*\") BytePointer"))
                        .put(new Info("char").valueTypes("char").pointerTypes("@Cast(\"char*\") BytePointer",
                                        "@Cast(\"char*\") String"))
                        .put(new Info("Nd4jPointer").cast().valueTypes("Pointer").pointerTypes("PointerPointer"))
                        .put(new Info("Nd4jLong").cast().valueTypes("long").pointerTypes("LongPointer", "LongBuffer",
                                        "long[]"))
                        .put(new Info("Nd4jStatus").cast().valueTypes("int").pointerTypes("IntPointer", "IntBuffer",
                                        "int[]"))
                        .put(new Info("float16").cast().valueTypes("short").pointerTypes("ShortPointer", "ShortBuffer",
                                        "short[]"))
                        .put(new Info("bfloat16").cast().valueTypes("short").pointerTypes("ShortPointer", "ShortBuffer",
                                        "short[]"));

        infoMap.put(new Info("__CUDACC__", "MAX_UINT", "HAVE_MKLDNN", "__CUDABLAS__").define(false))
               .put(new Info("__JAVACPP_HACK__", "LIBND4J_ALL_OPS").define(true))
               .put(new Info("std::initializer_list", "cnpy::NpyArray", "sd::NDArray::applyLambda", "sd::NDArray::applyPairwiseLambda",
                             "sd::graph::FlatResult", "sd::graph::FlatVariable", "sd::NDArray::subarray", "std::shared_ptr", "sd::PointerWrapper", "sd::PointerDeallocator").skip())
               .put(new Info("std::string").annotations("@StdString").valueTypes("BytePointer", "String")
                                           .pointerTypes("@Cast({\"char*\", \"std::string*\"}) BytePointer"))
               .put(new Info("std::pair<int,int>").pointerTypes("IntIntPair").define())
               .put(new Info("std::vector<std::vector<int> >").pointerTypes("IntVectorVector").define())
               .put(new Info("std::vector<std::vector<Nd4jLong> >").pointerTypes("LongVectorVector").define())
               .put(new Info("std::vector<const sd::NDArray*>").pointerTypes("ConstNDArrayVector").define())
               .put(new Info("std::vector<sd::NDArray*>").pointerTypes("NDArrayVector").define())
               .put(new Info("sd::graph::ResultWrapper").base("org.nd4j.nativeblas.ResultWrapperAbstraction").define())
               .put(new Info("bool").cast().valueTypes("boolean").pointerTypes("BooleanPointer", "boolean[]"))
               .put(new Info("sd::IndicesList").purify());

        /*
        String classTemplates[] = {
                "sd::NDArray",
                "sd::NDArrayList",
                "sd::ResultSet",
                "sd::OpArgsHolder",
                "sd::graph::GraphState",
                "sd::graph::Variable",
                "sd::graph::VariablesSet",
                "sd::graph::Stash",
                "sd::graph::VariableSpace",
                "sd::graph::Context",
                "sd::graph::ContextPrototype",
                "sd::ops::DeclarableOp",
                "sd::ops::DeclarableListOp",
                "sd::ops::DeclarableReductionOp",
                "sd::ops::DeclarableCustomOp",
                "sd::ops::BooleanOp",
                "sd::ops::BroadcastableOp",
                "sd::ops::LogicOp"};
        for (String t : classTemplates) {
            String s = t.substring(t.lastIndexOf(':') + 1);
            infoMap.put(new Info(t + "<float>").pointerTypes("Float" + s))
                   .put(new Info(t + "<float16>").pointerTypes("Half" + s))
                   .put(new Info(t + "<double>").pointerTypes("Double" + s));
        }
        */

        // pick up custom operations automatically from CustomOperations.h and headers in libnd4j
        String separator = properties.getProperty("platform.path.separator");
        String[] includePaths = properties.getProperty("platform.includepath").split(separator);
        File file = null;
        for (String path : includePaths) {
            file = new File(path, "ops/declarable/CustomOperations.h");
            if (file.exists()) {
                break;
            }
        }
        List<File> files = new ArrayList<>();
        List<String> opTemplates = new ArrayList<>();
        if(file == null) {
            throw new IllegalStateException("No file found in include paths. Please ensure one of the include paths leads to path/ops/declarable/CustomOperations.h");
        }
        files.add(file);
        File[] headers = new File(file.getParent(), "headers").listFiles();
        if(headers == null) {
            throw new IllegalStateException("No headers found for file " + file.getAbsolutePath());
        }
        files.addAll(Arrays.asList(headers));
        Collections.sort(files);
        for (File f : files) {
            try (Scanner scanner = new Scanner(f, "UTF-8")) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (line.startsWith("DECLARE_")) {
                        try {
                            int start = line.indexOf('(') + 1;
                            int end = line.indexOf(',');
                            if (end < start) {
                                end = line.indexOf(')');
                            }
                            String name = line.substring(start, end).trim();
                            opTemplates.add(name);
                        } catch(Exception e) {
                            throw new RuntimeException("Could not parse line from CustomOperations.h and headers: \"" + line + "\"", e);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Could not parse CustomOperations.h and headers", e);
            }
        }
        logger.info("Ops found in CustomOperations.h and headers: " + opTemplates);
        /*
        String floatOps = "", halfOps = "", doubleOps = "";
        for (String t : opTemplates) {
            String s = "sd::ops::" + t;
            infoMap.put(new Info(s + "<float>").pointerTypes("float_" + t))
                   .put(new Info(s + "<float16>").pointerTypes("half_" + t))
                   .put(new Info(s + "<double>").pointerTypes("double_" + t));
            floatOps  += "\n        float_" + t + ".class,";
            halfOps   += "\n        half_" + t + ".class,";
            doubleOps += "\n        double_" + t + ".class,";

        }
        infoMap.put(new Info().javaText("\n"
                                      + "    Class[] floatOps = {" + floatOps + "};" + "\n"
                                      + "    Class[] halfOps = {" + halfOps + "};" + "\n"
                                      + "    Class[] doubleOps = {" + doubleOps + "};"));
        */
        infoMap.put(new Info("sd::ops::OpRegistrator::updateMSVC").skip());
    }
}
