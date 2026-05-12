package io.github.eisop.runtimeframework.instrumentation;

import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.planning.BytecodeLocation;
import io.github.eisop.runtimeframework.planning.ClassContext;
import io.github.eisop.runtimeframework.planning.EnforcementPlanner;
import io.github.eisop.runtimeframework.planning.FlowEvent;
import io.github.eisop.runtimeframework.planning.InstrumentationAction;
import io.github.eisop.runtimeframework.planning.MethodContext;
import io.github.eisop.runtimeframework.planning.MethodPlan;
import io.github.eisop.runtimeframework.planning.TargetRef;
import io.github.eisop.runtimeframework.policy.ClassClassification;
import io.github.eisop.runtimeframework.policy.RuntimePolicy;
import io.github.eisop.runtimeframework.resolution.ParentMethod;
import io.github.eisop.runtimeframework.resolution.ResolutionEnvironment;
import io.github.eisop.runtimeframework.runtime.BoundaryBootstraps;
import io.github.eisop.runtimeframework.semantics.PropertyEmitter;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A CodeTransform that injects runtime checks based on an {@link EnforcementPlanner}. */
public class EnforcementTransform implements CodeTransform {

  private final EnforcementPlanner planner;
  private final PropertyEmitter propertyEmitter;
  private final MethodContext methodContext;
  private final boolean isCheckedScope;
  private final RuntimePolicy policy;
  private final ResolutionEnvironment resolutionEnvironment;
  private final boolean enableIndyBoundary;
  private final boolean emitEntryChecks;
  private final IndyReturnCheckRegistry returnCheckRegistry;
  private final ReferenceValueTracker valueTracker;
  private boolean entryChecksEmitted;
  private int currentBytecodeOffset;
  private int currentSourceLine;
  private static final ClassDesc BOUNDARY_BOOTSTRAPS =
      ClassDesc.of(BoundaryBootstraps.class.getName());
  private static final DirectMethodHandleDesc CHECKED_VIRTUAL_BOOTSTRAP =
      MethodHandleDesc.ofMethod(
          DirectMethodHandleDesc.Kind.STATIC,
          BOUNDARY_BOOTSTRAPS,
          "checkedVirtual",
          MethodTypeDesc.of(
              ConstantDescs.CD_CallSite,
              ConstantDescs.CD_MethodHandles_Lookup,
              ConstantDescs.CD_String,
              ConstantDescs.CD_MethodType,
              ConstantDescs.CD_Class,
              ConstantDescs.CD_String,
              ConstantDescs.CD_String,
              ConstantDescs.CD_MethodType));
  private static final DirectMethodHandleDesc CHECKED_VIRTUAL_WITH_FALLBACK_RETURN_CHECK_BOOTSTRAP =
      MethodHandleDesc.ofMethod(
          DirectMethodHandleDesc.Kind.STATIC,
          BOUNDARY_BOOTSTRAPS,
          "checkedVirtualWithFallbackReturnCheck",
          MethodTypeDesc.of(
              ConstantDescs.CD_CallSite,
              ConstantDescs.CD_MethodHandles_Lookup,
              ConstantDescs.CD_String,
              ConstantDescs.CD_MethodType,
              ConstantDescs.CD_Class,
              ConstantDescs.CD_String,
              ConstantDescs.CD_String,
              ConstantDescs.CD_MethodType,
              ConstantDescs.CD_MethodHandle));

  public EnforcementTransform(
      EnforcementPlanner planner,
      PropertyEmitter propertyEmitter,
      ClassModel classModel,
      MethodModel methodModel,
      boolean isCheckedScope,
      ClassLoader loader) {
    this(
        planner,
        propertyEmitter,
        classModel,
        methodModel,
        isCheckedScope,
        loader,
        null,
        ResolutionEnvironment.system(),
        false);
  }

