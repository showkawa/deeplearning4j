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

package org.deeplearning4j.spark.parameterserver.networking.v2;

import lombok.extern.slf4j.Slf4j;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.common.function.Supplier;
import org.nd4j.common.primitives.Atomic;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

@Slf4j
public class UpdaterParamsConsumer implements Subscriber<INDArray>, Supplier<INDArray> {
    protected transient final Atomic<INDArray> params = new Atomic<>();

    @Override
    public void onSubscribe(Subscription subscription) {
        // no-op
    }

    @Override
    public synchronized void onNext(INDArray array) {
        if (array != null)
            params.set(array);
    }

    @Override
    public void onError(Throwable throwable) {
        throw new RuntimeException(throwable);
    }

    @Override
    public void onComplete() {
        // no-op
    }

    @Override
    public INDArray get() {
        return params.get();
    }
}
