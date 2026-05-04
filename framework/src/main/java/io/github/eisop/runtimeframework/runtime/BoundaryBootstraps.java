package io.github.eisop.runtimeframework.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Bootstrap methods used by invokedynamic. */
public final class BoundaryBootstraps {

  public static final String CHECKED_CLASS_MARKER = "$runtimeframework$checked";

  private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

  private static final ClassValue<Boolean> CHECKED_CLASSES =
      new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
          try {
            Field marker = type.getDeclaredField(CHECKED_CLASS_MARKER);
            int modifiers = marker.getModifiers();
            return marker.getType() == boolean.class && Modifier.isStatic(modifiers);
          } catch (NoSuchFieldException ignored) {
            return false;
          }
        }
      };

  private BoundaryBootstraps() {}

  public static CallSite checkedVirtual(
      MethodHandles.Lookup callerLookup,
      String invokedName,
      MethodType invokedType,
      Class<?> owner,
      String originalName,
      String safeName,
      MethodType originalType)
      throws NoSuchMethodException, IllegalAccessException {
    MethodHandle safe = callerLookup.findVirtual(owner, safeName, originalType).asType(invokedType);
    MethodHandle original =
        callerLookup.findVirtual(owner, originalName, originalType).asType(invokedType);
    MethodHandle test =
        LOOKUP.findStatic(
            BoundaryBootstraps.class,
            "isCheckedReceiver",
            MethodType.methodType(boolean.class, Object.class));

    test = test.asType(MethodType.methodType(boolean.class, invokedType.parameterType(0)));
    if (invokedType.parameterCount() > 1) {
      test =
          MethodHandles.dropArguments(
              test, 1, invokedType.parameterList().subList(1, invokedType.parameterCount()));
    }

    return new ConstantCallSite(MethodHandles.guardWithTest(test, safe, original));
  }

  public static CallSite checkedVirtualWithFallbackReturnCheck(
      MethodHandles.Lookup callerLookup,
      String invokedName,
      MethodType invokedType,
      Class<?> owner,
      String originalName,
      String safeName,
      MethodType originalType,
      MethodHandle fallbackReturnFilter)
      throws NoSuchMethodException, IllegalAccessException {
    MethodHandle safe = callerLookup.findVirtual(owner, safeName, originalType).asType(invokedType);
    MethodHandle original = callerLookup.findVirtual(owner, originalName, originalType);
    original = MethodHandles.filterReturnValue(original, fallbackReturnFilter).asType(invokedType);
    MethodHandle test =
        LOOKUP.findStatic(
            BoundaryBootstraps.class,
            "isCheckedReceiver",
            MethodType.methodType(boolean.class, Object.class));

    test = test.asType(MethodType.methodType(boolean.class, invokedType.parameterType(0)));
    if (invokedType.parameterCount() > 1) {
      test =
          MethodHandles.dropArguments(
              test, 1, invokedType.parameterList().subList(1, invokedType.parameterCount()));
    }

    return new ConstantCallSite(MethodHandles.guardWithTest(test, safe, original));
  }

  public static boolean isCheckedReceiver(Object receiver) {
    return receiver != null && CHECKED_CLASSES.get(receiver.getClass());
  }
}
