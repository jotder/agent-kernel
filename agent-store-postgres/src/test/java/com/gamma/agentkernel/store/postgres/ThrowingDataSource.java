package com.gamma.agentkernel.store.postgres;

import java.io.PrintWriter;
import java.sql.Connection;
import java.util.logging.Logger;

import javax.sql.DataSource;

/**
 * A {@link DataSource} that fails if a connection is ever requested — used to prove a retriever
 * short-circuited (abstained) before touching JDBC.
 */
final class ThrowingDataSource implements DataSource {

    @Override
    public Connection getConnection() {
        throw new AssertionError("must not open a connection");
    }

    @Override
    public Connection getConnection(String username, String password) {
        throw new AssertionError("must not open a connection");
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
    }

    @Override
    public void setLoginTimeout(int seconds) {
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getGlobal();
    }

    @Override
    public <T> T unwrap(Class<T> iface) {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return false;
    }
}
