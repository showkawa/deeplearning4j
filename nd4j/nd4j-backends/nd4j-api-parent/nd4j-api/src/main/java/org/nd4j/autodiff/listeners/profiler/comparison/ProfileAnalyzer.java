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
package org.nd4j.autodiff.listeners.profiler.comparison;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.nd4j.autodiff.functions.DifferentialFunction;
import org.nd4j.autodiff.listeners.profiler.ProfilingListener;
import org.nd4j.autodiff.listeners.profiler.data.Phase;
import org.nd4j.autodiff.listeners.profiler.data.TraceEvent;
import org.nd4j.autodiff.listeners.profiler.data.TraceEvents;
import org.nd4j.common.base.Preconditions;
import org.nd4j.imports.converters.DifferentialFunctionClassHolder;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.common.primitives.Pair;
import org.nd4j.list.NDArrayList;
import org.nd4j.shade.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
public class ProfileAnalyzer {

    /**
     * Chrome profiler supports 2 formats:<br>
     * SameDiff == JSON Array Format<br>
     * TensorFlow == JSON Object Format<br>
     */
    public enum ProfileFormat {SAMEDIFF, TENSORFLOW}

    /**
     * Only applicable for profile comparisons.<br>
     * PROFILE1_PC - sort by profile 1 percentage of total time<br>
     * PROFILE2_PC - sort by profile 2 percentage of total time<br>
     * RATIO - sort by highest ratio (mean op time profile 1 / mean op time profile 2)
     */
    public enum SortBy {PROFILE1_PC, PROFILE2_PC, RATIO}

    /**
     * TEXT: Human readable, columns padded for alignment<br>
     * CSV: CSV format, comma separated
     */
    public enum OutputFormat {TEXT,CSV}


    /**
     * Summarize and print to stdout the specified profile file
     *
     * @param file          Profile file
     * @param profileFormat Format of the profiler file
     */
    public static void summarizeProfile(File file, ProfileFormat profileFormat) {
        System.out.println(summarizeProfileStr(file, profileFormat));
    }

    /**
     * Summarize and return as a string the specified profile file
     *
     * @param file          Profile file
     * @param profileFormat Format of the profiler file
     */
    public static String summarizeProfileStr(File file, ProfileFormat profileFormat) {
        TraceEvent[] events = getTraceEvents(file, profileFormat);
        return summarizeTraceEvents(events);
    }

    /**
     * Aggregate, summarize and print to stdout all .json profile files in the specified directory (not recursive)
     *
     * @param dir           Directory containing the profiles
     * @param profileFormat Profile format
     */
    public static void summarizeProfileDirectory(File dir, ProfileFormat profileFormat) {
        System.out.println(summarizeProfileDirectoryStr(dir, profileFormat));
    }

    /**
     * Aggregate, summarize and return as a String all .json profile files in the specified directory (not recursive)
     *
     * @param dir           Directory containing the profiles
     * @param profileFormat Profile format
     */
    public static String summarizeProfileDirectoryStr(File dir, ProfileFormat profileFormat) {
        return summarizeTraceEvents(getTraceEventsDir(dir, profileFormat));
    }

    /**
     * Load, aggregate and return the TraceEvent object from all profiles in the specified directory
     *
     * @param dir           Directory containing the profiles
     * @param profileFormat Profile format
     */
    public static TraceEvent[] getTraceEventsDir(File dir, ProfileFormat profileFormat) {
        File[] files = dir.listFiles();
        Preconditions.checkState(files != null && files.length > 0, "No profiles found in directory: %s", dir);
        List<TraceEvent> l = new ArrayList<>();
        for (File f : files) {
            if (!f.getName().endsWith(".json")) {
                log.info("Skipping non-JSON file in directory - {}", f.getAbsolutePath());
                continue;
            }
            TraceEvent[] e = getTraceEvents(f, profileFormat);
            Collections.addAll(l, e);
        }
        return l.toArray(new TraceEvent[0]);
    }

