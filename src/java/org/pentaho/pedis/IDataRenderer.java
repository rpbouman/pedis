package org.pentaho.pedis;

import java.io.OutputStream;
import java.io.IOException;

public interface IDataRenderer {
  public void render(OutputStream outputStream, Object data) throws Exception;
}
