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

package org.deeplearning4j.text.documentiterator;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.deeplearning4j.BaseDL4JTest;


import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.nd4j.common.io.ClassPathResource;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@Tag(TagNames.FILE_IO)
@NativeTag
public class FileDocumentIteratorTest extends BaseDL4JTest {



    @BeforeEach
    public void setUp() throws Exception {

    }

    /**
     * Checks actual number of documents retrieved by DocumentIterator
     * @throws Exception
     */
    @Test
    public void testNextDocument() throws Exception {
        ClassPathResource reuters5250 = new ClassPathResource("/reuters/5250");
        File f = reuters5250.getFile();

        DocumentIterator iter = new FileDocumentIterator(f.getAbsolutePath());

        log.info(f.getAbsolutePath());

        int cnt = 0;
        while (iter.hasNext()) {
            InputStream stream = iter.nextDocument();
            stream.close();
            cnt++;
        }

        assertEquals(24, cnt);
    }


    /**
     * Checks actual number of documents retrieved by DocumentIterator after being RESET
     * @throws Exception
     */
    @Test
    public void testDocumentReset() throws Exception {
        ClassPathResource reuters5250 = new ClassPathResource("/reuters/5250");
        File f = reuters5250.getFile();

        DocumentIterator iter = new FileDocumentIterator(f.getAbsolutePath());

        int cnt = 0;
        while (iter.hasNext()) {
            InputStream stream = iter.nextDocument();
            stream.close();
            cnt++;
        }

        iter.reset();

        while (iter.hasNext()) {
            InputStream stream = iter.nextDocument();
            stream.close();
            cnt++;
        }

        assertEquals(48, cnt);
    }

    @Test()
    @Timeout(5000)
    public void testEmptyDocument(@TempDir Path testDir) throws Exception {
        File f = Files.createTempFile(testDir,"newfile","bin").toFile();
        assertTrue(f.exists());
        assertEquals(0, f.length());

        try {
            DocumentIterator iter = new FileDocumentIterator(f.getAbsolutePath());
        } catch (Throwable t){
            String msg = t.getMessage();
            assertTrue(msg.contains("empty"));
        }
    }

    @Test()
    @Timeout(5000)
    public void testEmptyDocument2(@TempDir Path testDir) throws Exception {
        File dir = testDir.toFile();
        File f1 = new File(dir, "1.txt");
        FileUtils.writeStringToFile(f1, "line 1\nline2", StandardCharsets.UTF_8);
        File f2 = new File(dir, "2.txt");
        f2.createNewFile();
        File f3 = new File(dir, "3.txt");
        FileUtils.writeStringToFile(f3, "line 3\nline4", StandardCharsets.UTF_8);

        DocumentIterator iter = new FileDocumentIterator(dir);
        int count = 0;
        Set<String> lines = new HashSet<>();
        while(iter.hasNext()){
            String next = IOUtils.readLines(iter.nextDocument(), StandardCharsets.UTF_8).get(0);
            lines.add(next);
        }

        assertEquals(4, lines.size());
    }

}
