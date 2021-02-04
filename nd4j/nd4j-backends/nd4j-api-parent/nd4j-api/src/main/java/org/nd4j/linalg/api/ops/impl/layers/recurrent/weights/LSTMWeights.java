package org.nd4j.linalg.api.ops.impl.layers.recurrent.weights;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.layers.recurrent.LSTMBlockCell;
import org.nd4j.linalg.api.ops.impl.layers.recurrent.LSTMLayer;

/**
 * The weight configuration of a LSTM layer.  For {@link LSTMLayer} and {@link LSTMBlockCell}.
 *
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
public class LSTMWeights extends RNNWeights {

    /**
     * Input to hidden weights and hidden to hidden weights, with a shape of [inSize + numUnits, 4*numUnits].
     *
     * Input to hidden and hidden to hidden are concatenated in dimension 0,
     * so the input to hidden weights are [:inSize, :] and the hidden to hidden weights are [inSize:, :].
     */
    private SDVariable weights;
    private INDArray iWeights;

    /**
     * Cell peephole (t-1) connections to input modulation gate, with a shape of [numUnits].
     */
    private SDVariable inputPeepholeWeights;
    private INDArray iInputPeepholeWeights;

    /**
     * Cell peephole (t-1) connections to forget gate, with a shape of [numUnits].
     */
    private SDVariable forgetPeepholeWeights;
    private INDArray iForgetPeepholeWeights;

    /**
     * Cell peephole (t) connections to output gate, with a shape of [numUnits].
     */
    private SDVariable outputPeepholeWeights;
    private INDArray iOutputPeepholeWeights;

    /**
     * Input to hidden and hidden to hidden biases, with shape [1, 4*numUnits].
     */
    private SDVariable bias;
    private INDArray iBias;

    @Override
    public SDVariable[] args() {
        return filterNonNull(weights, inputPeepholeWeights, forgetPeepholeWeights, outputPeepholeWeights, bias);
    }

    @Override
    public INDArray[] arrayArgs() {
        return filterNonNull(iWeights, iInputPeepholeWeights, iForgetPeepholeWeights, iOutputPeepholeWeights, iBias);
    }
}
