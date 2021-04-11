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

package org.nd4j.evaluation.serde;

import org.nd4j.evaluation.classification.ROC;
import org.nd4j.shade.jackson.core.JsonGenerator;
import org.nd4j.shade.jackson.databind.JsonSerializer;
import org.nd4j.shade.jackson.databind.SerializerProvider;
import org.nd4j.shade.jackson.databind.jsontype.TypeSerializer;

import java.io.IOException;

public class ROCSerializer extends JsonSerializer<ROC> {
    @Override
    public void serialize(ROC roc, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
                    throws IOException {
        boolean empty = roc.getExampleCount() == 0;

        if (roc.isExact() && !empty) {
            //For exact ROC implementation: force AUC and AUPRC calculation, so result can be stored in JSON, such
            //that we have them once deserialized.
            //Due to potentially huge size, exact mode doesn't store the original predictions in JSON
            roc.calculateAUC();
            roc.calculateAUCPR();
        }
        jsonGenerator.writeNumberField("thresholdSteps", roc.getThresholdSteps());
        jsonGenerator.writeNumberField("countActualPositive", roc.getCountActualPositive());
        jsonGenerator.writeNumberField("countActualNegative", roc.getCountActualNegative());
        jsonGenerator.writeObjectField("counts", roc.getCounts());
        if(!empty) {
            jsonGenerator.writeNumberField("auc", roc.calculateAUC());
            jsonGenerator.writeNumberField("auprc", roc.calculateAUCPR());
        }
        if (roc.isExact() && !empty) {
            //Store ROC and PR curves only for exact mode... they are redundant + can be calculated again for thresholded mode
            jsonGenerator.writeObjectField("rocCurve", roc.getRocCurve());
            jsonGenerator.writeObjectField("prCurve", roc.getPrecisionRecallCurve());
        }
        jsonGenerator.writeBooleanField("isExact", roc.isExact());
        jsonGenerator.writeNumberField("exampleCount", roc.getExampleCount());
        jsonGenerator.writeBooleanField("rocRemoveRedundantPts", roc.isRocRemoveRedundantPts());
    }

    @Override
    public void serializeWithType(ROC value, JsonGenerator gen, SerializerProvider serializers, TypeSerializer typeSer)
                    throws IOException {
        typeSer.writeTypePrefixForObject(value, gen);
        serialize(value, gen, serializers);
        typeSer.writeTypeSuffixForObject(value, gen);
    }
}