  public EnforcementTransform(
      EnforcementPlanner planner,
      PropertyEmitter propertyEmitter,
      ClassModel classModel,
      MethodModel methodModel,
      boolean isCheckedScope,
      ClassLoader loader,
      RuntimePolicy policy,
      ResolutionEnvironment resolutionEnvironment,
      boolean enableIndyBoundary) {
    this(
        planner,
        propertyEmitter,
        classModel,
        methodModel,
        isCheckedScope,
        loader,
        policy,
        resolutionEnvironment,
        enableIndyBoundary,
        true,
        null);
  }

  public EnforcementTransform(
      EnforcementPlanner planner,
      PropertyEmitter propertyEmitter,
      ClassModel classModel,
      MethodModel methodModel,
      boolean isCheckedScope,
      ClassLoader loader,
      RuntimePolicy policy,
      ResolutionEnvironment resolutionEnvironment,
      boolean enableIndyBoundary,
      boolean emitEntryChecks) {
    this(
        planner,
        propertyEmitter,
        classModel,
        methodModel,
        isCheckedScope,
        loader,
        policy,
        resolutionEnvironment,
        enableIndyBoundary,
        emitEntryChecks,
        null);
  }

  public EnforcementTransform(
      EnforcementPlanner planner,
      PropertyEmitter propertyEmitter,
      ClassModel classModel,
      MethodModel methodModel,
      boolean isCheckedScope,
      ClassLoader loader,
      RuntimePolicy policy,
      ResolutionEnvironment resolutionEnvironment,
      boolean enableIndyBoundary,
      boolean emitEntryChecks,
      IndyReturnCheckRegistry returnCheckRegistry) {
    this.planner = planner;
    this.propertyEmitter = propertyEmitter;
    ClassContext classContext =
        new ClassContext(
            new ClassInfo(classModel.thisClass().asInternalName(), loader, null),
            classModel,
            isCheckedScope ? ClassClassification.CHECKED : ClassClassification.UNCHECKED);
    this.methodContext = new MethodContext(classContext, methodModel);
    this.isCheckedScope = isCheckedScope;
    this.policy = policy;
    this.resolutionEnvironment = resolutionEnvironment;
    this.enableIndyBoundary = enableIndyBoundary;
    this.emitEntryChecks = emitEntryChecks;
    this.returnCheckRegistry = returnCheckRegistry;
    this.valueTracker = new ReferenceValueTracker(ownerInternalName(), methodModel);
    this.entryChecksEmitted = false;
    this.currentBytecodeOffset = 0;
    this.currentSourceLine = BytecodeLocation.UNKNOWN_LINE;
  }

  @Override
  public void accept(CodeBuilder builder, CodeElement element) {
    if (element instanceof LineNumber lineNumber) {
      currentSourceLine = lineNumber.line();
    }

    if (element instanceof Instruction) {
      valueTracker.enterBytecode(currentBytecodeOffset);
    }

    if (maybeEmitEntryChecks(builder, element)) {
      if (element instanceof Instruction instruction) {
        valueTracker.acceptInstruction(instruction);
        currentBytecodeOffset += instruction.sizeInBytes();
      }
      return;
    }

    switch (element) {
      case FieldInstruction f -> handleField(builder, f, currentLocation());
      case ReturnInstruction r -> handleReturn(builder, r, currentLocation());
      case InvokeInstruction i -> handleInvoke(builder, i, currentLocation());
      case ArrayStoreInstruction a -> handleArrayStore(builder, a, currentLocation());
      case ArrayLoadInstruction a -> handleArrayLoad(builder, a, currentLocation());
      case StoreInstruction s -> handleStore(builder, s, currentLocation());
      default -> builder.with(element);
    }

    if (element instanceof Instruction instruction) {
      valueTracker.acceptInstruction(instruction);
      currentBytecodeOffset += instruction.sizeInBytes();
    }
  }

  private boolean maybeEmitEntryChecks(CodeBuilder builder, CodeElement element) {
    if (!emitEntryChecks) {
      return false;
    }

    if (entryChecksEmitted) {
      return false;
    }

    if (element instanceof LineNumber) {
      builder.with(element);
      emitParameterChecks(builder);
      entryChecksEmitted = true;
      return true;
    } else if (element instanceof Instruction) {
      emitParameterChecks(builder);
      entryChecksEmitted = true;
      return false;
    }

    return false;
  }

