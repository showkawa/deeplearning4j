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

package org.deeplearning4j.ui.module.train;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.Version;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.ext.web.RoutingContext;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.deeplearning4j.core.storage.Persistable;
import org.deeplearning4j.core.storage.StatsStorage;
import org.deeplearning4j.core.storage.StatsStorageEvent;
import org.deeplearning4j.core.storage.StatsStorageListener;
import org.deeplearning4j.common.config.DL4JSystemProperties;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.graph.GraphVertex;
import org.deeplearning4j.nn.conf.graph.LayerVertex;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.conf.serde.JsonMappers;
import org.deeplearning4j.ui.VertxUIServer;
import org.deeplearning4j.ui.api.HttpMethod;
import org.deeplearning4j.ui.api.I18N;
import org.deeplearning4j.ui.api.Route;
import org.deeplearning4j.ui.api.UIModule;
import org.deeplearning4j.ui.i18n.DefaultI18N;
import org.deeplearning4j.ui.i18n.I18NProvider;
import org.deeplearning4j.ui.i18n.I18NResource;
import org.deeplearning4j.ui.model.stats.StatsListener;
import org.deeplearning4j.ui.model.stats.api.Histogram;
import org.deeplearning4j.ui.model.stats.api.StatsInitializationReport;
import org.deeplearning4j.ui.model.stats.api.StatsReport;
import org.deeplearning4j.ui.model.stats.api.StatsType;
import org.nd4j.common.function.Function;
import org.nd4j.linalg.learning.config.IUpdater;
import org.nd4j.common.primitives.Pair;
import org.nd4j.common.primitives.Triple;
import org.nd4j.common.resources.Resources;
import org.nd4j.shade.jackson.core.JsonProcessingException;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class TrainModule implements UIModule {
    public static final double NAN_REPLACEMENT_VALUE = 0.0; //UI front-end chokes on NaN in JSON
    public static final int DEFAULT_MAX_CHART_POINTS = 512;
    private static final DecimalFormat df2 = new DecimalFormat("#.00");
    private static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private enum ModelType {
        MLN, CG, Layer
    }

    private final int maxChartPoints; //Technically, the way it's set up: won't exceed 2*maxChartPoints
    private Map<String, StatsStorage> knownSessionIDs = Collections.synchronizedMap(new HashMap<>());
    private String currentSessionID;
    private int currentWorkerIdx;
    private Map<String, AtomicInteger> workerIdxCount = new ConcurrentHashMap<>(); //Key: session ID
    private Map<String, Map<Integer, String>> workerIdxToName = new ConcurrentHashMap<>(); //Key: session ID
    private Map<String, Long> lastUpdateForSession = new ConcurrentHashMap<>();


    private final Configuration configuration;

    /**
     * TrainModule
     */
    public TrainModule() {
        String maxChartPointsProp = System.getProperty(DL4JSystemProperties.CHART_MAX_POINTS_PROPERTY);
        int value = DEFAULT_MAX_CHART_POINTS;
        if (maxChartPointsProp != null) {
            try {
                value = Integer.parseInt(maxChartPointsProp);
            } catch (NumberFormatException e) {
                log.warn("Invalid system property: {} = {}", DL4JSystemProperties.CHART_MAX_POINTS_PROPERTY, maxChartPointsProp);
            }
        }
        if (value >= 10) {
            maxChartPoints = value;
        } else {
            maxChartPoints = DEFAULT_MAX_CHART_POINTS;
        }

        configuration = new Configuration(new Version(2, 3, 23));
        configuration.setDefaultEncoding("UTF-8");
        configuration.setLocale(Locale.US);
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);

        configuration.setClassForTemplateLoading(TrainModule.class, "");
        try {
            File dir = Resources.asFile("templates/TrainingOverview.html.ftl").getParentFile();
            configuration.setDirectoryForTemplateLoading(dir);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public List<String> getCallbackTypeIDs() {
        return Collections.singletonList(StatsListener.TYPE_ID);
    }

    @Override
    public List<Route> getRoutes() {
        List<Route> r = new ArrayList<>();
        r.add(new Route("/train/multisession", HttpMethod.GET,
                (path, rc) -> rc.response().end(VertxUIServer.getInstance().isMultiSession() ? "true" : "false")));
        if (VertxUIServer.getInstance().isMultiSession()) {
            r.add(new Route("/train", HttpMethod.GET, (path, rc) -> this.listSessions(rc)));
            r.add(new Route("/train/:sessionId", HttpMethod.GET, (path, rc) -> {
                rc.response()
                        .putHeader("location", path.get(0) + "/overview")
                        .setStatusCode(HttpResponseStatus.FOUND.code())
                        .end();
            }));
            r.add(new Route("/train/:sessionId/overview", HttpMethod.GET, (path, rc) -> {
                if (knownSessionIDs.containsKey(path.get(0))) {
                    renderFtl("TrainingOverview.html.ftl", rc);
                } else {
                    sessionNotFound(path.get(0), rc.request().path(), rc);
                }
            }));
            r.add(new Route("/train/:sessionId/overview/data", HttpMethod.GET, (path, rc) -> {
                if (knownSessionIDs.containsKey(path.get(0))) {
                    getOverviewDataForSession(path.get(0), rc);
                } else {
                    sessionNotFound(path.get(0), rc.request().path(), rc);
                }
            }));
            r.add(new Route("/train/:sessionId/model", HttpMethod.GET, (path, rc) -> {
                if (knownSessionIDs.containsKey(path.get(0))) {
                    renderFtl("TrainingModel.html.ftl", rc);
                } else {
                    sessionNotFound(path.get(0), rc.request().path(), rc);
                }
            }));
            r.add(new Route("/train/:sessionId/model/graph", HttpMethod.GET, (path, rc) -> this.getModelGraphForSession(path.get(0), rc)));
            r.add(new Route("/train/:sessionId/model/data/:layerId", HttpMethod.GET, (path, rc) -> this.getModelDataForSession(path.get(0), path.get(1), rc)));
            r.add(new Route("/train/:sessionId/system", HttpMethod.GET, (path, rc) -> {
                if (knownSessionIDs.containsKey(path.get(0))) {
                    this.renderFtl("TrainingSystem.html.ftl", rc);
                } else {
                    sessionNotFound(path.get(0), rc.request().path(), rc);
                }
            }));
            r.add(new Route("/train/:sessionId/info", HttpMethod.GET, (path, rc) -> this.sessionInfoForSession(path.get(0), rc)));
            r.add(new Route("/train/:sessionId/system/data", HttpMethod.GET, (path, rc) -> this.getSystemDataForSession(path.get(0), rc)));
        } else {
            r.add(new Route("/train", HttpMethod.GET, (path, rc) -> rc.reroute("/train/overview")));
            r.add(new Route("/train/sessions/current", HttpMethod.GET, (path, rc) -> rc.response().end(currentSessionID == null ? "" : currentSessionID)));
            r.add(new Route("/train/sessions/set/:to", HttpMethod.GET, (path, rc) -> this.setSession(path.get(0), rc)));
            r.add(new Route("/train/overview", HttpMethod.GET, (path, rc) -> this.renderFtl("TrainingOverview.html.ftl", rc)));
            r.add(new Route("/train/overview/data", HttpMethod.GET, (path, rc) -> this.getOverviewData(rc)));
            r.add(new Route("/train/model", HttpMethod.GET, (path, rc) -> this.renderFtl("TrainingModel.html.ftl", rc)));
            r.add(new Route("/train/model/graph", HttpMethod.GET, (path, rc) -> this.getModelGraph(rc)));
            r.add(new Route("/train/model/data/:layerId", HttpMethod.GET, (path, rc) -> this.getModelData(path.get(0), rc)));
            r.add(new Route("/train/system", HttpMethod.GET, (path, rc) -> this.renderFtl("TrainingSystem.html.ftl", rc)));
            r.add(new Route("/train/sessions/info", HttpMethod.GET, (path, rc) -> this.sessionInfo(rc)));
            r.add(new Route("/train/system/data", HttpMethod.GET, (path, rc) -> this.getSystemData(rc)));
        }

        // common for single- and multi-session mode
        r.add(new Route("/train/sessions/lastUpdate/:sessionId", HttpMethod.GET, (path, rc) -> this.getLastUpdateForSession(path.get(0), rc)));
        r.add(new Route("/train/workers/setByIdx/:to", HttpMethod.GET, (path, rc) -> this.setWorkerByIdx(path.get(0), rc)));

        return r;
    }

    /**
     * Render a single Freemarker .ftl file from the /templates/ directory
     * @param file File to render
     * @param rc   Routing context
     */
    private void renderFtl(String file, RoutingContext rc) {
        String sessionId = rc.request().getParam("sessionID");
        String langCode = DefaultI18N.getInstance(sessionId).getDefaultLanguage();
        Map<String, String> input = DefaultI18N.getInstance().getMessages(langCode);
        String html;
        try {
            String content = FileUtils.readFileToString(Resources.asFile("templates/" + file), StandardCharsets.UTF_8);
            Template template = new Template(FilenameUtils.getName(file), new StringReader(content), configuration);
            StringWriter stringWriter = new StringWriter();
            template.process(input, stringWriter);
            html = stringWriter.toString();
        } catch (Throwable t) {
            log.error("", t);
            throw new RuntimeException(t);
        }

        rc.response().end(html);
    }

    /**
     * List training sessions. Returns a HTML list of training sessions
     */
    private synchronized void listSessions(RoutingContext rc) {
        StringBuilder sb = new StringBuilder("<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "        <meta charset=\"utf-8\">\n" +
                "        <title>Training sessions - DL4J Training UI</title>\n" +
                "    </head>\n" +
                "\n" +
                "    <body>\n" +
                "        <h1>DL4J Training UI</h1>\n" +
                "        <p>UI server is in multi-session mode." +
                " To visualize a training session, please select one from the following list.</p>\n" +
                "        <h2>List of attached training sessions</h2>\n");
        if (!knownSessionIDs.isEmpty()) {
            sb.append("        <ul>");
            for (String sessionId : knownSessionIDs.keySet()) {
                sb.append("            <li><a href=\"/train/")
                        .append(sessionId).append("\">")
                        .append(sessionId).append("</a></li>\n");
            }
            sb.append("        </ul>");
        } else {
            sb.append("No training session attached.");
        }

        sb.append("    </body>\n" +
                "</html>\n");

        rc.response()
                .putHeader("content-type", "text/html; charset=utf-8")
                .end(sb.toString());
    }

    /**
     * Load StatsStorage via provider, or return "not found"
     *
     * @param sessionId  session ID to look fo with provider
     * @param targetPath one of overview / model / system, or null
     * @param rc routing context
     */
    private void sessionNotFound(String sessionId, String targetPath, RoutingContext rc) {
        Function<String, Boolean> loader = VertxUIServer.getInstance().getStatsStorageLoader();
        if (loader != null && loader.apply(sessionId)) {
            if (targetPath != null) {
                rc.reroute(targetPath);
            } else {
                rc.response().end();
            }
        } else {
            rc.response().setStatusCode(HttpResponseStatus.NOT_FOUND.code())
                    .end("Unknown session ID: " + sessionId);
        }
    }

    @Override
    public synchronized void reportStorageEvents(Collection<StatsStorageEvent> events) {
        for (StatsStorageEvent sse : events) {
            if (StatsListener.TYPE_ID.equals(sse.getTypeID())) {
                if (sse.getEventType() == StatsStorageListener.EventType.PostStaticInfo
                        && StatsListener.TYPE_ID.equals(sse.getTypeID())
                        && !knownSessionIDs.containsKey(sse.getSessionID())) {
                    knownSessionIDs.put(sse.getSessionID(), sse.getStatsStorage());
                    if (VertxUIServer.getInstance().isMultiSession()) {
                        log.info("Adding training session {}/train/{} of StatsStorage instance {}",
                                VertxUIServer.getInstance().getAddress(), sse.getSessionID(), sse.getStatsStorage());
                    }
                }

                Long lastUpdate = lastUpdateForSession.get(sse.getSessionID());
                if (lastUpdate == null) {
                    lastUpdateForSession.put(sse.getSessionID(), sse.getTimestamp());
                } else if (sse.getTimestamp() > lastUpdate) {
                    lastUpdateForSession.put(sse.getSessionID(), sse.getTimestamp()); //Should be thread safe - read only elsewhere
                }
            }
        }

        if (currentSessionID == null)
            getDefaultSession();
    }

    @Override
    public synchronized void onAttach(StatsStorage statsStorage) {
        for (String sessionID : statsStorage.listSessionIDs()) {
            for (String typeID : statsStorage.listTypeIDsForSession(sessionID)) {
                if (!StatsListener.TYPE_ID.equals(typeID))
                    continue;
                knownSessionIDs.put(sessionID, statsStorage);
                if (VertxUIServer.getInstance().isMultiSession()) {
                    log.info("Adding training session {}/train/{} of StatsStorage instance {}",
                            VertxUIServer.getInstance().getAddress(), sessionID, statsStorage);
                }

                List<Persistable> latestUpdates = statsStorage.getLatestUpdateAllWorkers(sessionID, typeID);
                for (Persistable update : latestUpdates) {
                    long updateTime = update.getTimeStamp();
                    if (lastUpdateForSession.containsKey(sessionID) && lastUpdateForSession.get(sessionID) < updateTime) {
                        lastUpdateForSession.put(sessionID, updateTime);
                    }
                }
            }
        }

        if (currentSessionID == null)
            getDefaultSession();
    }

    @Override
    public synchronized void onDetach(StatsStorage statsStorage) {
        Set<String> toRemove = new HashSet<>();
        for (String s : knownSessionIDs.keySet()) {
            if (knownSessionIDs.get(s) == statsStorage) {
                toRemove.add(s);
                workerIdxCount.remove(s);
                workerIdxToName.remove(s);
                currentSessionID = null;
            }
        }
        for (String s : toRemove) {
            knownSessionIDs.remove(s);
            if (VertxUIServer.getInstance().isMultiSession()) {
                log.info("Removing training session {}/train/{} of StatsStorage instance {}.",
                        VertxUIServer.getInstance().getAddress(), s, statsStorage);
            }
            lastUpdateForSession.remove(s);
        }
        getDefaultSession();
    }

    private synchronized void getDefaultSession() {
        if (currentSessionID != null)
            return;

        long mostRecentTime = Long.MIN_VALUE;
        String sessionID = null;
        for (Map.Entry<String, StatsStorage> entry : knownSessionIDs.entrySet()) {
            List<Persistable> staticInfos = entry.getValue().getAllStaticInfos(entry.getKey(), StatsListener.TYPE_ID);
            if (staticInfos == null || staticInfos.isEmpty())
                continue;
            Persistable p = staticInfos.get(0);
            long thisTime = p.getTimeStamp();
            if (thisTime > mostRecentTime) {
                mostRecentTime = thisTime;
                sessionID = entry.getKey();
            }
        }

        if (sessionID != null) {
            currentSessionID = sessionID;
        }
    }

    private synchronized String getWorkerIdForIndex(String sessionId, int workerIdx) {
        if (sessionId == null)
            return null;

        Map<Integer, String> idxToId = workerIdxToName.computeIfAbsent(sessionId, k -> Collections.synchronizedMap(new HashMap<>()));

        if (idxToId.containsKey(workerIdx)) {
            return idxToId.get(workerIdx);
        }

        //Need to record new worker...
        //Get counter
        AtomicInteger counter = workerIdxCount.get(sessionId);
        if (counter == null) {
            counter = new AtomicInteger(0);
            workerIdxCount.put(sessionId, counter);
        }

        //Get all worker IDs
        StatsStorage ss = knownSessionIDs.get(sessionId);
        if (ss == null) {
            return null;
        }
        List<String> allWorkerIds = new ArrayList<>(ss.listWorkerIDsForSessionAndType(sessionId, StatsListener.TYPE_ID));
        Collections.sort(allWorkerIds);

        //Ensure all workers have been assigned an index
        for (String s : allWorkerIds) {
            if (idxToId.containsValue(s))
                continue;
            //Unknown worker ID:
            idxToId.put(counter.getAndIncrement(), s);
        }

        //May still return null if index is wrong/too high...
        return idxToId.get(workerIdx);
    }

    /**
     * Display, for each session: session ID, start time, number of workers, last update
     * Returns info for each session as JSON
     */
    private synchronized void sessionInfo(RoutingContext rc) {

        Map<String, Object> dataEachSession = new HashMap<>();
        for (Map.Entry<String, StatsStorage> entry : knownSessionIDs.entrySet()) {
            String sid = entry.getKey();
            StatsStorage ss = entry.getValue();
            Map<String, Object> dataThisSession = sessionData(sid, ss);
            dataEachSession.put(sid, dataThisSession);
        }
        rc.response()
                .putHeader("content-type", "application/json")
                .end(asJson(dataEachSession));
    }

    /**
     * Extract session data from {@link StatsStorage}
     *
     * @param sid session ID
     * @param ss  {@code StatsStorage} instance
     * @return session data map
     */
    private static Map<String, Object> sessionData(String sid, StatsStorage ss) {
        Map<String, Object> dataThisSession = new HashMap<>();
        List<String> workerIDs = ss.listWorkerIDsForSessionAndType(sid, StatsListener.TYPE_ID);
        int workerCount = (workerIDs == null ? 0 : workerIDs.size());
        List<Persistable> staticInfo = ss.getAllStaticInfos(sid, StatsListener.TYPE_ID);
        long initTime = Long.MAX_VALUE;
        if (staticInfo != null) {
            for (Persistable p : staticInfo) {
                initTime = Math.min(p.getTimeStamp(), initTime);
            }
        }

        long lastUpdateTime = Long.MIN_VALUE;
        List<Persistable> lastUpdatesAllWorkers = ss.getLatestUpdateAllWorkers(sid, StatsListener.TYPE_ID);
        for (Persistable p : lastUpdatesAllWorkers) {
            lastUpdateTime = Math.max(lastUpdateTime, p.getTimeStamp());
        }

        dataThisSession.put("numWorkers", workerCount);
        dataThisSession.put("initTime", initTime == Long.MAX_VALUE ? "" : initTime);
        dataThisSession.put("lastUpdate", lastUpdateTime == Long.MIN_VALUE ? "" : lastUpdateTime);

        // add hashmap of workers
        if (workerCount > 0) {
            dataThisSession.put("workers", workerIDs);
        }

        //Model info: type, # layers, # params...
        if (staticInfo != null && !staticInfo.isEmpty()) {
            StatsInitializationReport sr = (StatsInitializationReport) staticInfo.get(0);
            String modelClassName = sr.getModelClassName();
            if (modelClassName.endsWith("MultiLayerNetwork")) {
                modelClassName = "MultiLayerNetwork";
            } else if (modelClassName.endsWith("ComputationGraph")) {
                modelClassName = "ComputationGraph";
            }
            int numLayers = sr.getModelNumLayers();
            long numParams = sr.getModelNumParams();

            dataThisSession.put("modelType", modelClassName);
            dataThisSession.put("numLayers", numLayers);
            dataThisSession.put("numParams", numParams);
        } else {
            dataThisSession.put("modelType", "");
            dataThisSession.put("numLayers", "");
            dataThisSession.put("numParams", "");
        }
        return dataThisSession;
    }

    /**
     * Display, for given session: session ID, start time, number of workers, last update.
     * Returns info for session as JSON
     *
     * @param sessionId session ID
     */
    private synchronized void sessionInfoForSession(String sessionId, RoutingContext rc) {

        Map<String, Object> dataEachSession = new HashMap<>();
        StatsStorage ss = knownSessionIDs.get(sessionId);
        if (ss != null) {
            Map<String, Object> dataThisSession = sessionData(sessionId, ss);
            dataEachSession.put(sessionId, dataThisSession);
        }
        rc.response()
                .putHeader("content-type", "application/json")
                .end(asJson(dataEachSession));
    }

    private synchronized void setSession(String newSessionID, RoutingContext rc) {
        if (knownSessionIDs.containsKey(newSessionID)) {
            currentSessionID = newSessionID;
            currentWorkerIdx = 0;
            rc.response().end();
        } else {
            rc.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
        }
    }

    private void getLastUpdateForSession(String sessionID, RoutingContext rc) {
        Long lastUpdate = lastUpdateForSession.get(sessionID);
        if (lastUpdate != null) {
            rc.response().end(String.valueOf(lastUpdate));
            return;
        }
        rc.response().end("-1");
    }

    private void setWorkerByIdx(String newWorkerIdx, RoutingContext rc) {
        try {
            currentWorkerIdx = Integer.parseInt(newWorkerIdx);
        } catch (NumberFormatException e) {
            log.debug("Invalid call to setWorkerByIdx", e);
        }
        rc.response().end();
    }

    private static double fixNaN(double d) {
        return Double.isFinite(d) ? d : NAN_REPLACEMENT_VALUE;
    }

    private static void cleanLegacyIterationCounts(List<Integer> iterationCounts) {
        if (!iterationCounts.isEmpty()) {
            boolean allEqual = true;
            int maxStepSize = 1;
            int first = iterationCounts.get(0);
            int length = iterationCounts.size();
            int prevIterCount = first;
            for (int i = 1; i < length; i++) {
                int currIterCount = iterationCounts.get(i);
                if (allEqual && first != currIterCount) {
                    allEqual = false;
                }
                maxStepSize = Math.max(maxStepSize, prevIterCount - currIterCount);
                prevIterCount = currIterCount;
            }


            if (allEqual) {
                maxStepSize = 1;
            }

            for (int i = 0; i < length; i++) {
                iterationCounts.set(i, first + i * maxStepSize);
            }
        }
    }

    /**
     * Get last update time for given session ID, checking for null values
     *
     * @param sessionId session ID
     * @return last update time for session if found, or {@code null}
     */
    private Long getLastUpdateTime(String sessionId) {
        if (lastUpdateForSession != null && sessionId != null && lastUpdateForSession.containsKey(sessionId)) {
            return lastUpdateForSession.get(sessionId);
        } else {
            return -1L;
        }
    }

    /**
     * Get global {@link I18N} instance if {@link VertxUIServer#isMultiSession()} is {@code true}, or instance for session
     *
     * @param sessionId session ID
     * @return {@link I18N} instance
     */
    private I18N getI18N(String sessionId) {
        return VertxUIServer.getInstance().isMultiSession() ? I18NProvider.getInstance(sessionId) : I18NProvider.getInstance();
    }


    private void getOverviewData(RoutingContext rc) {
        getOverviewDataForSession(currentSessionID, rc);
    }

    private synchronized void getOverviewDataForSession(String sessionId, RoutingContext rc) {
        Long lastUpdateTime = getLastUpdateTime(sessionId);
        I18N i18N = getI18N(sessionId);

        //First pass (optimize later): query all data...
        StatsStorage ss = (sessionId == null ? null : knownSessionIDs.get(sessionId));
        String wid = getWorkerIdForIndex(sessionId, currentWorkerIdx);
        boolean noData = (sessionId == null) || (ss == null) || (wid == null);

        List<Integer> scoresIterCount = new ArrayList<>();
        List<Double> scores = new ArrayList<>();

        Map<String, Object> result = new HashMap<>();
        result.put("updateTimestamp", lastUpdateTime);
        result.put("scores", scores);
        result.put("scoresIter", scoresIterCount);

        //Get scores info
        long[] allTimes = (noData ? null : ss.getAllUpdateTimes(sessionId, StatsListener.TYPE_ID, wid));
        List<Persistable> updates = null;
        if (allTimes != null && allTimes.length > maxChartPoints) {
            int subsamplingFrequency = allTimes.length / maxChartPoints;
            LongArrayList timesToQuery = new LongArrayList(maxChartPoints + 2);
            int i = 0;
            for (; i < allTimes.length; i += subsamplingFrequency) {
                timesToQuery.add(allTimes[i]);
            }
            if ((i - subsamplingFrequency) != allTimes.length - 1) {
                //Also add final point
                timesToQuery.add(allTimes[allTimes.length - 1]);
            }
            updates = ss.getUpdates(sessionId, StatsListener.TYPE_ID, wid, timesToQuery.toLongArray());
        } else if (allTimes != null) {
            //Don't subsample
            updates = ss.getAllUpdatesAfter(sessionId, StatsListener.TYPE_ID, wid, 0);
        }
        if (updates == null || updates.isEmpty()) {
            noData = true;
        }

        //Collect update ratios for weights
        //Collect standard deviations: activations, gradients, updates
        Map<String, List<Double>> updateRatios = new HashMap<>(); //Mean magnitude (updates) / mean magnitude (parameters)
        result.put("updateRatios", updateRatios);

        Map<String, List<Double>> stdevActivations = new HashMap<>();
        Map<String, List<Double>> stdevGradients = new HashMap<>();
        Map<String, List<Double>> stdevUpdates = new HashMap<>();
        result.put("stdevActivations", stdevActivations);
        result.put("stdevGradients", stdevGradients);
        result.put("stdevUpdates", stdevUpdates);

        if (!noData) {
            Persistable u = updates.get(0);
            if (u instanceof StatsReport) {
                StatsReport sp = (StatsReport) u;
                Map<String, Double> map = sp.getMeanMagnitudes(StatsType.Parameters);
                if (map != null) {
                    for (String s : map.keySet()) {
                        if (!s.toLowerCase().endsWith("w"))
                            continue; //TODO: more robust "weights only" approach...
                        updateRatios.put(s, new ArrayList<>());
                    }
                }

                Map<String, Double> stdGrad = sp.getStdev(StatsType.Gradients);
                if (stdGrad != null) {
                    for (String s : stdGrad.keySet()) {
                        if (!s.toLowerCase().endsWith("w"))
                            continue; //TODO: more robust "weights only" approach...
                        stdevGradients.put(s, new ArrayList<>());
                    }
                }

                Map<String, Double> stdUpdate = sp.getStdev(StatsType.Updates);
                if (stdUpdate != null) {
                    for (String s : stdUpdate.keySet()) {
                        if (!s.toLowerCase().endsWith("w"))
                            continue; //TODO: more robust "weights only" approach...
                        stdevUpdates.put(s, new ArrayList<>());
                    }
                }


                Map<String, Double> stdAct = sp.getStdev(StatsType.Activations);
                if (stdAct != null) {
                    for (String s : stdAct.keySet()) {
                        stdevActivations.put(s, new ArrayList<>());
                    }
                }
            }
        }

        StatsReport last = null;
        int lastIterCount = -1;
        //Legacy issue - Spark training - iteration counts are used to be reset... which means: could go 0,1,2,0,1,2, etc...
        //Or, it could equally go 4,8,4,8,... or 5,5,5,5 - depending on the collection and averaging frequencies
        //Now, it should use the proper iteration counts
        boolean needToHandleLegacyIterCounts = false;
        if (!noData) {
            double lastScore;

            int totalUpdates = updates.size();
            int subsamplingFrequency = 1;
            if (totalUpdates > maxChartPoints) {
                subsamplingFrequency = totalUpdates / maxChartPoints;
            }

            int pCount = -1;
            int lastUpdateIdx = updates.size() - 1;
            for (Persistable u : updates) {
                pCount++;
                if (!(u instanceof StatsReport))
                    continue;

                last = (StatsReport) u;
                int iterCount = last.getIterationCount();

                if (iterCount <= lastIterCount) {
                    needToHandleLegacyIterCounts = true;
                }
                lastIterCount = iterCount;

                if (pCount > 0 && subsamplingFrequency > 1 && pCount % subsamplingFrequency != 0) {
                    //Skip this - subsample the data
                    if (pCount != lastUpdateIdx)
                        continue; //Always keep the most recent value
                }

                scoresIterCount.add(iterCount);
                lastScore = last.getScore();
                if (Double.isFinite(lastScore)) {
                    scores.add(lastScore);
                } else {
                    scores.add(NAN_REPLACEMENT_VALUE);
                }


                //Update ratios: mean magnitudes(updates) / mean magnitudes (parameters)
                Map<String, Double> updateMM = last.getMeanMagnitudes(StatsType.Updates);
                Map<String, Double> paramMM = last.getMeanMagnitudes(StatsType.Parameters);
                if (updateMM != null && paramMM != null && updateMM.size() > 0 && paramMM.size() > 0) {
                    for (String s : updateRatios.keySet()) {
                        List<Double> ratioHistory = updateRatios.get(s);
                        double currUpdate = updateMM.getOrDefault(s, 0.0);
                        double currParam = paramMM.getOrDefault(s, 0.0);
                        double ratio = currUpdate / currParam;
                        if (Double.isFinite(ratio)) {
                            ratioHistory.add(ratio);
                        } else {
                            ratioHistory.add(NAN_REPLACEMENT_VALUE);
                        }
                    }
                }

                //Standard deviations: gradients, updates, activations
                Map<String, Double> stdGrad = last.getStdev(StatsType.Gradients);
                Map<String, Double> stdUpd = last.getStdev(StatsType.Updates);
                Map<String, Double> stdAct = last.getStdev(StatsType.Activations);

                if (stdGrad != null) {
                    for (String s : stdevGradients.keySet()) {
                        double d = stdGrad.getOrDefault(s, 0.0);
                        stdevGradients.get(s).add(fixNaN(d));
                    }
                }
                if (stdUpd != null) {
                    for (String s : stdevUpdates.keySet()) {
                        double d = stdUpd.getOrDefault(s, 0.0);
                        stdevUpdates.get(s).add(fixNaN(d));
                    }
                }
                if (stdAct != null) {
                    for (String s : stdevActivations.keySet()) {
                        double d = stdAct.getOrDefault(s, 0.0);
                        stdevActivations.get(s).add(fixNaN(d));
                    }
                }
            }
        }

        if (needToHandleLegacyIterCounts) {
            cleanLegacyIterationCounts(scoresIterCount);
        }


        //----- Performance Info -----
        String[][] perfInfo = new String[][]{{i18N.getMessage("train.overview.perftable.startTime"), ""},
                {i18N.getMessage("train.overview.perftable.totalRuntime"), ""},
                {i18N.getMessage("train.overview.perftable.lastUpdate"), ""},
                {i18N.getMessage("train.overview.perftable.totalParamUpdates"), ""},
                {i18N.getMessage("train.overview.perftable.updatesPerSec"), ""},
                {i18N.getMessage("train.overview.perftable.examplesPerSec"), ""}};

        if (last != null) {
            perfInfo[2][1] = String.valueOf(dateFormat.format(new Date(last.getTimeStamp())));
            perfInfo[3][1] = String.valueOf(last.getTotalMinibatches());
            perfInfo[4][1] = String.valueOf(df2.format(last.getMinibatchesPerSecond()));
            perfInfo[5][1] = String.valueOf(df2.format(last.getExamplesPerSecond()));
        }

        result.put("perf", perfInfo);


        // ----- Model Info -----
        String[][] modelInfo = new String[][]{{i18N.getMessage("train.overview.modeltable.modeltype"), ""},
                {i18N.getMessage("train.overview.modeltable.nLayers"), ""},
                {i18N.getMessage("train.overview.modeltable.nParams"), ""}};
        if (!noData) {
            Persistable p = ss.getStaticInfo(sessionId, StatsListener.TYPE_ID, wid);
            if (p != null) {
                StatsInitializationReport initReport = (StatsInitializationReport) p;
                int nLayers = initReport.getModelNumLayers();
                long numParams = initReport.getModelNumParams();
                String className = initReport.getModelClassName();

                String modelType;
                if (className.endsWith("MultiLayerNetwork")) {
                    modelType = "MultiLayerNetwork";
                } else if (className.endsWith("ComputationGraph")) {
                    modelType = "ComputationGraph";
                } else {
                    modelType = className;
                    if (modelType.lastIndexOf('.') > 0) {
                        modelType = modelType.substring(modelType.lastIndexOf('.') + 1);
                    }
                }

                modelInfo[0][1] = modelType;
                modelInfo[1][1] = String.valueOf(nLayers);
                modelInfo[2][1] = String.valueOf(numParams);
            }
        }

        result.put("model", modelInfo);

        String json = asJson(result);

        rc.response()
                .putHeader("content-type", "application/json")
                .end(json);
    }

    private void getModelGraph(RoutingContext rc) {
        getModelGraphForSession(currentSessionID, rc);
    }

    private void getModelGraphForSession(String sessionId, RoutingContext rc) {

        boolean noData = (sessionId == null || !knownSessionIDs.containsKey(sessionId));
        StatsStorage ss = (noData ? null : knownSessionIDs.get(sessionId));
        List<Persistable> allStatic = (noData ? Collections.EMPTY_LIST
                : ss.getAllStaticInfos(sessionId, StatsListener.TYPE_ID));

        if (allStatic.isEmpty()) {
            rc.response().end();
            return;
        }

        TrainModuleUtils.GraphInfo gi = getGraphInfo(getConfig(sessionId));
        if (gi == null) {
            rc.response().end();
            return;
        }

        String json = asJson(gi);

        rc.response()
                .putHeader("content-type", "application/json")
                .end(json);
    }

    private TrainModuleUtils.GraphInfo getGraphInfo(Triple<MultiLayerConfiguration,
            ComputationGraphConfiguration, NeuralNetConfiguration> conf) {
        if (conf == null) {
            return null;
        }

        if (conf.getFirst() != null) {
            return TrainModuleUtils.buildGraphInfo(conf.getFirst());
        } else if (conf.getSecond() != null) {
            return TrainModuleUtils.buildGraphInfo(conf.getSecond());
        } else if (conf.getThird() != null) {
            return TrainModuleUtils.buildGraphInfo(conf.getThird());
        } else {
            return null;
        }
    }

    private Triple<MultiLayerConfiguration, ComputationGraphConfiguration, NeuralNetConfiguration> getConfig(String sessionId) {
        boolean noData = (sessionId == null || !knownSessionIDs.containsKey(sessionId));
        StatsStorage ss = (noData ? null : knownSessionIDs.get(sessionId));
        List<Persistable> allStatic = (noData ? Collections.EMPTY_LIST
                : ss.getAllStaticInfos(sessionId, StatsListener.TYPE_ID));
        if (allStatic.isEmpty())
            return null;

        StatsInitializationReport p = (StatsInitializationReport) allStatic.get(0);
        String modelClass = p.getModelClassName();
        String config = p.getModelConfigJson();

        if (modelClass.endsWith("MultiLayerNetwork")) {
            MultiLayerConfiguration conf = MultiLayerConfiguration.fromJson(config);
            return new Triple<>(conf, null, null);
        } else if (modelClass.endsWith("ComputationGraph")) {
            ComputationGraphConfiguration conf = ComputationGraphConfiguration.fromJson(config);
            return new Triple<>(null, conf, null);
        } else {
            try {
                NeuralNetConfiguration layer =
                        NeuralNetConfiguration.mapper().readValue(config, NeuralNetConfiguration.class);
                return new Triple<>(null, null, layer);
            } catch (Exception e) {
                log.error("",e);
            }
        }
        return null;
    }


    private void getModelData(String layerId, RoutingContext rc) {
        getModelDataForSession(currentSessionID, layerId, rc);
    }

    private void getModelDataForSession(String sessionId, String layerId, RoutingContext rc) {
        Long lastUpdateTime = getLastUpdateTime(sessionId);

        int layerIdx = Integer.parseInt(layerId); //TODO validation
        I18N i18N = getI18N(sessionId);

        //Model info for layer

        //First pass (optimize later): query all data...
        StatsStorage ss = (sessionId == null ? null : knownSessionIDs.get(sessionId));
        String wid = getWorkerIdForIndex(sessionId, currentWorkerIdx);
        boolean noData = (sessionId == null) || (ss == null) || (wid == null);

        Map<String, Object> result = new HashMap<>();
        result.put("updateTimestamp", lastUpdateTime);

        Triple<MultiLayerConfiguration, ComputationGraphConfiguration, NeuralNetConfiguration> conf = getConfig(sessionId);
        if (conf == null) {
            rc.response()
                    .putHeader("content-type", "application/json")
                    .end(asJson(result));
            return;
        }

        TrainModuleUtils.GraphInfo gi = getGraphInfo(conf);
        if (gi == null) {
            rc.response()
                    .putHeader("content-type", "application/json")
                    .end(asJson(result));
            return;
        }


        // Get static layer info
        String[][] layerInfoTable = getLayerInfoTable(sessionId, layerIdx, gi, i18N, noData, ss, wid);

        result.put("layerInfo", layerInfoTable);

        //First: get all data, and subsample it if necessary, to avoid returning too many points...
        long[] allTimes = (noData ? null : ss.getAllUpdateTimes(sessionId, StatsListener.TYPE_ID, wid));

        List<Persistable> updates = null;
        List<Integer> iterationCounts = null;
        boolean needToHandleLegacyIterCounts = false;
        if (allTimes != null && allTimes.length > maxChartPoints) {
            int subsamplingFrequency = allTimes.length / maxChartPoints;
            LongArrayList timesToQuery = new LongArrayList(maxChartPoints + 2);
            int i = 0;
            for (; i < allTimes.length; i += subsamplingFrequency) {
                timesToQuery.add(allTimes[i]);
            }
            if ((i - subsamplingFrequency) != allTimes.length - 1) {
                //Also add final point
                timesToQuery.add(allTimes[allTimes.length - 1]);
            }
            updates = ss.getUpdates(sessionId, StatsListener.TYPE_ID, wid, timesToQuery.toLongArray());
        } else if (allTimes != null) {
            //Don't subsample
            updates = ss.getAllUpdatesAfter(sessionId, StatsListener.TYPE_ID, wid, 0);
        }

        iterationCounts = new ArrayList<>(updates.size());
        int lastIterCount = -1;
        for (Persistable p : updates) {
            if (!(p instanceof StatsReport))
                continue;
            StatsReport sr = (StatsReport) p;
            int iterCount = sr.getIterationCount();

            if (iterCount <= lastIterCount) {
                needToHandleLegacyIterCounts = true;
            }
            iterationCounts.add(iterCount);
        }

        //Legacy issue - Spark training - iteration counts are used to be reset... which means: could go 0,1,2,0,1,2, etc...
        //Or, it could equally go 4,8,4,8,... or 5,5,5,5 - depending on the collection and averaging frequencies
        //Now, it should use the proper iteration counts
        if (needToHandleLegacyIterCounts) {
            cleanLegacyIterationCounts(iterationCounts);
        }

        //Get mean magnitudes line chart
        ModelType mt;
        if (conf.getFirst() != null)
            mt = ModelType.MLN;
        else if (conf.getSecond() != null)
            mt = ModelType.CG;
        else
            mt = ModelType.Layer;
        MeanMagnitudes mm = getLayerMeanMagnitudes(layerIdx, gi, updates, iterationCounts, mt);
        Map<String, Object> mmRatioMap = new HashMap<>();
        mmRatioMap.put("layerParamNames", mm.getRatios().keySet());
        mmRatioMap.put("iterCounts", mm.getIterations());
        mmRatioMap.put("ratios", mm.getRatios());
        mmRatioMap.put("paramMM", mm.getParamMM());
        mmRatioMap.put("updateMM", mm.getUpdateMM());
        result.put("meanMag", mmRatioMap);

        //Get activations line chart for layer
        Triple<int[], float[], float[]> activationsData = getLayerActivations(layerIdx, gi, updates, iterationCounts);
        Map<String, Object> activationMap = new HashMap<>();
        activationMap.put("iterCount", activationsData.getFirst());
        activationMap.put("mean", activationsData.getSecond());
        activationMap.put("stdev", activationsData.getThird());
        result.put("activations", activationMap);

        //Get learning rate vs. time chart for layer
        Map<String, Object> lrs = getLayerLearningRates(layerIdx, gi, updates, iterationCounts, mt);
        result.put("learningRates", lrs);

        //Parameters histogram data
        Persistable lastUpdate = (updates != null && !updates.isEmpty() ? updates.get(updates.size() - 1) : null);
        Map<String, Object> paramHistograms = getHistograms(layerIdx, gi, StatsType.Parameters, lastUpdate);
        result.put("paramHist", paramHistograms);

        //Updates histogram data
        Map<String, Object> updateHistograms = getHistograms(layerIdx, gi, StatsType.Updates, lastUpdate);
        result.put("updateHist", updateHistograms);

        rc.response()
                .putHeader("content-type", "application/json")
                .end(asJson(result));
    }

    private void getSystemData(RoutingContext rc) {
        getSystemDataForSession(currentSessionID, rc);
    }

    private void getSystemDataForSession(String sessionId, RoutingContext rc) {
        Long lastUpdate = getLastUpdateTime(sessionId);

        I18N i18n = getI18N(sessionId);

        //First: get the MOST RECENT update...
        //Then get all updates from most recent - 5 minutes -> TODO make this configurable...

        StatsStorage ss = (sessionId == null ? null : knownSessionIDs.get(sessionId));
        boolean noData = (ss == null);

        List<Persistable> allStatic = (noData ? Collections.EMPTY_LIST
                : ss.getAllStaticInfos(sessionId, StatsListener.TYPE_ID));
        List<Persistable> latestUpdates = (noData ? Collections.EMPTY_LIST
                : ss.getLatestUpdateAllWorkers(sessionId, StatsListener.TYPE_ID));


        long lastUpdateTime = -1;
        if (latestUpdates == null || latestUpdates.isEmpty()) {
            noData = true;
        } else {
            for (Persistable p : latestUpdates) {
                lastUpdateTime = Math.max(lastUpdateTime, p.getTimeStamp());
            }
        }

        long fromTime = lastUpdateTime - 5 * 60 * 1000; //TODO Make configurable
        List<Persistable> lastNMinutes =
                (noData ? null : ss.getAllUpdatesAfter(sessionId, StatsListener.TYPE_ID, fromTime));

        Map<String, Object> mem = getMemory(allStatic, lastNMinutes, i18n);
        Pair<Map<String, Object>, Map<String, Object>> hwSwInfo = getHardwareSoftwareInfo(allStatic, i18n);

        Map<String, Object> ret = new HashMap<>();
        ret.put("updateTimestamp", lastUpdate);
        ret.put("memory", mem);
        ret.put("hardware", hwSwInfo.getFirst());
        ret.put("software", hwSwInfo.getSecond());

        rc.response()
                .putHeader("content-type", "application/json")
                .end(asJson(ret));
    }

    private static String getLayerType(Layer layer) {
        String layerType = "n/a";
        if (layer != null) {
            try {
                layerType = layer.getClass().getSimpleName().replaceAll("Layer$", "");
            } catch (Exception e) {
            }
        }
        return layerType;
    }

    private static String[][] getLayerInfoTable(String sessionId, int layerIdx, TrainModuleUtils.GraphInfo gi, I18N i18N, boolean noData,
                                                StatsStorage ss, String wid) {
        List<String[]> layerInfoRows = new ArrayList<>();
        layerInfoRows.add(new String[]{i18N.getMessage("train.model.layerinfotable.layerName"),
                gi.getVertexNames().get(layerIdx)});
        layerInfoRows.add(new String[]{i18N.getMessage("train.model.layerinfotable.layerType"), ""});

        if (!noData) {
            Persistable p = ss.getStaticInfo(sessionId, StatsListener.TYPE_ID, wid);
            if (p != null) {
                StatsInitializationReport initReport = (StatsInitializationReport) p;
                String configJson = initReport.getModelConfigJson();
                String modelClass = initReport.getModelClassName();

                //TODO error handling...
                String layerType = "";
                Layer layer = null;
                NeuralNetConfiguration nnc = null;
                if (modelClass.endsWith("MultiLayerNetwork")) {
                    MultiLayerConfiguration conf = MultiLayerConfiguration.fromJson(configJson);
                    int confIdx = layerIdx - 1; //-1 because of input
                    if (confIdx >= 0) {
                        nnc = conf.getConf(confIdx);
                        layer = nnc.getLayer();
                    } else {
                        //Input layer
                        layerType = "Input";
                    }
                } else if (modelClass.endsWith("ComputationGraph")) {
                    ComputationGraphConfiguration conf = ComputationGraphConfiguration.fromJson(configJson);

                    String vertexName = gi.getVertexNames().get(layerIdx);

                    Map<String, GraphVertex> vertices = conf.getVertices();
                    if (vertices.containsKey(vertexName) && vertices.get(vertexName) instanceof LayerVertex) {
                        LayerVertex lv = (LayerVertex) vertices.get(vertexName);
                        nnc = lv.getLayerConf();
                        layer = nnc.getLayer();
                    } else if (conf.getNetworkInputs().contains(vertexName)) {
                        layerType = "Input";
                    } else {
                        GraphVertex gv = conf.getVertices().get(vertexName);
                        if (gv != null) {
                            layerType = gv.getClass().getSimpleName();
                        }
                    }
                } else if (modelClass.endsWith("VariationalAutoencoder")) {
                    layerType = gi.getVertexTypes().get(layerIdx);
                    Map<String, String> map = gi.getVertexInfo().get(layerIdx);
                    for (Map.Entry<String, String> entry : map.entrySet()) {
                        layerInfoRows.add(new String[]{entry.getKey(), entry.getValue()});
                    }
                }

                if (layer != null) {
                    layerType = getLayerType(layer);
                }

                if (layer != null) {
                    String activationFn = null;
                    if (layer instanceof FeedForwardLayer) {
                        FeedForwardLayer ffl = (FeedForwardLayer) layer;
                        layerInfoRows.add(new String[]{i18N.getMessage("train.model.layerinfotable.layerNIn"),
                                String.valueOf(ffl.getNIn())});
                        layerInfoRows.add(new String[]{i18N.getMessage("train.model.layerinfotable.layerSize"),
                                String.valueOf(ffl.getNOut())});
                    }
                    if (layer instanceof BaseLayer) {
                        BaseLayer bl = (BaseLayer) layer;
                        activationFn = bl.getActivationFn().toString();
                        long nParams = layer.initializer().numParams(nnc);
                        layerInfoRows.add(new String[]{i18N.getMessage("train.model.layerinfotable.layerNParams"),
                                String.valueOf(nParams)});
                        if (nParams > 0) {
                            try {
                                String str = JsonMappers.getMapper().writeValueAsString(bl.getWeightInitFn());
                                layerInfoRows.add(new String[]{
                                        i18N.getMessage("train.model.layerinfotable.layerWeightInit"), str});
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }

                            IUpdater u = bl.getIUpdater();
                            String us = (u == null ? "" : u.getClass().getSimpleName());
                            layerInfoRows.add(new String[]{i18N.getMessage("train.model.layerinfotable.layerUpdater"),
                                    us});

                            //TODO: Maybe L1/L2, dropout, updater-specific values etc
                        }
                    }

                    if (layer instanceof ConvolutionLayer || layer instanceof SubsamplingLayer) {
                        int[] kernel;
                        int[] stride;
                        int[] padding;
                        if (layer instanceof ConvolutionLayer) {
                            ConvolutionLayer cl = (ConvolutionLayer) layer;
                            kernel = cl.getKernelSize();
                            stride = cl.getStride();
                            padding = cl.getPadding();
                        } else {
                            SubsamplingLayer ssl = (SubsamplingLayer) layer;
                            kernel = ssl.getKernelSize();
                            stride = ssl.getStride();
                            padding = ssl.getPadding();
                            activationFn = null;
                            layerInfoRows.add(new String[]{
                                    i18N.getMessage("train.model.layerinfotable.layerSubsamplingPoolingType"),
                                    ssl.getPoolingType().toString()});
                        }
                        layerInfoRows.add(new String[]{i18N.getMessage("train.model.layerinfotable.layerCnnKernel"),
                                Arrays.toString(kernel)});
                        layerInfoRows.add(new String[]{i18N.getMessage("train.model.layerinfotable.layerCnnStride"),
                                Arrays.toString(stride)});
                        layerInfoRows.add(new String[]{i18N.getMessage("train.model.layerinfotable.layerCnnPadding"),
                                Arrays.toString(padding)});
                    }

                    if (activationFn != null) {
                        layerInfoRows.add(new String[]{i18N.getMessage("train.model.layerinfotable.layerActivationFn"),
                                activationFn});
                    }
                }
                layerInfoRows.get(1)[1] = layerType;
            }
        }

        return layerInfoRows.toArray(new String[layerInfoRows.size()][0]);
    }

    //TODO float precision for smaller transfers?
    //First: iteration. Second: ratios, by parameter
    private static MeanMagnitudes getLayerMeanMagnitudes(int layerIdx, TrainModuleUtils.GraphInfo gi,
                                                         List<Persistable> updates, List<Integer> iterationCounts, ModelType modelType) {
        if (gi == null) {
            return new MeanMagnitudes(Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyMap());
        }

        String layerName = gi.getVertexNames().get(layerIdx);
        if (modelType != ModelType.CG) {
            //Get the original name, for the index...
            layerName = gi.getOriginalVertexName().get(layerIdx);
        }
        String layerType = gi.getVertexTypes().get(layerIdx);
        if ("input".equalsIgnoreCase(layerType)) { //TODO better checking - other vertices, etc
            return new MeanMagnitudes(Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyMap());
        }

        List<Integer> iterCounts = new ArrayList<>();
        Map<String, List<Double>> ratioValues = new HashMap<>();
        Map<String, List<Double>> outParamMM = new HashMap<>();
        Map<String, List<Double>> outUpdateMM = new HashMap<>();

        if (updates != null) {
            int pCount = -1;
            for (Persistable u : updates) {
                pCount++;
                if (!(u instanceof StatsReport))
                    continue;
                StatsReport sp = (StatsReport) u;
                if (iterationCounts != null) {
                    iterCounts.add(iterationCounts.get(pCount));
                } else {
                    int iterCount = sp.getIterationCount();
                    iterCounts.add(iterCount);
                }


                //Info we want, for each parameter in this layer: mean magnitudes for parameters, updates AND the ratio of these
                Map<String, Double> paramMM = sp.getMeanMagnitudes(StatsType.Parameters);
                Map<String, Double> updateMM = sp.getMeanMagnitudes(StatsType.Updates);
                for (String s : paramMM.keySet()) {
                    String prefix;
                    if (modelType == ModelType.Layer) {
                        prefix = layerName;
                    } else {
                        prefix = layerName + "_";
                    }

                    if (s.startsWith(prefix)) {
                        //Relevant parameter for this layer...
                        String layerParam = s.substring(prefix.length());
                        double pmm = paramMM.getOrDefault(s, 0.0);
                        double umm = updateMM.getOrDefault(s, 0.0);
                        if (!Double.isFinite(pmm)) {
                            pmm = NAN_REPLACEMENT_VALUE;
                        }
                        if (!Double.isFinite(umm)) {
                            umm = NAN_REPLACEMENT_VALUE;
                        }
                        double ratio;
                        if (umm == 0.0 && pmm == 0.0) {
                            ratio = 0.0; //To avoid NaN from 0/0
                        } else {
                            ratio = umm / pmm;
                        }
                        List<Double> list = ratioValues.get(layerParam);
                        if (list == null) {
                            list = new ArrayList<>();
                            ratioValues.put(layerParam, list);
                        }
                        list.add(ratio);

                        List<Double> pmmList = outParamMM.get(layerParam);
                        if (pmmList == null) {
                            pmmList = new ArrayList<>();
                            outParamMM.put(layerParam, pmmList);
                        }
                        pmmList.add(pmm);

                        List<Double> ummList = outUpdateMM.get(layerParam);
                        if (ummList == null) {
                            ummList = new ArrayList<>();
                            outUpdateMM.put(layerParam, ummList);
                        }
                        ummList.add(umm);
                    }
                }
            }
        }

        return new MeanMagnitudes(iterCounts, ratioValues, outParamMM, outUpdateMM);
    }

    private static Triple<int[], float[], float[]> EMPTY_TRIPLE = new Triple<>(new int[0], new float[0], new float[0]);

    private static Triple<int[], float[], float[]> getLayerActivations(int index, TrainModuleUtils.GraphInfo gi,
                                                                       List<Persistable> updates, List<Integer> iterationCounts) {
        if (gi == null) {
            return EMPTY_TRIPLE;
        }

        String type = gi.getVertexTypes().get(index); //Index may be for an input, for example
        if ("input".equalsIgnoreCase(type)) {
            return EMPTY_TRIPLE;
        }
        List<String> origNames = gi.getOriginalVertexName();
        if (index < 0 || index >= origNames.size()) {
            return EMPTY_TRIPLE;
        }
        String layerName = origNames.get(index);

        int size = (updates == null ? 0 : updates.size());
        int[] iterCounts = new int[size];
        float[] mean = new float[size];
        float[] stdev = new float[size];
        int used = 0;
        if (updates != null) {
            int uCount = -1;
            for (Persistable u : updates) {
                uCount++;
                if (!(u instanceof StatsReport))
                    continue;
                StatsReport sp = (StatsReport) u;
                if (iterationCounts == null) {
                    iterCounts[used] = sp.getIterationCount();
                } else {
                    iterCounts[used] = iterationCounts.get(uCount);
                }

                Map<String, Double> means = sp.getMean(StatsType.Activations);
                Map<String, Double> stdevs = sp.getStdev(StatsType.Activations);

                //TODO PROPER VALIDATION ETC, ERROR HANDLING
                if (means != null && means.containsKey(layerName)) {
                    mean[used] = means.get(layerName).floatValue();
                    stdev[used] = stdevs.get(layerName).floatValue();
                    if (!Float.isFinite(mean[used])) {
                        mean[used] = (float) NAN_REPLACEMENT_VALUE;
                    }
                    if (!Float.isFinite(stdev[used])) {
                        stdev[used] = (float) NAN_REPLACEMENT_VALUE;
                    }
                    used++;
                }
            }
        }

        if (used != iterCounts.length) {
            iterCounts = Arrays.copyOf(iterCounts, used);
            mean = Arrays.copyOf(mean, used);
            stdev = Arrays.copyOf(stdev, used);
        }

        return new Triple<>(iterCounts, mean, stdev);
    }

    private static final Map<String, Object> EMPTY_LR_MAP = new HashMap<>();

    static {
        EMPTY_LR_MAP.put("iterCounts", new int[0]);
        EMPTY_LR_MAP.put("paramNames", Collections.EMPTY_LIST);
        EMPTY_LR_MAP.put("lrs", Collections.EMPTY_MAP);
    }

    private static Map<String, Object> getLayerLearningRates(int layerIdx, TrainModuleUtils.GraphInfo gi,
                                                             List<Persistable> updates, List<Integer> iterationCounts, ModelType modelType) {
        if (gi == null) {
            return Collections.emptyMap();
        }

        List<String> origNames = gi.getOriginalVertexName();

        String type = gi.getVertexTypes().get(layerIdx); //Index may be for an input, for example
        if ("input".equalsIgnoreCase(type)) {
            return EMPTY_LR_MAP;
        }

        if (layerIdx < 0 || layerIdx >= origNames.size()) {
            return EMPTY_LR_MAP;
        }

        String layerName = gi.getOriginalVertexName().get(layerIdx);

        int size = (updates == null ? 0 : updates.size());
        int[] iterCounts = new int[size];
        Map<String, float[]> byName = new HashMap<>();
        int used = 0;
        if (updates != null) {
            int uCount = -1;
            for (Persistable u : updates) {
                uCount++;
                if (!(u instanceof StatsReport))
                    continue;
                StatsReport sp = (StatsReport) u;
                if (iterationCounts == null) {
                    iterCounts[used] = sp.getIterationCount();
                } else {
                    iterCounts[used] = iterationCounts.get(uCount);
                }

                //TODO PROPER VALIDATION ETC, ERROR HANDLING
                Map<String, Double> lrs = sp.getLearningRates();

                String prefix;
                if (modelType == ModelType.Layer) {
                    prefix = layerName;
                } else {
                    prefix = layerName + "_";
                }

                for (String p : lrs.keySet()) {

                    if (p.startsWith(prefix)) {
                        String layerParamName = p.substring(Math.min(p.length(), prefix.length()));
                        if (!byName.containsKey(layerParamName)) {
                            byName.put(layerParamName, new float[size]);
                        }
                        float[] lrThisParam = byName.get(layerParamName);
                        lrThisParam[used] = lrs.get(p).floatValue();
                    }
                }
                used++;
            }
        }

        List<String> paramNames = new ArrayList<>(byName.keySet());
        Collections.sort(paramNames); //Sorted for consistency

        Map<String, Object> ret = new HashMap<>();
        ret.put("iterCounts", iterCounts);
        ret.put("paramNames", paramNames);
        ret.put("lrs", byName);

        return ret;
    }


    private static Map<String, Object> getHistograms(int layerIdx, TrainModuleUtils.GraphInfo gi, StatsType statsType,
                                                     Persistable p) {
        if (p == null)
            return null;
        if (!(p instanceof StatsReport))
            return null;
        StatsReport sr = (StatsReport) p;

        String layerName = gi.getOriginalVertexName().get(layerIdx);

        Map<String, Histogram> map = sr.getHistograms(statsType);

        List<String> paramNames = new ArrayList<>();

        Map<String, Object> ret = new HashMap<>();
        if (layerName != null) {
            for (String s : map.keySet()) {
                if (s.startsWith(layerName)) {
                    String paramName;
                    if (s.charAt(layerName.length()) == '_') {
                        //MLN or CG parameter naming convention
                        paramName = s.substring(layerName.length() + 1);
                    } else {
                        //Pretrain layer (VAE, AE) naming convention
                        paramName = s.substring(layerName.length());
                    }


                    paramNames.add(paramName);
                    Histogram h = map.get(s);
                    Map<String, Object> thisHist = new HashMap<>();
                    double min = h.getMin();
                    double max = h.getMax();
                    if (Double.isNaN(min)) {
                        //If either is NaN, both will be
                        min = NAN_REPLACEMENT_VALUE;
                        max = NAN_REPLACEMENT_VALUE;
                    }
                    thisHist.put("min", min);
                    thisHist.put("max", max);
                    thisHist.put("bins", h.getNBins());
                    thisHist.put("counts", h.getBinCounts());
                    ret.put(paramName, thisHist);
                }
            }
        }
        ret.put("paramNames", paramNames);

        return ret;
    }

    private static Map<String, Object> getMemory(List<Persistable> staticInfoAllWorkers,
                                                 List<Persistable> updatesLastNMinutes, I18N i18n) {

        Map<String, Object> ret = new HashMap<>();

        //First: map workers to JVMs
        Set<String> jvmIDs = new HashSet<>();
        Map<String, String> workersToJvms = new HashMap<>();
        Map<String, Integer> workerNumDevices = new HashMap<>();
        Map<String, String[]> deviceNames = new HashMap<>();
        for (Persistable p : staticInfoAllWorkers) {
            //TODO validation/checks
            StatsInitializationReport init = (StatsInitializationReport) p;
            String jvmuid = init.getSwJvmUID();
            workersToJvms.put(p.getWorkerID(), jvmuid);
            jvmIDs.add(jvmuid);

            int nDevices = init.getHwNumDevices();
            workerNumDevices.put(p.getWorkerID(), nDevices);

            if (nDevices > 0) {
                String[] deviceNamesArr = init.getHwDeviceDescription();
                deviceNames.put(p.getWorkerID(), deviceNamesArr);
            }
        }

        List<String> jvmList = new ArrayList<>(jvmIDs);
        Collections.sort(jvmList);

        //For each unique JVM, collect memory info
        //Do this by selecting the first worker
        int count = 0;
        for (String jvm : jvmList) {
            List<String> workersForJvm = new ArrayList<>();
            for (String s : workersToJvms.keySet()) {
                if (workersToJvms.get(s).equals(jvm)) {
                    workersForJvm.add(s);
                }
            }
            Collections.sort(workersForJvm);
            String wid = workersForJvm.get(0);

            int numDevices = workerNumDevices.get(wid);

            Map<String, Object> jvmData = new HashMap<>();

            List<Long> timestamps = new ArrayList<>();
            List<Float> fracJvm = new ArrayList<>();
            List<Float> fracOffHeap = new ArrayList<>();
            long[] lastBytes = new long[2 + numDevices];
            long[] lastMaxBytes = new long[2 + numDevices];

            List<List<Float>> fracDeviceMem = null;
            if (numDevices > 0) {
                fracDeviceMem = new ArrayList<>(numDevices);
                for (int i = 0; i < numDevices; i++) {
                    fracDeviceMem.add(new ArrayList<>());
                }
            }

            if (updatesLastNMinutes != null) {
                for (Persistable p : updatesLastNMinutes) {
                    //TODO single pass
                    if (!p.getWorkerID().equals(wid))
                        continue;
                    if (!(p instanceof StatsReport))
                        continue;

                    StatsReport sp = (StatsReport) p;

                    timestamps.add(sp.getTimeStamp());

                    long jvmCurrentBytes = sp.getJvmCurrentBytes();
                    long jvmMaxBytes = sp.getJvmMaxBytes();
                    long ohCurrentBytes = sp.getOffHeapCurrentBytes();
                    long ohMaxBytes = sp.getOffHeapMaxBytes();

                    double jvmFrac = jvmCurrentBytes / ((double) jvmMaxBytes);
                    double offheapFrac = ohCurrentBytes / ((double) ohMaxBytes);
                    if (Double.isNaN(jvmFrac))
                        jvmFrac = 0.0;
                    if (Double.isNaN(offheapFrac))
                        offheapFrac = 0.0;
                    fracJvm.add((float) jvmFrac);
                    fracOffHeap.add((float) offheapFrac);

                    lastBytes[0] = jvmCurrentBytes;
                    lastBytes[1] = ohCurrentBytes;

                    lastMaxBytes[0] = jvmMaxBytes;
                    lastMaxBytes[1] = ohMaxBytes;

                    if (numDevices > 0) {
                        long[] devBytes = sp.getDeviceCurrentBytes();
                        long[] devMaxBytes = sp.getDeviceMaxBytes();
                        for (int i = 0; i < numDevices; i++) {
                            double frac = devBytes[i] / ((double) devMaxBytes[i]);
                            if (Double.isNaN(frac))
                                frac = 0.0;
                            fracDeviceMem.get(i).add((float) frac);
                            lastBytes[2 + i] = devBytes[i];
                            lastMaxBytes[2 + i] = devMaxBytes[i];
                        }
                    }
                }
            }


            List<List<Float>> fracUtilized = new ArrayList<>();
            fracUtilized.add(fracJvm);
            fracUtilized.add(fracOffHeap);

            String[] seriesNames = new String[2 + numDevices];
            seriesNames[0] = i18n.getMessage("train.system.hwTable.jvmCurrent");
            seriesNames[1] = i18n.getMessage("train.system.hwTable.offHeapCurrent");
            boolean[] isDevice = new boolean[2 + numDevices];
            String[] devNames = deviceNames.get(wid);
            for (int i = 0; i < numDevices; i++) {
                seriesNames[2 + i] = devNames != null && devNames.length > i ? devNames[i] : "";
                fracUtilized.add(fracDeviceMem.get(i));
                isDevice[2 + i] = true;
            }

            jvmData.put("times", timestamps);
            jvmData.put("isDevice", isDevice);
            jvmData.put("seriesNames", seriesNames);
            jvmData.put("values", fracUtilized);
            jvmData.put("currentBytes", lastBytes);
            jvmData.put("maxBytes", lastMaxBytes);
            ret.put(String.valueOf(count), jvmData);

            count++;
        }

        return ret;
    }

    private static Pair<Map<String, Object>, Map<String, Object>> getHardwareSoftwareInfo(
            List<Persistable> staticInfoAllWorkers, I18N i18n) {
        Map<String, Object> retHw = new HashMap<>();
        Map<String, Object> retSw = new HashMap<>();

        //First: map workers to JVMs
        Set<String> jvmIDs = new HashSet<>();
        Map<String, StatsInitializationReport> staticByJvm = new HashMap<>();
        for (Persistable p : staticInfoAllWorkers) {
            //TODO validation/checks
            StatsInitializationReport init = (StatsInitializationReport) p;
            String jvmuid = init.getSwJvmUID();
            jvmIDs.add(jvmuid);
            staticByJvm.put(jvmuid, init);
        }

        List<String> jvmList = new ArrayList<>(jvmIDs);
        Collections.sort(jvmList);

        //For each unique JVM, collect hardware info
        int count = 0;
        for (String jvm : jvmList) {
            StatsInitializationReport sr = staticByJvm.get(jvm);

            //---- Hardware Info ----
            List<String[]> hwInfo = new ArrayList<>();
            int numDevices = sr.getHwNumDevices();
            String[] deviceDescription = sr.getHwDeviceDescription();
            long[] devTotalMem = sr.getHwDeviceTotalMemory();

            hwInfo.add(new String[]{i18n.getMessage("train.system.hwTable.jvmMax"),
                    String.valueOf(sr.getHwJvmMaxMemory())});
            hwInfo.add(new String[]{i18n.getMessage("train.system.hwTable.offHeapMax"),
                    String.valueOf(sr.getHwOffHeapMaxMemory())});
            hwInfo.add(new String[]{i18n.getMessage("train.system.hwTable.jvmProcs"),
                    String.valueOf(sr.getHwJvmAvailableProcessors())});
            hwInfo.add(new String[]{i18n.getMessage("train.system.hwTable.computeDevices"),
                    String.valueOf(numDevices)});
            for (int i = 0; i < numDevices; i++) {
                String label = i18n.getMessage("train.system.hwTable.deviceName") + " (" + i + ")";
                String name = (deviceDescription == null || i >= deviceDescription.length ? String.valueOf(i)
                        : deviceDescription[i]);
                hwInfo.add(new String[]{label, name});

                String memLabel = i18n.getMessage("train.system.hwTable.deviceMemory") + " (" + i + ")";
                String memBytes =
                        (devTotalMem == null || i >= devTotalMem.length ? "-" : String.valueOf(devTotalMem[i]));
                hwInfo.add(new String[]{memLabel, memBytes});
            }

            retHw.put(String.valueOf(count), hwInfo);

            //---- Software Info -----

            String nd4jBackend = sr.getSwNd4jBackendClass();
            if (nd4jBackend != null && nd4jBackend.contains(".")) {
                int idx = nd4jBackend.lastIndexOf('.');
                nd4jBackend = nd4jBackend.substring(idx + 1);
                String temp;
                switch (nd4jBackend) {
                    case "CpuNDArrayFactory":
                        temp = "CPU";
                        break;
                    case "JCublasNDArrayFactory":
                        temp = "CUDA";
                        break;
                    default:
                        temp = nd4jBackend;
                }
                nd4jBackend = temp;
            }

            String datatype = sr.getSwNd4jDataTypeName();
            if (datatype == null)
                datatype = "";
            else
                datatype = datatype.toLowerCase();

            List<String[]> swInfo = new ArrayList<>();
            swInfo.add(new String[]{i18n.getMessage("train.system.swTable.os"), sr.getSwOsName()});
            swInfo.add(new String[]{i18n.getMessage("train.system.swTable.hostname"), sr.getSwHostName()});
            swInfo.add(new String[]{i18n.getMessage("train.system.swTable.osArch"), sr.getSwArch()});
            swInfo.add(new String[]{i18n.getMessage("train.system.swTable.jvmName"), sr.getSwJvmName()});
            swInfo.add(new String[]{i18n.getMessage("train.system.swTable.jvmVersion"), sr.getSwJvmVersion()});
            swInfo.add(new String[]{i18n.getMessage("train.system.swTable.nd4jBackend"), nd4jBackend});
            swInfo.add(new String[]{i18n.getMessage("train.system.swTable.nd4jDataType"), datatype});

            retSw.put(String.valueOf(count), swInfo);

            count++;
        }

        return new Pair<>(retHw, retSw);
    }


    @AllArgsConstructor
    @Data
    private static class MeanMagnitudes {
        private List<Integer> iterations;
        private Map<String, List<Double>> ratios;
        private Map<String, List<Double>> paramMM;
        private Map<String, List<Double>> updateMM;
    }


    private static final String asJson(Object o) {
        try {
            return JsonMappers.getMapper().writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public List<I18NResource> getInternationalizationResources() {
        List<I18NResource> files = new ArrayList<>();
        String[] langs = new String[]{"de", "en", "ja", "ko", "ru", "zh"};
        addAll(files, "train", langs);
        addAll(files, "train.model", langs);
        addAll(files, "train.overview", langs);
        addAll(files, "train.system", langs);
        return files;
    }

    private static void addAll(List<I18NResource> to, String prefix, String... suffixes) {
        for (String s : suffixes) {
            to.add(new I18NResource("dl4j_i18n/" + prefix + "." + s));
        }
    }
}
