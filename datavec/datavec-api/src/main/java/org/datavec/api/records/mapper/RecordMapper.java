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

package org.datavec.api.records.mapper;

import lombok.Builder;
import lombok.Getter;
import org.datavec.api.conf.Configuration;
import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.records.writer.RecordWriter;
import org.datavec.api.split.InputSplit;
import org.datavec.api.split.partition.NumberOfRecordsPartitioner;
import org.datavec.api.split.partition.Partitioner;
import org.datavec.api.writable.Writable;

import java.util.List;

@Builder
public class RecordMapper {

    private RecordReader recordReader;
    private RecordWriter recordWriter;
    private InputSplit inputUrl;
    private InputSplit[] splitPerReader;
    private RecordReader[] readersToConcat;

    private InputSplit outputUrl;
    @Builder.Default
    private boolean callInitRecordReader = true;
    @Builder.Default
    private boolean callInitRecordWriter = true;
    @Builder.Default
    private boolean callInitPartitioner = true;
    @Builder.Default
    private Configuration configuration = new Configuration();

    private Configuration[] configurationsPerReader;

    @Getter
    @Builder.Default
    private Partitioner partitioner = new NumberOfRecordsPartitioner();
    private int batchSize;

    /**
     * Copy the {@link RecordReader}
     * data using the {@link RecordWriter}.
     * Note that unless batch is supported by
     * both the {@link RecordReader} and {@link RecordWriter}
     * then writes will happen one at a time.
     * You can see if batch is enabled via {@link RecordReader#batchesSupported()}
     * and {@link RecordWriter#supportsBatch()} respectively.
     * @throws Exception
     */
    public void copy() throws Exception {
        if(callInitRecordReader) {
            if(recordReader != null) {
                recordReader.initialize(configuration, inputUrl);
            }
            else {
                if(readersToConcat == null || splitPerReader == null)  {
                    throw new IllegalArgumentException("No readers or input  splits found.");
                }

                if(readersToConcat.length != splitPerReader.length) {
                    throw new IllegalArgumentException("One input split must be specified per record reader");
                }

                for(int i = 0; i < readersToConcat.length; i++) {
                    if(readersToConcat[i] == null) {
                        throw new IllegalStateException("Reader at record " + i + " was null!");
                    }
                    if(splitPerReader[i] == null) {
                        throw new IllegalStateException("Split at " + i + " is null!");
                    }
                    //allow for, but do not enforce configurations per reader.
                    if(configurationsPerReader != null) {
                        readersToConcat[i].initialize(configurationsPerReader[i], splitPerReader[i]);
                    }
                    else {
                        readersToConcat[i].initialize(configuration,splitPerReader[i]);
                    }
                }
            }
        }

        if(callInitPartitioner) {
            partitioner.init(configuration, outputUrl);
        }

        if(callInitRecordWriter) {
            recordWriter.initialize(configuration, outputUrl, partitioner);
        }

        if(recordReader != null) {
            write(recordReader,true);
        }
        else if(readersToConcat != null) {
            for(RecordReader recordReader : readersToConcat) {
                write(recordReader,false);
            }

            //close since we can't do it within the method
            recordWriter.close();
        }

    }


    private void write(RecordReader recordReader,boolean closeWriter) throws Exception {
        if(batchSize > 0 && recordReader.batchesSupported() && recordWriter.supportsBatch()) {
            while (recordReader.hasNext()) {
                List<List<Writable>> next = recordReader.next(batchSize);
                //ensure we can write a file for either the current or next iterations
                if (partitioner.needsNewPartition()) {
                    partitioner.currentOutputStream().flush();
                    partitioner.currentOutputStream().close();
                    partitioner.openNewStream();
                }
                //update records written
                partitioner.updatePartitionInfo(recordWriter.writeBatch(next));

            }

            partitioner.currentOutputStream().flush();
            recordReader.close();
            if (closeWriter) {
                partitioner.currentOutputStream().close();
                recordWriter.close();
            }
        }

        else {
            while(recordReader.hasNext()) {
                List<Writable> next = recordReader.next();
                //update records written
                partitioner.updatePartitionInfo(recordWriter.write(next));
                if(partitioner.needsNewPartition()) {
                    partitioner.openNewStream();
                }
            }
        }
    }
}
