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

package org.nd4j.evaluation.classification;

import lombok.*;
import org.apache.commons.lang3.ArrayUtils;
import org.nd4j.common.base.Preconditions;
import org.nd4j.evaluation.BaseEvaluation;
import org.nd4j.evaluation.IEvaluation;
import org.nd4j.evaluation.IMetric;
import org.nd4j.evaluation.curves.PrecisionRecallCurve;
import org.nd4j.evaluation.curves.RocCurve;
import org.nd4j.evaluation.serde.ROCSerializer;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.Op;
import org.nd4j.linalg.api.ops.impl.reduce.longer.MatchCondition;
import org.nd4j.linalg.api.ops.impl.transforms.comparison.CompareAndSet;
import org.nd4j.linalg.api.ops.impl.transforms.pairwise.arithmetic.MulOp;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.indexing.conditions.Condition;
import org.nd4j.linalg.indexing.conditions.Conditions;
import org.nd4j.common.primitives.Pair;
import org.nd4j.common.primitives.Triple;
import org.nd4j.shade.jackson.annotation.JsonIgnoreProperties;
import org.nd4j.shade.jackson.annotation.JsonTypeInfo;
import org.nd4j.shade.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.nd4j.linalg.indexing.NDArrayIndex.all;
import static org.nd4j.linalg.indexing.NDArrayIndex.interval;

@EqualsAndHashCode(callSuper = true,
        exclude = {"auc", "auprc", "probAndLabel", "exactAllocBlockSize", "rocCurve", "prCurve", "axis"})
@Data
@JsonIgnoreProperties({"probAndLabel", "exactAllocBlockSize"})
@JsonSerialize(using = ROCSerializer.class)
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY)
public class ROC extends BaseEvaluation<ROC> {

    /**
     * AUROC: Area under ROC curve<br>
     * AUPRC: Area under Precision-Recall Curve
     */
    public enum Metric implements IMetric {
        AUROC, AUPRC;

        @Override
        public Class<? extends IEvaluation> getEvaluationClass() {
            return ROC.class;
        }

        @Override
        public boolean minimize() {
            return false;
        }
    }

    private static final int DEFAULT_EXACT_ALLOC_BLOCK_SIZE = 2048;
    private final Map<Double, CountsForThreshold> counts = new LinkedHashMap<>();
    private int thresholdSteps;
    private long countActualPositive;
    private long countActualNegative;
    private Double auc;
    private Double auprc;
    private RocCurve rocCurve;
    private PrecisionRecallCurve prCurve;

    private boolean isExact;
    private INDArray probAndLabel;
    private int exampleCount = 0;
    private boolean rocRemoveRedundantPts;
    private int exactAllocBlockSize;
    protected int axis = 1;



    public ROC(int thresholdSteps, boolean rocRemoveRedundantPts, int exactAllocBlockSize, int axis) {
        this(thresholdSteps, rocRemoveRedundantPts, exactAllocBlockSize);
        this.axis = axis;
    }

    public ROC() {
        //Default to exact
        this(0);
    }

    /**
     * @param thresholdSteps Number of threshold steps to use for the ROC calculation. If set to 0: use exact calculation
     */
    public ROC(int thresholdSteps) {
        this(thresholdSteps, true);
    }

    /**
     * @param thresholdSteps        Number of threshold steps to use for the ROC calculation. If set to 0: use exact calculation
     * @param rocRemoveRedundantPts Usually set to true. If true,  remove any redundant points from ROC and P-R curves
     */
    public ROC(int thresholdSteps, boolean rocRemoveRedundantPts) {
        this(thresholdSteps, rocRemoveRedundantPts, DEFAULT_EXACT_ALLOC_BLOCK_SIZE);
    }

