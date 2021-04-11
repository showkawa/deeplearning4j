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

package org.nd4j.linalg;

import org.nd4j.common.config.ND4JClassLoading;
import org.nd4j.linalg.factory.Nd4jBackend;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class Nd4jTestSuite  {
    //the system property for what backends should run
    public final static String BACKENDS_TO_LOAD = "backends";
    private static List<Nd4jBackend> BACKENDS = new ArrayList<>();
    static {
        ServiceLoader<Nd4jBackend> loadedBackends = ND4JClassLoading.loadService(Nd4jBackend.class);
        for (Nd4jBackend backend : loadedBackends) {
            BACKENDS.add(backend);
        }
    }



    /**
     * Based on the jvm arguments, an empty list is returned
     * if all backends should be run.
     * If only certain backends should run, please
     * pass a csv to the jvm as follows:
     * -Dorg.nd4j.linalg.tests.backendstorun=your.class1,your.class2
     * @return the list of backends to run
     */
    public static List<String> backendsToRun() {
        List<String> ret = new ArrayList<>();
        String val = System.getProperty(BACKENDS_TO_LOAD, "");
        if (val.isEmpty())
            return ret;

        String[] clazzes = val.split(",");

        for (String s : clazzes)
            ret.add(s);
        return ret;

    }



}
