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

package org.deeplearning4j.iterator;

import org.deeplearning4j.BaseDL4JTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.common.io.ClassPathResource;
import org.deeplearning4j.iterator.provider.CollectionLabeledSentenceProvider;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.junit.jupiter.api.Test;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
@Tag(TagNames.FILE_IO)
@NativeTag
public class TestCnnSentenceDataSetIterator extends BaseDL4JTest {

    @BeforeEach
    public void before(){
        Nd4j.setDefaultDataTypes(DataType.FLOAT, DataType.FLOAT);
    }

    @Test
    public void testSentenceIterator() throws Exception {
        WordVectors w2v = WordVectorSerializer
                        .readWord2VecModel(new ClassPathResource("word2vec/googleload/sample_vec.bin").getFile());

        int vectorSize = w2v.lookupTable().layerSize();

        //        Collection<String> words = w2v.lookupTable().getVocabCache().words();
        //        for(String s : words){
        //            System.out.println(s);
        //        }

        List<String> sentences = new ArrayList<>();
        //First word: all present
        sentences.add("these balance Database model");
        sentences.add("into same THISWORDDOESNTEXIST are");
        int maxLength = 4;
        List<String> s1 = Arrays.asList("these", "balance", "Database", "model");
        List<String> s2 = Arrays.asList("into", "same", "are");

        List<String> labelsForSentences = Arrays.asList("Positive", "Negative");

        INDArray expLabels = Nd4j.create(new float[][] {{0, 1}, {1, 0}}); //Order of labels: alphabetic. Positive -> [0,1]

        boolean[] alongHeightVals = new boolean[] {true, false};

        for(boolean norm : new boolean[]{true, false}) {
            for (boolean alongHeight : alongHeightVals) {

                INDArray expectedFeatures;
                if (alongHeight) {
                    expectedFeatures = Nd4j.create(2, 1, maxLength, vectorSize);
                } else {
                    expectedFeatures = Nd4j.create(2, 1, vectorSize, maxLength);
                }

                int[] fmShape;
                if(alongHeight){
                    fmShape = new int[]{2, 1, 4, 1};
                } else {
                    fmShape = new int[]{2, 1, 1, 4};
                }
                INDArray expectedFeatureMask = Nd4j.create(new float[][]{{1, 1, 1, 1}, {1, 1, 1, 0}}).reshape('c', fmShape);


                for (int i = 0; i < 4; i++) {
                    INDArray v = norm ? w2v.getWordVectorMatrixNormalized(s1.get(i)) : w2v.getWordVectorMatrix(s1.get(i));
                    if (alongHeight) {
                        expectedFeatures.get(NDArrayIndex.point(0), NDArrayIndex.point(0), NDArrayIndex.point(i),
                                NDArrayIndex.all()).assign(v);
                    } else {
                        expectedFeatures.get(NDArrayIndex.point(0), NDArrayIndex.point(0), NDArrayIndex.all(),
                                NDArrayIndex.point(i)).assign(v);
                    }
                }

                for (int i = 0; i < 3; i++) {
                    INDArray v = norm ? w2v.getWordVectorMatrixNormalized(s2.get(i)) : w2v.getWordVectorMatrix(s2.get(i));
                    if (alongHeight) {
                        expectedFeatures.get(NDArrayIndex.point(1), NDArrayIndex.point(0), NDArrayIndex.point(i),
                                NDArrayIndex.all()).assign(v);
                    } else {
                        expectedFeatures.get(NDArrayIndex.point(1), NDArrayIndex.point(0), NDArrayIndex.all(),
                                NDArrayIndex.point(i)).assign(v);
                    }
                }


                LabeledSentenceProvider p = new CollectionLabeledSentenceProvider(sentences, labelsForSentences, null);
                CnnSentenceDataSetIterator dsi = new CnnSentenceDataSetIterator.Builder(CnnSentenceDataSetIterator.Format.CNN2D)
                        .sentenceProvider(p).useNormalizedWordVectors(norm)
                        .wordVectors(w2v).maxSentenceLength(256).minibatchSize(32).sentencesAlongHeight(alongHeight)
                        .build();

                //            System.out.println("alongHeight = " + alongHeight);
                DataSet ds = dsi.next();
                assertArrayEquals(expectedFeatures.shape(), ds.getFeatures().shape());
                assertEquals(expectedFeatures, ds.getFeatures());
                assertEquals(expLabels, ds.getLabels());
                assertEquals(expectedFeatureMask, ds.getFeaturesMaskArray());
                assertNull(ds.getLabelsMaskArray());

                INDArray s1F = dsi.loadSingleSentence(sentences.get(0));
                INDArray s2F = dsi.loadSingleSentence(sentences.get(1));
                INDArray sub1 = ds.getFeatures().get(NDArrayIndex.interval(0, 0, true), NDArrayIndex.all(),
                        NDArrayIndex.all(), NDArrayIndex.all());
                INDArray sub2;
                if (alongHeight) {

                    sub2 = ds.getFeatures().get(NDArrayIndex.interval(1, 1, true), NDArrayIndex.all(),
                            NDArrayIndex.interval(0, 3), NDArrayIndex.all());
                } else {
                    sub2 = ds.getFeatures().get(NDArrayIndex.interval(1, 1, true), NDArrayIndex.all(), NDArrayIndex.all(),
                            NDArrayIndex.interval(0, 3));
                }

                assertArrayEquals(sub1.shape(), s1F.shape());
                assertArrayEquals(sub2.shape(), s2F.shape());
                assertEquals(sub1, s1F);
                assertEquals(sub2, s2F);
            }
        }
    }


