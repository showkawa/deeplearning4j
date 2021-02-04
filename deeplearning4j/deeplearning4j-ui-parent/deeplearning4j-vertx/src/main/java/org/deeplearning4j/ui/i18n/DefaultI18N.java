/*******************************************************************************
 * Copyright (c) 2015-2018 Skymind, Inc.
 * Copyright (c) 2019 Konduit K.K.
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

package org.deeplearning4j.ui.i18n;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.deeplearning4j.common.config.DL4JClassLoading;
import org.deeplearning4j.ui.api.I18N;
import org.deeplearning4j.ui.api.UIModule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Default internationalization implementation.<br>
 * Content for internationalization is implemented using resource files.<br>
 * For the resource files: they should be specified as follows:<br>
 * 1. In the /dl4j_i18n/ directory in resources<br>
 * 2. Filenames should be "somekey.langcode" - for example, "index.en" or "index.ja"<br>
 * 3. Each key should be unique across all files. Any key can appear in any file; files may be split for convenience<br>
 * <p>
 * Loading of these UI resources is done as follows:<br>
 * - On initialization of the DefaultI18N:<br>
 * &nbsp;&nbsp;- Resource files for the default language are loaded<br>
 * - If a different language is requested, the content will be loaded on demand (and stored in memory for future use)<br>
 * Note that if a specified language does not have the specified key, the result from the defaultfallback language (English)
 * will be used instead.
 *
 * @author Alex Black
 */
@Slf4j
public class DefaultI18N implements I18N {

    public static final String DEFAULT_LANGUAGE = "en";
    public static final String FALLBACK_LANGUAGE = "en"; //use this if the specified language doesn't have the requested message

    private static DefaultI18N instance;
    private static Map<String, I18N> sessionInstances = Collections.synchronizedMap(new HashMap<>());
    private static Throwable languageLoadingException = null;


    private String currentLanguage = DEFAULT_LANGUAGE;
    private Map<String, Map<String, String>> messagesByLanguage = new HashMap<>();

    /**
     * Get global instance (used in single-session mode)
     * @return global instance
     */
    public static synchronized I18N getInstance() {
        if (instance == null)
            instance = new DefaultI18N();
        return instance;
    }

    /**
     * Get instance for session
     * @param sessionId session ID for multi-session mode, leave it {@code null} for global instance
     * @return instance for session, or global instance
     */
    public static synchronized I18N getInstance(String sessionId) {
        if (sessionId == null) {
            return getInstance();
        } else {
            if (!sessionInstances.containsKey(sessionId)) {
                sessionInstances.put(sessionId, new DefaultI18N());
            }
            return sessionInstances.get(sessionId);
        }
    }

    /**
     * Remove I18N instance for session
     * @param sessionId session ID
     * @return the previous value associated with {@code sessionId},
     * or null if there was no mapping for {@code sessionId}
     */
    public static synchronized I18N removeInstance(String sessionId) {
        return sessionInstances.remove(sessionId);
    }


    private DefaultI18N() {
        loadLanguages();
    }

    private synchronized void loadLanguages(){
        ServiceLoader<UIModule> loadedModules = DL4JClassLoading.loadService(UIModule.class);

        for (UIModule module : loadedModules){
            List<I18NResource> resources = module.getInternationalizationResources();
            for(I18NResource resource : resources){
                try {
                    String path = resource.getResource();
                    int idxLast = path.lastIndexOf('.');
                    if (idxLast < 0) {
                        log.warn("Skipping language resource file: cannot infer language: {}", path);
                        continue;
                    }

                    String langCode = path.substring(idxLast + 1).toLowerCase();
                    Map<String, String> map = messagesByLanguage.computeIfAbsent(langCode, k -> new HashMap<>());

                    parseFile(resource, map);
                } catch (Throwable t){
                    log.warn("Error parsing UI I18N content file; skipping: {}", resource.getResource(), t);
                    languageLoadingException = t;
                }
            }
        }
    }

    private void parseFile(I18NResource r, Map<String,String> results){
        List<String> lines;
        try (InputStream is = r.getInputStream()){
            lines = IOUtils.readLines(is, StandardCharsets.UTF_8);
        } catch (IOException e){
            log.debug("Error parsing UI I18N content file; skipping: {} - {}", r.getResource(), e.getMessage());
            return;
        }

        int count = 0;
        for (String line : lines) {
            if (!line.matches(".+=.*")) {
                log.debug("Invalid line in I18N file: {}, \"{}\"", r.getResource(), line);
                continue;
            }
            int idx = line.indexOf('=');
            String key = line.substring(0, idx);
            String value = line.substring(Math.min(idx + 1, line.length()));
            results.put(key, value);
            count++;
        }

        log.trace("Loaded {} messages from file {}", count, r.getResource());
    }

    @Override
    public String getMessage(String key) {
        return getMessage(currentLanguage, key);
    }

    @Override
    public String getMessage(String langCode, String key) {
        Map<String, String> messagesForLanguage = messagesByLanguage.get(langCode);

        String msg;
        if (messagesForLanguage != null) {
            msg = messagesForLanguage.get(key);
            if (msg == null && !FALLBACK_LANGUAGE.equals(langCode)) {
                //Try getting the result from the fallback language
                return getMessage(FALLBACK_LANGUAGE, key);
            }
        } else {
            msg = getMessage(FALLBACK_LANGUAGE, key);
        }
        return msg;
    }

    @Override
    public String getDefaultLanguage() {
        return currentLanguage;
    }

    @Override
    public void setDefaultLanguage(String langCode) {
        this.currentLanguage = langCode;
        log.debug("UI: Set language to {}", langCode);
    }

    @Override
    public Map<String, String> getMessages(String langCode) {
        //Start with map for default language
        //Then overwrite with the actual language - so any missing are reported in default language
        Map<String,String> ret = new HashMap<>(messagesByLanguage.get(FALLBACK_LANGUAGE));
        if(!langCode.equals(FALLBACK_LANGUAGE)){
            ret.putAll(messagesByLanguage.get(langCode));
        }
        return ret;
    }
}
