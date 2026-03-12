package io.github.eisop.runtimeframework.planning;

import java.lang.classfile.MethodModel;
import java.lang.constant.MethodTypeDesc;
import java.util.Objects;

/** Identifies the target contract to resolve for a particular flow. */
public sealed interface TargetRef
    permits TargetRef.MethodParameter,
        TargetRef.MethodReturn,
        TargetRef.InvokedMethod,
        TargetRef.Field,
        TargetRef.ArrayComponent,
        TargetRef.Local,
        TargetRef.Receiver {

  record MethodParameter(String ownerInternalName, MethodModel method, int parameterIndex)
      implements TargetRef {
    public MethodParameter {
      Objects.requireNonNull(ownerInternalName, "ownerInternalName");
      Objects.requireNonNull(method, "method");
    }
  }

  record MethodReturn(String ownerInternalName, MethodModel method) implements TargetRef {
    public MethodReturn {
      Objects.requireNonNull(ownerInternalName, "ownerInternalName");
      Objects.requireNonNull(method, "method");
    }
  }

  record InvokedMethod(String ownerInternalName, String methodName, MethodTypeDesc descriptor)
      implements TargetRef {
    public InvokedMethod {
      Objects.requireNonNull(ownerInternalName, "ownerInternalName");
      Objects.requireNonNull(methodName, "methodName");
      Objects.requireNonNull(descriptor, "descriptor");
    }
  }

  record Field(String ownerInternalName, String fieldName, String descriptor) implements TargetRef {
    public Field {
      Objects.requireNonNull(ownerInternalName, "ownerInternalName");
      Objects.requireNonNull(fieldName, "fieldName");
      Objects.requireNonNull(descriptor, "descriptor");
    }
  }

  record ArrayComponent(String arrayDescriptor, TargetRef arrayTarget) implements TargetRef {
    public ArrayComponent {
      Objects.requireNonNull(arrayDescriptor, "arrayDescriptor");
    }
  }

  record Local(MethodModel method, int slot, int bytecodeIndex) implements TargetRef {
    public Local {
      Objects.requireNonNull(method, "method");
    }
  }

  record Receiver(String ownerInternalName, MethodModel method) implements TargetRef {
    public Receiver {
      Objects.requireNonNull(ownerInternalName, "ownerInternalName");
      Objects.requireNonNull(method, "method");
    }
  }
}