    @Test
    public void testSentenceIteratorCNN1D_RNN() throws Exception {
        WordVectors w2v = WordVectorSerializer
                .readWord2VecModel(new ClassPathResource("word2vec/googleload/sample_vec.bin").getFile());

        int vectorSize = w2v.lookupTable().layerSize();

        List<String> sentences = new ArrayList<>();
        //First word: all present
        sentences.add("these balance Database model");
        sentences.add("into same THISWORDDOESNTEXIST are");
        int maxLength = 4;
        List<String> s1 = Arrays.asList("these", "balance", "Database", "model");
        List<String> s2 = Arrays.asList("into", "same", "are");

        List<String> labelsForSentences = Arrays.asList("Positive", "Negative");

        INDArray expLabels = Nd4j.create(new float[][] {{0, 1}, {1, 0}}); //Order of labels: alphabetic. Positive -> [0,1]

        for(boolean norm : new boolean[]{true, false}) {
            for(CnnSentenceDataSetIterator.Format f : new CnnSentenceDataSetIterator.Format[]{CnnSentenceDataSetIterator.Format.CNN1D, CnnSentenceDataSetIterator.Format.RNN}){

                INDArray expectedFeatures = Nd4j.create(2, vectorSize, maxLength);
                int[] fmShape = new int[]{2, 4};
                INDArray expectedFeatureMask = Nd4j.create(new float[][]{{1, 1, 1, 1}, {1, 1, 1, 0}}).reshape('c', fmShape);


                for (int i = 0; i < 4; i++) {
                    INDArray v = norm ? w2v.getWordVectorMatrixNormalized(s1.get(i)) : w2v.getWordVectorMatrix(s1.get(i));
                    expectedFeatures.get(NDArrayIndex.point(0), NDArrayIndex.all(),NDArrayIndex.point(i)).assign(v);
                }

                for (int i = 0; i < 3; i++) {
                    INDArray v = norm ? w2v.getWordVectorMatrixNormalized(s2.get(i)) : w2v.getWordVectorMatrix(s2.get(i));
                    expectedFeatures.get(NDArrayIndex.point(1), NDArrayIndex.all(), NDArrayIndex.point(i)).assign(v);
                }

                LabeledSentenceProvider p = new CollectionLabeledSentenceProvider(sentences, labelsForSentences, null);
                CnnSentenceDataSetIterator dsi = new CnnSentenceDataSetIterator.Builder(f)
                        .sentenceProvider(p).useNormalizedWordVectors(norm)
                        .wordVectors(w2v).maxSentenceLength(256).minibatchSize(32)
                        .build();

                DataSet ds = dsi.next();
                assertArrayEquals(expectedFeatures.shape(), ds.getFeatures().shape());
                assertEquals(expectedFeatures, ds.getFeatures());
                assertEquals(expLabels, ds.getLabels());
                assertEquals(expectedFeatureMask, ds.getFeaturesMaskArray());
                assertNull(ds.getLabelsMaskArray());

                INDArray s1F = dsi.loadSingleSentence(sentences.get(0));
                INDArray s2F = dsi.loadSingleSentence(sentences.get(1));
                INDArray sub1 = ds.getFeatures().get(NDArrayIndex.interval(0, 0, true), NDArrayIndex.all(), NDArrayIndex.all());
                INDArray sub2 = ds.getFeatures().get(NDArrayIndex.interval(1, 1, true), NDArrayIndex.all(), NDArrayIndex.interval(0, 3));

                assertArrayEquals(sub1.shape(), s1F.shape());
                assertArrayEquals(sub2.shape(), s2F.shape());
                assertEquals(sub1, s1F);
                assertEquals(sub2, s2F);
            }
        }
    }


