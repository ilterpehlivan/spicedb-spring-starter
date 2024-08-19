package org.ilt.fga;

public class FgaAuthorizationException extends RuntimeException {
  public FgaAuthorizationException(String message) {
    super(message);
  }

  public FgaAuthorizationException(String message, Exception e) {
    super(message, e);
  }
}