  private void handleField(CodeBuilder b, FieldInstruction f, BytecodeLocation location) {
    if (isFieldWrite(f)) {
      FlowEvent.FieldWrite event =
          new FlowEvent.FieldWrite(
              methodContext,
              location,
              new TargetRef.Field(
                  f.owner().asInternalName(),
                  f.name().stringValue(),
                  f.typeSymbol().descriptorString()),
              f.opcode() == Opcode.PUTSTATIC);
      emitPlannedActions(b, event, ActionTiming.BEFORE_INSTRUCTION);
      b.with(f);
    } else if (isFieldRead(f)) {
      b.with(f);
      if (isCheckedScope) {
        FlowEvent.FieldRead event =
            new FlowEvent.FieldRead(
                methodContext,
                location,
                new TargetRef.Field(
                    f.owner().asInternalName(),
                    f.name().stringValue(),
                    f.typeSymbol().descriptorString()));
        emitPlannedActions(b, event, ActionTiming.AFTER_INSTRUCTION);
      }
    } else {
      b.with(f);
    }
  }

  private void handleReturn(CodeBuilder b, ReturnInstruction r, BytecodeLocation location) {
    if (isCheckedScope) {
      FlowEvent.MethodReturn event =
          new FlowEvent.MethodReturn(
              methodContext,
              location,
              new TargetRef.MethodReturn(ownerInternalName(), methodContext.methodModel()));
      emitPlannedActions(b, event, ActionTiming.NORMAL_RETURN);
    } else {
      if (r.opcode() == Opcode.ARETURN) {
        FlowEvent.OverrideReturn event =
            new FlowEvent.OverrideReturn(
                methodContext,
                location,
                new TargetRef.MethodReturn(ownerInternalName(), methodContext.methodModel()));
        emitPlannedActions(b, event, ActionTiming.NORMAL_RETURN);
      }
    }
    b.with(r);
  }

  private void handleInvoke(CodeBuilder b, InvokeInstruction i, BytecodeLocation location) {
    boolean rewritten = maybeEmitCheckedBoundaryCall(b, i, location);
    if (!rewritten) {
      b.with(i);
    }
    if (isCheckedScope) {
      FlowEvent.BoundaryCallReturn event =
          new FlowEvent.BoundaryCallReturn(methodContext, location, returnBoundaryTarget(i));
      emitPlannedActions(b, event, ActionTiming.AFTER_INSTRUCTION);
    }
  }

