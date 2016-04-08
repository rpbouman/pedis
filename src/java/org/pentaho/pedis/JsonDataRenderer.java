package org.pentaho.pedis;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.DatabaseMetaData;

import java.io.OutputStream;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.List;

import org.pentaho.database.model.IDatabaseConnection;
import org.pentaho.database.model.DatabaseType;
import org.pentaho.database.model.DatabaseAccessType;

public class JsonDataRenderer implements IDataRenderer {

  protected static Map<Class, Map<String, Member>> members = new HashMap<Class, Map<String, Member>>();
  protected OutputStream outputStream;
  protected Map<Object, String> recursionGuard = new HashMap<Object, String>();

  public static final int RESULTSETCOLUMNS_UPPERCASE = 1;
  public static final int RESULTSETCOLUMNS_KEEPCASE = 0;
  public static final int RESULTSETCOLUMNS_LOWERCASE = -1;
  protected int resultSetColumnCase = RESULTSETCOLUMNS_KEEPCASE;

  public JsonDataRenderer() {
  }

  public void setResultSetColumnCase(int resultSetColumnCase){
    switch (resultSetColumnCase) {
      case RESULTSETCOLUMNS_UPPERCASE:
      case RESULTSETCOLUMNS_LOWERCASE:
      case RESULTSETCOLUMNS_KEEPCASE:
        break;
      default:
        throw new IllegalArgumentException("Not a valid value for resultSetColumnCase (" + resultSetColumnCase + ")");
    }
    this.resultSetColumnCase = resultSetColumnCase;
  }

  public void render(OutputStream outputStream, Object o) throws Exception {
    this.outputStream = outputStream;
    render(o);
  }

  protected void render(Object o) throws Exception {
    if (o == null) {
      renderPrimitive("null");
    }
    else
    if (o instanceof String) {
      renderString((String)o);
    }
    else
    if (o instanceof Date) {
      renderString(o.toString());
    }
    else
    if (o instanceof Character) {
      renderString(((Character)o).toString());
    }
    else
    if (o instanceof Boolean
    ||  o instanceof Number
    ) {
      renderPrimitive(o);
    }
    else
    if (o instanceof ResultSet) {
      renderResultSet((ResultSet)o);
    }
    else
    if (o instanceof ResultSetMetaData) {
      renderResultSetMetaData((ResultSetMetaData)o);
    }
    else
    if (o instanceof Map) {
      renderMap((Map<String, Object>)o);
    }
    else
    if (o instanceof List) {
      renderList((List)o);
    }
    else
    if (o instanceof IDatabaseConnection) {
      renderDatabaseConnection((IDatabaseConnection)o);
    }
    else
    if (o instanceof DatabaseType) {
      renderObject(((DatabaseType)o));
    }
    else
    if (o instanceof DatabaseAccessType) {
      renderString(((DatabaseAccessType)o).toString());
    }
    else {
      renderObject(o);
    }
  }

  protected void startObject() throws Exception {
    outputStream.write('{');
  }

  protected void endObject() throws Exception {
    outputStream.write('}');
  }

  protected void startArray() throws Exception {
    outputStream.write('[');
  }

  protected void endArray() throws Exception {
    outputStream.write(']');
  }

  protected void separator() throws Exception {
    outputStream.write(',');
  }

  protected void punctuator() throws Exception {
    outputStream.write(':');
  }

  protected void delimiter() throws Exception {
    outputStream.write('"');
  }

  protected void renderPrimitive(Object o) throws Exception {
    outputStream.write(o.toString().getBytes());
  }

  protected void renderString(String string) throws Exception{
    StringBuilder stringBuilder = new StringBuilder();
    int i;
    int n = string.length();
    char ch;
    for (i = 0; i < n; i++) {
      ch = string.charAt(i);
      switch (ch) {
        case '\b':
          stringBuilder.append("\\b");
          break;
        case '\f':
          stringBuilder.append("\\f");
          break;
        case '\n':
          stringBuilder.append("\\n");
          break;
        case '\r':
          stringBuilder.append("\\r");
          break;
        case '\t':
          stringBuilder.append("\\t");
          break;
        case '/':
        case '"':
        case '\\':
          stringBuilder.append('\\');
        default:
          stringBuilder.append(ch);
      }
    }
    delimiter();
    renderPrimitive(stringBuilder.toString());
    delimiter();
  }

  protected void renderDatabaseConnection(IDatabaseConnection databaseConnection) throws Exception {
    renderObject(databaseConnection, new String[]{"password"});
  }

