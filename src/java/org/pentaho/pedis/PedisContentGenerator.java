package org.pentaho.pedis;

import java.io.IOException;
import java.io.OutputStream;

import java.lang.reflect.Field;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;

import org.pentaho.commons.connection.IPentahoConnection;

import org.pentaho.platform.api.engine.IParameterProvider;
import org.pentaho.platform.api.engine.IPentahoSession;
import org.pentaho.platform.api.engine.IPluginResourceLoader;
import org.pentaho.platform.api.engine.PluginLifecycleException;

import org.pentaho.platform.api.repository.IContentItem;

import org.pentaho.platform.api.repository.datasource.IDatasourceMgmtService;
import org.pentaho.database.model.IDatabaseConnection;

import org.pentaho.platform.engine.core.system.PentahoSessionHolder;
import org.pentaho.platform.engine.core.system.PentahoSystem;

import org.pentaho.platform.engine.services.connection.PentahoConnectionFactory;

import org.pentaho.platform.plugin.services.connections.sql.SQLConnection;

import org.pentaho.platform.engine.services.solution.BaseContentGenerator;

import org.pentaho.platform.util.UUIDUtil;

public class PedisContentGenerator extends BaseContentGenerator{

  public static final String PATH_ROOT = "";
  public static final String PATH_CONNECTIONS = "connections";
  public static final String PATH_VERSION = "version";
  public static final String PATH_SCHEMAS = "schemas";
  public static final String PATH_CATALOGS = "catalogs";
  public static final String PATH_TABLES = "tables";
  public static final String PATH_COLUMNS = "columns";
  public static final String PATH_CLIENT_INFO = "clientInfo";
  public static final String PATH_CLIENT_INFO_PROPERTIES = "clientInfoProperties";
  public static final String PATH_FUNCTIONS = "functions";
  public static final String PATH_FUNCTION_COLUMNS = "functionColumns";
  public static final String PATH_PRIMARY_KEYS = "primaryKeys";
  public static final String PATH_IMPORTED_KEYS = "importedKeys";
  public static final String PATH_EXPORTED_KEYS = "exportedKeys";
  public static final String PATH_CROSS_REFERENCES = "crossReference";
  public static final String PATH_INDEX_INFO = "indexInfo";
  public static final String PATH_TABLE_INFO = "tableInfo";
  public static final String PATH_TABLE_TYPES = "tableTypes";
  public static final String PATH_TYPES = "types";
  public static final String PATH_QUERY = "query";

  public static final String PARAM_SQL = "sql";
  public static final String PARAM_OFFSET = "offset";
  public static final String PARAM_LIMIT = "limit";
  public static final String PARAM_DEBUG = "debug";
  public static final String PARAM_SCHEMA = "schema";
  public static final String PARAM_CATALOG = "catalog";
  public static final String PARAM_TABLE = "table";
  public static final String PARAM_FUNCTION = "function";
  public static final String PARAM_TABLE_TYPES = "types";
  public static final String PARAM_COLUMN = "column";
  public static final String PARAM_UNIQUE = "unique";
  public static final String PARAM_APPROXIMATE = "approximate";
  public static final String PARAM_PARENT_SCHEMA = "parentSchema";
  public static final String PARAM_PARENT_CATALOG = "parentCatalog";
  public static final String PARAM_PARENT_TABLE = "parentTable";
  public static final String PARAM_FOREIGN_SCHEMA = "foreignSchema";
  public static final String PARAM_FOREIGN_CATALOG = "foreignCatalog";
  public static final String PARAM_FOREIGN_TABLE = "foreignTable";

  public static final String version = "0.0.1";

  boolean debugEnabled = false;
  PedisLifecycleListener lifeCycleListener = PedisLifecycleListener.getInstance();
  IPluginResourceLoader resLoader = PentahoSystem.get(IPluginResourceLoader.class, null);
  IParameterProvider requestParameters = null;
  IParameterProvider pathParameters = null;
  IPentahoSession session = null;
  HttpServletRequest request;
  HttpServletResponse response;
  String method;
  String pathString;
  String[] path;
  OutputStream outputStream;
  IDataRenderer dataRenderer;
  Object responseData = null;
  IPentahoConnection pentahoConnection = null;
  SQLConnection sqlConnection = null;

  public Log getLogger() {
    return LogFactory.getLog(PedisContentGenerator.class);
  }

  protected void logExeption(Throwable t) {
    getLogger().error("Unexpected error occurred", t);
  }

