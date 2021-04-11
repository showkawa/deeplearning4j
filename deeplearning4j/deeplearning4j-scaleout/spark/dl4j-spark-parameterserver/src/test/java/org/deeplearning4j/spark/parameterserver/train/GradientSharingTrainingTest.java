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

package org.deeplearning4j.spark.parameterserver.train;

import lombok.extern.slf4j.Slf4j;
import org.apache.spark.api.java.JavaRDD;
import org.deeplearning4j.datasets.iterator.EarlyTerminationDataSetIterator;
import org.deeplearning4j.datasets.iterator.impl.IrisDataSetIterator;
import org.deeplearning4j.datasets.iterator.impl.MnistDataSetIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.BaseTrainingListener;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.deeplearning4j.optimize.solvers.accumulation.encoding.ThresholdAlgorithm;
import org.deeplearning4j.optimize.solvers.accumulation.encoding.threshold.AdaptiveThresholdAlgorithm;
import org.deeplearning4j.optimize.solvers.accumulation.encoding.threshold.FixedThresholdAlgorithm;
import org.deeplearning4j.spark.api.RDDTrainingApproach;
import org.deeplearning4j.spark.api.TrainingMaster;
import org.deeplearning4j.spark.impl.graph.SparkComputationGraph;
import org.deeplearning4j.spark.impl.multilayer.SparkDl4jMultiLayer;
import org.deeplearning4j.spark.parameterserver.training.SharedTrainingMaster;
import org.junit.jupiter.api.Disabled;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.learning.config.AMSGrad;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.parameterserver.distributed.conf.VoidConfiguration;
import org.nd4j.parameterserver.distributed.v2.enums.MeshBuildMode;

import java.io.File;
import java.io.Serializable;
import java.net.Inet4Address;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import org.deeplearning4j.spark.parameterserver.BaseSparkTest;

@Slf4j
//@Disabled("AB 2019/05/21 - Failing - Issue #7657")
@Tag(TagNames.FILE_IO)
@Tag(TagNames.SPARK)
@Tag(TagNames.DIST_SYSTEMS)
@NativeTag
@Tag(TagNames.LONG_TEST)
@Tag(TagNames.LARGE_RESOURCES)
public class GradientSharingTrainingTest extends BaseSparkTest {



    @Override
    public long getTimeoutMilliseconds() {
        return 180000L;
    }

