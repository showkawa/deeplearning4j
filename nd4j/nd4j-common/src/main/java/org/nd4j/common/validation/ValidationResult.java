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

package org.nd4j.common.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ValidationResult implements Serializable {

    private String formatType;       //Human readable format/model type
    private Class<?> formatClass;    //Actual class the format/model is (or should be)
    private String path;             //Path of file (if applicable)
    private boolean valid;           //Whether the file/model is valid
    private List<String> issues;     //List of issues (generally only present if not valid)
    private Throwable exception;     //Exception, if applicable



    @Override
    public String toString(){
        List<String> lines = new ArrayList<>();
        if(formatType != null) {
            lines.add("Format type: " + formatType);
        }
        if(formatClass != null){
            lines.add("Format class: " + formatClass.getName());
        }
        if(path != null){
            lines.add("Path: " + path);
        }
        lines.add("Format valid: " + valid);
        if(issues != null && !issues.isEmpty()){
            if(issues.size() == 1){
                addWithIndent(issues.get(0), lines, "Issue: ", "       ");
            } else {
                lines.add("Issues:");
                for (String s : issues) {
                    addWithIndent(s, lines, "- ", "  ");
                }
            }
        }
        if(exception != null){
            String ex = ExceptionUtils.getStackTrace(exception);
            lines.add("Stack Trace:");
            addWithIndent(ex, lines, "  ", "  ");
        }
        //Would use String.join but that's Java 8...
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for(String s : lines){
            if(!first)
                sb.append("\n");
            sb.append(s);
            first = false;
        }
        return sb.toString();
    }

    protected static void addWithIndent(String toAdd, List<String> list, String firstLineIndent, String laterLineIndent){
        String[] split = toAdd.split("\n");
        boolean first = true;
        for(String issueLine : split){
            list.add((first ? firstLineIndent : laterLineIndent) + issueLine);
            first = false;
        }
    }

}
