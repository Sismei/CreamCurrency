package com.agevate.creamcurrency.database;

import com.agevate.creamcurrency.CreamCurrency;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Optimized MySQL/MariaDB database connection using HikariCP.
 */
public class MySQLDatabase extends Database {

    private final HikariDataSource dataSource;

    public MySQLDatabase(CreamCurrency plugin) {
        super(plugin);

        ConfigurationSection config = plugin.getConfig().getConfigurationSection("database.mysql");

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("CreamCurrency-MySQL");

        String host = config.getString("host", "localhost");
        int port = config.getInt("port", 3306);
        String database = config.getString("database", "creamcurrency");
        boolean useSSL = config.getBoolean("ssl", false);

        // Build JDBC URL with optimizations
        StringBuilder jdbcUrl = new StringBuilder();
        jdbcUrl.append("jdbc:mysql://").append(host).append(":").append(port).append("/").append(database);
        jdbcUrl.append("?useSSL=").append(useSSL);
        jdbcUrl.append("&autoReconnect=true");
        jdbcUrl.append("&useUnicode=true");
        jdbcUrl.append("&characterEncoding=utf8");
        jdbcUrl.append("&useServerPrepStmts=true");
        jdbcUrl.append("&rewriteBatchedStatements=true");

        hikariConfig.setJdbcUrl(jdbcUrl.toString());
        hikariConfig.setUsername(config.getString("username", "root"));
        hikariConfig.setPassword(config.getString("password", ""));

        // HikariCP optimizations
        hikariConfig.setMaximumPoolSize(config.getInt("pool-size", 10));
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setMaxLifetime(1800000); // 30 minutes
        hikariConfig.setIdleTimeout(600000); // 10 minutes
        hikariConfig.setConnectionTimeout(30000); // 30 seconds
        hikariConfig.setLeakDetectionThreshold(60000); // 1 minute

        // MySQL performance properties
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "500");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
        hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
        hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
        hikariConfig.addDataSourceProperty("maintainTimeStats", "false");

        this.dataSource = new HikariDataSource(hikariConfig);
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