  protected void renderResultSetMetaData(ResultSetMetaData metadata) throws Exception {
    int columnCount = metadata.getColumnCount();
    int i;
    startArray();
    for (i = 1; i <= columnCount; i++) {
      if (i > 1) separator();
      startObject();
      renderObjectMember("displaySize", new Integer(metadata.getColumnDisplaySize(i)));
      separator();
      renderObjectMember("label", metadata.getColumnLabel(i));
      separator();
      renderObjectMember("name", metadata.getColumnName(i));
      separator();
      renderObjectMember("type", new Integer(metadata.getColumnType(i)));
      separator();
      renderObjectMember("typeName", metadata.getColumnTypeName(i));
      separator();
      renderObjectMember("precision", new Integer(metadata.getPrecision(i)));
      separator();
      renderObjectMember("scale", new Integer(metadata.getScale(i)));
      separator();
      renderObjectMember("schema", metadata.getSchemaName(i));
      separator();
      renderObjectMember("table", metadata.getTableName(i));
      separator();
      renderObjectMember("autoIncrement", new Boolean(metadata.isAutoIncrement(i)));
      separator();
      renderObjectMember("caseSensitive", new Boolean(metadata.isCaseSensitive(i)));
      separator();
      renderObjectMember("currency", new Boolean(metadata.isCurrency(i)));
      separator();
      renderObjectMember("nullable", new Integer(metadata.isNullable(i)));
      separator();
      renderObjectMember("readonly", new Boolean(metadata.isReadOnly(i)));
      separator();
      renderObjectMember("searchable", new Boolean(metadata.isSearchable(i)));
      separator();
      renderObjectMember("signed", new Boolean(metadata.isSigned(i)));
      separator();
      renderObjectMember("writeable", new Boolean(metadata.isWritable(i)));
      endObject();
    }
    endArray();
  }

  protected void renderResultSet(ResultSet resultSet) throws Exception {
    ResultSetMetaData metaData = resultSet.getMetaData();
    int columnCount = metaData.getColumnCount();
    int i;
    String name;
    Object value;
    String[] names = new String[columnCount+1];
    for (i = 1; i <= columnCount; i++) {
      name = metaData.getColumnName(i);
      switch (resultSetColumnCase) {
        case RESULTSETCOLUMNS_UPPERCASE:
          name = name.toUpperCase();
          break;
        case RESULTSETCOLUMNS_LOWERCASE:
          name = name.toLowerCase();
          break;
        case RESULTSETCOLUMNS_KEEPCASE:
        default:
      }
      names[i] = name;
    }
    int j = 0;
    startArray();
    while (resultSet.next()) {
      if (j++ > 0) {
        separator();
      }
      startObject();
      for (i = 1; i <= columnCount; i++){
        if (i > 1) {
          separator();
        }
        name = names[i];
        try {
          value = resultSet.getObject(i);
        } 
        catch (Exception ex){
          value = ex;
        }
        renderObjectMember(name, value);
      }
      endObject();
    }
    endArray();
  }

  protected void renderList(List list) throws Exception {
    Iterator iterator = list.iterator();
    int j = 0;
    startArray();
    while (iterator.hasNext()) {
      if (j++ > 0) separator();
      render(iterator.next());
    }
    endArray();
  }

  protected void renderMap(Map<String, Object> map) throws Exception {
    Set<String> keySet = map.keySet();
    Iterator<String> iterator = keySet.iterator();
    int j = 0;
    String name;
    startObject();
    while (iterator.hasNext()) {
      if (j++ > 0) separator();
      name = iterator.next();
      renderObjectMember(name, map.get(name));
    }
    endObject();
  }