    /**
     * @param thresholdSteps        Number of threshold steps to use for the ROC calculation. If set to 0: use exact calculation
     * @param rocRemoveRedundantPts Usually set to true. If true,  remove any redundant points from ROC and P-R curves
     * @param exactAllocBlockSize   if using exact mode, the block size relocation. Users can likely use the default
     *                              setting in almost all cases
     */
    public ROC(int thresholdSteps, boolean rocRemoveRedundantPts, int exactAllocBlockSize) {


        if (thresholdSteps > 0) {
            this.thresholdSteps = thresholdSteps;

            double step = 1.0 / thresholdSteps;
            for (int i = 0; i <= thresholdSteps; i++) {
                double currThreshold = i * step;
                counts.put(currThreshold, new CountsForThreshold(currThreshold));
            }

            isExact = false;
        } else {
            //Exact

            isExact = true;
        }
        this.rocRemoveRedundantPts = rocRemoveRedundantPts;
        this.exactAllocBlockSize = exactAllocBlockSize;
    }

    public static ROC fromJson(String json) {
        return fromJson(json, ROC.class);
    }

    /**
     * Set the axis for evaluation - this should be a size 1 dimension
     * For DL4J, this can be left as the default setting (axis = 1).<br>
     * Axis should be set as follows:<br>
     * For 2D (OutputLayer), shape [minibatch, numClasses] - axis = 1<br>
     * For 3D, RNNs/CNN1D (DL4J RnnOutputLayer), NCW format, shape [minibatch, numClasses, sequenceLength] - axis = 1<br>
     * For 3D, RNNs/CNN1D (DL4J RnnOutputLayer), NWC format, shape [minibatch, sequenceLength, numClasses] - axis = 2<br>
     * For 4D, CNN2D (DL4J CnnLossLayer), NCHW format, shape [minibatch, channels, height, width] - axis = 1<br>
     * For 4D, CNN2D, NHWC format, shape [minibatch, height, width, channels] - axis = 3<br>
     *
     * @param axis Axis to use for evaluation
     */
    public void setAxis(int axis){
        this.axis = axis;
    }

    /**
     * Get the axis - see {@link #setAxis(int)} for details
     */
    public int getAxis(){
        return axis;
    }



    private double getAuc() {
        if (auc != null) {
            return auc;
        }
        auc = calculateAUC();
        return auc;
    }

    /**
     * Calculate the AUROC - Area Under ROC Curve<br>
     * Utilizes trapezoidal integration internally
     *
     * @return AUC
     */
    public double calculateAUC() {
        if (auc != null) {
            return auc;
        }

        Preconditions.checkState(exampleCount > 0, "Unable to calculate AUC: no evaluation has been performed (no examples)");

        this.auc = getRocCurve().calculateAUC();
        return auc;
    }

