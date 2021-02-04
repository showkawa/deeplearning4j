/*******************************************************************************
 * Copyright (c) 2015-2018 Skymind, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

package org.nd4j.imports.converters;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.nd4j.autodiff.functions.DifferentialFunction;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.imports.descriptors.onnx.OnnxDescriptorParser;
import org.nd4j.imports.descriptors.onnx.OpDescriptor;
import org.nd4j.imports.descriptors.tensorflow.TensorflowDescriptorParser;
import org.nd4j.linalg.api.ops.*;
import org.nd4j.linalg.api.ops.impl.controlflow.compat.*;
import org.nd4j.linalg.api.ops.impl.layers.ExternalErrorsFunction;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.nd4j.linalg.factory.Nd4j;
import org.tensorflow.framework.OpDef;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

@Slf4j
public class DifferentialFunctionClassHolder {
    private Map<String, DifferentialFunction> nodeConverters = ImportClassMapping.getOpNameMapping();
    private Map<String, DifferentialFunction> tensorFlowNames = ImportClassMapping.getTFOpMappingFunctions();
    private Map<String, DifferentialFunction> onnxNames = ImportClassMapping.getOnnxOpMappingFunctions();
    private Map<Long,Class<?>> customOpHashToClass = new HashMap<>();
    private Map<Long,Map<String,Class<?>>> customOpHashToClasses = new HashMap<>(); //Only contains ops with 1 hash to multiple classes
    private List<String> missingOps = new ArrayList<>();

    private Map<String,OpDescriptor> onnxOpDescriptors;
    private Map<String,OpDef> tensorflowOpDescriptors;
    private Map<String,Map<String,Field>> fieldsForFunction;

    private static final Set<String>  fieldNamesOpsIgnore = new LinkedHashSet<String>(){{
        add("extraArgs");
        add("arrayInitialized");
        add("log");
        add("inputArguments");
        add("outputArguments");
        add("outputShapes");
        add("outputVariables");
        add("tArguments");
        add("iArguments");
        add("bArguments");
        add("dArguments");
        add("hash");
        add("opName");
        add("sameDiff");
        add("ownName");
    }};
    //When determining fields/properties, where should we terminate the search?
    //We don't wan to include every single field from every single superclass
    private static final Set<Class> classesToIgnore = new HashSet<>(Arrays.<Class>asList(
            Object.class
//            BaseOp.class    //Exclude x/y/z, n, numProcessed, extraArgs, etc
    ));

    private static final Map<Class<?>,Set<String>> classFieldsToIgnore = new HashMap<>();
    static {
        classFieldsToIgnore.put(BaseOp.class, new HashSet<>(Arrays.asList("x", "y", "z", "n", "numProcessed", "xVertexId", "yVertexId", "zVertexId", "extraArgz")));
    }

    @Getter
    private int countTotalTfOps;
    @Getter
    private int countTotalMappedOps;

    private static DifferentialFunctionClassHolder INSTANCE = new DifferentialFunctionClassHolder();

    /**
     * Get the fields for a given {@link DifferentialFunction}
     * @param function the function to get the fields for
     * @return the fields for a given function
     */
    public Map<String,Field> getFieldsForFunction(DifferentialFunction function) {
        return fieldsForFunction.get(function.opName());
    }

    /**
     * Get the op definition of a given
     * tensorflow op.
     *
     * Note that if the name does not exist,
     * an {@link ND4JIllegalStateException} will be thrown
     * @param name the name of the op
     * @return the op definition for a given op
     */
    public OpDef getOpDefByTensorflowName(String name) {
        if(!tensorflowOpDescriptors.containsKey(name)) {
            throw new ND4JIllegalStateException("No op found with name " + name);
        }

        return tensorflowOpDescriptors.get(name);
    }

    /**
     * Get the op definition of a given
     * onnx op
     * Note that if the name does not exist,
     * an {@link ND4JIllegalStateException}
     * will be thrown.
     * @param name the name of the op
     * @return the op definition for a given op
     */
    public OpDescriptor getOpDescriptorForOnnx(String name) {
        if(!onnxOpDescriptors.containsKey(name)) {
            throw new ND4JIllegalStateException("No op found with name " + name);
        }

        return onnxOpDescriptors.get(name);
    }

    /**
     * Get the
     * @param tensorflowName
     * @return
     */
    public DifferentialFunction getOpWithTensorflowName(String tensorflowName) {
        return tensorFlowNames.get(tensorflowName);
    }

    public DifferentialFunction getOpWithOnnxName(String onnxName) {
        return onnxNames.get(onnxName);
    }

    private DifferentialFunctionClassHolder() {
        fieldsForFunction = new LinkedHashMap<>();

        for(DifferentialFunction df : ImportClassMapping.getOpNameMapping().values()){

            try {
                //accumulate the field names for a given function
                //this is mainly used in import
                Map<String, Field> fieldNames = new LinkedHashMap<>();
                Class<? extends DifferentialFunction> current = df.getClass();
                val fields = new ArrayList<Field>();
                boolean isFirst = true;

                while (current.getSuperclass() != null && !classesToIgnore.contains(current.getSuperclass())) {

                    if (df.isConfigProperties() && isFirst) {

                        String fieldName = df.configFieldName();

                        if(fieldName == null)
                            fieldName = "config";

                        Field configField = null;
                        try{
                            configField = current.getDeclaredField(fieldName);
                        } catch (NoSuchFieldException e){
                            Class<?> currentConfig = current.getSuperclass();

                            // find a config field in superclasses
                            while(currentConfig.getSuperclass() != null){
                                try {
                                    configField = currentConfig.getDeclaredField(fieldName);
                                    break;
                                } catch (NoSuchFieldException e2){
                                    currentConfig = currentConfig.getSuperclass();
                                }
                            }
                        }

                        if(configField == null)
                            continue;

                        val configFieldClass = configField.getType();

                        for (val field : configFieldClass.getDeclaredFields()) {
                            if (!Modifier.isStatic(field.getModifiers()) && !fieldNamesOpsIgnore.contains(field.getName()) &&
                                    (!classFieldsToIgnore.containsKey(current) || !classFieldsToIgnore.get(current).contains(field.getName()))) {
                                fields.add(field);
                                field.setAccessible(true);
                                if (fieldNames.containsKey(field.getName())) {
                                    throw new IllegalStateException("Field with name " + field.getName() + " exists for multiple classes: "
                                            + fieldNames.get(field.getName()).getDeclaringClass().getName() + " and " + field.getDeclaringClass().getName());
                                }
                                fieldNames.put(field.getName(), field);
                            }
                        }
                    } else {
                        for (Field field : current.getDeclaredFields()) {
                            if (!Modifier.isStatic(field.getModifiers()) && !fieldNamesOpsIgnore.contains(field.getName()) &&
                                    (!classFieldsToIgnore.containsKey(current) || !classFieldsToIgnore.get(current).contains(field.getName()))) {
                                fields.add(field);
                                field.setAccessible(true);
                                if (fieldNames.containsKey(field.getName())) {
                                    throw new IllegalStateException("Field with name " + field.getName() + " exists for multiple classes: "
                                            + fieldNames.get(field.getName()).getDeclaringClass().getName() + " and " + field.getDeclaringClass().getName());
                                }
                                fieldNames.put(field.getName(), field);
                            }
                        }
                    }

                    // do something with current's fields
                    current = (Class<? extends DifferentialFunction>) current.getSuperclass();
                    isFirst = false;

                }

                fieldsForFunction.put(df.opName(), fieldNames);
            } catch (NoOpNameFoundException e) {
                log.trace("Skipping function  " + df.getClass());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        //get the op descriptors for onnx and tensorflow
        //this is used when validating operations
        try {
            tensorflowOpDescriptors = TensorflowDescriptorParser.opDescs();
            onnxOpDescriptors = OnnxDescriptorParser.onnxOpDescriptors();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        val map = new HashMap<>(Nd4j.getExecutioner().getCustomOperations());
        val set = map.keySet();
        set.removeAll(nodeConverters.keySet());
        missingOps.addAll(set);
        Collections.sort(missingOps);
        //log.debug("Missing " + set.size() + " ops!");

        countTotalTfOps = tensorflowOpDescriptors.size();

        //Work out total number of TF ops mapped
        Set<String> tfMappedOps = new HashSet<>();
        for(DifferentialFunction df : nodeConverters.values()){
            try{
                String[] tfNames = df.tensorflowNames();
                Collections.addAll(tfMappedOps, tfNames);
            } catch (NoOpNameFoundException e){
                //Ignore
            }
        }
        countTotalMappedOps = tfMappedOps.size();

        //Get custom ops - map from hash to class
        Map<String,CustomOpDescriptor> descriptorMap = Nd4j.getExecutioner().getCustomOperations();
        Set<Long> multiClassHashes = new HashSet<>();
        for (Map.Entry<String, CustomOpDescriptor> e : descriptorMap.entrySet()) {
            String name = e.getKey();
            DifferentialFunction df = getInstance(name);

            if (df == null) {
                //Can be no class for 2 reasons:
                //(a) op name aliases
                //(b) libnd4j ops with no corresponding ND4J op class
                continue;
            }

            if (!CustomOp.class.isAssignableFrom(df.getClass())) {
                //Not a custom op class
                continue;
            }

            long h = e.getValue().getHash();
            if (customOpHashToClass.containsKey(h)) {
                //One op hash mapped to multiple classes
                multiClassHashes.add(h);
            }
            customOpHashToClass.put(e.getValue().getHash(), df.getClass());
        }

        for (Map.Entry<String, CustomOpDescriptor> e : descriptorMap.entrySet()) {
            long h = e.getValue().getHash();
            if (multiClassHashes.contains(h)) {
                if (!customOpHashToClasses.containsKey(h)) {
                    customOpHashToClasses.put(h, new HashMap<String, Class<?>>());
                }
                Map<String, Class<?>> m = customOpHashToClasses.get(h);
                String name = e.getKey();
                DifferentialFunction df = getInstance(name);
                if(df == null)
                    continue;
                m.put(e.getKey(), df.getClass());
            }
        }
    }


    /***
     * Returns the missing onnx ops
     * @return
     */
    public Set<String> missingOnnxOps() {
        Set<String> copy = new HashSet<>(onnxOpDescriptors.keySet());
        copy.removeAll(onnxNames.keySet());
        return copy;
    }


    /***
     * Returns the missing tensorflow ops
     * @return
     */
    public Set<String> missingTensorflowOps() {
        Set<String> copy = new HashSet<>(tensorflowOpDescriptors.keySet());
        copy.removeAll(tensorFlowNames.keySet());
        return copy;
    }

    /**
     * Returns the missing ops
     * for c++ vs java.
     * @return
     */
    public List<String> missingOps() {
        return missingOps;
    }

    /**
     *
     * @param name
     * @return
     */
    public boolean hasName(String name) {
        return nodeConverters.containsKey(name);
    }


    public Set<String> opNames() {
        return nodeConverters.keySet();
    }

    /**
     *
     * @param name
     * @return
     */
    public DifferentialFunction getInstance(String name) {
        return nodeConverters.get(name);
    }

    public Class<?> customOpClassForHashAndName(long customOpHash, String name){
        switch (name) {
            case Enter.OP_NAME:
                return Enter.class;
            case Exit.OP_NAME:
                return Exit.class;
            case NextIteration.OP_NAME:
                return NextIteration.class;
            case Merge.OP_NAME:
                return Merge.class;
            case Switch.OP_NAME:
                return Switch.class;
            case LoopCond.OP_NAME:
                return LoopCond.class;
            case ExternalErrorsFunction.OP_NAME:
                return ExternalErrorsFunction.class;
            default:
                if(customOpHashToClasses.containsKey(customOpHash)){
                    return customOpHashToClasses.get(customOpHash).get(name);
                } else if(customOpHashToClass.containsKey(customOpHash)){
                    return customOpHashToClass.get(customOpHash);
                } else {
                    throw new IllegalStateException("No op known for hash: " + customOpHash);
                }
        }

    }

    public static DifferentialFunctionClassHolder getInstance() {
        return INSTANCE;
    }

    public Map<String,DifferentialFunction> getTensorFlowNames(){
        return Collections.unmodifiableMap(tensorFlowNames);
    }
}
