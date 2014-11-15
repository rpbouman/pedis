package org.pentaho.pedis;

import java.io.File;

import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

import javax.servlet.http.HttpServletResponse;

import org.pentaho.platform.api.engine.IPluginLifecycleListener;
import org.pentaho.platform.api.engine.PluginLifecycleException;
import org.pentaho.platform.api.engine.IPluginResourceLoader;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.api.engine.security.userroledao.IUserRoleDao;
import org.pentaho.platform.api.engine.security.userroledao.IPentahoRole;
import org.pentaho.platform.api.engine.IPentahoSession;
import org.pentaho.platform.core.mt.Tenant;

public class PedisLifecycleListener implements IPluginLifecycleListener {

  protected static PedisLifecycleListener instance = null;
  protected static String[] commands = {
    "version"
  };
  protected static Map<String,Class> contentTypes = new HashMap<String, Class>();
  protected static Set<String> privilegedRoles = new HashSet<String>();
  protected static Set<String> privilegedUsers = new HashSet<String>();
  protected static Map<String, Set<String>> privilegedTenantRoles = new HashMap<String, Set<String>>();
  protected static Map<String, Set<String>> privilegedTenantUsers = new HashMap<String, Set<String>>();

  protected static java.io.File plugindir = null;
  protected static IPluginResourceLoader resourceLoader;
  protected static String SETTINGS = "settings/";

  protected static String DEFAULT_CONTENT_TYPE_SETTINGS = SETTINGS + "default-content-type";
  protected static String defaultContentType = "application/json";

  protected static String DEBUG_ENABLED_SETTINGS = SETTINGS + "debug-enabled";
  protected static String debugEnabled = "false";

  protected static String CONTENT_TYPE_SETTINGS = SETTINGS + "content-types" + "/";

  protected static String PERMISSION_SETTINGS = SETTINGS + "permissions" + "/";
  protected static String USER_PERMISSION_SETTINGS = "users";
  protected static String ROLE_PERMISSION_SETTINGS = "roles";

  protected static String getPluginSetting(String path, String defaultValue) {
    String value = resourceLoader.getPluginSetting(PedisContentGenerator.class, path, null);
    if (value == null) {
      System.out.println("Warning: plugin setting for \"" + path + "\" not found.");
      System.out.println("Falling back to default: " + (defaultValue == null? "null" : defaultValue));
      value = defaultValue;
    }
    return value;
  }

  protected static boolean getPluginSetting(String path, boolean defaultValue) {
    return Boolean.TRUE.toString().equals(
      getPluginSetting(
        path,
        defaultValue ? Boolean.TRUE.toString() : Boolean.FALSE.toString()
      )
    );
  }
  /**
   * Called just prior to the plugin being registered
   * with the platform.  Note: This event does *not*
   * precede the detection of the plugin by any {@link IPluginProvider}s
   * @throws PluginLifecycleException if an error occurred
   */
  public void init() throws PluginLifecycleException {
    try {
      PedisLifecycleListener.instance = this;
      resourceLoader = PentahoSystem.get(IPluginResourceLoader.class, null);
      initPluginDir();
      initPermissions();
      defaultContentType = getPluginSetting(DEFAULT_CONTENT_TYPE_SETTINGS, defaultContentType);
      debugEnabled = getPluginSetting(DEBUG_ENABLED_SETTINGS, debugEnabled);
    } catch (Exception exception) {
      throw new PluginLifecycleException("An error occurred while loading the plugin.", exception);
    }
  }

  /**
   * Called after the plugin has been registered with the platform,
   * i.e. all content generators, components, etc. have been loaded.
   * @throws PluginLifecycleException if an error occurred
   */
  public void loaded() throws PluginLifecycleException {
  }

  /**
   * Called when the plugin needs to be unloaded. This
   * method should release all resources and return things
   * to a pre-loaded state.
   * @throws PluginLifecycleException if an error occurred
   */
  public void unLoaded() throws PluginLifecycleException {
    PedisLifecycleListener.instance = null;
  }

  public static PedisLifecycleListener getInstance() {
    return PedisLifecycleListener.instance;
  }

  public static String isDebugEnabled(){
    return debugEnabled;
  }

  public static String getDefaultContentType(){
    return defaultContentType;
  }

  public static File getPluginDir(){
    return plugindir;
  }

