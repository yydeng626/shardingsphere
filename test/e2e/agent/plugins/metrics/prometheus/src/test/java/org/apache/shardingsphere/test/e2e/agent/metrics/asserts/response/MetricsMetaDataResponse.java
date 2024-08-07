/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.test.e2e.agent.metrics.asserts.response;

import lombok.Getter;
import lombok.Setter;
import org.apache.shardingsphere.infra.util.json.JsonConfiguration;

import java.util.List;
import java.util.Map;

/**
 * Metrics meta data response.
 */
@Getter
@Setter
public final class MetricsMetaDataResponse implements JsonConfiguration {
    
    private String status;
    
    private Map<String, List<Metric>> data;
    
    private String errorType;
    
    private String error;
    
    /**
     * Metric.
     */
    @Getter
    @Setter
    public static final class Metric implements JsonConfiguration {
        
        private String type;
        
        private String help;
        
        private String unit;
    }
}
