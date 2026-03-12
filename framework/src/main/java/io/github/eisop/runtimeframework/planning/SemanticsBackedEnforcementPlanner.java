package io.github.eisop.runtimeframework.planning;

import io.github.eisop.runtimeframework.contracts.ValueContract;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.policy.RuntimePolicy;
import io.github.eisop.runtimeframework.resolution.ParentMethod;
import io.github.eisop.runtimeframework.resolution.ResolutionEnvironment;
import io.github.eisop.runtimeframework.runtime.AttributionKind;
import io.github.eisop.runtimeframework.semantics.CheckerSemantics;
import io.github.eisop.runtimeframework.semantics.ContractResolver;
import io.github.eisop.runtimeframework.semantics.ResolutionContext;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Planner implementation backed by checker-owned semantic contract resolution. */
public final class SemanticsBackedEnforcementPlanner implements EnforcementPlanner {

  private final RuntimePolicy policy;
  private final ContractResolver contracts;
  private final ResolutionEnvironment resolutionEnvironment;

  public SemanticsBackedEnforcementPlanner(
      RuntimePolicy policy, CheckerSemantics semantics, ResolutionEnvironment resolutionEnvironment) {
    this.policy = Objects.requireNonNull(policy, "policy");
    this.contracts = Objects.requireNonNull(semantics, "semantics").contracts();
    this.resolutionEnvironment =
        Objects.requireNonNull(resolutionEnvironment, "resolutionEnvironment");
  }

  @Override
  public MethodPlan planMethod(MethodContext methodContext, List<? extends FlowEvent> events) {
    ResolutionContext resolutionContext =
        ResolutionContext.forMethod(methodContext, resolutionEnvironment);
    List<InstrumentationAction> actions = new ArrayList<>();
    for (FlowEvent event : events) {
      if (!policy.allows(event)) {
        continue;
      }
      actions.addAll(planEvent(event, resolutionContext));
    }
    return new MethodPlan(actions);
  }

  @Override
  public boolean shouldGenerateBridge(ClassContext classContext, ParentMethod parentMethod) {
    return !planBridge(classContext, parentMethod).isEmpty();
  }

  @Override
  public BridgePlan planBridge(ClassContext classContext, ParentMethod parentMethod) {
    ResolutionContext resolutionContext =
        ResolutionContext.forClass(classContext, resolutionEnvironment);
    MethodModel method = parentMethod.method();
    String ownerInternalName = parentMethod.owner().thisClass().asInternalName();
    List<InstrumentationAction> actions = new ArrayList<>();
    int slotIndex = 1;

    for (int i = 0; i < method.methodTypeSymbol().parameterList().size(); i++) {
      ValueContract contract =
          contracts.resolve(
              new TargetRef.MethodParameter(ownerInternalName, method, i), resolutionContext);
      if (!contract.isEmpty()) {
        actions.add(
            new InstrumentationAction.ValueCheckAction(
                InjectionPoint.bridgeEntry(),
                new ValueAccess.LocalSlot(slotIndex),
                contract,
                AttributionKind.CALLER,
                DiagnosticSpec.of(
                    "Parameter "
                        + i
                        + " in inherited method "
                        + method.methodName().stringValue())));
      }
      slotIndex += parameterSlotSize(method, i);
    }

    ValueContract returnContract =
        contracts.resolve(new TargetRef.MethodReturn(ownerInternalName, method), resolutionContext);
    if (!returnContract.isEmpty()) {
      actions.add(
          new InstrumentationAction.ValueCheckAction(
              InjectionPoint.bridgeExit(),
              new ValueAccess.OperandStack(0),
              returnContract,
              AttributionKind.CALLER,
              DiagnosticSpec.of(
                  "Return value of inherited method " + method.methodName().stringValue())));
    }

    return new BridgePlan(parentMethod, actions);
  }

