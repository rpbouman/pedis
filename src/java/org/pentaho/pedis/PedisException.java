package org.pentaho.pedis;

import java.io.IOException;
import javax.servlet.http.HttpServletResponse;

import org.pentaho.platform.api.engine.IParameterProvider;
import org.pentaho.platform.api.engine.IContentGenerator;

class PedisException extends Exception {
  protected int code;

  PedisException(String message, int code) {
    super(message);
    setCode(code);
  }

  PedisException(String message, int code, Throwable cause) {
    super(message, cause);
    setCode(code);
  }

  public void setCode(int code) {
    this.code = code;
  }

  public int getCode() {
    return code;
  }

  public String getMessage() {
    String message = super.getMessage();
    Throwable cause = getCause();
    if (cause != null) {
      message += " " + cause.getMessage();
    }
    return message;
  }

  public void sendError(HttpServletResponse response) throws IOException {
    response.sendError(getCode(), getMessage());
  }
}