  private boolean maybeEmitCheckedBoundaryCall(
      CodeBuilder builder, InvokeInstruction instruction, BytecodeLocation location) {
    if (!enableIndyBoundary || !isCheckedScope || policy == null) {
      return false;
    }

    Opcode opcode = instruction.opcode();
    if (opcode != Opcode.INVOKEVIRTUAL
        && opcode != Opcode.INVOKESTATIC
        && opcode != Opcode.INVOKEINTERFACE) {
      return false;
    }

    String methodName = instruction.name().stringValue();
    if (methodName.equals("<init>") || methodName.contains("$runtimeframework$safe")) {
      return false;
    }

    String ownerInternalName = instruction.owner().asInternalName();
    ClassInfo ownerInfo =
        new ClassInfo(ownerInternalName, methodContext.classContext().classInfo().loader(), null);
    if (!policy.isChecked(ownerInfo)) {
      return false;
    }

    Optional<ResolutionEnvironment.ResolvedMethod> resolvedTarget =
        resolveBoundaryTarget(ownerInternalName, methodName, instruction.typeSymbol(), opcode);
    if (resolvedTarget.isEmpty()) {
      return false;
    }

    ClassDesc invocationOwnerDesc = ClassDesc.ofInternalName(ownerInternalName);
    ClassDesc targetOwnerDesc = ClassDesc.ofInternalName(resolvedTarget.get().ownerInternalName());
    if (opcode == Opcode.INVOKESTATIC) {
      builder.invokestatic(
          targetOwnerDesc,
          EnforcementInstrumenter.safeMethodName(methodName),
          instruction.typeSymbol(),
          instruction.isInterface());
      return true;
    }

    MethodTypeDesc invocationType =
        instruction.typeSymbol().insertParameterTypes(0, invocationOwnerDesc);
    MethodPlan fallbackReturnPlan =
        planner.planUncheckedReceiverFallbackReturn(
            methodContext,
            location,
            new TargetRef.InvokedMethod(
                resolvedTarget.get().ownerInternalName(), methodName, instruction.typeSymbol()));
    if (fallbackReturnPlan.isEmpty() || returnCheckRegistry == null) {
      builder.invokedynamic(
          DynamicCallSiteDesc.of(
              CHECKED_VIRTUAL_BOOTSTRAP,
              methodName,
              invocationType,
              targetOwnerDesc,
              methodName,
              EnforcementInstrumenter.safeMethodName(methodName),
              instruction.typeSymbol()));
      return true;
    }

    MethodHandleDesc fallbackReturnFilter =
        returnCheckRegistry.register(
            instruction.typeSymbol().returnType(), fallbackReturnPlan, location);
    builder.invokedynamic(
        DynamicCallSiteDesc.of(
            CHECKED_VIRTUAL_WITH_FALLBACK_RETURN_CHECK_BOOTSTRAP,
            methodName,
            invocationType,
            targetOwnerDesc,
            methodName,
            EnforcementInstrumenter.safeMethodName(methodName),
            instruction.typeSymbol(),
            fallbackReturnFilter));
    return true;
  }

  private Optional<ResolutionEnvironment.ResolvedMethod> resolveBoundaryTarget(
      String ownerInternalName, String methodName, MethodTypeDesc descriptor, Opcode opcode) {
    ClassLoader loader = methodContext.classContext().classInfo().loader();
    return resolveInvokeTarget(ownerInternalName, methodName, descriptor, opcode)
        .filter(
            method ->
                policy.isChecked(
                    new ClassInfo(method.ownerInternalName(), loader, null), method.ownerModel()))
        .filter(method -> targetMatchesCallOpcode(method.method(), opcode));
  }

  private TargetRef.InvokedMethod returnBoundaryTarget(InvokeInstruction instruction) {
    String ownerInternalName = instruction.owner().asInternalName();
    String methodName = instruction.name().stringValue();
    MethodTypeDesc descriptor = instruction.typeSymbol();
    String resolvedOwner =
        resolveInvokeTarget(ownerInternalName, methodName, descriptor, instruction.opcode())
            .filter(resolved -> !generatedBridgeWillHandle(ownerInternalName, resolved))
            .map(ResolutionEnvironment.ResolvedMethod::ownerInternalName)
            .orElse(ownerInternalName);
    return new TargetRef.InvokedMethod(resolvedOwner, methodName, descriptor);
  }

  private boolean generatedBridgeWillHandle(
      String ownerInternalName, ResolutionEnvironment.ResolvedMethod resolved) {
    if (ownerInternalName.equals(resolved.ownerInternalName())) {
      return false;
    }

    ClassLoader loader = methodContext.classContext().classInfo().loader();
    if (policy == null
        || policy.isChecked(
            new ClassInfo(resolved.ownerInternalName(), loader, null), resolved.ownerModel())
        || Modifier.isInterface(resolved.ownerModel().flags().flagsMask())
        || !isGeneratedBridgeCandidate(resolved.method())) {
      return false;
    }

    return resolutionEnvironment
        .loadClass(ownerInternalName, loader)
        .filter(model -> policy.isChecked(new ClassInfo(ownerInternalName, loader, null), model))
        .filter(model -> !Modifier.isInterface(model.flags().flagsMask()))
        .map(
            model ->
                planner.shouldGenerateBridge(
                    new ClassContext(
                        new ClassInfo(ownerInternalName, loader, null),
                        model,
                        ClassClassification.CHECKED),
                    new ParentMethod(resolved.ownerModel(), resolved.method())))
        .orElse(false);
  }