    @Test
    public void testCnnSentenceDataSetIteratorNoTokensEdgeCase() throws Exception {

        WordVectors w2v = WordVectorSerializer
                        .readWord2VecModel(new ClassPathResource("word2vec/googleload/sample_vec.bin").getFile());

        int vectorSize = w2v.lookupTable().layerSize();

        List<String> sentences = new ArrayList<>();
        //First 2 sentences - no valid words
        sentences.add("NOVALID WORDSHERE");
        sentences.add("!!!");
        sentences.add("these balance Database model");
        sentences.add("into same THISWORDDOESNTEXIST are");
        int maxLength = 4;
        List<String> s1 = Arrays.asList("these", "balance", "Database", "model");
        List<String> s2 = Arrays.asList("into", "same", "are");

        List<String> labelsForSentences = Arrays.asList("Positive", "Negative", "Positive", "Negative");

        INDArray expLabels = Nd4j.create(new float[][] {{0, 1}, {1, 0}}); //Order of labels: alphabetic. Positive -> [0,1]


        LabeledSentenceProvider p = new CollectionLabeledSentenceProvider(sentences, labelsForSentences, null);
        CnnSentenceDataSetIterator dsi = new CnnSentenceDataSetIterator.Builder(CnnSentenceDataSetIterator.Format.CNN2D).sentenceProvider(p).wordVectors(w2v)
                        .useNormalizedWordVectors(true)
                        .maxSentenceLength(256).minibatchSize(32).sentencesAlongHeight(false).build();

        //            System.out.println("alongHeight = " + alongHeight);
        DataSet ds = dsi.next();

        INDArray expectedFeatures = Nd4j.create(DataType.FLOAT, 2, 1, vectorSize, maxLength);

        INDArray expectedFeatureMask = Nd4j.create(new float[][] {{1, 1, 1, 1}, {1, 1, 1, 0}}).reshape('c', 2, 1, 1, 4);

        for (int i = 0; i < 4; i++) {
            expectedFeatures.get(NDArrayIndex.point(0), NDArrayIndex.point(0), NDArrayIndex.all(),
                            NDArrayIndex.point(i)).assign(w2v.getWordVectorMatrixNormalized(s1.get(i)));
        }

        for (int i = 0; i < 3; i++) {
            expectedFeatures.get(NDArrayIndex.point(1), NDArrayIndex.point(0), NDArrayIndex.all(),
                            NDArrayIndex.point(i)).assign(w2v.getWordVectorMatrixNormalized(s2.get(i)));
        }

        assertArrayEquals(expectedFeatures.shape(), ds.getFeatures().shape());
        assertEquals(expectedFeatures, ds.getFeatures());
        assertEquals(expLabels, ds.getLabels());
        assertEquals(expectedFeatureMask, ds.getFeaturesMaskArray());
        assertNull(ds.getLabelsMaskArray());


        //Sanity check on single sentence loading:
        INDArray allKnownWords = dsi.loadSingleSentence("these balance");
        INDArray withUnknown = dsi.loadSingleSentence("these NOVALID");
        assertNotNull(allKnownWords);
        assertNotNull(withUnknown);

        try {
            dsi.loadSingleSentence("NOVALID AlsoNotInVocab");
            fail("Expected exception");
        } catch (Throwable t){
            String m = t.getMessage();
            assertTrue(m.contains("RemoveWord") && m.contains("vocabulary"), m);
        }
    }