  private List<InstrumentationAction> planEvent(
      FlowEvent event, ResolutionContext resolutionContext) {
    return switch (event) {
      case FlowEvent.MethodParameter methodParameter ->
          planMethodParameter(methodParameter, resolutionContext);
      case FlowEvent.MethodReturn methodReturn -> planMethodReturn(methodReturn, resolutionContext);
      case FlowEvent.BoundaryCallReturn boundaryCallReturn ->
          planBoundaryCallReturn(boundaryCallReturn, resolutionContext);
      case FlowEvent.FieldRead fieldRead -> planFieldRead(fieldRead, resolutionContext);
      case FlowEvent.FieldWrite fieldWrite -> planFieldWrite(fieldWrite, resolutionContext);
      case FlowEvent.ArrayLoad arrayLoad -> planArrayLoad(arrayLoad, resolutionContext);
      case FlowEvent.ArrayStore arrayStore -> planArrayStore(arrayStore, resolutionContext);
      case FlowEvent.LocalStore localStore -> planLocalStore(localStore, resolutionContext);
      case FlowEvent.OverrideParameter overrideParameter ->
          planOverrideParameter(overrideParameter, resolutionContext);
      case FlowEvent.OverrideReturn overrideReturn ->
          planOverrideReturn(overrideReturn, resolutionContext);
      case FlowEvent.BridgeParameter ignored -> List.of();
      case FlowEvent.BridgeReturn ignored -> List.of();
      case FlowEvent.ConstructorEnter ignored -> List.of();
      case FlowEvent.ConstructorCommit ignored -> List.of();
      case FlowEvent.BoundaryReceiverUse ignored -> List.of();
    };
  }

  private List<InstrumentationAction> planMethodParameter(
      FlowEvent.MethodParameter event, ResolutionContext resolutionContext) {
    return planResolvedTarget(
        event.target(),
        resolutionContext,
        InjectionPoint.methodEntry(),
        new ValueAccess.LocalSlot(
            parameterSlot(event.target().method(), event.target().parameterIndex())),
        AttributionKind.CALLER,
        DiagnosticSpec.of("Parameter " + event.target().parameterIndex()));
  }

  private List<InstrumentationAction> planMethodReturn(
      FlowEvent.MethodReturn event, ResolutionContext resolutionContext) {
    return planResolvedTarget(
        event.target(),
        resolutionContext,
        InjectionPoint.normalReturn(event.location().bytecodeIndex()),
        new ValueAccess.OperandStack(0),
        AttributionKind.LOCAL,
        DiagnosticSpec.of(
            "Return value of " + event.target().method().methodName().stringValue()));
  }

  private List<InstrumentationAction> planBoundaryCallReturn(
      FlowEvent.BoundaryCallReturn event, ResolutionContext resolutionContext) {
    return planResolvedTarget(
        event.target(),
        resolutionContext,
        InjectionPoint.afterInstruction(event.location().bytecodeIndex()),
        new ValueAccess.OperandStack(0),
        AttributionKind.LOCAL,
        DiagnosticSpec.of("Return value of " + event.target().methodName() + " (Boundary)"));
  }

  private List<InstrumentationAction> planFieldRead(
      FlowEvent.FieldRead event, ResolutionContext resolutionContext) {
    return planResolvedTarget(
        event.target(),
        resolutionContext,
        InjectionPoint.afterInstruction(event.location().bytecodeIndex()),
        new ValueAccess.OperandStack(0),
        AttributionKind.LOCAL,
        DiagnosticSpec.of("Read Field '" + event.target().fieldName() + "'"));
  }

  private List<InstrumentationAction> planFieldWrite(
      FlowEvent.FieldWrite event, ResolutionContext resolutionContext) {
    String displayName =
        event.isStaticAccess()
            ? "Static Field '" + event.target().fieldName() + "'"
            : "Field '" + event.target().fieldName() + "'";

    return planResolvedTarget(
        event.target(),
        resolutionContext,
        InjectionPoint.beforeInstruction(event.location().bytecodeIndex()),
        new ValueAccess.FieldWriteValue(event.isStaticAccess()),
        AttributionKind.LOCAL,
        DiagnosticSpec.of(displayName));
  }

  private List<InstrumentationAction> planArrayLoad(
      FlowEvent.ArrayLoad event, ResolutionContext resolutionContext) {
    return planResolvedTarget(
        event.target(),
        resolutionContext,
        InjectionPoint.afterInstruction(event.location().bytecodeIndex()),
        new ValueAccess.OperandStack(0),
        AttributionKind.LOCAL,
        DiagnosticSpec.of("Array Element Read"));
  }

  private List<InstrumentationAction> planArrayStore(
      FlowEvent.ArrayStore event, ResolutionContext resolutionContext) {
    return planResolvedTarget(
        event.target(),
        resolutionContext,
        InjectionPoint.beforeInstruction(event.location().bytecodeIndex()),
        new ValueAccess.OperandStack(0),
        AttributionKind.LOCAL,
        DiagnosticSpec.of("Array Element Write"));
  }

