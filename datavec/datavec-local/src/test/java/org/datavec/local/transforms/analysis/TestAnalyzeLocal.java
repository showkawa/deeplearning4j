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

package org.datavec.local.transforms.analysis;

import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.records.reader.impl.csv.CSVRecordReader;
import org.datavec.api.split.FileSplit;
import org.datavec.api.transform.analysis.DataAnalysis;
import org.datavec.api.transform.analysis.columns.NumericalColumnAnalysis;
import org.datavec.api.transform.schema.Schema;
import org.datavec.api.util.ndarray.RecordConverter;
import org.datavec.api.writable.Writable;
import org.datavec.local.transforms.AnalyzeLocal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.common.io.ClassPathResource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
@Tag(TagNames.FILE_IO)
@Tag(TagNames.JAVA_ONLY)
public class TestAnalyzeLocal {



    @Test
    public void testAnalysisBasic() throws Exception {

        RecordReader rr = new CSVRecordReader();
        rr.initialize(new FileSplit(new ClassPathResource("iris.txt").getFile()));

        Schema s = new Schema.Builder()
                .addColumnsDouble("0", "1", "2", "3")
                .addColumnInteger("label")
                .build();

        DataAnalysis da = AnalyzeLocal.analyze(s, rr);

        System.out.println(da);

        //Compare:
        List<List<Writable>> list = new ArrayList<>();
        rr.reset();
        while(rr.hasNext()){
            list.add(rr.next());
        }

        INDArray arr = RecordConverter.toMatrix(DataType.DOUBLE, list);
        INDArray mean = arr.mean(0);
        INDArray std = arr.std(0);

        for( int i = 0; i < 5; i++) {
            double m = ((NumericalColumnAnalysis)da.getColumnAnalysis().get(i)).getMean();
            double stddev = ((NumericalColumnAnalysis)da.getColumnAnalysis().get(i)).getSampleStdev();
            assertEquals(mean.getDouble(i), m, 1e-3);
            assertEquals(std.getDouble(i), stddev, 1e-3);
        }

    }

}
