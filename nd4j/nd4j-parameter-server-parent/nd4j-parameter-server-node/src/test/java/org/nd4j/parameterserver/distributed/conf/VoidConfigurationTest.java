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

package org.nd4j.parameterserver.distributed.conf;

import org.junit.jupiter.api.Disabled;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.nd4j.common.tests.BaseND4JTest;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.exception.ND4JIllegalStateException;

import static org.junit.jupiter.api.Assertions.*;

@Disabled
@Tag(TagNames.FILE_IO)
@Tag(TagNames.DIST_SYSTEMS)
@NativeTag
public class VoidConfigurationTest extends BaseND4JTest {



    @Test
    public void testNetworkMask1() throws Exception {
        VoidConfiguration configuration = new VoidConfiguration();
        configuration.setNetworkMask("192.168.1.0/24");

        assertEquals("192.168.1.0/24", configuration.getNetworkMask());
    }


    @Test
    public void testNetworkMask2() throws Exception {
        VoidConfiguration configuration = new VoidConfiguration();
        configuration.setNetworkMask("192.168.1.12");

        assertEquals("192.168.1.0/24", configuration.getNetworkMask());
    }

    @Test
    public void testNetworkMask5() throws Exception {
        VoidConfiguration configuration = new VoidConfiguration();
        configuration.setNetworkMask("192.168.0.0/16");

        assertEquals("192.168.0.0/16", configuration.getNetworkMask());
    }

    @Test
    public void testNetworkMask6() throws Exception {
        VoidConfiguration configuration = new VoidConfiguration();
        configuration.setNetworkMask("192.168.0.0/8");

        assertEquals("192.168.0.0/8", configuration.getNetworkMask());
    }

    @Test()
    public void testNetworkMask3() throws Exception {
        assertThrows(ND4JIllegalStateException.class,() -> {
            VoidConfiguration configuration = new VoidConfiguration();
            configuration.setNetworkMask("192.256.1.1/24");

            assertEquals("192.168.1.0/24", configuration.getNetworkMask());
        });

    }

    @Test()
    public void testNetworkMask4() throws Exception {
        assertThrows(ND4JIllegalStateException.class,() -> {
            VoidConfiguration configuration = new VoidConfiguration();
            configuration.setNetworkMask("0.0.0.0/8");

            assertEquals("192.168.1.0/24", configuration.getNetworkMask());
        });

    }

    @Override
    public long getTimeoutMilliseconds() {
        return Long.MAX_VALUE;
    }
}
