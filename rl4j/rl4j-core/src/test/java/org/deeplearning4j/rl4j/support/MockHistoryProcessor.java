package org.deeplearning4j.rl4j.support;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.deeplearning4j.rl4j.learning.IHistoryProcessor;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;

public class MockHistoryProcessor implements IHistoryProcessor {

    public int startMonitorCallCount = 0;
    public int stopMonitorCallCount = 0;

    private final Configuration config;
    private final CircularFifoQueue<INDArray> history;

    public final ArrayList<INDArray> recordCalls;
    public final ArrayList<INDArray> addCalls;

    public MockHistoryProcessor(Configuration config) {

        this.config = config;
        history = new CircularFifoQueue<>(config.getHistoryLength());
        recordCalls = new ArrayList<INDArray>();
        addCalls = new ArrayList<INDArray>();
    }

    @Override
    public Configuration getConf() {
        return config;
    }

    @Override
    public INDArray[] getHistory() {
        INDArray[] array = new INDArray[getConf().getHistoryLength()];
        for (int i = 0; i < config.getHistoryLength(); i++) {
            array[i] = history.get(i).castTo(Nd4j.dataType());
        }
        return array;
    }

    @Override
    public void record(INDArray image) {
        recordCalls.add(image);
    }

    @Override
    public void add(INDArray image) {
        addCalls.add(image);
        history.add(image);
    }

    @Override
    public void startMonitor(String filename, int[] shape) {
        ++startMonitorCallCount;
    }

    @Override
    public void stopMonitor() {
        ++stopMonitorCallCount;
    }

    @Override
    public boolean isMonitoring() {
        return false;
    }

    @Override
    public double getScale() {
        return 255.0;
    }
}
