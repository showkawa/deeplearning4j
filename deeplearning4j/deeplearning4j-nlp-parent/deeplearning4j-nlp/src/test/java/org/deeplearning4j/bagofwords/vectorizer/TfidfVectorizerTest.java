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

package org.deeplearning4j.bagofwords.vectorizer;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.deeplearning4j.BaseDL4JTest;


import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.nd4j.common.io.ClassPathResource;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.documentiterator.LabelsSource;
import org.deeplearning4j.text.documentiterator.SimpleLabelAwareIterator;
import org.deeplearning4j.text.sentenceiterator.CollectionSentenceIterator;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareFileSentenceIterator;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizer.DefaultTokenizer;
import org.deeplearning4j.text.tokenization.tokenizer.Tokenizer;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.nd4j.common.util.SerializationUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Adam Gibson
 */
@Slf4j
@Tag(TagNames.FILE_IO)
@NativeTag
public class TfidfVectorizerTest extends BaseDL4JTest {




    @Test()
    @Timeout(60000L)
    public void testTfIdfVectorizer(@TempDir Path testDir) throws Exception {
        val rootDir = testDir.toFile();
        ClassPathResource resource = new ClassPathResource("tripledir/");
        resource.copyDirectory(rootDir);

        assertTrue(rootDir.isDirectory());

        LabelAwareSentenceIterator iter = new LabelAwareFileSentenceIterator(rootDir);
        TokenizerFactory tokenizerFactory = new DefaultTokenizerFactory();

        TfidfVectorizer vectorizer = new TfidfVectorizer.Builder().setMinWordFrequency(1)
                .setStopWords(new ArrayList<String>()).setTokenizerFactory(tokenizerFactory).setIterator(iter)
                .allowParallelTokenization(false)
                //                .labels(labels)
                //                .cleanup(true)
                .build();

        vectorizer.fit();
        VocabWord word = vectorizer.getVocabCache().wordFor("file.");
        assertNotNull(word);
        assertEquals(word, vectorizer.getVocabCache().tokenFor("file."));
        assertEquals(3, vectorizer.getVocabCache().totalNumberOfDocs());

        assertEquals(3, word.getSequencesCount());
        assertEquals(3, word.getElementFrequency(), 0.1);

        VocabWord word1 = vectorizer.getVocabCache().wordFor("1");

        assertEquals(1, word1.getSequencesCount());
        assertEquals(1, word1.getElementFrequency(), 0.1);

        log.info("Labels used: " + vectorizer.getLabelsSource().getLabels());
        assertEquals(3, vectorizer.getLabelsSource().getNumberOfLabelsUsed());

        assertEquals(3, vectorizer.getVocabCache().totalNumberOfDocs());

        assertEquals(11, vectorizer.numWordsEncountered());

        INDArray vector = vectorizer.transform("This is 3 file.");
        log.info("TF-IDF vector: " + Arrays.toString(vector.data().asDouble()));

        VocabCache<VocabWord> vocabCache = vectorizer.getVocabCache();

        assertEquals(.04402, vector.getDouble(vocabCache.tokenFor("This").getIndex()), 0.001);
        assertEquals(.04402, vector.getDouble(vocabCache.tokenFor("is").getIndex()), 0.001);
        assertEquals(0.119, vector.getDouble(vocabCache.tokenFor("3").getIndex()), 0.001);
        assertEquals(0, vector.getDouble(vocabCache.tokenFor("file.").getIndex()), 0.001);



        DataSet dataSet = vectorizer.vectorize("This is 3 file.", "label3");
        //assertEquals(0.0, dataSet.getLabels().getDouble(0), 0.1);
        //assertEquals(0.0, dataSet.getLabels().getDouble(1), 0.1);
        //assertEquals(1.0, dataSet.getLabels().getDouble(2), 0.1);
        int cnt = 0;
        for (int i = 0; i < 3; i++) {
            if (dataSet.getLabels().getDouble(i) > 0.1)
                cnt++;
        }

        assertEquals(1, cnt);



        File tempFile = Files.createTempFile(testDir,"somefile","bin").toFile();
        tempFile.delete();

        SerializationUtils.saveObject(vectorizer, tempFile);

        TfidfVectorizer vectorizer2 = SerializationUtils.readObject(tempFile);
        vectorizer2.setTokenizerFactory(tokenizerFactory);

        dataSet = vectorizer2.vectorize("This is 3 file.", "label2");
        assertEquals(vector, dataSet.getFeatures());
    }

