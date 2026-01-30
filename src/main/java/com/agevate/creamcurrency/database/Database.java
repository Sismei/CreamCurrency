package com.agevate.creamcurrency.database;

import com.agevate.creamcurrency.CreamCurrency;
import java.sql.Connection;
import java.sql.SQLException;

public abstract class Database {
    protected final CreamCurrency plugin;

    public Database(CreamCurrency plugin) {
        this.plugin = plugin;
    }

    public abstract Connection getConnection() throws SQLException;

    public abstract void close();
}
