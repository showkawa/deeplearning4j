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

package org.nd4j.autodiff.opvalidation;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.nd4j.OpValidationSuite;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.autodiff.validation.OpTestCase;
import org.nd4j.autodiff.validation.OpValidation;
import org.nd4j.autodiff.validation.TestCase;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.iter.NdIndexIterator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.api.ops.impl.reduce.bool.All;
import org.nd4j.linalg.api.ops.random.custom.RandomBernoulli;
import org.nd4j.linalg.api.ops.random.custom.RandomExponential;
import org.nd4j.linalg.api.ops.random.impl.BinomialDistribution;
import org.nd4j.linalg.api.shape.LongShapeDescriptor;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.common.function.Function;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@NativeTag
@Tag(TagNames.RNG)
public class RandomOpValidation extends BaseOpValidation {


    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testRandomOpsSDVarShape(Nd4jBackend backend) {
        Nd4j.getRandom().setSeed(12345);
        List<String> failed = new ArrayList<>();

        for (long[] shape : Arrays.asList(new long[]{1000}, new long[]{100, 10}, new long[]{40, 5, 5})) {

            for (int i = 0; i < 4; i++) {
                INDArray arr = Nd4j.createFromArray(shape).castTo(DataType.INT);

                Nd4j.getRandom().setSeed(12345);
                SameDiff sd = SameDiff.create();
                SDVariable shapeVar = sd.constant("shape", arr);
                SDVariable otherVar = sd.var("misc", Nd4j.rand(shape));

                SDVariable rand;
                Function<INDArray, String> checkFn;
                String name;
                switch (i) {
                    case 0:
                        name = "randomUniform";
                        rand = sd.random().uniform(1, 2, DataType.DOUBLE, shape);
                        checkFn = in -> {
                            double min = in.minNumber().doubleValue();
                            double max = in.maxNumber().doubleValue();
                            double mean = in.meanNumber().doubleValue();
                            if (min >= 1 && max <= 2 && (in.length() == 1 || Math.abs(mean - 1.5) < 0.2))
                                return null;
                            return "Failed: min = " + min + ", max = " + max + ", mean = " + mean;
                        };
                        break;
                    case 1:
                        name = "randomNormal";
                        rand = sd.random().normal(1, 1, DataType.DOUBLE, shape);
                        checkFn = in -> {
                            double mean = in.meanNumber().doubleValue();
                            double stdev = in.std(true).getDouble(0);
                            if (in.length() == 1 || (Math.abs(mean - 1) < 0.2 && Math.abs(stdev - 1) < 0.2))
                                return null;
                            return "Failed: mean = " + mean + ", stdev = " + stdev;
                        };
                        break;
                    case 2:
                        name = "randomBernoulli";
                        rand = sd.random().bernoulli(0.5, DataType.DOUBLE, shape);
                        checkFn = in -> {
                            double mean = in.meanNumber().doubleValue();
                            double min = in.minNumber().doubleValue();
                            double max = in.maxNumber().doubleValue();
                            int sum0 = Transforms.not(in.castTo(DataType.BOOL)).castTo(DataType.DOUBLE).sumNumber().intValue();
                            int sum1 = in.sumNumber().intValue();
                            if ((in.length() == 1 && min == max && (min == 0 || min == 1)) ||
                                    (Math.abs(mean - 0.5) < 0.1 && min == 0 && max == 1 && (sum0 + sum1) == in.length()))
                                return null;
                            return "Failed: bernoulli - sum0 = " + sum0 + ", sum1 = " + sum1;
                        };
                        break;
                    case 3:
                        name = "randomExponential";
                        final double lambda = 2;
                        rand = sd.random().exponential(lambda, DataType.DOUBLE, shape);
                        checkFn = in -> {
                            double mean = in.meanNumber().doubleValue();
                            double min = in.minNumber().doubleValue();
                            double std = in.stdNumber().doubleValue();
                            //mean: 1/lambda; std: 1/lambda
                            if ((in.length() == 1 && min > 0) || (Math.abs(mean - 1 / lambda) < 0.1 && min >= 0 && Math.abs(std - 1 / lambda) < 0.1))
                                return null;
                            return "Failed: exponential: mean=" + mean + ", std = " + std + ", min=" + min;
                        };
                        break;
                    default:
                        throw new RuntimeException();
                }

                SDVariable loss;
                if (shape.length > 0) {
                    loss = rand.std(true);
                } else {
                    loss = rand.mean();
                }

                String msg = name + " - " + Arrays.toString(shape);
                TestCase tc = new TestCase(sd)
                        .gradientCheck(false)
                        .testName(msg)
                        .expected(rand, checkFn)
                        .testFlatBufferSerialization(TestCase.TestSerialization.NONE);  //Can't compare values due to randomness

                log.info("TEST: " + msg);

                String err = OpValidation.validate(tc, true);
                if (err != null) {
                    failed.add(err);
                }
            }
        }

        assertEquals(0, failed.size(),failed.toString());
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testRandomOpsLongShape(Nd4jBackend backend) {
        List<String> failed = new ArrayList<>();

        for (long[] shape : Arrays.asList(new long[]{1000}, new long[]{100, 10}, new long[]{40, 5, 5})) {

            for (int i = 0; i < 6; i++) {

                Nd4j.getRandom().setSeed(12345);
                SameDiff sd = SameDiff.create();

                SDVariable rand;
                Function<INDArray, String> checkFn;
                String name;
                switch (i) {
                    case 0:
                        name = "randomBernoulli";
                        rand = sd.random().bernoulli(0.5, DataType.DOUBLE, shape);
                        checkFn = in -> {
                            double mean = in.meanNumber().doubleValue();
                            double min = in.minNumber().doubleValue();
                            double max = in.maxNumber().doubleValue();
                            int sum0 = Transforms.not(in.castTo(DataType.BOOL)).castTo(DataType.DOUBLE).sumNumber().intValue();
                            int sum1 = in.sumNumber().intValue();
                            if ((in.length() == 1 && min == max && (min == 0 || min == 1)) ||
                                    (Math.abs(mean - 0.5) < 0.1 && min == 0 && max == 1 && (sum0 + sum1) == in.length()))
                                return null;
                            return "Failed: bernoulli - sum0 = " + sum0 + ", sum1 = " + sum1;
                        };
                        break;
                    case 1:
                        name = "normal";
                        rand = sd.random().normal(1, 2, DataType.DOUBLE, shape);
                        checkFn = in -> {
                            double mean = in.meanNumber().doubleValue();
                            double stdev = in.std(true).getDouble(0);
                            if (in.length() == 1 || (Math.abs(mean - 1) < 0.2 && Math.abs(stdev - 2) < 0.1))
                                return null;
                            return "Failed: mean = " + mean + ", stdev = " + stdev;
                        };
                        break;
                    case 2:
                        name = "randomBinomial";
                        rand = sd.random().binomial(4, 0.5, DataType.DOUBLE, shape);
                        checkFn = in -> {
                            NdIndexIterator iter = new NdIndexIterator(in.shape());
                            while(iter.hasNext()){
                                long[] idx = iter.next();
                                double d = in.getDouble(idx);
                                if(d < 0 || d > 4 || d != Math.floor(d)){
                                    return "Falied - binomial: indexes " + Arrays.toString(idx) + ", value " + d;
                                }
                            }
                            return null;
                        };
                        break;
                    case 3:
                        name = "randomUniform";
                        rand = sd.random().uniform(1, 2, DataType.DOUBLE, shape);
                        checkFn = in -> {
                            double min = in.minNumber().doubleValue();
                            double max = in.maxNumber().doubleValue();
                            double mean = in.meanNumber().doubleValue();
                            if (min >= 1 && max <= 2 && (in.length() == 1 || Math.abs(mean - 1.5) < 0.1))
                                return null;
                            return "Failed: min = " + min + ", max = " + max + ", mean = " + mean;
                        };
                        break;
                    case 4:
                        if(OpValidationSuite.IGNORE_FAILING){
                            //https://github.com/eclipse/deeplearning4j/issues/6036
                            continue;
                        }
                        name = "truncatednormal";
                        rand = sd.random().normalTruncated(1, 2, DataType.DOUBLE, shape);
                        checkFn = in -> {
                            double mean = in.meanNumber().doubleValue();
                            double stdev = in.std(true).getDouble(0);
                            if (in.length() == 1 || (Math.abs(mean - 1) < 0.1 && Math.abs(stdev - 2) < 0.2))
                                return null;
                            return "Failed: mean = " + mean + ", stdev = " + stdev;
                        };
                        break;
                    case 5:
                        name = "lognormal";
                        rand = sd.random().logNormal(1, 2, DataType.DOUBLE, shape);
                        //Note: lognormal parameters are mean and stdev of LOGARITHM of values
                        checkFn = in -> {
                            INDArray log = Transforms.log(in, true);
                            double mean = log.meanNumber().doubleValue();
                            double stdev = log.std(true).getDouble(0);
                            if (in.length() == 1 || (Math.abs(mean - 1) < 0.2 && Math.abs(stdev - 2) < 0.1))
                                return null;
                            return "Failed: mean = " + mean + ", stdev = " + stdev;
                        };
                        break;
                    default:
                        throw new RuntimeException();
                }

                SDVariable loss;
                if (shape.length > 0) {
                    loss = rand.std(true);
                } else {
                    loss = rand.mean();
                }

                String msg = name + " - " + Arrays.toString(shape);
                TestCase tc = new TestCase(sd)
                        .gradientCheck(false)
                        .testName(msg)
                        .expected(rand, checkFn)
                        .testFlatBufferSerialization(TestCase.TestSerialization.NONE);  //Can't compare values due to randomness

                log.info("TEST: " + msg);

                String err = OpValidation.validate(tc, true);
                if (err != null) {
                    failed.add(err);
                }
            }
        }

        assertEquals(0, failed.size(),failed.toString());
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testRandomBinomial(){

        INDArray z = Nd4j.create(new long[]{10});
//        Nd4j.getExecutioner().exec(new BinomialDistribution(z, 4, 0.5));
        Nd4j.getExecutioner().exec(new BinomialDistribution(z, 4, 0.5));

        System.out.println(z);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testUniformRankSimple(Nd4jBackend backend) {

        INDArray arr = Nd4j.createFromArray(new double[]{100.0});
//        OpTestCase tc = new OpTestCase(DynamicCustomOp.builder("randomuniform")
//                .addInputs(arr)
//                .addOutputs(Nd4j.createUninitialized(new long[]{100}))
//                .addFloatingPointArguments(0.0, 1.0)
//                .build());

//        OpTestCase tc = new OpTestCase(new DistributionUniform(arr, Nd4j.createUninitialized(new long[]{100}), 0, 1));
        OpTestCase tc = new OpTestCase(new RandomBernoulli(arr, Nd4j.createUninitialized(new long[]{100}), 0.5));

        tc.expectedOutput(0, LongShapeDescriptor.fromShape(new long[]{100}, DataType.FLOAT), in -> {
            double min = in.minNumber().doubleValue();
            double max = in.maxNumber().doubleValue();
            double mean = in.meanNumber().doubleValue();
            if (min >= 0 && max <= 1 && (in.length() == 1 || Math.abs(mean - 0.5) < 0.2))
                return null;
            return "Failed: min = " + min + ", max = " + max + ", mean = " + mean;
        });

        String err = OpValidation.validate(tc);
        assertNull(err);

        double d = arr.getDouble(0);

        assertEquals(100.0, d, 0.0);

    }


    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testRandomExponential(Nd4jBackend backend) {
        long length = 1_000_000;
        INDArray shape = Nd4j.createFromArray(new double[]{length});
        INDArray out = Nd4j.createUninitialized(new long[]{length});
        double lambda = 2;
        RandomExponential op = new RandomExponential(shape, out, lambda);

        Nd4j.getExecutioner().exec(op);

        double min = out.minNumber().doubleValue();
        double mean = out.meanNumber().doubleValue();
        double std = out.stdNumber().doubleValue();

        double expMean = 1.0/lambda;
        double expStd = 1.0/lambda;

        assertTrue(min >= 0.0);
        assertEquals(expMean, mean, 0.1,"mean");
        assertEquals( expStd, std, 0.1,"std");
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testRange(){
        //Technically deterministic, not random...

        double[][] testCases = new double[][]{
                {3,18,3},
                {3,1,-0.5},
                {0,5,1}
        };

        List<INDArray> exp = Arrays.asList(
                Nd4j.create(new double[]{3, 6, 9, 12, 15}).castTo(DataType.FLOAT),
                Nd4j.create(new double[]{3, 2.5, 2, 1.5}).castTo(DataType.FLOAT),
                Nd4j.create(new double[]{0, 1, 2, 3, 4}).castTo(DataType.FLOAT));

        for(int i=0; i<testCases.length; i++ ){
            double[] d = testCases[i];
            INDArray e = exp.get(i);

            SameDiff sd = SameDiff.create();
            SDVariable range = sd.range(d[0], d[1], d[2], DataType.FLOAT);

            SDVariable loss = range.std(true);

            TestCase tc = new TestCase(sd)
                    .expected(range, e)
                    .testName(Arrays.toString(d))
                    .gradientCheck(false);

            assertNull(OpValidation.validate(tc));
        }
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testAllEmptyReduce(Nd4jBackend backend) {
        INDArray x = Nd4j.createFromArray(true, true, true);
        All all = new All(x);
        all.setEmptyReduce(true);   //For TF compatibility - empty array for axis (which means no-op - and NOT all array reduction)
        INDArray out = Nd4j.exec(all);
        assertEquals(x, out);
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testUniformDtype(Nd4jBackend backend) {
        Nd4j.getRandom().setSeed(12345);
        for(DataType t : new DataType[]{DataType.FLOAT, DataType.DOUBLE}) {
            SameDiff sd = SameDiff.create();
            SDVariable shape = sd.constant("shape", Nd4j.createFromArray(1, 100));
            SDVariable out = sd.random.uniform(0, 10, t, 1, 100);
            INDArray arr = out.eval();
            assertEquals(t, arr.dataType());
            if (t.equals(DataType.DOUBLE)) {
                double min = arr.minNumber().doubleValue();
                double max = arr.maxNumber().doubleValue();
                double mean = arr.meanNumber().doubleValue();
                assertEquals(0, min, 0.5);
                assertEquals(10, max, 0.5);
                assertEquals(5.5, mean, 1);
            }
            else if (t.equals(DataType.FLOAT)) {
                float min = arr.minNumber().floatValue();
                float max = arr.maxNumber().floatValue();
                float mean = arr.meanNumber().floatValue();
                assertEquals(0, min, 0.5);
                assertEquals(10, max, 0.5);
                assertEquals(5.0, mean, 1);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testRandomExponential2(){
        Nd4j.getRandom().setSeed(12345);
        DynamicCustomOp op = DynamicCustomOp.builder("random_exponential")
                .addInputs(Nd4j.createFromArray(100))
                .addOutputs(Nd4j.create(DataType.FLOAT, 100))
                .addFloatingPointArguments(0.5)
                .build();

        Nd4j.exec(op);

        INDArray out = op.getOutputArgument(0);
        int count0 = out.eq(0.0).castTo(DataType.INT32).sumNumber().intValue();
        int count1 = out.eq(1.0).castTo(DataType.INT32).sumNumber().intValue();

        assertEquals(0, count0);
        assertEquals(0, count1);

        double min = out.minNumber().doubleValue();
        double max = out.maxNumber().doubleValue();

        assertTrue(min > 0.0,String.valueOf(min));
        assertTrue( max > 1.0,String.valueOf(max));
    }
}
