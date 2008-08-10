package com.boostfield.hbj.portal.exception;

public class BadCaptchaException extends RuntimeException {

  public BadCaptchaException(String message) {
    super(message);
  }

  public BadCaptchaException(String message, Throwable cause) {
    super(message, cause);
  }
}