  private List<InstrumentationAction> planLocalStore(
      FlowEvent.LocalStore event, ResolutionContext resolutionContext) {
    return planResolvedTarget(
        event.target(),
        resolutionContext,
        InjectionPoint.beforeInstruction(event.location().bytecodeIndex()),
        new ValueAccess.OperandStack(0),
        AttributionKind.LOCAL,
        DiagnosticSpec.of("Local Variable Assignment (Slot " + event.target().slot() + ")"));
  }

  private List<InstrumentationAction> planOverrideParameter(
      FlowEvent.OverrideParameter event, ResolutionContext resolutionContext) {
    Optional<CheckedOverrideTarget> target =
        findCheckedOverrideTarget(event.methodContext(), resolutionContext.loader());
    if (target.isEmpty()) {
      return List.of();
    }

    TargetRef.MethodParameter parameterTarget =
        new TargetRef.MethodParameter(
            target.get().ownerInternalName(),
            target.get().method(),
            event.target().parameterIndex());
    return planResolvedTarget(
        parameterTarget,
        resolutionContext,
        InjectionPoint.methodEntry(),
        new ValueAccess.LocalSlot(
            parameterSlot(event.methodContext().methodModel(), event.target().parameterIndex())),
        AttributionKind.CALLER,
        DiagnosticSpec.of(
            "Parameter "
                + event.target().parameterIndex()
                + " in overridden method "
                + event.methodContext().methodModel().methodName().stringValue()));
  }

  private List<InstrumentationAction> planOverrideReturn(
      FlowEvent.OverrideReturn event, ResolutionContext resolutionContext) {
    Optional<CheckedOverrideTarget> target =
        findCheckedOverrideTarget(event.methodContext(), resolutionContext.loader());
    if (target.isEmpty()) {
      return List.of();
    }

    return planResolvedTarget(
        new TargetRef.MethodReturn(target.get().ownerInternalName(), target.get().method()),
        resolutionContext,
        InjectionPoint.normalReturn(event.location().bytecodeIndex()),
        new ValueAccess.OperandStack(0),
        AttributionKind.LOCAL,
        DiagnosticSpec.of(
            "Return value of overridden method "
                + event.methodContext().methodModel().methodName().stringValue()));
  }

  private List<InstrumentationAction> planResolvedTarget(
      TargetRef target,
      ResolutionContext resolutionContext,
      InjectionPoint injectionPoint,
      ValueAccess valueAccess,
      AttributionKind attribution,
      DiagnosticSpec diagnostic) {
    ValueContract contract = contracts.resolve(target, resolutionContext);
    if (contract.isEmpty()) {
      return List.of();
    }
    return List.of(
        new InstrumentationAction.ValueCheckAction(
            injectionPoint, valueAccess, contract, attribution, diagnostic));
  }

  private Optional<CheckedOverrideTarget> findCheckedOverrideTarget(
      MethodContext methodContext, ClassLoader loader) {
    ClassModel classModel = methodContext.classContext().classModel();
    Optional<ClassModel> parentModelOpt = resolutionEnvironment.loadSuperclass(classModel, loader);
    while (parentModelOpt.isPresent()) {
      ClassModel parentModel = parentModelOpt.get();
      String ownerInternalName = parentModel.thisClass().asInternalName();
      if ("java/lang/Object".equals(ownerInternalName)) {
        return Optional.empty();
      }

      if (policy.isChecked(new ClassInfo(ownerInternalName, loader, null), parentModel)) {
        for (MethodModel method : parentModel.methods()) {
          if (sameSignature(methodContext.methodModel(), method)) {
            return Optional.of(new CheckedOverrideTarget(ownerInternalName, method));
          }
        }
      }

      parentModelOpt = resolutionEnvironment.loadSuperclass(parentModel, loader);
    }
    return Optional.empty();
  }

  private static boolean sameSignature(MethodModel left, MethodModel right) {
    return left.methodName().stringValue().equals(right.methodName().stringValue())
        && left
            .methodTypeSymbol()
            .descriptorString()
            .equals(right.methodTypeSymbol().descriptorString());
  }

  private static int parameterSlot(MethodModel method, int parameterIndex) {
    int slotIndex = Modifier.isStatic(method.flags().flagsMask()) ? 0 : 1;
    for (int i = 0; i < parameterIndex; i++) {
      slotIndex += parameterSlotSize(method, i);
    }
    return slotIndex;
  }

  private static int parameterSlotSize(MethodModel method, int parameterIndex) {
    String descriptor =
        method.methodTypeSymbol().parameterList().get(parameterIndex).descriptorString();
    return descriptor.equals("J") || descriptor.equals("D") ? 2 : 1;
  }
  private record CheckedOverrideTarget(String ownerInternalName, MethodModel method) {}
}
