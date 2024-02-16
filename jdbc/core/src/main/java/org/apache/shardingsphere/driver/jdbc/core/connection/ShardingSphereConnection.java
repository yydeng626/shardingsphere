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

package org.apache.shardingsphere.driver.jdbc.core.connection;

import lombok.Getter;
import org.apache.shardingsphere.driver.jdbc.adapter.AbstractConnectionAdapter;
import org.apache.shardingsphere.driver.jdbc.core.datasource.metadata.ShardingSphereDatabaseMetaData;
import org.apache.shardingsphere.driver.jdbc.core.statement.ShardingSpherePreparedStatement;
import org.apache.shardingsphere.driver.jdbc.core.statement.ShardingSphereStatement;
import org.apache.shardingsphere.driver.jdbc.exception.connection.ConnectionClosedException;
import org.apache.shardingsphere.infra.exception.core.ShardingSpherePreconditions;
import org.apache.shardingsphere.infra.executor.sql.process.ProcessEngine;
import org.apache.shardingsphere.infra.metadata.user.Grantee;
import org.apache.shardingsphere.infra.session.connection.ConnectionContext;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.transaction.api.TransactionType;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Savepoint;
import java.sql.Statement;

/**
 * ShardingSphere connection.
 */
public final class ShardingSphereConnection extends AbstractConnectionAdapter {
    
    private final ProcessEngine processEngine = new ProcessEngine();
    
    @Getter
    private final String databaseName;
    
    @Getter
    private final ContextManager contextManager;
    
    @Getter
    private final DriverDatabaseConnectionManager databaseConnectionManager;
    
    @Getter
    private final String processId;
    
    private boolean autoCommit = true;
    
    private int transactionIsolation = TRANSACTION_READ_UNCOMMITTED;
    
    private boolean readOnly;
    
    private volatile boolean closed;
    
    public ShardingSphereConnection(final String databaseName, final ContextManager contextManager) {
        this.databaseName = databaseName;
        this.contextManager = contextManager;
        databaseConnectionManager = new DriverDatabaseConnectionManager(databaseName, contextManager);
        processId = processEngine.connect(new Grantee("", ""), databaseName);
    }
    
