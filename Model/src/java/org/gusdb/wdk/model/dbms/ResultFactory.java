/**
 * 
 */
package org.gusdb.wdk.model.dbms;

import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.query.Column;
import org.gusdb.wdk.model.query.Query;
import org.gusdb.wdk.model.query.QueryInstance;
import org.json.JSONException;

/**
 * @author Jerric Gao
 * 
 */
public class ResultFactory {

    private Logger logger = Logger.getLogger(ResultFactory.class);

    private DBPlatform platform;
    private CacheFactory cacheFactory;

    public ResultFactory(WdkModel wdkModel) throws SQLException {
        this.platform = wdkModel.getQueryPlatform();
        this.cacheFactory = new CacheFactory(wdkModel, platform);
    }

    public CacheFactory getCacheFactory() {
        return cacheFactory;
    }

    public ResultList getCachedResults(QueryInstance instance)
            throws SQLException, NoSuchAlgorithmException, WdkModelException,
            JSONException, WdkUserException {

        // get the cached sql
        Query query = instance.getQuery();
        QueryInfo queryInfo = cacheFactory.getQueryInfo(query);
        StringBuffer sql = new StringBuffer("SELECT ");
        boolean firstColumn = true;
        for (Column column : query.getColumns()) {
            if (firstColumn) firstColumn = false;
            else sql.append(", ");
            sql.append(column.getName());
        }
        sql.append(" FROM ").append(queryInfo.getCacheTable());
        sql.append(" WHERE ").append(CacheFactory.COLUMN_INSTANCE_ID);
        sql.append(" = ").append(instance.getInstanceId());

        // get the resultList
        DataSource dataSource = platform.getDataSource();
        ResultSet resultSet = SqlUtils.executeQuery(dataSource, sql.toString());
        return new SqlResultList(resultSet);
    }

    public int getInstanceId(QueryInstance instance, String[] indexColumns)
            throws SQLException, NoSuchAlgorithmException, WdkModelException,
            JSONException, WdkUserException {
        Query query = instance.getQuery();
        QueryInfo queryInfo = cacheFactory.getQueryInfo(query);
        // get the query instance id; null if not exist
        Integer instanceId = checkInstanceId(instance, queryInfo);
        if (instanceId == null) // instance cache not exist, create it
            instanceId = createCache(instance, queryInfo, indexColumns);
        instance.setInstanceId(instanceId);
        return instanceId;
    }

    public String getCacheTable(QueryInstance instance, String[] indexColumn)
            throws SQLException, WdkModelException, NoSuchAlgorithmException,
            JSONException, WdkUserException {
        // make sure the instance is cached
        getInstanceId(instance, indexColumn);
        Query query = instance.getQuery();
        QueryInfo queryInfo = cacheFactory.getQueryInfo(query);
        return queryInfo.getCacheTable();
    }

    /**
     * look up the cache index table for the query instance; if the instance
     * doesn't exist, return null;
     * 
     * @param instance
     * @return
     * @throws SQLException
     * @throws WdkUserException
     * @throws JSONException
     * @throws WdkModelException
     * @throws NoSuchAlgorithmException
     * @throws SQLException
     * @throws JSONException
     * @throws WdkModelException
     * @throws NoSuchAlgorithmException
     * @throws WdkUserException
     */
    private Integer checkInstanceId(QueryInstance instance, QueryInfo queryInfo)
            throws NoSuchAlgorithmException, WdkModelException, JSONException,
            WdkUserException, SQLException {
        String checksum = instance.getChecksum();
        StringBuffer sql = new StringBuffer("SELECT ");
        sql.append(CacheFactory.COLUMN_INSTANCE_ID).append(", ");
        sql.append(CacheFactory.COLUMN_RESULT_MESSAGE);
        sql.append(" FROM ").append(CacheFactory.TABLE_INSTANCE);
        sql.append(" WHERE ").append(CacheFactory.COLUMN_QUERY_ID);
        sql.append(" = ").append(queryInfo.getQueryId());
        sql.append(" AND ").append(CacheFactory.COLUMN_INSTANCE_CHECKSUM);
        sql.append(" = '").append(checksum).append("'");

        DataSource dataSource = platform.getDataSource();
        ResultSet resultSet = null;
        try {
            resultSet = SqlUtils.executeQuery(dataSource, sql.toString());
            if (!resultSet.next()) return null;

            int instanceId = resultSet.getInt(CacheFactory.COLUMN_INSTANCE_ID);
            String message = platform.getClobData(resultSet,
                    CacheFactory.COLUMN_RESULT_MESSAGE);
            instance.setResultMessage(message);
            instance.setInstanceId(instanceId);
            return instanceId;
        } finally {
            SqlUtils.closeResultSet(resultSet);
        }
    }

