package com.agevate.creamcurrency.database;

import com.agevate.creamcurrency.CreamCurrency;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * Optimized SQLite database connection using HikariCP.
 */
public class SQLiteDatabase extends Database {

    private final HikariDataSource dataSource;

    public SQLiteDatabase(CreamCurrency plugin) {
        super(plugin);

        File dataFile = new File(plugin.getDataFolder(), "database.db");
        if (!dataFile.getParentFile().exists()) {
            dataFile.getParentFile().mkdirs();
        }

        try {
            if (!dataFile.exists()) {
                dataFile.createNewFile();
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create database file!", e);
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("CreamCurrency-SQLite");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + dataFile.getAbsolutePath());

        // SQLite optimizations
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("cache_size", "10000");
        config.addDataSourceProperty("temp_store", "MEMORY");

        // Connection pool settings (SQLite is single-writer, so keep pool small)
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setMaxLifetime(1800000); // 30 minutes
        config.setIdleTimeout(600000); // 10 minutes
        config.setConnectionTimeout(30000); // 30 seconds

        // Disable connection testing for SQLite (faster)
        config.setConnectionTestQuery(null);

        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