  protected OutputStream getOutputStream(String mimeType) throws Exception {
    if (outputHandler == null) {
      throw new Exception("No output handler");
    }
    IContentItem contentItem = outputHandler.getOutputContentItem("response", "content", instanceId, mimeType);
    if (contentItem == null) {
      throw new Exception("No content item");
    }
    OutputStream outputStream = contentItem.getOutputStream(null);
    if (outputStream == null || !PentahoSystem.getInitializedOK() ) {
      throw new Exception("No output stream"); //$NON-NLS-1$
    }
    return outputStream;
  }

  protected void throwNotFound() throws PedisException {
    throw new PedisException(
      "Not found (" + pathString + ")",
      HttpServletResponse.SC_NOT_FOUND
    );
  }

  protected void throwMethodNotAllowed() throws PedisException {
    throw new PedisException(
      "Method " + method + " not allowed.",
      HttpServletResponse.SC_METHOD_NOT_ALLOWED
    );
  }

  protected void throwInternalServerError(String message) throws PedisException {
    throw new PedisException(
      message,
      HttpServletResponse.SC_INTERNAL_SERVER_ERROR
    );
  }

  protected void throwInternalServerError(String message, Throwable cause) throws PedisException {
    throw new PedisException(
      message,
      HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
      cause
    );
  }

  protected void throwForbidden(String message) throws PedisException {
    throw new PedisException(
      message,
      HttpServletResponse.SC_FORBIDDEN
    );
  }

  protected void checkPermissions() throws PedisException {
    if (lifeCycleListener.checkPermissions(session)) return;
    throwForbidden("Failed permissions check.");
  }

  /**
   *  Set up local variables to process the request.
   */
  protected boolean initializeRequest() throws Exception {
    setInstanceId(UUIDUtil.getUUIDAsString());
    pathParameters = parameterProviders.get("path");
    response = (HttpServletResponse)pathParameters.getParameter("httpresponse");
    session = PentahoSessionHolder.getSession();
    checkPermissions();
    pathString = pathParameters.getStringParameter("path", "");
    if (pathString.length() > 0 && pathString.charAt(0) == '/') {
      pathString = pathString.substring(1);
    }
    if (pathString.length() > 0 && pathString.charAt(pathString.length() - 1) == '/') {
      pathString = pathString.substring(0, pathString.length() - 1);
    }
    path = pathString.split("/");
    request = (HttpServletRequest)pathParameters.getParameter("httprequest");
    method = request.getMethod();
    if (!initContentType()) return false;
    requestParameters = parameterProviders.get(IParameterProvider.SCOPE_REQUEST);
    debugEnabled = Boolean.TRUE.toString().equals(
      requestParameters.getStringParameter(
        PARAM_DEBUG,
        PedisLifecycleListener.isDebugEnabled()
      )
    );
    return true;
  }