    /**
     * Whether hold transaction or not.
     *
     * @return true or false
     */
    public boolean isHoldTransaction() {
        return databaseConnectionManager.getConnectionTransaction().isHoldTransaction(autoCommit);
    }
    
    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return new ShardingSphereDatabaseMetaData(this);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql) throws SQLException {
        return new ShardingSpherePreparedStatement(this, sql);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency) throws SQLException {
        return new ShardingSpherePreparedStatement(this, sql, resultSetType, resultSetConcurrency);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
        return new ShardingSpherePreparedStatement(this, sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys) throws SQLException {
        return new ShardingSpherePreparedStatement(this, sql, autoGeneratedKeys);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes) throws SQLException {
        return new ShardingSpherePreparedStatement(this, sql, Statement.RETURN_GENERATED_KEYS);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql, final String[] columnNames) throws SQLException {
        return new ShardingSpherePreparedStatement(this, sql, columnNames);
    }
    
    @Override
    public Statement createStatement() {
        return new ShardingSphereStatement(this);
    }
    
    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency) {
        return new ShardingSphereStatement(this, resultSetType, resultSetConcurrency);
    }
    
    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) {
        return new ShardingSphereStatement(this, resultSetType, resultSetConcurrency, resultSetHoldability);
    }
    
    @Override
    public CallableStatement prepareCall(final String sql) throws SQLException {
        // TODO Support single DataSource scenario for now. Implement ShardingSphereCallableStatement to support multi DataSource scenarios.
        return databaseConnectionManager.getRandomConnection().prepareCall(sql);
    }
    
    @Override
    public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency) throws SQLException {
        // TODO Support single DataSource scenario for now. Implement ShardingSphereCallableStatement to support multi DataSource scenarios.
        return databaseConnectionManager.getRandomConnection().prepareCall(sql, resultSetType, resultSetConcurrency);
    }
    
    @Override
    public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
        // TODO Support single DataSource scenario for now. Implement ShardingSphereCallableStatement to support multi DataSource scenarios.
        return databaseConnectionManager.getRandomConnection().prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }
    
    @Override
    public boolean getAutoCommit() {
        return autoCommit;
    }
    
    @Override
    public void setAutoCommit(final boolean autoCommit) throws SQLException {
        this.autoCommit = autoCommit;
        if (databaseConnectionManager.getConnectionTransaction().isLocalTransaction()) {
            processLocalTransaction();
        } else {
            processDistributedTransaction();
        }
    }
    
    private void processLocalTransaction() throws SQLException {
        databaseConnectionManager.setAutoCommit(autoCommit);
        if (!autoCommit) {
            getConnectionContext().getTransactionContext().setInTransaction(true);
        }
    }
    
    private void processDistributedTransaction() throws SQLException {
        switch (databaseConnectionManager.getConnectionTransaction().getDistributedTransactionOperationType(autoCommit)) {
            case BEGIN:
                beginDistributedTransaction();
                break;
            case COMMIT:
                databaseConnectionManager.getConnectionTransaction().commit();
                break;
            default:
                break;
        }
    }
    
    private void beginDistributedTransaction() throws SQLException {
        databaseConnectionManager.close();
        databaseConnectionManager.getConnectionTransaction().begin();
        getConnectionContext().getTransactionContext().setInTransaction(true);
    }
    
    /**
     * Handle auto commit.
     *
     * @throws SQLException SQL exception
     */
    public void handleAutoCommit() throws SQLException {
        if (!autoCommit && !databaseConnectionManager.getConnectionTransaction().isInTransaction()) {
            if (TransactionType.isDistributedTransaction(databaseConnectionManager.getConnectionTransaction().getTransactionType())) {
                beginDistributedTransaction();
            } else {
                getConnectionContext().getTransactionContext().setInTransaction(true);
            }
        }
    }
    
    @Override
    public void commit() throws SQLException {
        try {
            databaseConnectionManager.commit();
        } finally {
            databaseConnectionManager.getConnectionTransaction().setRollbackOnly(false);
            getConnectionContext().close();
        }
    }
    
    @Override
    public void rollback() throws SQLException {
        try {
            databaseConnectionManager.rollback();
        } finally {
            databaseConnectionManager.getConnectionTransaction().setRollbackOnly(false);
            getConnectionContext().close();
        }
    }
    
    @Override
    public void rollback(final Savepoint savepoint) throws SQLException {
        checkClose();
        databaseConnectionManager.rollback(savepoint);
    }
    
    @Override
    public Savepoint setSavepoint(final String name) throws SQLException {
        checkClose();
        if (!isHoldTransaction()) {
            throw new SQLFeatureNotSupportedException("Savepoint can only be used in transaction blocks.");
        }
        return databaseConnectionManager.setSavepoint(name);
    }
    
    @Override
    public Savepoint setSavepoint() throws SQLException {
        checkClose();
        ShardingSpherePreconditions.checkState(isHoldTransaction(), () -> new SQLFeatureNotSupportedException("Savepoint can only be used in transaction blocks."));
        return databaseConnectionManager.setSavepoint();
    }
    
    @Override
    public void releaseSavepoint(final Savepoint savepoint) throws SQLException {
        checkClose();
        if (!isHoldTransaction()) {
            return;
        }
        databaseConnectionManager.releaseSavepoint(savepoint);
    }
    
    private void checkClose() throws SQLException {
        ShardingSpherePreconditions.checkState(!isClosed(), () -> new ConnectionClosedException().toSQLException());
    }
    
    @SuppressWarnings("MagicConstant")
    @Override
    public int getTransactionIsolation() throws SQLException {
        return databaseConnectionManager.getTransactionIsolation().orElseGet(() -> transactionIsolation);
    }
    
    @Override
    public void setTransactionIsolation(final int level) throws SQLException {
        transactionIsolation = level;
        databaseConnectionManager.setTransactionIsolation(level);
    }
    
    @Override
    public boolean isReadOnly() {
        return readOnly;
    }
    
    @Override
    public void setReadOnly(final boolean readOnly) throws SQLException {
        this.readOnly = readOnly;
        databaseConnectionManager.setReadOnly(readOnly);
    }
    
    @Override
    public boolean isValid(final int timeout) throws SQLException {
        return databaseConnectionManager.isValid(timeout);
    }
    
    @Override
    public Array createArrayOf(final String typeName, final Object[] elements) throws SQLException {
        return databaseConnectionManager.getRandomConnection().createArrayOf(typeName, elements);
    }
    
    @Override
    public String getSchema() {
        // TODO return databaseName for now in getSchema(), the same as before
        return databaseName;
    }
    
    @Override
    public boolean isClosed() {
        return closed;
    }
    
    @Override
    public void close() throws SQLException {
        closed = true;
        databaseConnectionManager.close();
        processEngine.disconnect(processId);
    }
    
    private ConnectionContext getConnectionContext() {
        return databaseConnectionManager.getConnectionContext();
    }
}
