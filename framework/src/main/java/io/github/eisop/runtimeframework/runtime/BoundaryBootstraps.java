package io.github.eisop.runtimeframework.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    MethodHandle test = safeDispatchTest(owner, originalName, safeName, originalType, invokedType);

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
    MethodHandle test = safeDispatchTest(owner, originalName, safeName, originalType, invokedType);

    return new ConstantCallSite(MethodHandles.guardWithTest(test, safe, original));
  }

  public static boolean isCheckedReceiver(Object receiver) {
    return receiver != null && CHECKED_CLASSES.get(receiver.getClass());
  }

  private static MethodHandle safeDispatchTest(
      Class<?> owner,
      String originalName,
      String safeName,
      MethodType originalType,
      MethodType invokedType)
      throws NoSuchMethodException, IllegalAccessException {
    SafeDispatchGuard guard = new SafeDispatchGuard(owner, originalName, safeName, originalType);
    MethodHandle test =
        LOOKUP
            .findVirtual(
                SafeDispatchGuard.class, "test", MethodType.methodType(boolean.class, Object.class))
            .bindTo(guard);

    test = test.asType(MethodType.methodType(boolean.class, invokedType.parameterType(0)));
    if (invokedType.parameterCount() > 1) {
      test =
          MethodHandles.dropArguments(
              test, 1, invokedType.parameterList().subList(1, invokedType.parameterCount()));
    }
    return test;
  }

  private record DispatchTarget(Class<?> owner, Method method) {}

  private static final class SafeDispatchGuard {
    private final Class<?> owner;
    private final String originalName;
    private final String safeName;
    private final MethodType originalType;
    private final ClassValue<Boolean> safeDispatchClasses =
        new ClassValue<>() {
          @Override
          protected Boolean computeValue(Class<?> receiverClass) {
            return computeSafeDispatch(receiverClass);
          }
        };

    SafeDispatchGuard(
        Class<?> owner, String originalName, String safeName, MethodType originalType) {
      this.owner = owner;
      this.originalName = originalName;
      this.safeName = safeName;
      this.originalType = originalType;
    }

    @SuppressWarnings("UnusedMethod")
    boolean test(Object receiver) {
      return receiver != null && safeDispatchClasses.get(receiver.getClass());
    }

    private boolean computeSafeDispatch(Class<?> receiverClass) {
      if (!CHECKED_CLASSES.get(receiverClass)) {
        return false;
      }

      DispatchTarget original = dispatchTarget(receiverClass, originalName);
      DispatchTarget safe = dispatchTarget(receiverClass, safeName);
      if (original == null || safe == null) {
        return false;
      }

      Method safeMethod = safe.method();
      int safeModifiers = safeMethod.getModifiers();
      return original.owner() == safe.owner()
          && safeMethod.isSynthetic()
          && !Modifier.isAbstract(safeModifiers)
          && !Modifier.isNative(safeModifiers)
          && !Modifier.isStatic(safeModifiers);
    }

    private DispatchTarget dispatchTarget(Class<?> receiverClass, String name) {
      DispatchTarget classTarget = classDispatchTarget(receiverClass, name);
      if (classTarget != null || !owner.isInterface()) {
        return classTarget;
      }
      return interfaceDefaultTarget(receiverClass, name);
    }

    private DispatchTarget classDispatchTarget(Class<?> receiverClass, String name) {
      for (Class<?> current = receiverClass; current != null; current = current.getSuperclass()) {
        Method method = declaredMethod(current, name);
        if (method != null && isInstanceDispatchMethod(method)) {
          return new DispatchTarget(current, method);
        }
      }
      return null;
    }

    private DispatchTarget interfaceDefaultTarget(Class<?> receiverClass, String name) {
      List<DispatchTarget> candidates = new ArrayList<>();
      Set<Class<?>> visited = new HashSet<>();
      for (Class<?> current = receiverClass; current != null; current = current.getSuperclass()) {
        for (Class<?> candidate : current.getInterfaces()) {
          collectInterfaceTargets(candidate, name, visited, candidates);
        }
      }
      collectInterfaceTargets(owner, name, visited, candidates);
      return selectMaximallySpecificDefault(candidates);
    }

    private void collectInterfaceTargets(
        Class<?> interfaceClass,
        String name,
        Set<Class<?>> visited,
        List<DispatchTarget> candidates) {
      if (!interfaceClass.isInterface() || !visited.add(interfaceClass)) {
        return;
      }

      Method method = declaredMethod(interfaceClass, name);
      if (method != null && isInstanceDispatchMethod(method)) {
        candidates.add(new DispatchTarget(interfaceClass, method));
      }

      for (Class<?> parent : interfaceClass.getInterfaces()) {
        collectInterfaceTargets(parent, name, visited, candidates);
      }
    }

    private DispatchTarget selectMaximallySpecificDefault(List<DispatchTarget> candidates) {
      DispatchTarget selected = null;
      for (DispatchTarget candidate : maximallySpecific(candidates)) {
        if (!candidate.method().isDefault()) {
          continue;
        }
        if (selected != null) {
          return null;
        }
        selected = candidate;
      }
      return selected;
    }

    private List<DispatchTarget> maximallySpecific(List<DispatchTarget> candidates) {
      List<DispatchTarget> maximallySpecific = new ArrayList<>();
      for (DispatchTarget candidate : candidates) {
        if (isLessSpecificThanAnotherCandidate(candidate, candidates)) {
          continue;
        }
        maximallySpecific.add(candidate);
      }
      return maximallySpecific;
    }

    private boolean isLessSpecificThanAnotherCandidate(
        DispatchTarget candidate, List<DispatchTarget> candidates) {
      for (DispatchTarget other : candidates) {
        if (!candidate.equals(other) && candidate.owner().isAssignableFrom(other.owner())) {
          return true;
        }
      }
      return false;
    }

    private Method declaredMethod(Class<?> declaringClass, String name) {
      for (Method method : declaringClass.getDeclaredMethods()) {
        if (matches(method, name)) {
          return method;
        }
      }
      return null;
    }

    private boolean matches(Method method, String name) {
      return method.getName().equals(name)
          && method.getReturnType() == originalType.returnType()
          && Arrays.equals(method.getParameterTypes(), originalType.parameterArray());
    }

    private boolean isInstanceDispatchMethod(Method method) {
      int modifiers = method.getModifiers();
      return !Modifier.isStatic(modifiers) && !Modifier.isPrivate(modifiers);
    }
  }
}