    @Test
    @Disabled
    public void trainSanityCheck(@TempDir Path testDir) throws Exception {

        for(boolean mds : new boolean[]{false, true}) {
            INDArray last = null;

            INDArray lastDup = null;
            for (String s : new String[]{"paths", "direSparkSequenceVectorsTestct", "export"}) {
                System.out.println("--------------------------------------------------------------------------------------------------------------");
                log.info("Starting: {} - {}", s, (mds ? "MultiDataSet" : "DataSet"));
                boolean isPaths = "paths".equals(s);

                RDDTrainingApproach rddTrainingApproach;
                switch (s) {
                    case "direct":
                        rddTrainingApproach = RDDTrainingApproach.Direct;
                        break;
                    case "export":
                        rddTrainingApproach = RDDTrainingApproach.Export;
                        break;
                    case "paths":
                        rddTrainingApproach = RDDTrainingApproach.Direct;   //Actualy not used for fitPaths
                        break;
                    default:
                        throw new RuntimeException();
                }

                File temp = testDir.toFile();


                //TODO this probably won't work everywhere...
                String controller = Inet4Address.getLocalHost().getHostAddress();
                String networkMask = controller.substring(0, controller.lastIndexOf('.')) + ".0" + "/16";

                VoidConfiguration voidConfiguration = VoidConfiguration.builder()
                        .unicastPort(40123) // Should be open for IN/OUT communications on all Spark nodes
                        .networkMask(networkMask) // Local network mask
                        .controllerAddress(controller)
                        .meshBuildMode(MeshBuildMode.PLAIN) // everyone is connected to the master
                        .build();
                TrainingMaster tm = new SharedTrainingMaster.Builder(voidConfiguration, 2, new AdaptiveThresholdAlgorithm(1e-3), 16)
                        .rngSeed(12345)
                        .collectTrainingStats(false)
                        .batchSizePerWorker(16) // Minibatch size for each worker
                        .workersPerNode(2) // Workers per node
                        .rddTrainingApproach(rddTrainingApproach)
                        .exportDirectory("file:///" + temp.getAbsolutePath().replaceAll("\\\\", "/"))
                        .build();


                ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                        .seed(12345)
                        .updater(new AMSGrad(0.1))
                        .graphBuilder()
                        .addInputs("in")
                        .layer("out", new OutputLayer.Builder().nIn(784).nOut(10).activation(Activation.SOFTMAX)
                                .lossFunction(LossFunctions.LossFunction.MCXENT).build(), "in")
                        .setOutputs("out")
                        .build();


                SparkComputationGraph sparkNet = new SparkComputationGraph(sc, conf, tm);
                sparkNet.setCollectTrainingStats(tm.getIsCollectTrainingStats());

//                System.out.println(Arrays.toString(sparkNet.getNetwork().params().get(NDArrayIndex.point(0), NDArrayIndex.interval(0, 256)).dup().data().asFloat()));
                File f = new File(testDir.toFile(),"test-dir-1");
                f.mkdirs();
                DataSetIterator iter = new MnistDataSetIterator(16, true, 12345);
                int count = 0;
                List<String> paths = new ArrayList<>();
                List<DataSet> ds = new ArrayList<>();
                while (iter.hasNext() && count++ < 8) {
                    DataSet d = iter.next();
                    if (isPaths) {
                        File out = new File(f, count + ".bin");
                        if(mds){
                            d.toMultiDataSet().save(out);
                        } else {
                            d.save(out);
                        }
                        String path = "file:///" + out.getAbsolutePath().replaceAll("\\\\", "/");
                        paths.add(path);
                    }
                    ds.add(d);
                }

                int numIter = 1;
                double[] acc = new double[numIter + 1];
                for (int i = 0; i < numIter; i++) {
                    //Check accuracy before:
                    DataSetIterator testIter = new EarlyTerminationDataSetIterator(new MnistDataSetIterator(32, false, 12345), 10);
                    Evaluation eBefore = sparkNet.getNetwork().evaluate(testIter);

                    INDArray paramsBefore = sparkNet.getNetwork().params().dup();
                    ComputationGraph after;
                    if(mds) {
                        //Fitting from MultiDataSet
                        List<MultiDataSet> mdsList = new ArrayList<>();
                        for(DataSet d : ds){
                            mdsList.add(d.toMultiDataSet());
                        }
                        switch (s) {
                            case "direct":
                            case "export":
                                JavaRDD<MultiDataSet> dsRDD = sc.parallelize(mdsList);
                                after = sparkNet.fitMultiDataSet(dsRDD);
                                break;
                            case "paths":
                                JavaRDD<String> pathRdd = sc.parallelize(paths);
                                after = sparkNet.fitPathsMultiDataSet(pathRdd);
                                break;
                            default:
                                throw new RuntimeException();
                        }
                    } else {
                        //Fitting from DataSet
                        switch (s) {
                            case "direct":
                            case "export":
                                JavaRDD<DataSet> dsRDD = sc.parallelize(ds);
                                after = sparkNet.fit(dsRDD);
                                break;
                            case "paths":
                                JavaRDD<String> pathRdd = sc.parallelize(paths);
                                after = sparkNet.fitPaths(pathRdd);
                                break;
                            default:
                                throw new RuntimeException();
                        }
                    }

                    INDArray paramsAfter = after.params();
//                    System.out.println(Arrays.toString(paramsBefore.get(NDArrayIndex.point(0), NDArrayIndex.interval(0, 256)).dup().data().asFloat()));
//                    System.out.println(Arrays.toString(paramsAfter.get(NDArrayIndex.point(0), NDArrayIndex.interval(0, 256)).dup().data().asFloat()));
//                    System.out.println(Arrays.toString(
//                            Transforms.abs(paramsAfter.sub(paramsBefore)).get(NDArrayIndex.point(0), NDArrayIndex.interval(0, 256)).dup().data().asFloat()));
                    assertNotEquals(paramsBefore, paramsAfter);


                    testIter = new EarlyTerminationDataSetIterator(new MnistDataSetIterator(32, false, 12345), 10);
                    Evaluation eAfter = after.evaluate(testIter);

                    double accAfter = eAfter.accuracy();
                    double accBefore = eBefore.accuracy();
                    assertTrue(accAfter >= accBefore + 0.005, "after: " + accAfter + ", before=" + accBefore);

                    if (i == 0) {
                        acc[0] = eBefore.accuracy();
                    }
                    acc[i + 1] = eAfter.accuracy();
                }
                log.info("Accuracies: {}", Arrays.toString(acc));
                last = sparkNet.getNetwork().params();
                lastDup = last.dup();
            }
        }
    }


