package com.rentpilot.commute;
public class CommuteException extends RuntimeException {
 private final String code;
 public CommuteException(String code) { super(code); this.code = code; }
 public String code() { return code; }
}