  public IDataRenderer getContentType(String contentType) throws PedisException {
    IDataRenderer dataRenderer = null;
    Class contentTypeHandlerClass = contentTypes.get(contentType);
    if (contentTypeHandlerClass == null) {
      String contentTypeTag = escapeTagName(contentType);
      String contentTypeClassName = getPluginSetting(CONTENT_TYPE_SETTINGS + contentTypeTag, null);
      if (contentTypeClassName == null) return null;
      try {
        contentTypeHandlerClass = Class.forName(contentTypeClassName);
        if (contentTypeHandlerClass.isInterface()) {
          throw new PedisException(
            contentTypeClassName + " is an interface, not a class.",
            HttpServletResponse.SC_INTERNAL_SERVER_ERROR
          );
        }
        Class iDataRenderer = IDataRenderer.class;
        if (!iDataRenderer.isAssignableFrom(contentTypeHandlerClass)) {
          throw new PedisException(
            "The specified class " + contentTypeClassName + " does not implement " + iDataRenderer.getName(),
            HttpServletResponse.SC_INTERNAL_SERVER_ERROR
          );
        }
        contentTypes.put(contentType, contentTypeHandlerClass);
      }
      catch (ClassNotFoundException classNotFoundException) {
        throw new PedisException(
          "Could not find contentTypeHandler class " + contentTypeClassName + " for requested content type (" + contentType + ")",
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          classNotFoundException
        );
      }
    }
    try {
      dataRenderer = (IDataRenderer)contentTypeHandlerClass.newInstance();
    }
    catch (Exception exception) {
      throw new PedisException(
        "Couldn't instantiate content handler of type " + contentTypeHandlerClass.getName(),
        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
        exception
      );
    }
    return dataRenderer;
  }

  protected void initPluginDir() throws URISyntaxException {
    List<URL>  list = resourceLoader.findResources(PedisContentGenerator.class, "plugin.xml");
    URL url = list.get(0);
    URI uri;
    uri = url.toURI();
    File file = new File(uri);
    plugindir = file.getParentFile();
  }

  protected Set<String> initPermissions(String key) {
    String[] list;
    String setting;
    Set<String> permissions;

    setting = getPluginSetting(PERMISSION_SETTINGS + key, null);
    if (setting == null) {
      permissions = Collections.EMPTY_SET;
    }
    else {
      permissions = new HashSet<String>();
      list = setting.split(",");
      for (String item : list) {
        permissions.add(item.trim());
      }
    }
    return permissions;
  }

  protected void initPermissions() {
    privilegedRoles = initPermissions(ROLE_PERMISSION_SETTINGS);
    privilegedUsers = initPermissions(USER_PERMISSION_SETTINGS);
  }

  protected void initTenantPermissions(String tenantId) {
    Set<String> permissions;
    permissions = initPermissions("tenants/" + tenantId + "/" + ROLE_PERMISSION_SETTINGS);
    privilegedTenantRoles.put(tenantId, permissions);
    permissions = initPermissions("tenants/" + tenantId + "/" + USER_PERMISSION_SETTINGS);
    privilegedTenantUsers.put(tenantId, permissions);
  }

  public boolean checkPermissions(IPentahoSession pentahoSession){
    String user = pentahoSession.getName();
    if (privilegedUsers.contains(user)) return true;

    IUserRoleDao userRoleDao = PentahoSystem.get(IUserRoleDao.class, "userRoleDaoProxy", pentahoSession);
    String tenantId = (String) pentahoSession.getAttribute(IPentahoSession.TENANT_ID_KEY);
    List<IPentahoRole> roles = userRoleDao.getUserRoles(new Tenant(tenantId, true), pentahoSession.getName());

    for (IPentahoRole role : roles) {
      if (privilegedRoles.contains(role.getName())) return true;
    }

    if (!privilegedTenantRoles.containsKey(tenantId)) {
      initTenantPermissions(tenantId);
    }

    Set<String> _privilegedTenantRoles = privilegedTenantRoles.get(tenantId);
    for (IPentahoRole role : roles) {
      if (_privilegedTenantRoles.contains(role.getName())) return true;
    }

    Set<String> _privilegedTenantUsers = privilegedTenantUsers.get(tenantId);

    if (_privilegedTenantUsers.contains(user)) return true;

    if (
      _privilegedTenantRoles.isEmpty() &&
      _privilegedTenantUsers.isEmpty() &&
      privilegedUsers.isEmpty() &&
      privilegedRoles.isEmpty()
    ) return true;
    return false;
  }

  public static String escapeTagName(String tagName) {
    StringBuilder escapedTagName = new StringBuilder();
    char ch;
    int n = tagName.length();
    for (int i = 0; i < n; i++) {
      ch = tagName.charAt(i);
      if (ch >= '0' && ch <= '9' || ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z'){
        escapedTagName.append(ch);
      }
      else {
        escapedTagName.append("_x");
        escapedTagName.append(Integer.toHexString((int)ch));
        escapedTagName.append("_");
      }
    }
    return escapedTagName.toString();
  }
}
