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

package org.apache.shardingsphere.readwritesplitting.config.rule;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.readwritesplitting.transaction.TransactionalReadQueryStrategy;

import java.util.List;

/**
 * Readwrite-splitting data source group rule configuration.
 */
@RequiredArgsConstructor
@Getter
public final class ReadwriteSplittingDataSourceGroupRuleConfiguration {
    
    private final String name;
    
    private final String writeDataSourceName;
    
    private final List<String> readDataSourceNames;
    
    private final TransactionalReadQueryStrategy transactionalReadQueryStrategy;
    
    private final String loadBalancerName;
    
    public ReadwriteSplittingDataSourceGroupRuleConfiguration(final String name, final String writeDataSourceName, final List<String> readDataSourceNames, final String loadBalancerName) {
        this(name, writeDataSourceName, readDataSourceNames, TransactionalReadQueryStrategy.DYNAMIC, loadBalancerName);
    }
}