    public void testTfIdfVectorizerFromLabelAwareIterator() throws Exception {
        LabelledDocument doc1 = new LabelledDocument();
        doc1.addLabel("dog");
        doc1.setContent("it barks like a dog");

        LabelledDocument doc2 = new LabelledDocument();
        doc2.addLabel("cat");
        doc2.setContent("it meows like a cat");

        List<LabelledDocument> docs = new ArrayList<>(2);
        docs.add(doc1);
        docs.add(doc2);

        LabelAwareIterator iterator = new SimpleLabelAwareIterator(docs);
        TokenizerFactory tokenizerFactory = new DefaultTokenizerFactory();

        TfidfVectorizer vectorizer = new TfidfVectorizer
                .Builder()
                .setMinWordFrequency(1)
                .setStopWords(new ArrayList<String>())
                .setTokenizerFactory(tokenizerFactory)
                .setIterator(iterator)
                .allowParallelTokenization(false)
                .build();

        vectorizer.fit();

        DataSet dataset = vectorizer.vectorize("it meows like a cat", "cat");
        assertNotNull(dataset);

        LabelsSource source = vectorizer.getLabelsSource();
        assertEquals(2, source.getNumberOfLabelsUsed());
        List<String> labels = source.getLabels();
        assertEquals("dog", labels.get(0));
        assertEquals("cat", labels.get(1));
    }

    @Test()
    @Timeout(10000L)
    public void testParallelFlag1() throws Exception {
        val vectorizer = new TfidfVectorizer.Builder()
                .allowParallelTokenization(false)
                .build();

        assertFalse(vectorizer.isParallel);
    }


    @Test()
    @Timeout(20000L)
    public void testParallelFlag2() throws Exception {
        assertThrows(ND4JIllegalStateException.class,() -> {
            val collection = new ArrayList<String>();
            collection.add("First string");
            collection.add("Second string");
            collection.add("Third string");
            collection.add("");
            collection.add("Fifth string");
//        collection.add("caboom");

            val vectorizer = new TfidfVectorizer.Builder()
                    .allowParallelTokenization(false)
                    .setIterator(new CollectionSentenceIterator(collection))
                    .setTokenizerFactory(new ExplodingTokenizerFactory(8, -1))
                    .build();

            vectorizer.buildVocab();


            log.info("Fitting vectorizer...");

            vectorizer.fit();
        });

    }

    @Test()
    @Timeout(20000L)
    public void testParallelFlag3() throws Exception {
        assertThrows(ND4JIllegalStateException.class,() -> {
            val collection = new ArrayList<String>();
            collection.add("First string");
            collection.add("Second string");
            collection.add("Third string");
            collection.add("");
            collection.add("Fifth string");
            collection.add("Long long long string");
            collection.add("Sixth string");

            val vectorizer = new TfidfVectorizer.Builder()
                    .allowParallelTokenization(false)
                    .setIterator(new CollectionSentenceIterator(collection))
                    .setTokenizerFactory(new ExplodingTokenizerFactory(-1, 4))
                    .build();

            vectorizer.buildVocab();


            log.info("Fitting vectorizer...");

            vectorizer.fit();
        });

    }


    protected class ExplodingTokenizerFactory extends DefaultTokenizerFactory {
        protected int triggerSentence;
        protected int triggerWord;
        protected AtomicLong cnt = new AtomicLong(0);

        protected ExplodingTokenizerFactory(int triggerSentence, int triggerWord) {
            this.triggerSentence = triggerSentence;
            this.triggerWord = triggerWord;
        }

        @Override
        public Tokenizer create(String toTokenize) {

            if (triggerSentence >= 0 && cnt.incrementAndGet() >= triggerSentence)
                throw new ND4JIllegalStateException("TokenizerFactory exploded");


            val tkn = new ExplodingTokenizer(toTokenize, triggerWord);

            return tkn;
        }
    }

    protected class ExplodingTokenizer extends DefaultTokenizer {
        protected int triggerWord;

        public ExplodingTokenizer(String string, int triggerWord) {
            super(string);

            this.triggerWord = triggerWord;
            if (this.triggerWord >= 0)
                if (this.countTokens() >= triggerWord)
                    throw new ND4JIllegalStateException("Tokenizer exploded");
        }
    }
}
