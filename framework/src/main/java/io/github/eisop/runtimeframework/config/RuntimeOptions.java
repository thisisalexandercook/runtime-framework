package io.github.eisop.runtimeframework.config;

import java.util.Objects;
import java.util.Properties;

/** Immutable runtime configuration parsed from JVM system properties. */
public record RuntimeOptions(
    String checkedClasses,
    boolean globalMode,
    boolean trustAnnotatedFor,
    String handlerClassName,
    String checkerClassName,
    boolean indyBoundaryEnabled) {

  public static final String CHECKED_CLASSES_PROPERTY = "runtime.classes";
  public static final String GLOBAL_MODE_PROPERTY = "runtime.global";
  public static final String TRUST_ANNOTATED_FOR_PROPERTY = "runtime.trustAnnotatedFor";
  public static final String HANDLER_CLASS_PROPERTY = "runtime.handler";
  public static final String CHECKER_CLASS_PROPERTY = "runtime.checker";
  public static final String INDY_BOUNDARY_PROPERTY = "runtime.indy.boundary";

  public static final String DEFAULT_CHECKED_CLASSES = "";
  public static final boolean DEFAULT_GLOBAL_MODE = false;
  public static final boolean DEFAULT_TRUST_ANNOTATED_FOR = false;
  public static final String DEFAULT_HANDLER_CLASS = "";
  public static final String DEFAULT_CHECKER_CLASS =
      "io.github.eisop.runtimeframework.checker.nullness.NullnessRuntimeChecker";
  public static final boolean DEFAULT_INDY_BOUNDARY_ENABLED = true;

  public RuntimeOptions {
    checkedClasses = Objects.requireNonNull(checkedClasses, "checkedClasses").trim();
    handlerClassName = Objects.requireNonNull(handlerClassName, "handlerClassName").trim();
    checkerClassName = Objects.requireNonNull(checkerClassName, "checkerClassName").trim();
    if (checkerClassName.isEmpty()) {
      checkerClassName = DEFAULT_CHECKER_CLASS;
    }
  }

  public static RuntimeOptions defaults() {
    return new RuntimeOptions(
        DEFAULT_CHECKED_CLASSES,
        DEFAULT_GLOBAL_MODE,
        DEFAULT_TRUST_ANNOTATED_FOR,
        DEFAULT_HANDLER_CLASS,
        DEFAULT_CHECKER_CLASS,
        DEFAULT_INDY_BOUNDARY_ENABLED);
  }

  public static RuntimeOptions fromSystemProperties() {
    return fromProperties(System.getProperties());
  }

  public static RuntimeOptions fromProperties(Properties properties) {
    Objects.requireNonNull(properties, "properties");
    return new RuntimeOptions(
        stringProperty(properties, CHECKED_CLASSES_PROPERTY, DEFAULT_CHECKED_CLASSES),
        booleanProperty(properties, GLOBAL_MODE_PROPERTY, DEFAULT_GLOBAL_MODE),
        booleanProperty(properties, TRUST_ANNOTATED_FOR_PROPERTY, DEFAULT_TRUST_ANNOTATED_FOR),
        stringProperty(properties, HANDLER_CLASS_PROPERTY, DEFAULT_HANDLER_CLASS),
        stringProperty(properties, CHECKER_CLASS_PROPERTY, DEFAULT_CHECKER_CLASS),
        booleanProperty(properties, INDY_BOUNDARY_PROPERTY, DEFAULT_INDY_BOUNDARY_ENABLED));
  }

  public boolean hasCheckedClasses() {
    return !checkedClasses.isBlank();
  }

  public boolean hasHandlerClassName() {
    return !handlerClassName.isBlank();
  }

  private static String stringProperty(Properties properties, String key, String defaultValue) {
    String value = properties.getProperty(key);
    return value == null || value.isBlank() ? defaultValue : value;
  }

  private static boolean booleanProperty(Properties properties, String key, boolean defaultValue) {
    String value = properties.getProperty(key);
    return (value == null || value.isBlank()) ? defaultValue : Boolean.parseBoolean(value);
  }
}