    /**
     * Load and return the TraceEvent object from the specified profile file
     *
     * @param file          Profile file
     * @param profileFormat Profile format
     */
    public static TraceEvent[] getTraceEvents(File file, ProfileFormat profileFormat) {
        return getTraceEvents(file, profileFormat, true);
    }

    public static TraceEvent[] getTraceEvents(File file, ProfileFormat profileFormat, boolean aggregateTFSubOps) {
        ObjectMapper json = ProfilingListener.jsonMapper();

        String content = null;
        try(BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file))) {
            try {
                content = IOUtils.toString(bufferedInputStream, Charset.defaultCharset());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


        if (!content.matches(".*]\\s*")) {
            if (content.endsWith(",")) {
                //Has comma, missing ]
                content = content.substring(0, content.length() - 1) + "]";
            } else if (content.endsWith(",\n")) {
                //Has comma and newline, missing ]
                content = content.substring(0, content.length() - 2) + "]";
            } else {
                content = content + "]";
            }
        }

        TraceEvent[] events;
        if (profileFormat == ProfileFormat.SAMEDIFF) {
            try {
                events = json.readValue(content, TraceEvent[].class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            //TF format
            TraceEvents traceEvents;
            try {
                traceEvents = json.readValue(content, TraceEvents.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            events = traceEvents.getTraceEvents().toArray(new TraceEvent[0]);

            //Clean up TF format - sometimes things like "Softmax" are actually profiled as "_MklSoftmax"
            //And we'll align TF names to SameDiff names
            for (TraceEvent te : events) {
                if (TF_PROFILE_ALIASES.containsKey(te.getName())) {
                    te.setName(TF_PROFILE_ALIASES.get(te.getName()));
                }

                DifferentialFunction df = DifferentialFunctionClassHolder.getInstance().getOpWithTensorflowName(te.getName());
                if (df != null) {
                    te.setName(df.opName());
                }
            }


            if(aggregateTFSubOps) {
                //For CUDA ops, TF will log sub-ops like:
                //fire2/e1x1/Conv2D:Conv2D#id=74,device=/job:localhost/replica:0/task:0/device:GPU:0,async=false#@@cudnn::maxwell::gemm::computeOffsetsKernel(cudnn::maxwell::gemm::ComputeOffsetsParams)
                //fire2/e1x1/Conv2D:Conv2D#id=74,device=/job:localhost/replica:0/task:0/device:GPU:0,async=false#@@maxwell_scudnn_128x64_relu_interior_nn
                //fire2/e1x1/Conv2D:Conv2D#id=74,device=/job:localhost/replica:0/task:0/device:GPU:0,async=false#@@void tensorflow::functor::ShuffleInTensor3Simple<float, 2, 1, 0, false>(int, float const*, tensorflow::functor::Dimension<3>, float*)
                //We'll join these into one op, then strip everything after the ":" to recover the op name

                //Also, TF has multiple sub-ops like this, sequentially, that need to be joined:
                //19 = {TraceEvent@3157} "TraceEvent(name=Conv2D#id=80,device=/job, categories=null, ph=X, ts=1576896601259742, dur=466, tts=null, pid=5, tid=0, args={name=conv1/Conv2D, op=Conv2D#id=80,device=/job}, cname=null)"
                //20 = {TraceEvent@3181} "TraceEvent(name=Conv2D#id=80,device=/job, categories=null, ph=X, ts=1576896601260229, dur=29, tts=null, pid=5, tid=0, args={name=conv1/Conv2D, op=Conv2D#id=80,device=/job}, cname=null)"
                //21 = {TraceEvent@3206} "TraceEvent(name=Conv2D#id=80,device=/job, categories=null, ph=X, ts=1576896601260329, dur=31, tts=null, pid=5, tid=0, args={name=conv1/Conv2D, op=Conv2D#id=80,device=/job}, cname=null)"
                //22 = {TraceEvent@3247} "TraceEvent(name=Conv2D#id=80,device=/job, categories=null, ph=X, ts=1576896601260390, dur=4998, tts=null, pid=5, tid=0, args={name=conv1/Conv2D, op=Conv2D#id=80,device=/job}, cname=null)"

                Map<String,TraceEvent> map = new HashMap<>();       //Key: Op name with ID
                List<TraceEvent> out = new ArrayList<>();
                TraceEvent last = null;
                for(TraceEvent te : events){
                    if(last != null && last.getPh() == Phase.X && te.getPh() == Phase.X &&
                            last.getName().equals(te.getName()) &&
                            last.getArgs() != null && te.getArgs() != null &&
                            last.getArgs().get("name").equals(te.getArgs().get("name")) &&
                            last.getArgs().get("op").equals(te.getArgs().get("op"))){
                        //Aggregate - same names, ops, etc
                        last.setDur(last.getDur() + te.getDur());
                        continue;
                    }

                    last = te;
                    if(te.getArgs() == null || te.getArgs().isEmpty()) {
                        out.add(te);
                        continue;
                    }


                    String n = (String) te.getArgs().get("name");

                    //Aggregate by op name...
                    //"fire2/e1x1/Conv2D:Conv2D#id=74,device=/job:localhost/replica:0/..." -> "fire2/e1x1/Conv2D"
                    //We're relying on TF's "one iteration per json file" here
                    if(n.matches("[\\w/_-]+:[\\w/_-]+#id=\\d+.*")) {
                        int idx = n.indexOf("#");
                        String sub1 = n.substring(0, idx);
                        String sub;
                        if (sub1.contains(":")) {
                            sub = sub1.substring(0, sub1.lastIndexOf(":"));
                        } else {
                            sub = sub1;
                        }
                        if (map.containsKey(sub)) {
                            TraceEvent t = map.get(sub);
                            Long dur = t.getDur();
                            if (dur == null && te.getDur() == null)
                                continue;
                            t.setDur(dur == null ? te.getDur() : dur + (te.getDur() == null ? 0 : te.getDur()));
                        } else {
                            map.put(sub, te);
                            out.add(te);
                        }
                    } else {
                        if(map.containsKey(n)){
                            TraceEvent t = map.get(n);
                            t.setDur(t.getDur() + te.getDur());
                        } else {
                            map.put(n, te);
                            out.add(te);
                        }
                    }
                }

                //Strip everything after ":" in "fire2/e1x1/Conv2D:Conv2D#id=74,device=/job:localhost/..."
                for( int i = 0; i < out.size(); i++) {
                    TraceEvent te = out.get(i);
                    if(te.getArgs() == null || te.getArgs().isEmpty()){
                        continue;
                    }

                    String n = (String) te.getArgs().get("name");
                    if(n.matches("[\\w/_-]+:[\\w/_-]+#id=\\d+.*")){
                        int idx = n.indexOf(':');
                        String sub = n.substring(0,idx);
                        te.getArgs().put("name", sub);
                    }
                }

                events = out.toArray(new TraceEvent[0]);
            }
        }

        return events;
    }

    /**
     * Summarize the specified TraceEvents as a String
     *
     * @param events Events to summarize
     */
    public static String summarizeTraceEvents(TraceEvent[] events) {
        Pair<Long, Map<String, OpStats>> p = aggregateTraceEvents(events);
        final Map<String, OpStats> stats = p.getSecond();
        long allOpsUs = p.getFirst();

        //Summarize by op type:
        List<String> l = new ArrayList<>(stats.keySet());
        Collections.sort(l, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return -Long.compare(stats.get(o1).getSumUs(), stats.get(o2).getSumUs());
            }
        });

        //Work out longest name and op name:
        int longestName = 30;
        int longestOpName = 30;
        for (String s : l) {
            longestName = Math.max(longestName, s.length() + 1);
            longestOpName = Math.max(longestOpName, stats.get(s).getOpName().length() + 1);
        }

        StringBuilder sb = new StringBuilder();
        String headerFormat = "%-" + longestName + "s%-" + longestOpName + "s%-10s%-10s%-10s%-10s%-10s%-10s\n";
        sb.append(String.format(headerFormat, "Op Name", "Op", "Count", "Total uS", "%", "Min", "Max", "Std"));
        String format = "%-" + longestName + "s%-" + longestOpName + "s%-10d%-10d%-10.2f%-10d%-10d%-10.2f\n";
        for (String s : l) {
            OpStats st = stats.get(s);
            double pc = (100.0 * st.getSumUs()) / allOpsUs;
            INDArray arr = st.getTimesUs().array();
            long min = arr.minNumber().longValue();
            long max = arr.maxNumber().longValue();
            double std = arr.stdNumber().doubleValue();
            sb.append(String.format(format, s, st.getOpName(), st.getCount(), st.getSumUs(), pc, min, max, std));
        }

        return sb.toString();
    }

    private static Pair<Long, Map<String, OpStats>> aggregateTraceEvents(TraceEvent[] events) {
        //Summarize by op (instance) name:
        final Map<String, OpStats> stats = new HashMap<>();
        for (TraceEvent e : events) {
            if (e.getPh() != Phase.X || e.getDur() == null) {
                continue;
            }

            OpStats s;
            String instanceName = (String) e.getArgs().get("name");
            if (stats.containsKey(instanceName)) {
                s = stats.get(instanceName);
            } else {
                s = new OpStats(instanceName, e.getName(), 0, new NDArrayList(DataType.LONG, 0), null);
                stats.put(instanceName, s);
            }
            s.setCount(s.getCount() + 1);
            s.getTimesUs().add((double) e.getDur());
        }

        long allOpsUs = 0;
        for (OpStats s : stats.values()) {
            s.setSumUs( s.getTimesUs().array().sumNumber().longValue());
            allOpsUs += s.getSumUs();
        }

        return new Pair<>(allOpsUs, stats);
    }
    /**
     * Compare the specified profile files, sorted by profile 1 % of total time
     *
     * @param file1   First profile file
     * @param file2   Second profile file
     * @param format1 Format of first profile
     * @param format2 Format of second profile
     * @return Comparison summary as a String
     */
    public static String compareProfiles(@NonNull File file1, @NonNull File file2, @NonNull ProfileFormat format1, @NonNull ProfileFormat format2) {
        return compareProfiles(file1, file2, format1, format2, false, false, null, null, SortBy.PROFILE1_PC);
    }

    /**
     * Compare the specified profile files or directory
     *
     * @param file1       First profile file or directory of profiles
     * @param file2       Second profile file or directory of profiles
     * @param format1     Format for first profile file/s
     * @param format2     Format for second profile file/s
     * @param firstIsDir  True if the first File object is a directory
     * @param secondIsDir True if the second File object is a directory
     * @param name1       Name of the first profile (just for display purposes). Optional
     * @param name2       Name of the second profile (just for display purposes). Optional
     * @param sortBy      What to sort the summary results by
     * @return Comparison summary as a String
     */
    public static String compareProfiles(@NonNull File file1, @NonNull File file2, @NonNull ProfileFormat format1, @NonNull ProfileFormat format2,
                                         boolean firstIsDir, boolean secondIsDir, String name1, String name2, final SortBy sortBy) {
        return compareProfiles(Config.builder()
                .profile1(file1)
                .profile2(file2)
                .profile1Format(format1)
                .profile2Format(format2)
                .profile1IsDir(firstIsDir)
                .profile2IsDir(secondIsDir)
                .p1Name(name1)
                .p2Name(name2)
                .sortBy(sortBy)
                .build());
    }

    public static String compareProfiles(final Config c){
        TraceEvent[] t1 = c.profile1IsDir() ? getTraceEventsDir(c.profile1(), c.profile1Format()) : getTraceEvents(c.profile1(), c.profile1Format());
        TraceEvent[] t2 = c.profile2IsDir() ? getTraceEventsDir(c.profile2(), c.profile2Format()) : getTraceEvents(c.profile2(), c.profile2Format());

        final Pair<Long, Map<String, OpStats>> p1 = aggregateTraceEvents(t1);
        final Pair<Long, Map<String, OpStats>> p2 = aggregateTraceEvents(t2);

        List<String> l = new ArrayList<>(c.sortBy() != SortBy.PROFILE2_PC ? p1.getSecond().keySet() : p2.getSecond().keySet());
        Collections.sort(l, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                switch (c.sortBy()) {
                    case PROFILE1_PC:
                        return -Long.compare(p1.getSecond().get(o1).getSumUs(), p1.getSecond().get(o2).getSumUs());
                    case PROFILE2_PC:
                        return -Long.compare(p2.getSecond().get(o1).getSumUs(), p2.getSecond().get(o2).getSumUs());
                    case RATIO:
                        double m1a = meanTime(p1, o1);
                        double m1b = meanTime(p1, o2);
                        double m2a = meanTime(p2, o1);
                        double m2b = meanTime(p2, o2);
                        double ratio1 = m1a / m2a;
                        double ratio2 = m1b / m2b;
                        return -Double.compare(ratio1, ratio2);
                    default:
                        throw new RuntimeException();
                }
            }
        });

        Set<String> set = new HashSet<>(l);


        StringBuilder sb = new StringBuilder();
        sb.append("1 = ").append(c.p1Name() == null ? "Profile 1" : c.p1Name()).append("\n")
                .append("2 = ").append(c.p2Name() == null ? "Profile 2" : c.p2Name()).append("\n");

        //Work out longest name and op name:
        int longestName = 30;
        int longestOpName = 30;
        Map<String, OpStats> stats = c.sortBy() == SortBy.PROFILE2_PC ? p2.getSecond() : p1.getSecond();
        for (String s : l) {
            longestName = Math.max(longestName, s.length() + 1);
            longestOpName = Math.max(longestOpName, stats.get(s).getOpName().length() + 1);
        }

        String headerFormat;
        String format;
        if(c.format() == null || c.format() == OutputFormat.TEXT){
            headerFormat = "%-" + longestName + "s%-" + longestOpName + "s%-10s%-10s%-16s%-13s%-13s%-14s%-14s%-12s%-12s%-14s%-14s%-10s%-10s%-10s%-10s\n";
            format = "%-" + longestName + "s%-" + longestOpName + "s%-10d%-10d%-16.2f%-13.2f%-13.2f%-14d%-14d%-12.2f%-12.2f%-14d%-14d%-10d%-10d%-10.2f%-10.2f\n";
        } else {
            headerFormat = "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n";
            format = "%s,%s,%d,%d,%.2f,%.2f,%.2f,%d,%d,%.2f,%.2f,%d,%d,%d,%d,%.2f,%.2f\n";
        }
        sb.append(String.format(headerFormat, "Op Name", "Op", "Count (1)", "Count (2)", "Mean Ratio 1/2", "Mean (1)", "Mean (2)", "Total uS (1)", "Total uS (2)", "% (1)", "% (2)", "Min (1)", "Min (2)", "Max (1)", "Max (2)", "Std (1)", "Std (2)"));


        for (String s : l) {
            OpStats s1 = p1.getSecond().get(s);
            OpStats s2 = p2.getSecond().get(s);

            if(c.filter() != null && !c.filter().apply(s1, s2))
                continue;

            double m1 = s1 == null ? 0 : s1.getTimesUs().array().meanNumber().doubleValue();
            double m2 = s2 == null ? 0 : s2.getTimesUs().array().meanNumber().doubleValue();
            double ratio = m1 / m2;

            double pc1 = s1 == null ? 0 : 100.0 * s1.getSumUs() / p1.getFirst();
            double pc2 = s2 == null ? 0 : 100.0 * s2.getSumUs() / p2.getFirst();

            sb.append(String.format(format, s, s1 != null ? s1.getOpName() : s2.getOpName(),
                    s1 != null ? s1.getCount() : 0,
                    s2 != null ? s2.getCount() : 0,
                    //Ratio of means, means
                    ratio,
                    m1, m2,
                    //Total us, percent of op total
                    s1 != null ? s1.getSumUs() : 0,
                    s2 != null ? s2.getSumUs() : 0,
                    pc1, pc2,
                    //Min, max, std
                    s1 != null ? s1.getTimesUs().array().minNumber().longValue() : 0,
                    s2 != null ? s2.getTimesUs().array().minNumber().longValue() : 0,
                    s1 != null ? s1.getTimesUs().array().maxNumber().longValue() : 0,
                    s2 != null ? s2.getTimesUs().array().maxNumber().longValue() : 0,
                    s1 != null ? s1.getTimesUs().array().stdNumber().doubleValue() : 0.0,
                    s2 != null ? s2.getTimesUs().array().stdNumber().doubleValue() : 0.0));
        }

        boolean header = false;
        String headerFormat2 = null;
        String format3 = null;
        List<String> toAppend = null;
        for (String s : (c.sortBy() == SortBy.PROFILE2_PC ? p1.getSecond().keySet() : p2.getSecond().keySet())) {

            if (!set.contains(s)) {
                Map<String, OpStats> m = c.sortBy() == SortBy.PROFILE2_PC ? p1.getSecond() : p2.getSecond();
                OpStats st = m.get(s);
                if(c.filter() != null){
                    OpStats other = c.sortBy() == SortBy.PROFILE2_PC ? p1.getSecond().get(s) : p2.getSecond().get(s);
                    boolean keep = c.filter().apply(other, st);
                    if(!keep)
                        continue;
                }

                if (!header) {
                    toAppend = new ArrayList<>();

                    longestName = 30;
                    longestOpName = 30;
                    for(String s2 : m.keySet()){
                        longestName = Math.max(longestName, s2.length()+1);
                        longestOpName = Math.max(longestOpName, m.get(s2).getOpName().length()+1);
                    }
                    if(c.format() == null || c.format() == OutputFormat.TEXT) {
                        headerFormat2 = "%-" + longestName + "s%-" + longestOpName + "s%-10s%-10s%-10s%-10s%-10s%-10s\n";
                        format3 = "%-" + longestName + "s%-" + longestOpName + "s%-10d%-10d%-10.2f%-10d%-10d%-10.2f\n";
                    } else {
                        headerFormat2 = "%s,%s,%s,%s,%s,%s,%s,%s\n";
                        format3 = "%s,%s,%d,%d,%.2f,%d,%d,%.2f\n";
                    }

                    sb.append(" *** Operations not in profile ").append(c.sortBy() == SortBy.PROFILE2_PC ? "1" : "2").append(" but in profile ")
                            .append(c.sortBy() == SortBy.PROFILE2_PC ? "2" : "1").append(" ***\n");
                    sb.append(String.format(headerFormat2, "Op Name", "Op", "Count", "Total uS", "%", "Min", "Max", "Std"));
                    header = true;
                }
                long allOpsUs = c.sortBy() == SortBy.PROFILE2_PC ? p1.getFirst() : p2.getFirst();
                double pc = (100.0 * st.getTimesUs().array().sumNumber().longValue()) / allOpsUs;
                INDArray arr = st.getTimesUs().array();
                long min = arr.minNumber().longValue();
                long max = arr.maxNumber().longValue();
                double std = arr.stdNumber().doubleValue();
                toAppend.add(String.format(format3, s, st.getOpName(), st.getCount(), st.getSumUs(), pc, min, max, std));
            }
        }
        if(toAppend != null){
            Collections.sort(toAppend);
            for(String s : toAppend){
                sb.append(s);
            }
        }

        return sb.toString();
    }

    private static double meanTime(Pair<Long, Map<String, OpStats>> p, String name) {
        if (!p.getSecond().containsKey(name)) {
            return 0.0;
        }
        return p.getSecond().get(name).getTimesUs().array().meanNumber().doubleValue();
    }


    private static Map<String, String> TF_PROFILE_ALIASES = new HashMap<>();

    static {
        TF_PROFILE_ALIASES.put("_MklSoftmax", "Softmax");
    }

}