    /**
     * Get the ROC curve, as a set of (threshold, falsePositive, truePositive) points
     *
     * @return ROC curve
     */
    public RocCurve getRocCurve() {
        if (rocCurve != null) {
            return rocCurve;
        }

        Preconditions.checkState(exampleCount > 0, "Unable to get ROC curve: no evaluation has been performed (no examples)");

        if (isExact) {
            //Sort ascending. As we decrease threshold, more are predicted positive.
            //if(prob <= threshold> predict 0, otherwise predict 1
            //So, as we iterate from i=0..length, first 0 to i (inclusive) are predicted class 1, all others are predicted class 0
            INDArray pl = getProbAndLabelUsed();
            INDArray sorted = Nd4j.sortRows(pl, 0, false);
            INDArray isPositive = sorted.getColumn(1,true);
            INDArray isNegative = sorted.getColumn(1,true).rsub(1.0);

            INDArray cumSumPos = isPositive.cumsum(-1);
            INDArray cumSumNeg = isNegative.cumsum(-1);
            val length = sorted.size(0);

            INDArray t = Nd4j.create(DataType.DOUBLE, length + 2, 1);
            t.put(new INDArrayIndex[]{interval(1, length + 1), all()}, sorted.getColumn(0,true));

            INDArray fpr = Nd4j.create(DataType.DOUBLE, length + 2, 1);
            fpr.put(new INDArrayIndex[]{interval(1, length + 1), all()},
                    cumSumNeg.div(countActualNegative));

            INDArray tpr = Nd4j.create(DataType.DOUBLE, length + 2, 1);
            tpr.put(new INDArrayIndex[]{interval(1, length + 1), all()},
                    cumSumPos.div(countActualPositive));

            //Edge cases
            t.putScalar(0, 0, 1.0);
            fpr.putScalar(0, 0, 0.0);
            tpr.putScalar(0, 0, 0.0);
            fpr.putScalar(length + 1, 0, 1.0);
            tpr.putScalar(length + 1, 0, 1.0);


            double[] x_fpr_out = fpr.data().asDouble();
            double[] y_tpr_out = tpr.data().asDouble();
            double[] tOut = t.data().asDouble();

            //Note: we can have multiple FPR for a given TPR, and multiple TPR for a given FPR
            //These can be omitted, without changing the area (as long as we keep the edge points)
            if (rocRemoveRedundantPts) {
                Pair<double[][], int[][]> p = removeRedundant(tOut, x_fpr_out, y_tpr_out, null, null, null);
                double[][] temp = p.getFirst();
                tOut = temp[0];
                x_fpr_out = temp[1];
                y_tpr_out = temp[2];
            }

            this.rocCurve = new RocCurve(tOut, x_fpr_out, y_tpr_out);

            return rocCurve;
        } else {

            double[][] out = new double[3][thresholdSteps + 1];
            int i = 0;
            for (Map.Entry<Double, CountsForThreshold> entry : counts.entrySet()) {
                CountsForThreshold c = entry.getValue();
                double tpr = c.getCountTruePositive() / ((double) countActualPositive);
                double fpr = c.getCountFalsePositive() / ((double) countActualNegative);

                out[0][i] = c.getThreshold();
                out[1][i] = fpr;
                out[2][i] = tpr;
                i++;
            }
            return new RocCurve(out[0], out[1], out[2]);
        }
    }

    protected INDArray getProbAndLabelUsed() {
        if (probAndLabel == null || exampleCount == 0) {
            return null;
        }
        return probAndLabel.get(interval(0, exampleCount), all());
    }

    private static Pair<double[][], int[][]> removeRedundant(double[] threshold, double[] x, double[] y, int[] tpCount,
                                                             int[] fpCount, int[] fnCount) {
        double[] t_compacted = new double[threshold.length];
        double[] x_compacted = new double[x.length];
        double[] y_compacted = new double[y.length];
        int[] tp_compacted = null;
        int[] fp_compacted = null;
        int[] fn_compacted = null;
        boolean hasInts = false;
        if (tpCount != null) {
            tp_compacted = new int[tpCount.length];
            fp_compacted = new int[fpCount.length];
            fn_compacted = new int[fnCount.length];
            hasInts = true;
        }
        int lastOutPos = -1;
        for (int i = 0; i < threshold.length; i++) {

            boolean keep;
            if (i == 0 || i == threshold.length - 1) {
                keep = true;
            } else {
                boolean ommitSameY = y[i - 1] == y[i] && y[i] == y[i + 1];
                boolean ommitSameX = x[i - 1] == x[i] && x[i] == x[i + 1];
                keep = !ommitSameX && !ommitSameY;
            }

            if (keep) {
                lastOutPos++;
                t_compacted[lastOutPos] = threshold[i];
                y_compacted[lastOutPos] = y[i];
                x_compacted[lastOutPos] = x[i];
                if (hasInts) {
                    tp_compacted[lastOutPos] = tpCount[i];
                    fp_compacted[lastOutPos] = fpCount[i];
                    fn_compacted[lastOutPos] = fnCount[i];
                }
            }
        }

        if (lastOutPos < x.length - 1) {
            t_compacted = Arrays.copyOfRange(t_compacted, 0, lastOutPos + 1);
            x_compacted = Arrays.copyOfRange(x_compacted, 0, lastOutPos + 1);
            y_compacted = Arrays.copyOfRange(y_compacted, 0, lastOutPos + 1);
            if (hasInts) {
                tp_compacted = Arrays.copyOfRange(tp_compacted, 0, lastOutPos + 1);
                fp_compacted = Arrays.copyOfRange(fp_compacted, 0, lastOutPos + 1);
                fn_compacted = Arrays.copyOfRange(fn_compacted, 0, lastOutPos + 1);
            }
        }

        return new Pair<>(new double[][]{t_compacted, x_compacted, y_compacted},
                hasInts ? new int[][]{tp_compacted, fp_compacted, fn_compacted} : null);
    }

