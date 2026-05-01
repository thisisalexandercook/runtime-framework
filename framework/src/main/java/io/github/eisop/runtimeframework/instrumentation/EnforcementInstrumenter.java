package io.github.eisop.runtimeframework.instrumentation;

import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.planning.BridgePlan;
import io.github.eisop.runtimeframework.planning.ClassContext;
import io.github.eisop.runtimeframework.planning.EnforcementPlanner;
import io.github.eisop.runtimeframework.planning.InstrumentationAction;
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
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Modifier;
import java.util.List;

public class EnforcementInstrumenter extends RuntimeInstrumenter {

  private final EnforcementPlanner planner;
  private final HierarchyResolver hierarchyResolver;
  private final PropertyEmitter propertyEmitter;
  private final RuntimePolicy policy;
  private final ResolutionEnvironment resolutionEnvironment;
  private final boolean enableIndyBoundary;

  public EnforcementInstrumenter(EnforcementPlanner planner, HierarchyResolver hierarchyResolver) {
    this(planner, hierarchyResolver, null);
  }

  public EnforcementInstrumenter(
      EnforcementPlanner planner,
      HierarchyResolver hierarchyResolver,
      PropertyEmitter propertyEmitter) {
    this(planner, hierarchyResolver, propertyEmitter, null, ResolutionEnvironment.system());
  }

  public EnforcementInstrumenter(
      EnforcementPlanner planner,
      HierarchyResolver hierarchyResolver,
      PropertyEmitter propertyEmitter,
      RuntimePolicy policy,
      ResolutionEnvironment resolutionEnvironment) {
    this.planner = planner;
    this.hierarchyResolver = hierarchyResolver;
    this.propertyEmitter = propertyEmitter;
    this.policy = policy;
    this.resolutionEnvironment = resolutionEnvironment;
    this.enableIndyBoundary = Boolean.getBoolean("runtime.indy.boundary");
  }

  @Override
  protected CodeTransform createCodeTransform(
      ClassModel classModel, MethodModel methodModel, boolean isCheckedScope, ClassLoader loader) {
    return new EnforcementTransform(
        planner,
        propertyEmitter,
        classModel,
        methodModel,
        isCheckedScope,
        loader,
        policy,
        resolutionEnvironment,
        enableIndyBoundary);
  }

  @Override
  public ClassTransform asClassTransform(
      ClassModel classModel, ClassLoader loader, boolean isCheckedScope) {
    if (!enableIndyBoundary || !isCheckedScope) {
      return super.asClassTransform(classModel, loader, isCheckedScope);
    }

    return new ClassTransform() {
      @Override
      public void accept(ClassBuilder classBuilder, ClassElement classElement) {
        if (classElement instanceof MethodModel methodModel && methodModel.code().isPresent()) {
          if (isSplitCandidate(methodModel) && !hasSafeMethodCollision(classModel, methodModel)) {
            emitSplitMethod(classBuilder, classModel, methodModel, loader);
          } else {
            classBuilder.transformMethod(
                methodModel,
                (methodBuilder, methodElement) -> {
                  if (methodElement instanceof CodeAttribute codeModel) {
                    methodBuilder.transformCode(
                        codeModel,
                        createCodeTransform(classModel, methodModel, isCheckedScope, loader));
                  } else {
                    methodBuilder.with(methodElement);
                  }
                });
          }
        } else {
          classBuilder.with(classElement);
        }
      }

      @Override
      public void atEnd(ClassBuilder builder) {
        emitCheckedClassMarker(builder, classModel);
        generateBridgeMethods(builder, classModel, loader);
      }
    };
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
                          new EnforcementTransform(
                              planner,
                              propertyEmitter,
                              classModel,
                              methodModel,
                              true,
                              loader,
                              policy,
                              resolutionEnvironment,
                              enableIndyBoundary,
                              false)));
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
            enableIndyBoundary,
            false)
        .emitParameterChecks(builder);

    builder.aload(0);
    int slotIndex = 1;
    for (ClassDesc parameterType : methodModel.methodTypeSymbol().parameterList()) {
      TypeKind type = TypeKind.from(parameterType);
      loadLocal(builder, type, slotIndex);
      slotIndex += type.slotSize();
    }

    ClassDesc owner = ClassDesc.ofInternalName(classModel.thisClass().asInternalName());
    builder.invokevirtual(
        owner,
        safeMethodName(methodModel.methodName().stringValue()),
        methodModel.methodTypeSymbol());
    returnResult(
        builder,
        ClassDesc.ofDescriptor(methodModel.methodTypeSymbol().returnType().descriptorString()));
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
    String methodName = method.methodName().stringValue();
    int flags = method.flags().flagsMask();
    return method.code().isPresent()
        && !methodName.equals("<init>")
        && !methodName.equals("<clinit>")
        && !methodName.contains("$runtimeframework$safe")
        && Modifier.isPublic(flags)
        && !Modifier.isStatic(flags)
        && !Modifier.isPrivate(flags)
        && !Modifier.isFinal(flags)
        && !Modifier.isSynchronized(flags)
        && !Modifier.isNative(flags)
        && !Modifier.isAbstract(flags)
        && (flags & AccessFlag.BRIDGE.mask()) == 0
        && (flags & AccessFlag.SYNTHETIC.mask()) == 0;
  }

  static String safeMethodName(String methodName) {
    return methodName + "$runtimeframework$safe";
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
        case ENTRY ->
            action.injectionPoint().kind()
                == io.github.eisop.runtimeframework.planning.InjectionPoint.Kind.BRIDGE_ENTRY;
        case EXIT ->
            action.injectionPoint().kind()
                == io.github.eisop.runtimeframework.planning.InjectionPoint.Kind.BRIDGE_EXIT;
      };
    }
  }
}
