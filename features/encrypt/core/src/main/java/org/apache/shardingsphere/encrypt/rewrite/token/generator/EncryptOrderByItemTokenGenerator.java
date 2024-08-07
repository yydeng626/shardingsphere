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

package org.apache.shardingsphere.encrypt.rewrite.token.generator;

import lombok.Setter;
import org.apache.shardingsphere.encrypt.exception.syntax.UnsupportedEncryptSQLException;
import org.apache.shardingsphere.encrypt.rewrite.aware.EncryptRuleAware;
import org.apache.shardingsphere.encrypt.rule.EncryptRule;
import org.apache.shardingsphere.encrypt.rule.table.EncryptTable;
import org.apache.shardingsphere.infra.binder.context.segment.select.orderby.OrderByItem;
import org.apache.shardingsphere.infra.binder.context.statement.SQLStatementContext;
import org.apache.shardingsphere.infra.binder.context.statement.dml.SelectStatementContext;
import org.apache.shardingsphere.infra.binder.context.type.TableAvailable;
import org.apache.shardingsphere.infra.database.core.type.DatabaseTypeRegistry;
import org.apache.shardingsphere.infra.exception.core.ShardingSpherePreconditions;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereSchema;
import org.apache.shardingsphere.infra.rewrite.sql.token.generator.CollectionSQLTokenGenerator;
import org.apache.shardingsphere.infra.rewrite.sql.token.generator.aware.SchemaMetaDataAware;
import org.apache.shardingsphere.infra.rewrite.sql.token.pojo.SQLToken;
import org.apache.shardingsphere.infra.rewrite.sql.token.pojo.generic.SubstitutableColumnNameToken;
import org.apache.shardingsphere.sql.parser.statement.core.segment.dml.column.ColumnSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.dml.order.item.ColumnOrderByItemSegment;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;

/**
 * Order by item token generator for encrypt.
 */
@Setter
public final class EncryptOrderByItemTokenGenerator implements CollectionSQLTokenGenerator<SQLStatementContext>, SchemaMetaDataAware, EncryptRuleAware {
    
    private String databaseName;
    
    private Map<String, ShardingSphereSchema> schemas;
    
    private EncryptRule encryptRule;
    
    @Override
    public boolean isGenerateSQLToken(final SQLStatementContext sqlStatementContext) {
        return sqlStatementContext instanceof SelectStatementContext && containsOrderByItem(sqlStatementContext);
    }
    
    @Override
    public Collection<SQLToken> generateSQLTokens(final SQLStatementContext sqlStatementContext) {
        Collection<SQLToken> result = new LinkedHashSet<>();
        String defaultSchema = new DatabaseTypeRegistry(sqlStatementContext.getDatabaseType()).getDefaultSchemaName(databaseName);
        ShardingSphereSchema schema = ((TableAvailable) sqlStatementContext).getTablesContext().getSchemaName().map(schemas::get).orElseGet(() -> schemas.get(defaultSchema));
        for (OrderByItem each : getOrderByItems(sqlStatementContext)) {
            if (each.getSegment() instanceof ColumnOrderByItemSegment) {
                ColumnSegment columnSegment = ((ColumnOrderByItemSegment) each.getSegment()).getColumn();
                Map<String, String> columnTableNames = ((TableAvailable) sqlStatementContext).getTablesContext().findTableNames(Collections.singleton(columnSegment), schema);
                result.addAll(generateSQLTokensWithColumnSegments(Collections.singleton(columnSegment), columnTableNames));
            }
        }
        return result;
    }
    
    private Collection<SubstitutableColumnNameToken> generateSQLTokensWithColumnSegments(final Collection<ColumnSegment> columnSegments, final Map<String, String> columnTableNames) {
        Collection<SubstitutableColumnNameToken> result = new LinkedList<>();
        for (ColumnSegment each : columnSegments) {
            String tableName = columnTableNames.getOrDefault(each.getExpression(), "");
            Optional<EncryptTable> encryptTable = encryptRule.findEncryptTable(tableName);
            String columnName = each.getIdentifier().getValue();
            ShardingSpherePreconditions.checkState(!encryptTable.isPresent() || !encryptTable.get().isEncryptColumn(columnName), () -> new UnsupportedEncryptSQLException("ORDER BY"));
        }
        return result;
    }
    
    private Collection<OrderByItem> getOrderByItems(final SQLStatementContext sqlStatementContext) {
        if (!(sqlStatementContext instanceof SelectStatementContext)) {
            return Collections.emptyList();
        }
        Collection<OrderByItem> result = new LinkedList<>();
        SelectStatementContext statementContext = (SelectStatementContext) sqlStatementContext;
        if (!statementContext.getOrderByContext().isGenerated()) {
            result.addAll(statementContext.getOrderByContext().getItems());
        }
        for (SelectStatementContext each : statementContext.getSubqueryContexts().values()) {
            result.addAll(getOrderByItems(each));
        }
        return result;
    }
    
    private boolean containsOrderByItem(final SQLStatementContext sqlStatementContext) {
        if (!(sqlStatementContext instanceof SelectStatementContext)) {
            return false;
        }
        SelectStatementContext statementContext = (SelectStatementContext) sqlStatementContext;
        if (!statementContext.getOrderByContext().getItems().isEmpty() && !statementContext.getOrderByContext().isGenerated()) {
            return true;
        }
        for (SelectStatementContext each : statementContext.getSubqueryContexts().values()) {
            if (containsOrderByItem(each)) {
                return true;
            }
        }
        return false;
    }
}
