package io.github.eisop.testutils;

public record ExpectedError(long lineNumber, String expectedMessage) {
  @Override
  public String toString() {
    return "Line " + lineNumber + ": " + expectedMessage;
  }
}
