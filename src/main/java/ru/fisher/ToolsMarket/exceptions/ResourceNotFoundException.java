package ru.fisher.ToolsMarket.exceptions;

public class ResourceNotFoundException extends RuntimeException {
  public ResourceNotFoundException(String message) {
      super(message);
  }
}