    private int createCache(QueryInstance instance, QueryInfo queryInfo,
            String[] indexColumns) throws JSONException, SQLException,
            WdkUserException, NoSuchAlgorithmException, WdkModelException {
        DataSource dataSource = platform.getDataSource();

        // start transaction
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        try {
            // add instance into cache index table, and get instanceId back
            // get a new id for the instance
            int instanceId = platform.getNextId(null,
                    CacheFactory.TABLE_INSTANCE);
            instance.setInstanceId(instanceId);

            // check whether need to create the cache;
            String cacheTable = queryInfo.getCacheTable();
            if (!platform.checkTableExists(null, cacheTable)) {
                // create the cache using the result of the first query
                instance.createCache(connection, cacheTable, instanceId,
                        indexColumns);
                // disable the stats on the new cache table
                String schema = platform.getWdkModel().getModelConfig().getAppDB().getLogin();
                platform.disableStatistics(connection, schema, cacheTable);
            } else {// insert result into existing cache table
                instance.insertToCache(connection, cacheTable, instanceId);
            }

            // instance record is added after the cache is created to make sure
            // if there is something wrong with the query, nothing was cached.
            // check the id again, if it has been created, discard the newly
            // inserted data and use the old one.
            Integer newId = checkInstanceId(instance, queryInfo);
            if (newId == null) {
                String checksum = instance.getChecksum();
                try {
                    addCacheInstance(connection, queryInfo, instance,
                            instanceId, checksum);
                } catch (SQLException ex) {
                    // the row must be inserted by other process at the moment.
                    // If so, retrieve it; otherwise, throw error.
                    newId = checkInstanceId(instance, queryInfo);
                    if (newId == null) throw ex;
                }
            } else {
                instanceId = newId;
            }

            connection.commit();
            return instanceId;
        } catch (SQLException ex) {
            logger.error("Failed to create cache for query ["
                    + instance.getQuery().getFullName() + "]");
            connection.rollback();
            throw ex;
        } catch (WdkUserException ex) {
            connection.rollback();
            throw ex;
        } catch (NoSuchAlgorithmException ex) {
            connection.rollback();
            throw ex;
        } catch (WdkModelException ex) {
            connection.rollback();
            throw ex;
        } catch (JSONException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(true);
            connection.close();
        }
    }

    public void createCacheTableIndex(Connection connection, String cacheTable,
            String[] indexColumns) throws SQLException {
        // create index on query instance id
        StringBuffer sqlId = new StringBuffer("CREATE INDEX ");
        sqlId.append(cacheTable).append("_idx01 ON ").append(cacheTable);
        sqlId.append(" (").append(CacheFactory.COLUMN_INSTANCE_ID).append(")");

        // create index on other columns
        StringBuffer sqlOther = null;
        if (indexColumns != null) {
            sqlOther = new StringBuffer("CREATE INDEX ");
            sqlOther.append(cacheTable).append("_idx02 ON ").append(cacheTable);
            sqlOther.append(" (");
            boolean firstColumn = true;
            for (String column : indexColumns) {
                if (firstColumn) firstColumn = false;
                else sqlOther.append(", ");
                sqlOther.append(column);
            }
            sqlOther.append(")");
        }

        Statement stmt = null;
        try {
            stmt = connection.createStatement();

            stmt.execute(sqlId.toString());
            if (indexColumns != null) stmt.execute(sqlOther.toString());
        } finally {
            if (stmt != null) stmt.close();
        }
    }

    private void addCacheInstance(Connection connection, QueryInfo queryInfo,
            QueryInstance instance, int instanceId, String checksum)
            throws SQLException, NoSuchAlgorithmException, WdkModelException,
            JSONException, WdkUserException {
        StringBuffer sql = new StringBuffer("INSERT INTO ");
        sql.append(CacheFactory.TABLE_INSTANCE).append(" (");
        sql.append(CacheFactory.COLUMN_INSTANCE_ID).append(", ");
        sql.append(CacheFactory.COLUMN_QUERY_ID).append(", ");
        sql.append(CacheFactory.COLUMN_INSTANCE_CHECKSUM).append(", ");
        sql.append(CacheFactory.COLUMN_RESULT_MESSAGE);
        sql.append(") VALUES (?, ?, ?, ?)");

        PreparedStatement ps = null;
        try {
            ps = connection.prepareStatement(sql.toString());
            ps.setInt(1, instanceId);
            ps.setInt(2, queryInfo.getQueryId());
            ps.setString(3, checksum);
            platform.setClobData(ps, 4, instance.getResultMessage(), false);
            ps.executeUpdate();
        } finally {
            // close the statement manually, since we cannot close the
            // connection; it's not committed yet.
            if (ps != null) ps.close();
        }
    }
}
