package io.github.eisop.testutils;

public record ExpectedError(String filename, long lineNumber, String expectedMessage) {
  @Override
  public String toString() {
    return filename + ":" + lineNumber + ": " + expectedMessage;
  }
}