    @Test
    public void testCnnSentenceDataSetIteratorNoValidTokensNextEdgeCase() throws Exception {
        //Case: 2 minibatches, of size 2
        //First minibatch: OK
        //Second minibatch: would be empty
        //Therefore: after first minibatch is returned, .hasNext() should return false

        WordVectors w2v = WordVectorSerializer
                        .readWord2VecModel(new ClassPathResource("word2vec/googleload/sample_vec.bin").getFile());

        int vectorSize = w2v.lookupTable().layerSize();

        List<String> sentences = new ArrayList<>();
        sentences.add("these balance Database model");
        sentences.add("into same THISWORDDOESNTEXIST are");
        //Last 2 sentences - no valid words
        sentences.add("NOVALID WORDSHERE");
        sentences.add("!!!");
        int maxLength = 4;
        List<String> s1 = Arrays.asList("these", "balance", "Database", "model");
        List<String> s2 = Arrays.asList("into", "same", "are");

        List<String> labelsForSentences = Arrays.asList("Positive", "Negative", "Positive", "Negative");

        INDArray expLabels = Nd4j.create(new float[][] {{0, 1}, {1, 0}}); //Order of labels: alphabetic. Positive -> [0,1]


        LabeledSentenceProvider p = new CollectionLabeledSentenceProvider(sentences, labelsForSentences, null);
        CnnSentenceDataSetIterator dsi = new CnnSentenceDataSetIterator.Builder(CnnSentenceDataSetIterator.Format.CNN2D).sentenceProvider(p).wordVectors(w2v)
                        .useNormalizedWordVectors(true)
                        .maxSentenceLength(256).minibatchSize(2).sentencesAlongHeight(false).build();

        assertTrue(dsi.hasNext());
        DataSet ds = dsi.next();

        assertFalse(dsi.hasNext());


        INDArray expectedFeatures = Nd4j.create(2, 1, vectorSize, maxLength);

        INDArray expectedFeatureMask = Nd4j.create(new float[][] {{1, 1, 1, 1}, {1, 1, 1, 0}}).reshape('c', 2, 1, 1, 4);

        for (int i = 0; i < 4; i++) {
            expectedFeatures.get(NDArrayIndex.point(0), NDArrayIndex.point(0), NDArrayIndex.all(),
                            NDArrayIndex.point(i)).assign(w2v.getWordVectorMatrixNormalized(s1.get(i)));
        }

        for (int i = 0; i < 3; i++) {
            expectedFeatures.get(NDArrayIndex.point(1), NDArrayIndex.point(0), NDArrayIndex.all(),
                            NDArrayIndex.point(i)).assign(w2v.getWordVectorMatrixNormalized(s2.get(i)));
        }

        assertArrayEquals(expectedFeatures.shape(), ds.getFeatures().shape());
        assertEquals(expectedFeatures, ds.getFeatures());
        assertEquals(expLabels, ds.getLabels());
        assertEquals(expectedFeatureMask, ds.getFeaturesMaskArray());
        assertNull(ds.getLabelsMaskArray());
    }


    @Test
    public void testCnnSentenceDataSetIteratorUseUnknownVector() throws Exception {

        WordVectors w2v = WordVectorSerializer
                .readWord2VecModel(new ClassPathResource("word2vec/googleload/sample_vec.bin").getFile());

        List<String> sentences = new ArrayList<>();
        sentences.add("these balance Database model");
        sentences.add("into same THISWORDDOESNTEXIST are");
        //Last 2 sentences - no valid words
        sentences.add("NOVALID WORDSHERE");
        sentences.add("!!!");

        List<String> labelsForSentences = Arrays.asList("Positive", "Negative", "Positive", "Negative");


        LabeledSentenceProvider p = new CollectionLabeledSentenceProvider(sentences, labelsForSentences, null);
        CnnSentenceDataSetIterator dsi = new CnnSentenceDataSetIterator.Builder(CnnSentenceDataSetIterator.Format.CNN1D)
                .unknownWordHandling(CnnSentenceDataSetIterator.UnknownWordHandling.UseUnknownVector)
                .sentenceProvider(p).wordVectors(w2v)
                .useNormalizedWordVectors(true)
                .maxSentenceLength(256).minibatchSize(4).sentencesAlongHeight(false).build();

        assertTrue(dsi.hasNext());
        DataSet ds = dsi.next();

        assertFalse(dsi.hasNext());

        INDArray f = ds.getFeatures();
        assertEquals(4, f.size(0));

        INDArray unknown = w2v.getWordVectorMatrix(w2v.getUNK());
        if(unknown == null)
            unknown = Nd4j.create(DataType.FLOAT, f.size(1));

        assertEquals(unknown, f.get(NDArrayIndex.point(2), NDArrayIndex.all(), NDArrayIndex.point(0)));
        assertEquals(unknown, f.get(NDArrayIndex.point(2), NDArrayIndex.all(), NDArrayIndex.point(1)));
        assertEquals(unknown.like(), f.get(NDArrayIndex.point(2), NDArrayIndex.all(), NDArrayIndex.point(3)));

        assertEquals(unknown, f.get(NDArrayIndex.point(3), NDArrayIndex.all(), NDArrayIndex.point(0)));
        assertEquals(unknown.like(), f.get(NDArrayIndex.point(2), NDArrayIndex.all(), NDArrayIndex.point(1)));

        //Sanity check on single sentence loading:
        INDArray allKnownWords = dsi.loadSingleSentence("these balance");
        INDArray withUnknown = dsi.loadSingleSentence("these NOVALID");
        INDArray allUnknown = dsi.loadSingleSentence("NOVALID AlsoNotInVocab");
        assertNotNull(allKnownWords);
        assertNotNull(withUnknown);
        assertNotNull(allUnknown);
    }
}
