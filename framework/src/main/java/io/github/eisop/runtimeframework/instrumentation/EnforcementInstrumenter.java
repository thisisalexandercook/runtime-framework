package io.github.eisop.runtimeframework.instrumentation;

import io.github.eisop.runtimeframework.config.RuntimeOptions;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.planning.BytecodeLocation;
import io.github.eisop.runtimeframework.planning.BridgePlan;
import io.github.eisop.runtimeframework.planning.ClassContext;
import io.github.eisop.runtimeframework.planning.EnforcementPlanner;
import io.github.eisop.runtimeframework.planning.InjectionPoint.Kind;
import io.github.eisop.runtimeframework.planning.InstrumentationAction;
import io.github.eisop.runtimeframework.planning.MethodPlan;
import io.github.eisop.runtimeframework.policy.ClassClassification;
import io.github.eisop.runtimeframework.policy.RuntimePolicy;
import io.github.eisop.runtimeframework.resolution.HierarchyResolver;
import io.github.eisop.runtimeframework.resolution.ParentMethod;
import io.github.eisop.runtimeframework.resolution.ResolutionEnvironment;
import io.github.eisop.runtimeframework.runtime.BoundaryBootstraps;
import io.github.eisop.runtimeframework.semantics.PropertyEmitter;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class EnforcementInstrumenter extends RuntimeInstrumenter {

  private static final ClassDesc ASSERTION_ERROR = ClassDesc.of("java.lang.AssertionError");
  private static final MethodTypeDesc ASSERTION_ERROR_STRING_CTOR =
      MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String);
  private static final String RETURN_FILTER_PREFIX = "$runtimeframework$indyReturnCheck$";

  private final EnforcementPlanner planner;
  private final HierarchyResolver hierarchyResolver;
  private final PropertyEmitter propertyEmitter;
  private final RuntimePolicy policy;
  private final ResolutionEnvironment resolutionEnvironment;
  private final RuntimeOptions options;

  public EnforcementInstrumenter(EnforcementPlanner planner, HierarchyResolver hierarchyResolver) {
    this(planner, hierarchyResolver, null);
  }

  public EnforcementInstrumenter(
      EnforcementPlanner planner,
      HierarchyResolver hierarchyResolver,
      PropertyEmitter propertyEmitter) {
    this(
        planner,
        hierarchyResolver,
        propertyEmitter,
        null,
        ResolutionEnvironment.system(),
        RuntimeOptions.fromSystemProperties());
  }

  public EnforcementInstrumenter(
      EnforcementPlanner planner,
      HierarchyResolver hierarchyResolver,
      PropertyEmitter propertyEmitter,
      RuntimePolicy policy,
      ResolutionEnvironment resolutionEnvironment) {
    this(
        planner,
        hierarchyResolver,
        propertyEmitter,
        policy,
        resolutionEnvironment,
        RuntimeOptions.fromSystemProperties());
  }

  public EnforcementInstrumenter(
      EnforcementPlanner planner,
      HierarchyResolver hierarchyResolver,
      PropertyEmitter propertyEmitter,
      RuntimePolicy policy,
      ResolutionEnvironment resolutionEnvironment,
      RuntimeOptions options) {
    this.planner = planner;
    this.hierarchyResolver = hierarchyResolver;
    this.propertyEmitter = propertyEmitter;
    this.policy = policy;
    this.resolutionEnvironment = resolutionEnvironment;
    this.options = Objects.requireNonNull(options, "options");
  }

  @Override
  protected CodeTransform createCodeTransform(
      ClassModel classModel, MethodModel methodModel, boolean isCheckedScope, ClassLoader loader) {
    return createCodeTransform(classModel, methodModel, isCheckedScope, loader, null);
  }

  private CodeTransform createCodeTransform(
      ClassModel classModel,
      MethodModel methodModel,
      boolean isCheckedScope,
      ClassLoader loader,
      EnforcementTransform.IndyReturnCheckRegistry returnCheckRegistry) {
    return new EnforcementTransform(
        planner,
        propertyEmitter,
        classModel,
        methodModel,
        isCheckedScope,
        loader,
        policy,
        resolutionEnvironment,
        options.indyBoundaryEnabled(),
        true,
        returnCheckRegistry);
  }

  @Override
  public ClassTransform asClassTransform(
      ClassModel classModel, ClassLoader loader, boolean isCheckedScope) {
    if (!options.indyBoundaryEnabled() || !isCheckedScope) {
      return super.asClassTransform(classModel, loader, isCheckedScope);
    }

    List<GeneratedReturnFilter> returnFilters = new ArrayList<>();
    EnforcementTransform.IndyReturnCheckRegistry returnCheckRegistry =
        newReturnFilterRegistry(classModel, returnFilters);

    if (isInterface(classModel)) {
      return asCheckedInterfaceTransform(
          classModel, loader, isCheckedScope, returnFilters, returnCheckRegistry);
    }

    return new ClassTransform() {
      @Override
      public void accept(ClassBuilder classBuilder, ClassElement classElement) {
        if (classElement instanceof MethodModel methodModel && methodModel.code().isPresent()) {
          if (isSplitCandidate(methodModel) && !hasSafeMethodCollision(classModel, methodModel)) {
            emitSplitMethodByKind(
                classBuilder, classModel, methodModel, loader, returnCheckRegistry);
          } else {
            transformMethod(
                classBuilder, classModel, methodModel, loader, isCheckedScope, returnCheckRegistry);
          }
        } else {
          classBuilder.with(classElement);
        }
      }

      @Override
      public void atEnd(ClassBuilder builder) {
        emitReturnFilterMethods(builder, returnFilters);
        emitCheckedClassMarker(builder, classModel);
        generateBridgeMethods(builder, classModel, loader);
      }
    };
  }

  private ClassTransform asCheckedInterfaceTransform(
      ClassModel classModel,
      ClassLoader loader,
      boolean isCheckedScope,
      List<GeneratedReturnFilter> returnFilters,
      EnforcementTransform.IndyReturnCheckRegistry returnCheckRegistry) {
    return new ClassTransform() {
      @Override
      public void accept(ClassBuilder classBuilder, ClassElement classElement) {
        if (classElement instanceof MethodModel methodModel) {
          boolean hasSafeCollision = hasSafeMethodCollision(classModel, methodModel);
          if (methodModel.code().isPresent()) {
            if (isSplitCandidate(methodModel) && !hasSafeCollision) {
              emitSplitMethodByKind(
                  classBuilder, classModel, methodModel, loader, returnCheckRegistry);
            } else {
              transformMethod(
                  classBuilder,
                  classModel,
                  methodModel,
                  loader,
                  isCheckedScope,
                  returnCheckRegistry);
            }
          } else {
            classBuilder.with(classElement);
            if (isInterfaceSafeStubCandidate(methodModel) && !hasSafeCollision) {
              emitInterfaceSafeStub(classBuilder, methodModel);
            }
          }
        } else {
          classBuilder.with(classElement);
        }
      }

      @Override
      public void atEnd(ClassBuilder builder) {
        emitReturnFilterMethods(builder, returnFilters);
      }
    };
  }

  private void transformMethod(
      ClassBuilder classBuilder,
      ClassModel classModel,
      MethodModel methodModel,
      ClassLoader loader,
      boolean isCheckedScope,
      EnforcementTransform.IndyReturnCheckRegistry returnCheckRegistry) {
    classBuilder.transformMethod(
        methodModel,
        (methodBuilder, methodElement) -> {
          if (methodElement instanceof CodeAttribute codeModel) {
            methodBuilder.transformCode(
                codeModel,
                createCodeTransform(
                    classModel, methodModel, isCheckedScope, loader, returnCheckRegistry));
          } else {
            methodBuilder.with(methodElement);
          }
        });
  }

  private void emitSplitMethodByKind(
      ClassBuilder builder,
      ClassModel classModel,
      MethodModel methodModel,
      ClassLoader loader,
      EnforcementTransform.IndyReturnCheckRegistry returnCheckRegistry) {
    if (isBridgeSplitCandidate(methodModel)) {
      emitSplitBridgeMethod(builder, classModel, methodModel, loader);
    } else {
      emitSplitMethod(builder, classModel, methodModel, loader, returnCheckRegistry);
    }
  }

  private boolean hasSafeMethodCollision(ClassModel classModel, MethodModel methodModel) {
    String safeName = safeMethodName(methodModel.methodName().stringValue());
    String descriptor = methodModel.methodType().stringValue();
    return classModel.methods().stream()
        .anyMatch(
            candidate ->
                candidate.methodName().stringValue().equals(safeName)
                    && candidate.methodType().stringValue().equals(descriptor));
  }

  private void emitSplitMethod(
      ClassBuilder builder,
      ClassModel classModel,
      MethodModel methodModel,
      ClassLoader loader,
      EnforcementTransform.IndyReturnCheckRegistry returnCheckRegistry) {
    String originalName = methodModel.methodName().stringValue();
    MethodTypeDesc desc = methodModel.methodTypeSymbol();
    int originalFlags = methodModel.flags().flagsMask();
    int safeFlags = originalFlags | AccessFlag.SYNTHETIC.mask();
    String safeName = safeMethodName(originalName);

    builder.withMethod(
        safeName,
        desc,
        safeFlags,
        safeBuilder -> {
          methodModel
              .code()
              .ifPresent(
                  codeModel ->
                      safeBuilder.transformCode(
                          codeModel,
                          new EnforcementTransform(
                              planner,
                              propertyEmitter,
                              classModel,
                              methodModel,
                              true,
                              loader,
                              policy,
                              resolutionEnvironment,
                              options.indyBoundaryEnabled(),
                              false,
                              returnCheckRegistry)));
        });

    builder.withMethod(
        originalName,
        desc,
        originalFlags,
        wrapperBuilder -> {
          for (MethodElement element : methodModel) {
            if (!(element instanceof CodeAttribute)) {
              wrapperBuilder.with(element);
            }
          }
          wrapperBuilder.withCode(
              codeBuilder -> emitWrapperBody(codeBuilder, classModel, methodModel, loader));
        });
  }

  private void emitSplitBridgeMethod(
      ClassBuilder builder, ClassModel classModel, MethodModel methodModel, ClassLoader loader) {
    String originalName = methodModel.methodName().stringValue();
    MethodTypeDesc desc = methodModel.methodTypeSymbol();
    int originalFlags = methodModel.flags().flagsMask();
    int safeFlags = originalFlags | AccessFlag.SYNTHETIC.mask();
    String safeName = safeMethodName(originalName);

    builder.withMethod(
        safeName,
        desc,
        safeFlags,
        safeBuilder -> {
          methodModel
              .code()
              .ifPresent(
                  codeModel ->
                      safeBuilder.transformCode(
                          codeModel,
                          new BridgeSafeTransform(methodModel, loader)));
        });

    builder.withMethod(
        originalName,
        desc,
        originalFlags,
        wrapperBuilder -> {
          for (MethodElement element : methodModel) {
            if (!(element instanceof CodeAttribute)) {
              wrapperBuilder.with(element);
            }
          }
          wrapperBuilder.withCode(
              codeBuilder -> emitWrapperBody(codeBuilder, classModel, methodModel, loader));
        });
  }

  private void emitWrapperBody(
      CodeBuilder builder, ClassModel classModel, MethodModel methodModel, ClassLoader loader) {
    new EnforcementTransform(
            planner,
            propertyEmitter,
            classModel,
            methodModel,
            true,
            loader,
            policy,
            resolutionEnvironment,
            options.indyBoundaryEnabled(),
            false)
        .emitParameterChecks(builder);

    boolean isStatic = Modifier.isStatic(methodModel.flags().flagsMask());
    if (!isStatic) {
      builder.aload(0);
    }

    int slotIndex = isStatic ? 0 : 1;
    for (ClassDesc parameterType : methodModel.methodTypeSymbol().parameterList()) {
      TypeKind type = TypeKind.from(parameterType);
      loadLocal(builder, type, slotIndex);
      slotIndex += type.slotSize();
    }

    ClassDesc owner = ClassDesc.ofInternalName(classModel.thisClass().asInternalName());
    String safeName = safeMethodName(methodModel.methodName().stringValue());
    if (isStatic) {
      builder.invokestatic(
          owner,
          safeName,
          methodModel.methodTypeSymbol(),
          Modifier.isInterface(classModel.flags().flagsMask()));
    } else if (isInterface(classModel)) {
      builder.invokeinterface(owner, safeName, methodModel.methodTypeSymbol());
    } else {
      builder.invokevirtual(owner, safeName, methodModel.methodTypeSymbol());
    }
    returnResult(
        builder,
        ClassDesc.ofDescriptor(methodModel.methodTypeSymbol().returnType().descriptorString()));
  }

  private void emitInterfaceSafeStub(ClassBuilder builder, MethodModel methodModel) {
    String originalName = methodModel.methodName().stringValue();
    MethodTypeDesc desc = methodModel.methodTypeSymbol();
    int stubFlags =
        (methodModel.flags().flagsMask() | AccessFlag.SYNTHETIC.mask())
            & ~AccessFlag.ABSTRACT.mask();

    builder.withMethod(
        safeMethodName(originalName),
        desc,
        stubFlags,
        methodBuilder ->
            methodBuilder.withCode(
                codeBuilder ->
                    codeBuilder
                        .new_(ASSERTION_ERROR)
                        .dup()
                        .ldc("Checked interface safe method has no checked implementation")
                        .invokespecial(
                            ASSERTION_ERROR, "<init>", ASSERTION_ERROR_STRING_CTOR)
                        .athrow()));
  }

  private EnforcementTransform.IndyReturnCheckRegistry newReturnFilterRegistry(
      ClassModel classModel, List<GeneratedReturnFilter> returnFilters) {
    ClassDesc owner = ClassDesc.ofInternalName(classModel.thisClass().asInternalName());
    return (returnType, plan, location) -> {
      MethodTypeDesc descriptor = MethodTypeDesc.of(returnType, returnType);
      String name = nextReturnFilterName(classModel, returnFilters, descriptor);
      returnFilters.add(new GeneratedReturnFilter(name, descriptor, plan, location));
      return MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, owner, name, descriptor);
    };
  }

  private String nextReturnFilterName(
      ClassModel classModel, List<GeneratedReturnFilter> returnFilters, MethodTypeDesc descriptor) {
    int index = returnFilters.size();
    while (true) {
      String candidate = RETURN_FILTER_PREFIX + index;
      boolean existsInClass =
          classModel.methods().stream()
              .anyMatch(
                  method ->
                      method.methodName().stringValue().equals(candidate)
                          && method.methodTypeSymbol()
                              .descriptorString()
                              .equals(descriptor.descriptorString()));
      boolean existsInGenerated =
          returnFilters.stream()
              .anyMatch(
                  filter ->
                      filter.name().equals(candidate)
                          && filter
                              .descriptor()
                              .descriptorString()
                              .equals(descriptor.descriptorString()));
      if (!existsInClass && !existsInGenerated) {
        return candidate;
      }
      index++;
    }
  }

  private void emitReturnFilterMethods(
      ClassBuilder builder, List<GeneratedReturnFilter> returnFilters) {
    for (GeneratedReturnFilter filter : returnFilters) {
      builder.withMethod(
          filter.name(),
          filter.descriptor(),
          AccessFlag.PRIVATE.mask() | AccessFlag.STATIC.mask() | AccessFlag.SYNTHETIC.mask(),
          methodBuilder ->
              methodBuilder.withCode(
                  codeBuilder -> {
                    if (filter.location().hasSourceLine()) {
                      codeBuilder.lineNumber(filter.location().sourceLine());
                    }
                    ClassDesc returnType = filter.descriptor().returnType();
                    loadLocal(codeBuilder, TypeKind.from(returnType), 0);
                    emitReturnFilterActions(codeBuilder, filter.plan());
                    returnResult(codeBuilder, returnType);
                  }));
    }
  }

  private void emitReturnFilterActions(CodeBuilder builder, MethodPlan plan) {
    for (InstrumentationAction action : plan.actions()) {
      switch (action) {
        case InstrumentationAction.ValueCheckAction valueCheckAction ->
            emitValueCheckAction(builder, valueCheckAction);
        case InstrumentationAction.LifecycleHookAction ignored ->
            throw new IllegalStateException("LifecycleHookAction emission is not implemented yet");
      }
    }
  }

  private void emitCheckedClassMarker(ClassBuilder builder, ClassModel classModel) {
    boolean markerExists =
        classModel.fields().stream()
            .anyMatch(
                field ->
                    field
                        .fieldName()
                        .stringValue()
                        .equals(BoundaryBootstraps.CHECKED_CLASS_MARKER));
    if (!markerExists) {
      builder.withField(
          BoundaryBootstraps.CHECKED_CLASS_MARKER,
          ClassDesc.ofDescriptor("Z"),
          AccessFlag.PUBLIC.mask() | AccessFlag.STATIC.mask() | AccessFlag.SYNTHETIC.mask());
    }
  }

  static boolean isSplitCandidate(MethodModel method) {
    return isRegularSplitCandidate(method) || isBridgeSplitCandidate(method);
  }

  private static boolean isRegularSplitCandidate(MethodModel method) {
    String methodName = method.methodName().stringValue();
    int flags = method.flags().flagsMask();
    return method.code().isPresent()
        && !methodName.equals("<init>")
        && !methodName.equals("<clinit>")
        && !methodName.contains("$runtimeframework$safe")
        && !Modifier.isPrivate(flags)
        && !Modifier.isNative(flags)
        && !Modifier.isAbstract(flags)
        && (flags & AccessFlag.BRIDGE.mask()) == 0
        && (flags & AccessFlag.SYNTHETIC.mask()) == 0;
  }

  private static boolean isBridgeSplitCandidate(MethodModel method) {
    String methodName = method.methodName().stringValue();
    int flags = method.flags().flagsMask();
    return method.code().isPresent()
        && !methodName.equals("<init>")
        && !methodName.equals("<clinit>")
        && !methodName.contains("$runtimeframework$safe")
        && !Modifier.isPrivate(flags)
        && !Modifier.isStatic(flags)
        && !Modifier.isNative(flags)
        && !Modifier.isAbstract(flags)
        && (flags & AccessFlag.BRIDGE.mask()) != 0
        && (flags & AccessFlag.SYNTHETIC.mask()) != 0;
  }

  static boolean isInterfaceSafeStubCandidate(MethodModel method) {
    String methodName = method.methodName().stringValue();
    int flags = method.flags().flagsMask();
    return method.code().isEmpty()
        && !methodName.equals("<init>")
        && !methodName.equals("<clinit>")
        && !methodName.contains("$runtimeframework$safe")
        && Modifier.isPublic(flags)
        && Modifier.isAbstract(flags)
        && !Modifier.isStatic(flags)
        && !Modifier.isPrivate(flags)
        && (flags & AccessFlag.BRIDGE.mask()) == 0
        && (flags & AccessFlag.SYNTHETIC.mask()) == 0;
  }

  static String safeMethodName(String methodName) {
    return methodName + "$runtimeframework$safe";
  }

  private static boolean isInterface(ClassModel classModel) {
    return Modifier.isInterface(classModel.flags().flagsMask());
  }

  private record GeneratedReturnFilter(
      String name, MethodTypeDesc descriptor, MethodPlan plan, BytecodeLocation location) {}

  private final class BridgeSafeTransform implements CodeTransform {
    private final MethodModel bridgeMethod;
    private final ClassLoader loader;

    BridgeSafeTransform(MethodModel bridgeMethod, ClassLoader loader) {
      this.bridgeMethod = bridgeMethod;
      this.loader = loader;
    }

    @Override
    public void accept(CodeBuilder builder, CodeElement element) {
      if (element instanceof InvokeInstruction invoke && maybeEmitSafeForward(builder, invoke)) {
        return;
      }
      builder.with(element);
    }

    private boolean maybeEmitSafeForward(CodeBuilder builder, InvokeInstruction invoke) {
      Opcode opcode = invoke.opcode();
      if (opcode != Opcode.INVOKEVIRTUAL
          && opcode != Opcode.INVOKEINTERFACE
          && opcode != Opcode.INVOKESPECIAL
          && opcode != Opcode.INVOKESTATIC) {
        return false;
      }

      String methodName = invoke.name().stringValue();
      if (!methodName.equals(bridgeMethod.methodName().stringValue())
          || methodName.contains("$runtimeframework$safe")
          || invoke
              .typeSymbol()
              .descriptorString()
              .equals(bridgeMethod.methodTypeSymbol().descriptorString())) {
        return false;
      }

      String ownerInternalName = invoke.owner().asInternalName();
      if (policy == null
          || !policy.isChecked(new ClassInfo(ownerInternalName, loader, null))
          || !hasSafeForwardTarget(ownerInternalName, methodName, invoke.typeSymbol(), opcode)) {
        return false;
      }

      ClassDesc owner = ClassDesc.ofInternalName(ownerInternalName);
      String safeName = safeMethodName(methodName);
      if (opcode == Opcode.INVOKESTATIC) {
        builder.invokestatic(owner, safeName, invoke.typeSymbol(), invoke.isInterface());
      } else if (opcode == Opcode.INVOKEINTERFACE) {
        builder.invokeinterface(owner, safeName, invoke.typeSymbol());
      } else if (opcode == Opcode.INVOKESPECIAL) {
        builder.invokespecial(owner, safeName, invoke.typeSymbol());
      } else {
        builder.invokevirtual(owner, safeName, invoke.typeSymbol());
      }
      return true;
    }

    private boolean hasSafeForwardTarget(
        String ownerInternalName, String methodName, MethodTypeDesc descriptor, Opcode opcode) {
      return resolveSafeForwardTarget(ownerInternalName, methodName, descriptor, opcode)
          .filter(
              method ->
                  policy.isChecked(
                      new ClassInfo(method.ownerInternalName(), loader, null),
                      method.ownerModel()))
          .filter(method -> EnforcementInstrumenter.isSplitCandidate(method.method()))
          .filter(method -> methodMatchesOpcode(method.method(), opcode))
          .isPresent();
    }

    private Optional<ResolutionEnvironment.ResolvedMethod> resolveSafeForwardTarget(
        String ownerInternalName, String methodName, MethodTypeDesc descriptor, Opcode opcode) {
      return switch (opcode) {
        case INVOKEVIRTUAL ->
            resolutionEnvironment.findResolvedVirtualMethod(
                ownerInternalName, methodName, descriptor.descriptorString(), loader);
        case INVOKEINTERFACE ->
            resolutionEnvironment.findResolvedInterfaceMethod(
                ownerInternalName, methodName, descriptor.descriptorString(), loader);
        case INVOKESTATIC, INVOKESPECIAL ->
            resolutionEnvironment
                .loadClass(ownerInternalName, loader)
                .flatMap(
                    model ->
                        resolutionEnvironment
                            .findDeclaredMethod(
                                ownerInternalName,
                                methodName,
                                descriptor.descriptorString(),
                                loader)
                            .map(
                                method ->
                                    new ResolutionEnvironment.ResolvedMethod(
                                        ownerInternalName, model, method)));
        default -> Optional.empty();
      };
    }

    private boolean methodMatchesOpcode(MethodModel method, Opcode opcode) {
      boolean methodIsStatic = Modifier.isStatic(method.flags().flagsMask());
      return (opcode == Opcode.INVOKESTATIC) == methodIsStatic;
    }
  }

  @Override
  protected void generateBridgeMethods(ClassBuilder builder, ClassModel model, ClassLoader loader) {
    ClassContext classContext =
        new ClassContext(
            new ClassInfo(model.thisClass().asInternalName(), loader, null),
            model,
            ClassClassification.CHECKED);
    for (ParentMethod parentMethod : hierarchyResolver.resolveUncheckedMethods(model, loader)) {
      if (planner.shouldGenerateBridge(classContext, parentMethod)) {
        emitBridge(builder, planner.planBridge(classContext, parentMethod));
      }
    }
  }

  private void emitBridge(ClassBuilder builder, BridgePlan plan) {
    ParentMethod parentMethod = plan.parentMethod();
    MethodModel method = parentMethod.method();
    String methodName = method.methodName().stringValue();
    MethodTypeDesc desc = method.methodTypeSymbol();

    builder.withMethod(
        methodName,
        desc,
        Modifier.PUBLIC,
        methodBuilder -> {
          methodBuilder.withCode(
              codeBuilder -> {
                List<ClassDesc> paramTypes = desc.parameterList();

                emitBridgeActions(codeBuilder, plan, BridgeActionTiming.ENTRY);

                codeBuilder.aload(0);
                int slotIndex = 1;
                for (ClassDesc pType : paramTypes) {
                  TypeKind type = TypeKind.from(pType);
                  loadLocal(codeBuilder, type, slotIndex);
                  slotIndex += type.slotSize();
                }

                ClassDesc parentDesc =
                    ClassDesc.of(
                        parentMethod.owner().thisClass().asInternalName().replace('/', '.'));
                codeBuilder.invokespecial(parentDesc, methodName, desc);

                emitBridgeActions(codeBuilder, plan, BridgeActionTiming.EXIT);

                returnResult(
                    codeBuilder, ClassDesc.ofDescriptor(desc.returnType().descriptorString()));
              });
        });
  }

  private void emitBridgeActions(CodeBuilder builder, BridgePlan plan, BridgeActionTiming timing) {
    for (InstrumentationAction action : plan.actions()) {
      if (timing.matches(action)) {
        emitBridgeAction(builder, action);
      }
    }
  }

  private void emitBridgeAction(CodeBuilder builder, InstrumentationAction action) {
    switch (action) {
      case InstrumentationAction.ValueCheckAction valueCheckAction ->
          emitValueCheckAction(builder, valueCheckAction);
      case InstrumentationAction.LifecycleHookAction ignored ->
          throw new IllegalStateException("LifecycleHookAction emission is not implemented yet");
    }
  }

  private void emitValueCheckAction(
      CodeBuilder builder, InstrumentationAction.ValueCheckAction action) {
    if (propertyEmitter == null) {
      throw new IllegalStateException("ValueCheckAction emission requires a property emitter");
    }
    for (var requirement : action.contract().requirements()) {
      propertyEmitter.emitCheck(
          builder, requirement, action.valueAccess(), action.attribution(), action.diagnostic());
    }
  }

  private void loadLocal(CodeBuilder b, TypeKind type, int slot) {
    switch (type) {
      case INT, BYTE, CHAR, SHORT, BOOLEAN -> b.iload(slot);
      case LONG -> b.lload(slot);
      case FLOAT -> b.fload(slot);
      case DOUBLE -> b.dload(slot);
      case REFERENCE -> b.aload(slot);
      default -> throw new IllegalArgumentException("Unknown type");
    }
  }

  private void returnResult(CodeBuilder b, ClassDesc returnType) {
    String desc = returnType.descriptorString();
    if (desc.equals("V")) b.return_();
    else if (desc.equals("I")
        || desc.equals("Z")
        || desc.equals("B")
        || desc.equals("S")
        || desc.equals("C")) b.ireturn();
    else if (desc.equals("J")) b.lreturn();
    else if (desc.equals("F")) b.freturn();
    else if (desc.equals("D")) b.dreturn();
    else b.areturn();
  }

  private enum BridgeActionTiming {
    ENTRY,
    EXIT;

    private boolean matches(InstrumentationAction action) {
      return switch (this) {
        case ENTRY -> action.injectionPoint().kind() == Kind.BRIDGE_ENTRY;
        case EXIT -> action.injectionPoint().kind() == Kind.BRIDGE_EXIT;
      };
    }
  }
}