    @Test @Disabled //AB https://github.com/eclipse/deeplearning4j/issues/8985
    public void differentNetsTrainingTest(@TempDir Path testDir) throws Exception {
        int batch = 3;

        File temp = testDir.toFile();
        DataSet ds = new IrisDataSetIterator(150, 150).next();
        List<DataSet> list = ds.asList();
        Collections.shuffle(list, new Random(12345));
        int pos = 0;
        int dsCount = 0;
        while (pos < list.size()) {
            List<DataSet> l2 = new ArrayList<>();
            for (int i = 0; i < 3 && pos < list.size(); i++) {
                l2.add(list.get(pos++));
            }
            DataSet d = DataSet.merge(l2);
            File f = new File(temp, dsCount++ + ".bin");
            d.save(f);
        }

        INDArray last = null;
        INDArray lastDup = null;
        for (int i = 0; i < 2; i++) {
            System.out.println("||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
            log.info("Starting: {}", i);

            MultiLayerConfiguration conf;
            if (i == 0) {
                conf = new NeuralNetConfiguration.Builder()
                        .weightInit(WeightInit.XAVIER)
                        .seed(12345)
                        .list()
                        .layer(new OutputLayer.Builder().nIn(4).nOut(3).activation(Activation.SOFTMAX).lossFunction(LossFunctions.LossFunction.MCXENT).build())
                        .build();
            } else {
                conf = new NeuralNetConfiguration.Builder()
                        .weightInit(WeightInit.XAVIER)
                        .seed(12345)
                        .list()
                        .layer(new DenseLayer.Builder().nIn(4).nOut(4).activation(Activation.TANH).build())
                        .layer(new OutputLayer.Builder().nIn(4).nOut(3).activation(Activation.SOFTMAX).lossFunction(LossFunctions.LossFunction.MCXENT).build())
                        .build();
            }
            MultiLayerNetwork net = new MultiLayerNetwork(conf);
            net.init();


            //TODO this probably won't work everywhere...
            String controller = Inet4Address.getLocalHost().getHostAddress();
            String networkMask = controller.substring(0, controller.lastIndexOf('.')) + ".0" + "/16";

            VoidConfiguration voidConfiguration = VoidConfiguration.builder()
                    .unicastPort(40123) // Should be open for IN/OUT communications on all Spark nodes
                    .networkMask(networkMask) // Local network mask
                    .controllerAddress(controller)
                    .build();
            TrainingMaster tm = new SharedTrainingMaster.Builder(voidConfiguration, 2, new FixedThresholdAlgorithm(1e-4), batch)
                    .rngSeed(12345)
                    .collectTrainingStats(false)
                    .batchSizePerWorker(batch) // Minibatch size for each worker
                    .workersPerNode(2) // Workers per node
                    .build();


            SparkDl4jMultiLayer sparkNet = new SparkDl4jMultiLayer(sc, net, tm);

            //System.out.println(Arrays.toString(sparkNet.getNetwork().params().get(NDArrayIndex.point(0), NDArrayIndex.interval(0, 256)).dup().data().asFloat()));

            String fitPath = "file:///" + temp.getAbsolutePath().replaceAll("\\\\", "/");
            INDArray paramsBefore = net.params().dup();
            for( int j=0; j<3; j++ ) {
                sparkNet.fit(fitPath);
            }

            INDArray paramsAfter = net.params();
            assertNotEquals(paramsBefore, paramsAfter);

            //Also check we don't have any issues
            if(i == 0) {
                last = sparkNet.getNetwork().params();
                lastDup = last.dup();
            } else {
                assertEquals(lastDup, last);
            }
        }
    }


    @Test
    public void testEpochUpdating(@TempDir Path testDir) throws Exception {
        //Ensure that epoch counter is incremented properly on the workers

        File temp = testDir.resolve("new-dir-" + UUID.randomUUID().toString()).toFile();
        temp.mkdirs();

        //TODO this probably won't work everywhere...
        String controller = Inet4Address.getLocalHost().getHostAddress();
        String networkMask = controller.substring(0, controller.lastIndexOf('.')) + ".0" + "/16";

        VoidConfiguration voidConfiguration = VoidConfiguration.builder()
                .unicastPort(40123) // Should be open for IN/OUT communications on all Spark nodes
                .networkMask(networkMask) // Local network mask
                .controllerAddress(controller)
                .meshBuildMode(MeshBuildMode.PLAIN) // everyone is connected to the master
                .build();
        SharedTrainingMaster tm = new SharedTrainingMaster.Builder(voidConfiguration, 2, new AdaptiveThresholdAlgorithm(1e-3), 16)
                .rngSeed(12345)
                .collectTrainingStats(false)
                .batchSizePerWorker(16) // Minibatch size for each worker
                .workersPerNode(2) // Workers per node
                .exportDirectory("file:///" + temp.getAbsolutePath().replaceAll("\\\\", "/"))
                .build();


        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(12345)
                .updater(new AMSGrad(0.001))
                .graphBuilder()
                .addInputs("in")
                .layer("out", new OutputLayer.Builder().nIn(784).nOut(10).activation(Activation.SOFTMAX)
                        .lossFunction(LossFunctions.LossFunction.MCXENT).build(), "in")
                .setOutputs("out")
                .build();


        SparkComputationGraph sparkNet = new SparkComputationGraph(sc, conf, tm);
        sparkNet.setListeners(new TestListener());

        DataSetIterator iter = new MnistDataSetIterator(16, true, 12345);
        int count = 0;
        List<String> paths = new ArrayList<>();
        List<DataSet> ds = new ArrayList<>();
        File f = new File(testDir.toFile(),"test-dir-1");
        f.mkdirs();
        while (iter.hasNext() && count++ < 8) {
            DataSet d = iter.next();
            File out = new File(f, count + ".bin");
            d.save(out);
            String path = "file:///" + out.getAbsolutePath().replaceAll("\\\\", "/");
            paths.add(path);
            ds.add(d);
        }

        JavaRDD<String> pathRdd = sc.parallelize(paths);
        for( int i = 0; i < 3; i++) {
            ThresholdAlgorithm ta = tm.getThresholdAlgorithm();
            sparkNet.fitPaths(pathRdd);
            //Check also that threshold algorithm was updated/averaged
            ThresholdAlgorithm taAfter = tm.getThresholdAlgorithm();
            assertTrue(ta != taAfter, "Threshold algorithm should have been updated with different instance after averaging");
            AdaptiveThresholdAlgorithm ataAfter = (AdaptiveThresholdAlgorithm) taAfter;
            assertFalse(Double.isNaN(ataAfter.getLastSparsity()));
            assertFalse(Double.isNaN(ataAfter.getLastThreshold()));
        }

        Set<Integer> expectedEpochs = new HashSet<>(Arrays.asList(0, 1, 2));
        assertEquals(expectedEpochs, TestListener.epochs);
    }

    private static class TestListener extends BaseTrainingListener implements Serializable {
        private static final Set<Integer> iterations = Collections.newSetFromMap(new ConcurrentHashMap<>());
        private static final Set<Integer> epochs = Collections.newSetFromMap(new ConcurrentHashMap<>());
        @Override
        public void iterationDone(Model model, int iteration, int epoch) {
            iterations.add(iteration);
            epochs.add(epoch);
        }
    }
}