  private boolean isGeneratedBridgeCandidate(MethodModel method) {
    int flags = method.flags().flagsMask();
    return !Modifier.isPrivate(flags)
        && !Modifier.isStatic(flags)
        && !Modifier.isFinal(flags)
        && (flags & AccessFlag.SYNTHETIC.mask()) == 0
        && (flags & AccessFlag.BRIDGE.mask()) == 0;
  }

  private Optional<ResolutionEnvironment.ResolvedMethod> resolveInvokeTarget(
      String ownerInternalName, String methodName, MethodTypeDesc descriptor, Opcode opcode) {
    ClassLoader loader = methodContext.classContext().classInfo().loader();
    return switch (opcode) {
      case INVOKEVIRTUAL ->
          resolutionEnvironment.findResolvedVirtualMethod(
              ownerInternalName, methodName, descriptor.descriptorString(), loader);
      case INVOKEINTERFACE ->
          resolutionEnvironment.findResolvedInterfaceMethod(
              ownerInternalName, methodName, descriptor.descriptorString(), loader);
      case INVOKESTATIC ->
          resolutionEnvironment.findResolvedStaticMethod(
              ownerInternalName, methodName, descriptor.descriptorString(), loader);
      case INVOKESPECIAL ->
          resolutionEnvironment
              .loadClass(ownerInternalName, loader)
              .flatMap(
                  model ->
                      resolutionEnvironment
                          .findDeclaredMethod(
                              ownerInternalName, methodName, descriptor.descriptorString(), loader)
                          .map(
                              method ->
                                  new ResolutionEnvironment.ResolvedMethod(
                                      ownerInternalName, model, method)));
      default -> Optional.empty();
    };
  }

  private boolean targetMatchesCallOpcode(MethodModel target, Opcode opcode) {
    boolean targetIsStatic = Modifier.isStatic(target.flags().flagsMask());
    if (opcode == Opcode.INVOKEINTERFACE) {
      return !targetIsStatic
          && (EnforcementInstrumenter.isSplitCandidate(target)
              || EnforcementInstrumenter.isInterfaceSafeStubCandidate(target));
    }
    if (!EnforcementInstrumenter.isSplitCandidate(target)) {
      return opcode == Opcode.INVOKEVIRTUAL
          && !targetIsStatic
          && EnforcementInstrumenter.isAbstractClassSafeStubCandidate(target);
    }
    return (opcode == Opcode.INVOKESTATIC) == targetIsStatic;
  }

  private void handleArrayStore(CodeBuilder b, ArrayStoreInstruction a, BytecodeLocation location) {
    if (a.opcode() == Opcode.AASTORE) {
      FlowEvent.ArrayStore event =
          new FlowEvent.ArrayStore(
              methodContext,
              location,
              valueTracker
                  .arrayComponentTarget(2)
                  .orElseGet(() -> new TargetRef.ArrayComponent("[Ljava/lang/Object;", null)));
      emitPlannedActions(b, event, ActionTiming.BEFORE_INSTRUCTION);
    }
    b.with(a);
  }

  private void handleArrayLoad(CodeBuilder b, ArrayLoadInstruction a, BytecodeLocation location) {
    b.with(a);
    if (isCheckedScope && a.opcode() == Opcode.AALOAD) {
      FlowEvent.ArrayLoad event =
          new FlowEvent.ArrayLoad(
              methodContext,
              location,
              valueTracker
                  .arrayComponentTarget(1)
                  .orElseGet(() -> new TargetRef.ArrayComponent("[Ljava/lang/Object;", null)));
      emitPlannedActions(b, event, ActionTiming.AFTER_INSTRUCTION);
    }
  }

