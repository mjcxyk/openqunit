package com.qunar.base.qunit.database;

import com.google.common.collect.Lists;
import com.qunar.base.qunit.util.PropertyUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: zhaohuiyu
 * Date: 12/14/12
 */
public class PooledDataSource implements DataSource {
    private static final Logger logger = LoggerFactory.getLogger(PooledDataSource.class);

    private final static Map<String, DataSource> DATASOURCEWRAPPERS = new HashMap<String, DataSource>();

    private DataSource dataSource;

    private String database;

    public PooledDataSource(String database) {
        this.database = database;
        dataSource = getDataSource();
    }

    private DataSource getDataSource() {
        DataSource dataSource = DATASOURCEWRAPPERS.get(database);
        if (dataSource == null) {
            dataSource = createDataSource(database);
            DATASOURCEWRAPPERS.put(database, dataSource);
        }
        return dataSource;
    }

    private DataSource createDataSource(String database) {
        String driverClass = PropertyUtils.getProperty(database + ".jdbc.driver");
        String url = PropertyUtils.getProperty(database + ".jdbc.url");
        String username = PropertyUtils.getProperty(database + ".jdbc.username");
        String password = PropertyUtils.getProperty(database + ".jdbc.password");

        if (StringUtils.isBlank(driverClass)) {
            String message = String.format("数据库%s driver配置不正确(正确格式%s.jdbc.driver=)", database, database);
            logger.error(message);
            throw new RuntimeException(message);
        } else if (StringUtils.isBlank(url)) {
            String message = String.format("数据库%s url配置不正确(正确格式%s.jdbc.url=)", database, database);
            logger.error(message);
            throw new RuntimeException(message);
        } else if (StringUtils.isBlank(username)) {
            String message = String.format("数据库%s username配置不正确(正确格式%s.jdbc.username=)", database, database);
            logger.error(message);
            throw new RuntimeException(message);
        } else if (StringUtils.isBlank(password)) {
            String message = String.format("数据库%s password配置不正确(正确格式%s.jdbc.password=)", database, database);
            logger.error(message);
            throw new RuntimeException(message);
        }
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            logger.error("{} 数据库驱动不存在，请确保你在pom文件里引入了对应的依赖，或者确认你配置的驱动类名称是否正确", driverClass, e);
            throw new RuntimeException("数据库驱动不存在", e);
        }

        PoolConfiguration p = new PoolProperties();

        p.setValidationQuery("select 1");
        p.setTestOnBorrow(true);
        p.setTestWhileIdle(true);
        p.setFairQueue(false);
        p.setJmxEnabled(false);
        p.setTestOnReturn(false);
        p.setValidationInterval(30000);
        p.setTimeBetweenEvictionRunsMillis(30000);
        p.setCommitOnReturn(true);
        p.setMinIdle(10);
        p.setMaxIdle(10);
        p.setMaxActive(40);
        p.setInitialSize(10);
        p.setMaxWait(10000);
        p.setRemoveAbandonedTimeout(10);
        p.setMinEvictableIdleTimeMillis(20000);
        p.setLogAbandoned(false);
        p.setRemoveAbandoned(false);
        p.setDriverClassName(driverClass);
        p.setUrl(url);
        p.setUsername(username);
        p.setPassword(password);
        org.apache.tomcat.jdbc.pool.DataSource datasource = null;
        datasource = new org.apache.tomcat.jdbc.pool.DataSource();
        datasource.setPoolProperties(p);
        return datasource;
    }

    @Deprecated
    // fixme: 本函数中使用的链接未关闭,如果重新启用需要修复
    public List<String> getTimeType(List<String> tableNames){
        Connection connection;
        ResultSet resultSet = null;
        ArrayList<String>  timeColumn= Lists.newArrayList();
        try {
            connection = dataSource.getConnection();
            for (String tableName : tableNames) {
                resultSet = connection.getMetaData().getColumns(null, null, tableName, null);
                while (resultSet.next()) {
                    if(resultSet.getString("TYPE_NAME")=="DATETIME"||resultSet.getString("TYPE_NAME")=="DATE"){
                        timeColumn.add(resultSet.getString("COLUMN_NAME"));
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return timeColumn;
    }
    public void close() {
        ((org.apache.tomcat.jdbc.pool.DataSourceProxy) dataSource).close();
    }

    public String getDataBaseType() {
        String jdbcUrl = PropertyUtils.getProperty(database + ".jdbc.url");
        jdbcUrl = jdbcUrl.substring("jdbc:".length());
        return parseUrl(jdbcUrl);
    }

    private String parseUrl(String url) {
        URI uri = URI.create(url);
        return uri.getScheme();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public Connection getConnection(String s, String s2) throws SQLException {
        return dataSource.getConnection(s, s2);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return dataSource.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter printWriter) throws SQLException {
        dataSource.setLogWriter(printWriter);
    }

    @Override
    public void setLoginTimeout(int i) throws SQLException {
        dataSource.setLoginTimeout(i);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return dataSource.getLoginTimeout();
    }


    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return java.util.logging.Logger.getLogger("global-sql");
    }


    @Override
    public <T> T unwrap(Class<T> tClass) throws SQLException {
        return dataSource.unwrap(tClass);
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass) throws SQLException {
        return dataSource.isWrapperFor(aClass);
    }

    public String getDatabase() {
        return database;
    }

}
