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

package org.deeplearning4j.text.sentenceiterator;

import org.deeplearning4j.BaseDL4JTest;


import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.nd4j.common.resources.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Deprecated module")
public class PrefetchingSentenceIteratorTest extends BaseDL4JTest {



    protected static final Logger log = LoggerFactory.getLogger(PrefetchingSentenceIteratorTest.class);

    @Test
    public void testHasMoreLinesFile() throws Exception {
        File file = Resources.asFile("/big/raw_sentences.txt");
        BasicLineIterator iterator = new BasicLineIterator(file);

        PrefetchingSentenceIterator fetcher =
                        new PrefetchingSentenceIterator.Builder(iterator).setFetchSize(1000).build();

        log.info("Phase 1 starting");

        int cnt = 0;
        while (fetcher.hasNext()) {
            String line = fetcher.nextSentence();
            //            log.info(line);
            cnt++;
        }


        assertEquals(97162, cnt);

        log.info("Phase 2 starting");
        fetcher.reset();

        cnt = 0;
        while (fetcher.hasNext()) {
            String line = fetcher.nextSentence();
            cnt++;
        }

        assertEquals(97162, cnt);
    }

    @Test
    public void testLoadedIterator1() throws Exception {
        File file = Resources.asFile("/big/raw_sentences.txt");
        BasicLineIterator iterator = new BasicLineIterator(file);

        PrefetchingSentenceIterator fetcher =
                        new PrefetchingSentenceIterator.Builder(iterator).setFetchSize(1000).build();

        log.info("Phase 1 starting");

        int cnt = 0;
        while (fetcher.hasNext()) {
            String line = fetcher.nextSentence();
            // we'll imitate some workload in current thread by using ThreadSleep.
            // there's no need to keep it enabled forever, just uncomment next line if you're going to test this iterator.
            // otherwise this test will

            //    Thread.sleep(0, 10);

            cnt++;
            if (cnt % 10000 == 0)
                log.info("Line processed: " + cnt);
        }
    }

    @Test
    public void testPerformance1() throws Exception {
        File file = Resources.asFile("/big/raw_sentences.txt");

        BasicLineIterator iterator = new BasicLineIterator(file);

        PrefetchingSentenceIterator fetcher = new PrefetchingSentenceIterator.Builder(new BasicLineIterator(file))
                        .setFetchSize(500000).build();

        long time01 = System.currentTimeMillis();
        int cnt0 = 0;
        while (iterator.hasNext()) {
            iterator.nextSentence();
            cnt0++;
        }
        long time02 = System.currentTimeMillis();

        long time11 = System.currentTimeMillis();
        int cnt1 = 0;
        while (fetcher.hasNext()) {
            fetcher.nextSentence();
            cnt1++;
        }
        long time12 = System.currentTimeMillis();

        log.info("Basic iterator: " + (time02 - time01));

        log.info("Prefetched iterator: " + (time12 - time11));

        long difference = (time12 - time11) - (time02 - time01);
        log.info("Difference: " + difference);

        // on small corpus time difference can fluctuate a lot
        // but it's still can be used as effectiveness measurement
        assertTrue(difference < 150);
    }
}