  protected Map<String, Member> loadMembers(Class c) throws Exception {
    Map<String, Member> members = new HashMap<String, Member>();
    int i, modifiers;
    int j = 0;
    Field[] fs = c.getFields();
    Field f;
    for (i = 0; i < fs.length; i++) {
      f = fs[i];
      modifiers = f.getModifiers();
      if (Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers)) continue;
      members.put(f.getName(), f);
    }
    Method[] ms = c.getMethods();
    Method m;
    String name;
    for (i = 0; i < ms.length; i++) {
      m = ms[i];
      modifiers = m.getModifiers();
      if (Modifier.isStatic(modifiers)
      ||  Modifier.isAbstract(modifiers)
      || !Modifier.isPublic(modifiers)
      ||  m.getParameterTypes().length != 0
      ) continue;
      name = m.getName();
      if (name.startsWith("get") && !"getClass".equals(name)) {
        name = name.substring("get".length());
      }
      else
      if (name.startsWith("is")) {
        name = name.substring("is".length());
      }
      else {
        continue;
      }
      name = name.substring(0, 1).toLowerCase() + name.substring(1);
      members.put(name, m);
    }
    this.members.put(c, members);
    return members;
  }

  protected Map<String, Member> loadDatabaseMetaDataMembers() throws Exception {
    Map<String, Member> members = new HashMap<String, Member>();
    Class c = DatabaseMetaData.class;
    int i;
    int j = 0;
    Field[] fs = c.getFields();
    Field f;
    for (i = 0; i < fs.length; i++) {
      f = fs[i];
      members.put(f.getName(), f);
    }
    Method[] ms = c.getMethods();
    Method m;
    String name;
    Class returnType;
    for (i = 0; i < ms.length; i++) {
      m = ms[i];
      if (m.getParameterTypes().length != 0) continue;
      returnType = m.getReturnType();
      if (!
        ( returnType.equals(String.class)
        ||returnType.equals(Character.class)
        ||returnType.equals(Number.class)
        ||returnType.equals(Boolean.class)
        ||ResultSet.class.isAssignableFrom(returnType)
        )
      ) continue;
      name = m.getName();
      if ("getClass".equals(name)) continue;
      if (name.startsWith("get")) {
        name = name.substring("get".length());
      }
      char char1 = name.charAt(0);
      if (name.length() > 1) {
        char char2 = name.charAt(1);
        if (Character.isUpperCase(char1) && Character.isLowerCase(char2)) {
          name = name.substring(0,1).toLowerCase() + name.substring(1);
        }
      }
      members.put(name, m);
    }
    this.members.put(c, members);
    return members;
  }

  protected void renderArray(Object array) throws Exception{
    startArray();
    int length = Array.getLength(array);
    for (int i = 0; i < length; i++){
      if (i > 0) outputStream.write(',');
      render(Array.get(array, i));
    }
    endArray();
  }

  protected void renderObjectMember(String name, Object value) throws Exception {
    renderString(name);
    punctuator();
    render(value);
  }

  protected Map<String, Member> getMembersForClass(Class c) throws Exception {
    Map<String, Member> members;
    boolean isDatabaseMetaData = (DatabaseMetaData.class.isAssignableFrom(c));
    if (!DatabaseMetaData.class.equals(c) && isDatabaseMetaData) {
      members = getMembersForClass(DatabaseMetaData.class);
    }
    else
    if (!this.members.containsKey(c)) {
      if (isDatabaseMetaData) {
        members = loadDatabaseMetaDataMembers();
      }
      else {
        members = loadMembers(c);
      }
    }
    else {
      members = this.members.get(c);
    }
    return members;
  }

  protected void renderObject(Object object, String[] excludeMembers) throws Exception {
    List<String> excludeMembersList = excludeMembers == null ? Collections.<String>emptyList() : Arrays.asList(excludeMembers);
    Class c = object.getClass();
    int j = 0;
    if (c.isArray()) {
      renderArray(object);
      return;
    }
    startObject();
    String oid = null;
    if (recursionGuard.containsKey(object)) {
      oid = recursionGuard.get(object);
    }
    else {
      oid = object.toString();
      recursionGuard.put(object, oid);
      Map<String, Member> members = getMembersForClass(c);
      Set<String> keySet = members.keySet();
      Iterator<String> iterator = keySet.iterator();
      String name;
      Member member;
      Field field;
      Method method;
      Object value = null;
      while (iterator.hasNext()) {
        name = iterator.next();
        if (excludeMembersList.contains(name)) continue;
        if (j++ > 0) outputStream.write(',');
        member = members.get(name);
        if (member instanceof Field) {
          field = (Field)member;
          try {
            value = field.get(object);
          } catch (Throwable fe) {
            value = fe;
          }
        }
        else
        if (member instanceof Method) {
          method = (Method)member;
          try {
            value = method.invoke(object, null);
          }
          catch (Throwable me) {
            value = me;
          }
        }
        renderObjectMember(name, value);
      }
    }
    //if (j++ > 0) outputStream.write(',');
    //renderObjectMember("_oid", oid);
    endObject();
  }

  protected void renderObject(Object object) throws Exception {
    renderObject(object, (String[])null);
  }



}