    private double getAuprc() {
        if (auprc != null) {
            return auprc;
        }
        auprc = calculateAUCPR();
        return auprc;
    }

    /**
     * Calculate the area under the precision/recall curve - aka AUCPR
     *
     * @return
     */
    public double calculateAUCPR() {
        if (auprc != null) {
            return auprc;
        }

        Preconditions.checkState(exampleCount > 0, "Unable to calculate AUPRC: no evaluation has been performed (no examples)");

        auprc = getPrecisionRecallCurve().calculateAUPRC();
        return auprc;
    }

    /**
     * Get the precision recall curve as array.
     * return[0] = threshold array<br>
     * return[1] = precision array<br>
     * return[2] = recall array<br>
     *
     * @return
     */
    public PrecisionRecallCurve getPrecisionRecallCurve() {

        if (prCurve != null) {
            return prCurve;
        }

        Preconditions.checkState(exampleCount > 0, "Unable to get PR curve: no evaluation has been performed (no examples)");

        double[] thresholdOut;
        double[] precisionOut;
        double[] recallOut;
        int[] tpCountOut;
        int[] fpCountOut;
        int[] fnCountOut;

        if (isExact) {
            INDArray pl = getProbAndLabelUsed();
            INDArray sorted = Nd4j.sortRows(pl, 0, false);
            INDArray isPositive = sorted.getColumn(1,true);

            INDArray cumSumPos = isPositive.cumsum(-1);
            val length = sorted.size(0);

            /*
            Sort descending. As we iterate: decrease probability threshold T... all values <= T are predicted
            as class 0, all others are predicted as class 1

            Precision:  sum(TP) / sum(predicted pos at threshold)
            Recall:     sum(TP) / total actual positives

            predicted positive at threshold: # values <= threshold, i.e., just i
             */

            INDArray t = Nd4j.create(DataType.DOUBLE, length + 2, 1);
            t.put(new INDArrayIndex[]{interval(1, length + 1), all()}, sorted.getColumn(0,true));

            INDArray linspace = Nd4j.linspace(1, length, length, DataType.DOUBLE);
            INDArray precision = cumSumPos.castTo(DataType.DOUBLE).div(linspace.reshape(cumSumPos.shape()));
            INDArray prec = Nd4j.create(DataType.DOUBLE, length + 2, 1);
            prec.put(new INDArrayIndex[]{interval(1, length + 1), all()}, precision);

            //Recall/TPR
            INDArray rec = Nd4j.create(DataType.DOUBLE, length + 2, 1);
            rec.put(new INDArrayIndex[]{interval(1, length + 1), all()},
                    cumSumPos.div(countActualPositive));

            //Edge cases
            t.putScalar(0, 0, 1.0);
            prec.putScalar(0, 0, 1.0);
            rec.putScalar(0, 0, 0.0);
            prec.putScalar(length + 1, 0, cumSumPos.getDouble(cumSumPos.length() - 1) / length);
            rec.putScalar(length + 1, 0, 1.0);

            thresholdOut = t.data().asDouble();
            precisionOut = prec.data().asDouble();
            recallOut = rec.data().asDouble();

            //Counts. Note the edge cases
            tpCountOut = new int[thresholdOut.length];
            fpCountOut = new int[thresholdOut.length];
            fnCountOut = new int[thresholdOut.length];

            for (int i = 1; i < tpCountOut.length - 1; i++) {
                tpCountOut[i] = cumSumPos.getInt(i - 1);
                fpCountOut[i] = i - tpCountOut[i]; //predicted positive - true positive
                fnCountOut[i] = (int) countActualPositive - tpCountOut[i];
            }

            //Edge cases: last idx -> threshold of 0.0, all predicted positive
            tpCountOut[tpCountOut.length - 1] = (int) countActualPositive;
            fpCountOut[tpCountOut.length - 1] = (int) (exampleCount - countActualPositive);
            fnCountOut[tpCountOut.length - 1] = 0;
            //Edge case: first idx -> threshold of 1.0, all predictions negative
            tpCountOut[0] = 0;
            fpCountOut[0] = 0; //(int)(exampleCount - countActualPositive);  //All negatives are predicted positive
            fnCountOut[0] = (int) countActualPositive;

            //Finally: 2 things to do
            //(a) Reverse order: lowest to highest threshold
            //(b) remove unnecessary/rendundant points (doesn't affect graph or AUPRC)

            ArrayUtils.reverse(thresholdOut);
            ArrayUtils.reverse(precisionOut);
            ArrayUtils.reverse(recallOut);
            ArrayUtils.reverse(tpCountOut);
            ArrayUtils.reverse(fpCountOut);
            ArrayUtils.reverse(fnCountOut);

            if (rocRemoveRedundantPts) {
                Pair<double[][], int[][]> pair = removeRedundant(thresholdOut, precisionOut, recallOut, tpCountOut,
                        fpCountOut, fnCountOut);
                double[][] temp = pair.getFirst();
                int[][] temp2 = pair.getSecond();
                thresholdOut = temp[0];
                precisionOut = temp[1];
                recallOut = temp[2];
                tpCountOut = temp2[0];
                fpCountOut = temp2[1];
                fnCountOut = temp2[2];
            }
        } else {
            thresholdOut = new double[counts.size()];
            precisionOut = new double[counts.size()];
            recallOut = new double[counts.size()];
            tpCountOut = new int[counts.size()];
            fpCountOut = new int[counts.size()];
            fnCountOut = new int[counts.size()];

            int i = 0;
            for (Map.Entry<Double, CountsForThreshold> entry : counts.entrySet()) {
                double t = entry.getKey();
                CountsForThreshold c = entry.getValue();
                long tpCount = c.getCountTruePositive();
                long fpCount = c.getCountFalsePositive();
                //For edge cases: http://stats.stackexchange.com/questions/1773/what-are-correct-values-for-precision-and-recall-in-edge-cases
                //precision == 1 when FP = 0 -> no incorrect positive predictions
                //recall == 1 when no dataset positives are present (got all 0 of 0 positives)
                double precision;
                if (tpCount == 0 && fpCount == 0) {
                    //At this threshold: no predicted positive cases
                    precision = 1.0;
                } else {
                    precision = tpCount / (double) (tpCount + fpCount);
                }

                double recall;
                if (countActualPositive == 0) {
                    recall = 1.0;
                } else {
                    recall = tpCount / ((double) countActualPositive);
                }

                thresholdOut[i] = c.getThreshold();
                precisionOut[i] = precision;
                recallOut[i] = recall;

                tpCountOut[i] = (int) tpCount;
                fpCountOut[i] = (int) fpCount;
                fnCountOut[i] = (int) (countActualPositive - tpCount);
                i++;
            }
        }

        prCurve = new PrecisionRecallCurve(thresholdOut, precisionOut, recallOut, tpCountOut, fpCountOut, fnCountOut,
                exampleCount);
        return prCurve;
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class CountsForThreshold implements Serializable, Cloneable {
        private double threshold;
        private long countTruePositive;
        private long countFalsePositive;

        public CountsForThreshold(double threshold) {
            this(threshold, 0, 0);
        }

        @Override
        public CountsForThreshold clone() {
            return new CountsForThreshold(threshold, countTruePositive, countFalsePositive);
        }

        public void incrementFalsePositive(long count) {
            countFalsePositive += count;
        }

        public void incrementTruePositive(long count) {
            countTruePositive += count;
        }
    }

    /**
     * Evaluate (collect statistics for) the given minibatch of data.
     * For time series (3 dimensions) use {@link #evalTimeSeries(INDArray, INDArray)} or {@link #evalTimeSeries(INDArray, INDArray, INDArray)}
     *
     * @param labels      Labels / true outcomes
     * @param predictions Predictions
     */
    @Override
    public void eval(INDArray labels, INDArray predictions, INDArray mask, List<? extends Serializable> recordMetaData) {

        Triple<INDArray, INDArray, INDArray> p = BaseEvaluation.reshapeAndExtractNotMasked(labels, predictions, mask, axis);
        if (p == null) {
            //All values masked out; no-op
            return;
        }
        INDArray labels2d = p.getFirst();
        INDArray predictions2d = p.getSecond();

        if (labels2d.rank() == 3 && predictions2d.rank() == 3) {
            //Assume time series input -> reshape to 2d
            evalTimeSeries(labels2d, predictions2d);
        }
        if (labels2d.rank() > 2 || predictions2d.rank() > 2 || labels2d.size(1) != predictions2d.size(1)
                || labels2d.size(1) > 2) {
            throw new IllegalArgumentException("Invalid input data shape: labels shape = "
                    + Arrays.toString(labels2d.shape()) + ", predictions shape = "
                    + Arrays.toString(predictions2d.shape()) + "; require rank 2 array with size(1) == 1 or 2");
        }

        if (labels2d.dataType() != predictions2d.dataType())
            labels2d = labels2d.castTo(predictions2d.dataType());

        //Check for NaNs in predictions - without this, evaulation could silently be intepreted as class 0 prediction due to argmax
        long count = Nd4j.getExecutioner().execAndReturn(new MatchCondition(predictions2d, Conditions.isNan())).getFinalResult().longValue();
        Preconditions.checkState(count == 0, "Cannot perform evaluation with NaN(s) present:" +
                " %s NaN(s) present in predictions INDArray", count);

        double step = 1.0 / thresholdSteps;
        boolean singleOutput = labels2d.size(1) == 1;

        if (isExact) {
            //Exact approach: simply add them to the storage for later computation/use

            if (probAndLabel == null) {
                //Do initial allocation
                val initialSize = Math.max(labels2d.size(0), exactAllocBlockSize);
                probAndLabel = Nd4j.create(DataType.DOUBLE, new long[]{initialSize, 2}, 'c'); //First col: probability of class 1. Second col: "is class 1"
            }

            //Allocate a larger array if necessary
            if (exampleCount + labels2d.size(0) >= probAndLabel.size(0)) {
                val newSize = probAndLabel.size(0) + Math.max(exactAllocBlockSize, labels2d.size(0));
                INDArray newProbAndLabel = Nd4j.create(DataType.DOUBLE, new long[]{newSize, 2}, 'c');
                if (exampleCount > 0) {
                    //If statement to handle edge case: no examples, but we need to re-allocate right away
                    newProbAndLabel.get(interval(0, exampleCount), all()).assign(
                            probAndLabel.get(interval(0, exampleCount), all()));
                }
                probAndLabel = newProbAndLabel;
            }

            //put values
            INDArray probClass1;
            INDArray labelClass1;
            if (singleOutput) {
                probClass1 = predictions2d;
                labelClass1 = labels2d;
            } else {
                probClass1 = predictions2d.getColumn(1,true);
                labelClass1 = labels2d.getColumn(1,true);
            }
            val currMinibatchSize = labels2d.size(0);
            probAndLabel.get(interval(exampleCount, exampleCount + currMinibatchSize),
                    NDArrayIndex.point(0)).assign(probClass1);

            probAndLabel.get(interval(exampleCount, exampleCount + currMinibatchSize),
                    NDArrayIndex.point(1)).assign(labelClass1);

            int countClass1CurrMinibatch = labelClass1.sumNumber().intValue();
            countActualPositive += countClass1CurrMinibatch;
            countActualNegative += labels2d.size(0) - countClass1CurrMinibatch;
        } else {
            //Thresholded approach
            INDArray positivePredictedClassColumn;
            INDArray positiveActualClassColumn;
            INDArray negativeActualClassColumn;

            if (singleOutput) {
                //Single binary variable case
                positiveActualClassColumn = labels2d;
                negativeActualClassColumn = labels2d.rsub(1.0); //1.0 - label
                positivePredictedClassColumn = predictions2d;
            } else {
                //Standard case - 2 output variables (probability distribution)
                positiveActualClassColumn = labels2d.getColumn(1,true);
                negativeActualClassColumn = labels2d.getColumn(0,true);
                positivePredictedClassColumn = predictions2d.getColumn(1,true);
            }

            //Increment global counts - actual positive/negative observed
            countActualPositive += positiveActualClassColumn.sumNumber().intValue();
            countActualNegative += negativeActualClassColumn.sumNumber().intValue();

            //Here: calculate true positive rate (TPR) vs. false positive rate (FPR) at different threshold

            INDArray ppc = null;
            INDArray itp = null;
            INDArray ifp = null;
            for (int i = 0; i <= thresholdSteps; i++) {
                double currThreshold = i * step;

                //Work out true/false positives - do this by replacing probabilities (predictions) with 1 or 0 based on threshold
                Condition condGeq = Conditions.greaterThanOrEqual(currThreshold);
                Condition condLeq = Conditions.lessThanOrEqual(currThreshold);

                if (ppc == null) {
                    ppc = positivePredictedClassColumn.dup(positiveActualClassColumn.ordering());
                } else {
                    ppc.assign(positivePredictedClassColumn);
                }
                Op op = new CompareAndSet(ppc, 1.0, condGeq);
                INDArray predictedClass1 = Nd4j.getExecutioner().exec(op);
                op = new CompareAndSet(predictedClass1, 0.0, condLeq);
                predictedClass1 = Nd4j.getExecutioner().exec(op);


                //True positives: occur when positive predicted class and actual positive actual class...
                //False positive occurs when positive predicted class, but negative actual class
                INDArray isTruePositive; // = predictedClass1.mul(positiveActualClassColumn); //If predicted == 1 and actual == 1 at this threshold: 1x1 = 1. 0 otherwise
                INDArray isFalsePositive; // = predictedClass1.mul(negativeActualClassColumn); //If predicted == 1 and actual == 0 at this threshold: 1x1 = 1. 0 otherwise
                if (i == 0) {
                    isTruePositive = predictedClass1.mul(positiveActualClassColumn);
                    isFalsePositive = predictedClass1.mul(negativeActualClassColumn);
                    itp = isTruePositive;
                    ifp = isFalsePositive;
                } else {
                    isTruePositive = Nd4j.getExecutioner().exec(new MulOp(predictedClass1, positiveActualClassColumn, itp))[0];
                    isFalsePositive = Nd4j.getExecutioner().exec(new MulOp(predictedClass1, negativeActualClassColumn, ifp))[0];
                }

                //Counts for this batch:
                int truePositiveCount = isTruePositive.sumNumber().intValue();
                int falsePositiveCount = isFalsePositive.sumNumber().intValue();

                //Increment counts for this thold
                CountsForThreshold thresholdCounts = counts.get(currThreshold);
                thresholdCounts.incrementTruePositive(truePositiveCount);
                thresholdCounts.incrementFalsePositive(falsePositiveCount);
            }
        }

        exampleCount += labels2d.size(0);
        auc = null;
        auprc = null;
        rocCurve = null;
        prCurve = null;
    }

    /**
     * Merge this ROC instance with another.
     * This ROC instance is modified, by adding the stats from the other instance.
     *
     * @param other ROC instance to combine with this one
     */
    @Override
    public void merge(ROC other) {
        if (this.thresholdSteps != other.thresholdSteps) {
            throw new UnsupportedOperationException(
                    "Cannot merge ROC instances with different numbers of threshold steps ("
                            + this.thresholdSteps + " vs. " + other.thresholdSteps + ")");
        }
        this.countActualPositive += other.countActualPositive;
        this.countActualNegative += other.countActualNegative;
        this.auc = null;
        this.auprc = null;
        this.rocCurve = null;
        this.prCurve = null;


        if (isExact) {
            if (other.exampleCount == 0) {
                return;
            }

            if (this.exampleCount == 0) {
                this.exampleCount = other.exampleCount;
                this.probAndLabel = other.probAndLabel;
                return;
            }

            if (this.exampleCount + other.exampleCount > this.probAndLabel.size(0)) {
                //Allocate new array
                val newSize = this.probAndLabel.size(0) + Math.max(other.probAndLabel.size(0), exactAllocBlockSize);
                INDArray newProbAndLabel = Nd4j.create(DataType.DOUBLE, newSize, 2);
                newProbAndLabel.put(new INDArrayIndex[]{interval(0, exampleCount), all()}, probAndLabel.get(interval(0, exampleCount), all()));
                probAndLabel = newProbAndLabel;
            }

            INDArray toPut = other.probAndLabel.get(interval(0, other.exampleCount), all());
            probAndLabel.put(new INDArrayIndex[]{
                            interval(exampleCount, exampleCount + other.exampleCount), all()},
                    toPut);
        } else {
            for (Double d : this.counts.keySet()) {
                CountsForThreshold cft = this.counts.get(d);
                CountsForThreshold otherCft = other.counts.get(d);
                cft.countTruePositive += otherCft.countTruePositive;
                cft.countFalsePositive += otherCft.countFalsePositive;
            }
        }

        this.exampleCount += other.exampleCount;
    }

    @Override
    public void reset() {
        countActualPositive = 0L;
        countActualNegative = 0L;
        counts.clear();

        if (isExact) {
            probAndLabel = null;
        } else {
            double step = 1.0 / thresholdSteps;
            for (int i = 0; i <= thresholdSteps; i++) {
                double currThreshold = i * step;
                counts.put(currThreshold, new CountsForThreshold(currThreshold));
            }
        }

        exampleCount = 0;
        auc = null;
        auprc = null;
    }

    @Override
    public String stats() {
        if(this.exampleCount == 0){
            return "ROC: No data available (no data has been performed)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("AUC (Area under ROC Curve):                ").append(calculateAUC()).append("\n");
        sb.append("AUPRC (Area under Precision/Recall Curve): ").append(calculateAUCPR());
        if (!isExact) {
            sb.append("\n");
            sb.append("[Note: Thresholded AUC/AUPRC calculation used with ").append(thresholdSteps)
                    .append(" steps); accuracy may reduced compared to exact mode]");
        }
        return sb.toString();
    }

    @Override
    public String toString(){
        return stats();
    }

    public double scoreForMetric(Metric metric){
        switch (metric){
            case AUROC:
                return calculateAUC();
            case AUPRC:
                return calculateAUCPR();
            default:
                throw new IllegalStateException("Unknown metric: " + metric);
        }
    }

    @Override
    public double getValue(IMetric metric){
        if(metric instanceof Metric){
            return scoreForMetric((Metric) metric);
        } else
            throw new IllegalStateException("Can't get value for non-ROC Metric " + metric);
    }

    @Override
    public ROC newInstance() {
        return new ROC(thresholdSteps, rocRemoveRedundantPts, exactAllocBlockSize, axis);
    }
}