  private void handleStore(CodeBuilder b, StoreInstruction s, BytecodeLocation location) {
    if (isCheckedScope) {
      boolean isRefStore =
          switch (s.opcode()) {
            case ASTORE, ASTORE_0, ASTORE_1, ASTORE_2, ASTORE_3 -> true;
            default -> false;
          };
      if (isRefStore) {
        FlowEvent.LocalStore event =
            new FlowEvent.LocalStore(
                methodContext,
                location,
                new TargetRef.Local(
                    methodContext.methodModel(),
                    s.slot(),
                    location.bytecodeIndex() + s.sizeInBytes()));
        emitPlannedActions(b, event, ActionTiming.BEFORE_INSTRUCTION);
      }
    }
    b.with(s);
  }

  public void emitParameterChecks(CodeBuilder builder) {
    MethodModel methodModel = methodContext.methodModel();
    MethodTypeDesc methodDesc = methodModel.methodTypeSymbol();
    int paramCount = methodDesc.parameterList().size();
    List<FlowEvent> events = new ArrayList<>(paramCount);
    for (int i = 0; i < paramCount; i++) {
      BytecodeLocation entryLocation = BytecodeLocation.at(-1, currentSourceLine);
      if (isCheckedScope) {
        events.add(
            new FlowEvent.MethodParameter(
                methodContext,
                entryLocation,
                new TargetRef.MethodParameter(ownerInternalName(), methodModel, i)));
      } else {
        events.add(
            new FlowEvent.OverrideParameter(
                methodContext,
                entryLocation,
                new TargetRef.MethodParameter(ownerInternalName(), methodModel, i)));
      }
    }
    if (!events.isEmpty()) {
      emitActions(builder, planner.planMethod(methodContext, events), ActionTiming.METHOD_ENTRY);
    }
  }

  private boolean isFieldWrite(FieldInstruction f) {
    return f.opcode() == Opcode.PUTFIELD || f.opcode() == Opcode.PUTSTATIC;
  }

  private boolean isFieldRead(FieldInstruction f) {
    return f.opcode() == Opcode.GETFIELD || f.opcode() == Opcode.GETSTATIC;
  }

  private void emitPlannedActions(CodeBuilder builder, FlowEvent event, ActionTiming timing) {
    emitActions(builder, planner.planMethod(methodContext, List.of(event)), timing);
  }

  private void emitActions(CodeBuilder builder, MethodPlan plan, ActionTiming timing) {
    for (InstrumentationAction action : plan.actions()) {
      if (timing.matches(action)) {
        emitAction(builder, action);
      }
    }
  }

  private void emitAction(CodeBuilder builder, InstrumentationAction action) {
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

  private BytecodeLocation currentLocation() {
    return BytecodeLocation.at(currentBytecodeOffset, currentSourceLine);
  }

  private String ownerInternalName() {
    return methodContext.classContext().classInfo().internalName();
  }

  interface IndyReturnCheckRegistry {
    MethodHandleDesc register(ClassDesc returnType, MethodPlan plan, BytecodeLocation location);
  }

  private enum ActionTiming {
    METHOD_ENTRY,
    BEFORE_INSTRUCTION,
    AFTER_INSTRUCTION,
    NORMAL_RETURN;

    private boolean matches(InstrumentationAction action) {
      return switch (this) {
        case METHOD_ENTRY ->
            action.injectionPoint().kind()
                == io.github.eisop.runtimeframework.planning.InjectionPoint.Kind.METHOD_ENTRY;
        case BEFORE_INSTRUCTION ->
            action.injectionPoint().kind()
                == io.github.eisop.runtimeframework.planning.InjectionPoint.Kind.BEFORE_INSTRUCTION;
        case AFTER_INSTRUCTION ->
            action.injectionPoint().kind()
                == io.github.eisop.runtimeframework.planning.InjectionPoint.Kind.AFTER_INSTRUCTION;
        case NORMAL_RETURN ->
            action.injectionPoint().kind()
                == io.github.eisop.runtimeframework.planning.InjectionPoint.Kind.NORMAL_RETURN;
      };
    }
  }
}
