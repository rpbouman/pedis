(function(window) {

var _createXhr;
if (window.XMLHttpRequest) _createXhr = function(){
    return new window.XMLHttpRequest();
}
else
if (window.ActiveXObject) _createXhr = function(){
    return new window.ActiveXObject("MSXML2.XMLHTTP.3.0");
}

var Pedis;

Pedis = function(options) {
  this.options = options || {
    url: "/pentaho/content/pedis"
  };
};

Pedis.PARAM_SCHEMA = "schema";
Pedis.PARAM_CATALOG = "catalog";
Pedis.PARAM_TABLE = "table";
Pedis.PARAM_FUNCTION = "function";
Pedis.PARAM_TABLE_TYPES = "types";
Pedis.PARAM_COLUMN = "column";
Pedis.PARAM_UNIQUE = "unique";
Pedis.PARAM_APPROXIMATE = "approximate";
Pedis.PARAM_PARENT_SCHEMA = "parentSchema";
Pedis.PARAM_PARENT_CATALOG = "parentCatalog";
Pedis.PARAM_PARENT_TABLE = "parentTable";
Pedis.PARAM_FOREIGN_SCHEMA = "foreignSchema";
Pedis.PARAM_FOREIGN_CATALOG = "foreignCatalog";
Pedis.PARAM_FOREIGN_TABLE = "foreignTable";

Pedis.prototype = {
  request: function(options) {
    var me = this,
        xhr = _createXhr(),
        url = me.options.url
    ;
    url += "/" + options.path.join("/");
    var query = "", params = options.params, name, value, i, n;
    for (name in params) {
      if ((value = params[name]) === null) continue;
      if (value) {
        if (value.constructor !== Array) value = [value];
        n = value.length;
        for (i = 0; i < n; i++) {
          if (query.length) query += "&";
          query += name + "=" + encodeURIComponent(value[i]);
        }
      }
    }
    if (query.length) url += "?" + query;
    xhr.open("GET", url, true);
    xhr.setRequestHeader("Accept", "application/json");
    xhr.onreadystatechange = function() {
      var scope = options.scope ? options.scope : window;
      switch (xhr.readyState) {
        case 0:
          if (typeof(options.aborted) === "function") {
            options.aborted.call(scope, me, options);
          }
          break;
        case 4:
          var errorText = null;
          switch (xhr.status) {
            case 200:
              if (typeof(options.success) === "function") {
                var data;
                try {
                  data = JSON.parse(xhr.responseText);
                  options.success.call(scope, me, options, data);
                  break;
                }
                catch (e) {
                  errorText = e.toString();
                }
              }
              break;
            default:
              if (typeof(options.failure) === "function") {
                options.failure.call(
                  scope, me, options, status,
                  errorText ? errorText : xhr.statusText
                );
              }
              break;
          }
          break;
      }
    };
    xhr.send(null);
  },
  requestVersion: function(options) {
    options.path = ["version"];
    this.request(options);
  },
  requestConnections: function(options) {
    options.path = ["connections"];
    this.request(options);
  },
  requestConnection: function(options, connection) {
    connection = connection || options.connection;
    options.path = ["connections", connection];
    this.request(options);
  },
  requestCatalogs: function(options, connection) {
    connection = connection || options.connection;
    options.path = ["connections", connection, "catalogs"];
    this.request(options);
  },
  requestSchemas: function(options, connection) {
    connection = connection || options.connection;
    options.path = ["connections", connection, "schemas"];
    this.request(options);
  },
  requestTables: function(options, connection) {
    connection = connection || options.connection;
    options.path = ["connections", connection, "tables"];
    this.request(options);
  },
  requestColumns: function(options, connection) {
    connection = connection || options.connection;
    options.path = ["connections", connection, "columns"];
    this.request(options);
  },
  requestTableInfo: function(options, connection) {
    connection = connection || options.connection;
    options.path = ["connections", connection, "tableInfo"];
    this.request(options);
  },
  requestQuery: function(options, connection) {
    connection = connection || options.connection;
    options.path = ["connections", connection, "query"];
    this.request(options);
  },
  sortResultSet: function(){
    var resultSet = arguments[0], keys = [], key;
    var i, n = arguments.length, argument, j, m;
    for (i = i; i < n; i++) {
      argument = arguments[i];
      if (iArr(argument)) {
        m = argument.length;
        for (j = 0; j < m; j++){
          key = argument[j];
          if (iStr(key)) keys.push(key);
          else
          throw "Key(" + i + "," + j + ") must be a String";
        }
      }
      else
      if (iStr(argument)) keys.push(argument);
      else throw "Key (" + i + ") must be a String";
    }
    n = keys.length;
    return resultSet.sort(function(a, b){
      var A, B;
      for (i = 0; i < n; i++) {
        key = keys[i];
        A = a[key.toUpperCase()] || a[key.toLowerCase()];
        B = b[key.toUpperCase()] || b[key.toLowerCase()];
        if (A < B) return -1;
        else
        if (A > B) return 1;
      }
      return 0;
    });
  }
};

if (typeof(define)==="function" && define.amd) {
  define(function (){
      return Pedis;
  });
}
else window.Pedis = Pedis;

return Pedis;
})(typeof exports === "undefined" ? window : exports);