  /**
   *  Examine the request to establish what content type we will use for the response
   */
  protected boolean initContentType() throws IOException, Exception {
    String message;
    String contentType = null;
    String accept = request.getHeader("Accept");
    if (accept == null) {
      contentType = lifeCycleListener.getDefaultContentType();
      dataRenderer = lifeCycleListener.getContentType(contentType);
    }
    else {
      String[] acceptTypes = accept.split(",");
      int indexOfSemicolon;
      for (int i = 0; i < acceptTypes.length; i++){
        contentType = acceptTypes[i];
        indexOfSemicolon = contentType.indexOf(";");
        if (indexOfSemicolon != -1) {
          contentType = contentType.substring(0, indexOfSemicolon);
        }
        dataRenderer = lifeCycleListener.getContentType(contentType);
        if (dataRenderer != null) break;
      }
    }
    if (dataRenderer == null) {
      error("Error fetching " + pathString);
      message = "No contentTypeHandler found in settings.xml for requested content type (" + accept + ")";
      error(message);
      response.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, message);
      return false;
    }
    outputStream = getOutputStream(contentType);
    return true;
  }

  protected void renderData(Object data) throws Exception {
    dataRenderer.render(outputStream, data);
  }

  protected void renderData() throws Exception {
    renderData(responseData);
  }

  protected void cleanUp(){
    if (responseData instanceof ResultSet) {
      try {
        ((ResultSet)responseData).close();
        setResultSetColumnCase(JsonDataRenderer.RESULTSETCOLUMNS_KEEPCASE);
      }
      catch(Exception exception1) {
        error("Exception closing resultset", exception1);
      }
    }
    if (pentahoConnection != null && !pentahoConnection.isClosed()) {
      try {
        pentahoConnection.close();
      }
      catch (Exception exception3) {
        error("Exception closing pentaho connection", exception3);
      }
    }
    if (sqlConnection != null && !sqlConnection.isClosed()) {
      try {
        sqlConnection.close();
      }
      catch (Exception exception2) {
        error("Exception closing connection", exception2);
      }
    }
  }

  protected void setResultSetColumnCase(int resultSetColumnCase){
    if (dataRenderer instanceof JsonDataRenderer){
      JsonDataRenderer jsonDataRenderer = (JsonDataRenderer)dataRenderer;
      jsonDataRenderer.setResultSetColumnCase(resultSetColumnCase);
    }
  }

  protected void setResultSetColumnUpperCase(){
    setResultSetColumnCase(JsonDataRenderer.RESULTSETCOLUMNS_UPPERCASE);
  }

  protected void handleRoot() {
    final String v = this.version;
    responseData = new Object() {
      public String version = v;
    };
  }

  protected void handleVersion(){
    responseData = version;
  }

  protected IDatasourceMgmtService getDatasourceMgmtService() {
    return PentahoSystem.get(
      IDatasourceMgmtService.class,
      session
    );
  }

  protected IDatabaseConnection getDatasource(String name) throws Exception {
    IDatasourceMgmtService datasourceMgmtService = getDatasourceMgmtService();
    return datasourceMgmtService.getDatasourceByName(name);
  }

  protected IDatabaseConnection getDatasource() throws Exception {
    return getDatasource(path[1]);
  }

  protected IPentahoConnection getPentahoConnection(IDatabaseConnection datasource) throws PedisException {
    pentahoConnection = PentahoConnectionFactory.getConnection(
      IPentahoConnection.SQL_DATASOURCE,
      datasource.getName(),
      session,
      this
    );
    if (pentahoConnection == null) {
      throwInternalServerError(
        "Couldn't get connection for datasource " + datasource.getName()
      );
    }
    return pentahoConnection;
  }

  protected IPentahoConnection getPentahoConnection() throws Exception {
    return getPentahoConnection(getDatasource());
  }

  protected SQLConnection getSQLConnection(IDatabaseConnection datasource) throws Exception {
    IPentahoConnection pentahoConnection = this.getPentahoConnection(datasource);
    return (SQLConnection)pentahoConnection;
  }

  protected SQLConnection getSQLConnection() throws Exception {
    return getSQLConnection(getDatasource());
  }

  protected Connection getJdbcConnection(IDatabaseConnection datasource) throws Exception {
    SQLConnection sqlConnection = getSQLConnection(datasource);
    Connection jdbcConnection = sqlConnection.getNativeConnection();
    return jdbcConnection;
  }

  protected Connection getJdbcConnection() throws Exception {
    return getJdbcConnection(getDatasource());
  }

  protected DatabaseMetaData getDatabaseMetaData(Connection connection) throws Exception {
    return connection.getMetaData();
  }

  protected DatabaseMetaData getDatabaseMetaData() throws Exception {
    return getDatabaseMetaData(getJdbcConnection());
  }

  protected void handleConnections() throws Exception{
    IDatasourceMgmtService datasourceMgmtService = getDatasourceMgmtService();
    List<IDatabaseConnection> datasources = datasourceMgmtService.getDatasources();
    responseData = datasources;
  }

  protected void handleConnection() throws Exception {
    IDatabaseConnection datasource = getDatasource(path[1]);
    if (datasource == null) {
      throwNotFound();
    }
    Connection connection = getJdbcConnection(datasource);
    DatabaseMetaData databaseMetaData = getDatabaseMetaData(connection);
    setResultSetColumnUpperCase();
    responseData = databaseMetaData;
  }

  protected void handleSchemata() throws Exception {
    DatabaseMetaData databaseMetaData = getDatabaseMetaData();
    setResultSetColumnUpperCase();
    responseData = databaseMetaData.getSchemas();
  }

  protected void handleCatalogs() throws Exception {
    DatabaseMetaData databaseMetaData = getDatabaseMetaData();
    setResultSetColumnUpperCase();
    responseData = databaseMetaData.getCatalogs();
  }

  protected void handleClientInfo() throws Exception {
    Connection connection = getJdbcConnection();
    responseData = connection.getClientInfo();
  }

  protected void handleClientInfoProperties() throws Exception {
    DatabaseMetaData databaseMetaData = getDatabaseMetaData();
    responseData = databaseMetaData.getClientInfoProperties();
  }

  protected void handleTableTypes() throws Exception {
    DatabaseMetaData databaseMetaData = getDatabaseMetaData();
    setResultSetColumnUpperCase();
    responseData = databaseMetaData.getTableTypes();
  }

  protected String getCatalogParameter() {
    return requestParameters.getStringParameter(PARAM_CATALOG, null);
  }

  protected String getSchemaParameter() {
    return requestParameters.getStringParameter(PARAM_SCHEMA, null);
  }

  protected String getFunctionParameter() {
    return requestParameters.getStringParameter(PARAM_FUNCTION, "%");
  }

  protected String getTableParameter() {
    return requestParameters.getStringParameter(PARAM_TABLE, "%");
  }

  protected String getParentCatalogParameter() {
    return requestParameters.getStringParameter(PARAM_PARENT_CATALOG, null);
  }

  protected String getParentSchemaParameter() {
    return requestParameters.getStringParameter(PARAM_PARENT_SCHEMA, null);
  }

  protected String getParentTableParameter() {
    return requestParameters.getStringParameter(PARAM_PARENT_TABLE, "%");
  }

  protected String getForeignCatalogParameter() {
    return requestParameters.getStringParameter(PARAM_FOREIGN_CATALOG, null);
  }

  protected String getForeignSchemaParameter() {
    return requestParameters.getStringParameter(PARAM_FOREIGN_SCHEMA, null);
  }

  protected String getForeignTableParameter() {
    return requestParameters.getStringParameter(PARAM_FOREIGN_TABLE, "%");
  }

  protected String getColumnParameter() {
    return requestParameters.getStringParameter(PARAM_COLUMN, "%");
  }

  protected String[] getTableTypesParameter() {
    return requestParameters.getStringArrayParameter(PARAM_TABLE_TYPES, null);
  }

  protected boolean getUniqueParameter() {
    return Boolean.parseBoolean(requestParameters.getStringParameter(PARAM_UNIQUE, "false"));
  }

  protected boolean getApproximateParameter() {
    return Boolean.parseBoolean(requestParameters.getStringParameter(PARAM_APPROXIMATE, "true"));
  }

  protected String getSqlParameter() {
    return requestParameters.getStringParameter(PARAM_SQL, null);
  }

  protected int getIntParameter(String name, int defaultValue) {
    String value = requestParameters.getStringParameter(name, Integer.toString(defaultValue));
    return Integer.parseInt(value);
  }

  protected int getOffsetParameter() {
    return getIntParameter(PARAM_OFFSET, 0);
  }

  protected int getLimitParameter() {
    return getIntParameter(PARAM_LIMIT, Integer.MAX_VALUE);
  }

  protected void handleFunctions() throws Exception {
    DatabaseMetaData databaseMetaData = getDatabaseMetaData();
    String catalog = getCatalogParameter();
    String schema = getSchemaParameter();
    String function = getFunctionParameter();
    setResultSetColumnUpperCase();
    responseData = databaseMetaData.getFunctions(catalog, schema, function);
  }

  protected void handleFunctionColumns() throws Exception {
    DatabaseMetaData databaseMetaData = getDatabaseMetaData();
    String catalog = getCatalogParameter();
    String schema = getSchemaParameter();
    String function = getFunctionParameter();
    String column = getColumnParameter();
    setResultSetColumnUpperCase();
    responseData = databaseMetaData.getFunctionColumns(catalog, schema, function, column);
  }

  protected void handleTables() throws Exception {
    DatabaseMetaData databaseMetaData = getDatabaseMetaData();
    String catalog = getCatalogParameter();
    String schema = getSchemaParameter();
    String table = getTableParameter();
    String[] types = getTableTypesParameter();
    setResultSetColumnUpperCase();
    responseData = databaseMetaData.getTables(catalog, schema, table, types);
  }

  protected void handleTableInfo() throws Exception {
    DatabaseMetaData databaseMetaData = getDatabaseMetaData();
    String catalog = getCatalogParameter();
    String schema = getSchemaParameter();
    String table = getTableParameter();
    String[] types = getTableTypesParameter();
    setResultSetColumnUpperCase();

    ResultSet resultset;
    resultset = databaseMetaData.getTables(catalog, schema, table, types);
    if (!resultset.next()) {
      throwNotFound();
    }
    catalog = resultset.getString("TABLE_CAT");
    schema = resultset.getString("TABLE_SCHEM");
    table = resultset.getString("TABLE_NAME");

    Map<String, Object> map, detail, row;
    responseData = (map = new HashMap<String, Object>());

    ResultSetMetaData resultsetMetaData;
    resultsetMetaData = resultset.getMetaData();
    int n, i;

    n = resultsetMetaData.getColumnCount();
    String columnName;
    for (i = 1; i <= n; i++){
      columnName = resultsetMetaData.getColumnName(i);
      columnName = columnName.toUpperCase();
      map.put(columnName, resultset.getObject(i));
    }
    if (resultset.next()) {
      throwInternalServerError("Multiple tables not supported");
    }
    resultset.close();

    map.put("columns", databaseMetaData.getColumns(catalog, schema, table, "%"));
    map.put("primaryKey", databaseMetaData.getPrimaryKeys(catalog, schema, table));
    map.put("indexInfo", databaseMetaData.getIndexInfo(catalog, schema, table, false, true));
    map.put("importedKeys", databaseMetaData.getImportedKeys(catalog, schema, table));
    map.put("exportedKeys", databaseMetaData.getExportedKeys(catalog, schema, table));
    map.put("bestRowIdentifier", databaseMetaData.getBestRowIdentifier(catalog, schema, table, databaseMetaData.bestRowTransaction, true));
  }

  protected void handleColumns() throws Exception {
    DatabaseMetaData databaseMetaData = getDatabaseMetaData();
    String catalog = getCatalogParameter();
    String schema = getSchemaParameter();
    String table = getTableParameter();
    String column = getColumnParameter();
    setResultSetColumnUpperCase();
    responseData = databaseMetaData.getColumns(catalog, schema, table, column);
  }

  protected void handleIndexInfo() throws Exception {
    DatabaseMetaData databaseMetaData = getDatabaseMetaData();
    String catalog = getCatalogParameter();
    String schema = getSchemaParameter();
    String table = getTableParameter();
    boolean unique = getUniqueParameter();
    boolean approximate = getApproximateParameter();
    setResultSetColumnUpperCase();
    responseData = databaseMetaData.getIndexInfo(catalog, schema, table, unique, approximate);
  }

  protected void handlePrimaryKeys() throws Exception {
    DatabaseMetaData databaseMetaData = getDatabaseMetaData();
    String catalog = getCatalogParameter();
    String schema = getSchemaParameter();
    String table = getTableParameter();
    setResultSetColumnUpperCase();
    responseData = databaseMetaData.getPrimaryKeys(catalog, schema, table);
  }

  protected void handleImportedKeys() throws Exception {
    DatabaseMetaData databaseMetaData = getDatabaseMetaData();
    String catalog = getCatalogParameter();
    String schema = getSchemaParameter();
    String table = getTableParameter();
    setResultSetColumnUpperCase();
    responseData = databaseMetaData.getImportedKeys(catalog, schema, table);
  }

  protected void handleExportedKeys() throws Exception {
    DatabaseMetaData databaseMetaData = getDatabaseMetaData();
    String catalog = getCatalogParameter();
    String schema = getSchemaParameter();
    String table = getTableParameter();
    setResultSetColumnUpperCase();
    responseData = databaseMetaData.getExportedKeys(catalog, schema, table);
  }

  protected void handleCrossReferences() throws Exception {
    DatabaseMetaData databaseMetaData = getDatabaseMetaData();
    String parentCatalog = getParentCatalogParameter();
    String parentSchema = getParentSchemaParameter();
    String parentTable = getParentTableParameter();
    String foreignCatalog = getForeignCatalogParameter();
    String foreignSchema = getForeignSchemaParameter();
    String foreignTable = getForeignTableParameter();
    setResultSetColumnUpperCase();
    responseData = databaseMetaData.getCrossReference(parentCatalog, parentSchema, parentTable, foreignCatalog, foreignSchema, foreignTable);
  }

  protected void handleColumnTypes() throws Exception {
    DatabaseMetaData databaseMetaData = getDatabaseMetaData();
    setResultSetColumnUpperCase();
    responseData = databaseMetaData.getTypeInfo();
  }

  protected void handleTypes() throws Exception {
    Map <String, Integer> map = new HashMap<String, Integer>();
    Class c = Types.class;
    Field[] fields = c.getDeclaredFields();
    Field field;
    for (int i = 0; i < fields.length; i++) {
      field = fields[i];
      map.put(field.getName(), (Integer)field.get(null));
    }
    responseData = map;
  }

  protected boolean checkSqlIsQuery(String sql) {
    return sql.toUpperCase().indexOf("SELECT") == 0;
  }

  protected void handleQuery() throws Exception {
    String sql = getSqlParameter();
    if (sql == null) {
      throwInternalServerError("parameter SQL required");
    }
    if (!checkSqlIsQuery(sql)) {
      throwInternalServerError("SQL is not a query");
    }
    Connection connection = getJdbcConnection();
    int offset = getOffsetParameter();
    int limit = getLimitParameter();
    if (offset != 0 && limit != Integer.MAX_VALUE) {
      DatabaseMetaData databaseMetadata = getDatabaseMetaData(connection);
      String product = databaseMetadata.getDatabaseProductName();
      //TODO: rewrite the query to return only limit rows starting from position offset.
      //use the productName to decide on the syntax.
    }
    Statement statement = connection.createStatement();
    ResultSet resultset = statement.executeQuery(sql);
    Map <String, Object> map = new HashMap<String, Object>();
    map.put("rows", resultset);
    map.put("columns", resultset.getMetaData());
    responseData = map;
  }

  protected void handleRequest() throws Exception {
    //TODO: check request type
    switch (path.length) {
      case 0:
        break;
      case 1:
        if (PATH_ROOT.equals(path[0])) {
          handleRoot();
        }
        else
        if (PATH_CONNECTIONS.equals(path[0])) {
          handleConnections();
        }
        else
        if (PATH_TYPES.equals(path[0])) {
          handleTypes();
        }
        else
        if (PATH_VERSION.equals(path[0])){
          handleVersion();
        }
        else {
          throwNotFound();
        }
        break;
      case 2:
        if (PATH_CONNECTIONS.equals(path[0])) {
          handleConnection();
        }
        else {
          throwNotFound();
        }
        break;
      case 3:
        if (PATH_CONNECTIONS.equals(path[0])) {
          if (PATH_CATALOGS.equals(path[2])) {
            handleCatalogs();
          }
          else
          if (PATH_CLIENT_INFO.equals(path[2])) {
            handleClientInfo();
          }
          else
          if (PATH_CLIENT_INFO_PROPERTIES.equals(path[2])) {
            handleClientInfoProperties();
          }
          else
          if (PATH_COLUMNS.equals(path[2])) {
            handleColumns();
          }
          else
          if (PATH_CROSS_REFERENCES.equals(path[2])) {
            handleCrossReferences();
          }
          else
          if (PATH_EXPORTED_KEYS.equals(path[2])) {
            handleExportedKeys();
          }
          else
          if (PATH_FUNCTIONS.equals(path[2])) {
            handleFunctions();
          }
          else
          if (PATH_FUNCTION_COLUMNS.equals(path[2])) {
            handleFunctionColumns();
          }
          else
          if (PATH_IMPORTED_KEYS.equals(path[2])) {
            handleImportedKeys();
          }
          else
          if (PATH_INDEX_INFO.equals(path[2])) {
            handleIndexInfo();
          }
          else
          if (PATH_PRIMARY_KEYS.equals(path[2])) {
            handlePrimaryKeys();
          }
          else
          if (PATH_QUERY.equals(path[2])) {
            handleQuery();
          }
          else
          if (PATH_SCHEMAS.equals(path[2])) {
            handleSchemata();
          }
          else
          if (PATH_TABLES.equals(path[2])) {
            handleTables();
          }
          else
          if (PATH_TABLE_INFO.equals(path[2])) {
            handleTableInfo();
          }
          else
          if (PATH_TABLE_TYPES.equals(path[2])) {
            handleTableTypes();
          }
          else
          if (PATH_TYPES.equals(path[2])) {
            handleColumnTypes();
          }
          else {
            throwNotFound();
          }
        }
        else {
          throwNotFound();
        }
        break;
    }
  }

  public void createContent() throws Exception {
    PedisException pedisException = null;
    try {
      initializeRequest();
      handleRequest();
      renderData();
    }
    catch (PedisException exception1) {
      pedisException = exception1;
    }
    catch (Exception exception2) {
      pedisException = new PedisException(exception2.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, exception2);
    }
    finally {
      cleanUp();
    }
    if (pedisException != null) {
      logExeption(pedisException);
      response.setStatus(pedisException.getCode());
      pedisException.sendError(response);
    }
  }

}
