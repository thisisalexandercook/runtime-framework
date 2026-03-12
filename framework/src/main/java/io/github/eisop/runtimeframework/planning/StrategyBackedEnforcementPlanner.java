package io.github.eisop.runtimeframework.planning;

import io.github.eisop.runtimeframework.core.CheckGenerator;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.resolution.ParentMethod;
import io.github.eisop.runtimeframework.resolution.ResolutionEnvironment;
import io.github.eisop.runtimeframework.strategy.InstrumentationStrategy;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeKind;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compatibility adapter that expresses the current {@link InstrumentationStrategy} behavior through
 * the new planner model.
 */
public final class StrategyBackedEnforcementPlanner implements EnforcementPlanner {

  private final InstrumentationStrategy strategy;
  private final ResolutionEnvironment resolutionEnvironment;

  public StrategyBackedEnforcementPlanner(InstrumentationStrategy strategy) {
    this(strategy, ResolutionEnvironment.system());
  }

  public StrategyBackedEnforcementPlanner(
      InstrumentationStrategy strategy, ResolutionEnvironment resolutionEnvironment) {
    this.strategy = Objects.requireNonNull(strategy, "strategy");
    this.resolutionEnvironment =
        Objects.requireNonNull(resolutionEnvironment, "resolutionEnvironment");
  }

  @Override
  public MethodPlan planMethod(MethodContext methodContext, List<? extends FlowEvent> events) {
    List<InstrumentationAction> actions = new ArrayList<>();
    for (FlowEvent event : events) {
      actions.addAll(planEvent(event));
    }
    return new MethodPlan(actions);
  }

  @Override
  public boolean shouldGenerateBridge(ClassContext classContext, ParentMethod parentMethod) {
    return strategy.shouldGenerateBridge(parentMethod);
  }

  @Override
  public BridgePlan planBridge(ClassContext classContext, ParentMethod parentMethod) {
    MethodModel method = parentMethod.method();
    List<InstrumentationAction> actions = new ArrayList<>();
    int slotIndex = 1;

    for (int i = 0; i < method.methodTypeSymbol().parameterList().size(); i++) {
      TypeKind type = TypeKind.from(method.methodTypeSymbol().parameterList().get(i));
      CheckGenerator generator = strategy.getBridgeParameterCheck(parentMethod, i);
      if (generator != null) {
        actions.add(
            new InstrumentationAction.LegacyCheckAction(
                InjectionPoint.bridgeEntry(),
                new ValueAccess.LocalSlot(slotIndex),
                type,
                generator,
                DiagnosticSpec.of(
                    "Parameter "
                        + i
                        + " in inherited method "
                        + method.methodName().stringValue())));
      }
      slotIndex += type.slotSize();
    }

    CheckGenerator returnGenerator = strategy.getBridgeReturnCheck(parentMethod);
    if (returnGenerator != null) {
      actions.add(
          new InstrumentationAction.LegacyCheckAction(
              InjectionPoint.bridgeExit(),
              new ValueAccess.OperandStack(0),
              TypeKind.REFERENCE,
              returnGenerator,
              DiagnosticSpec.of(
                  "Return value of inherited method " + method.methodName().stringValue())));
    }

    return new BridgePlan(parentMethod, actions);
  }

  private List<InstrumentationAction> planEvent(FlowEvent event) {
    return switch (event) {
      case FlowEvent.MethodParameter methodParameter -> planMethodParameter(methodParameter);
      case FlowEvent.MethodReturn methodReturn -> planMethodReturn(methodReturn);
      case FlowEvent.BoundaryCallReturn boundaryCallReturn -> planBoundaryCallReturn(boundaryCallReturn);
      case FlowEvent.FieldRead fieldRead -> planFieldRead(fieldRead);
      case FlowEvent.FieldWrite fieldWrite -> planFieldWrite(fieldWrite);
      case FlowEvent.ArrayLoad arrayLoad -> planArrayLoad(arrayLoad);
      case FlowEvent.ArrayStore arrayStore -> planArrayStore(arrayStore);
      case FlowEvent.LocalStore localStore -> planLocalStore(localStore);
      case FlowEvent.OverrideReturn overrideReturn -> planOverrideReturn(overrideReturn);
      case FlowEvent.BridgeParameter ignored -> List.of();
      case FlowEvent.BridgeReturn ignored -> List.of();
      case FlowEvent.OverrideParameter ignored -> List.of();
      case FlowEvent.ConstructorEnter ignored -> List.of();
      case FlowEvent.ConstructorCommit ignored -> List.of();
      case FlowEvent.BoundaryReceiverUse ignored -> List.of();
    };
  }

  private List<InstrumentationAction> planMethodParameter(FlowEvent.MethodParameter event) {
    TargetRef.MethodParameter target = event.target();
    TypeKind type = TypeKind.from(target.method().methodTypeSymbol().parameterList().get(target.parameterIndex()));
    CheckGenerator generator =
        strategy.getParameterCheck(target.method(), target.parameterIndex(), type);
    if (generator == null) {
      return List.of();
    }

    return List.of(
        new InstrumentationAction.LegacyCheckAction(
            InjectionPoint.methodEntry(),
            new ValueAccess.LocalSlot(parameterSlot(target.method(), target.parameterIndex())),
            type,
            generator,
            DiagnosticSpec.of("Parameter " + target.parameterIndex())));
  }

  private List<InstrumentationAction> planMethodReturn(FlowEvent.MethodReturn event) {
    TypeKind type = TypeKind.from(event.target().method().methodTypeSymbol().returnType());
    CheckGenerator generator = strategy.getReturnCheck(event.target().method());
    if (generator == null) {
      return List.of();
    }

    return List.of(
        new InstrumentationAction.LegacyCheckAction(
            InjectionPoint.normalReturn(event.location().bytecodeIndex()),
            new ValueAccess.OperandStack(0),
            type,
            generator,
            DiagnosticSpec.of(
                "Return value of " + event.target().method().methodName().stringValue())));
  }

  private List<InstrumentationAction> planBoundaryCallReturn(FlowEvent.BoundaryCallReturn event) {
    ClassLoader loader = loader(event.methodContext());
    TargetRef.InvokedMethod target = event.target();
    CheckGenerator generator =
        strategy.getBoundaryCallCheck(target.ownerInternalName(), target.descriptor(), loader);
    if (generator == null) {
      return List.of();
    }

    return List.of(
        new InstrumentationAction.LegacyCheckAction(
            InjectionPoint.afterInstruction(event.location().bytecodeIndex()),
            new ValueAccess.OperandStack(0),
            TypeKind.from(target.descriptor().returnType()),
            generator,
            DiagnosticSpec.of("Return value of " + target.methodName() + " (Boundary)")));
  }

  private List<InstrumentationAction> planFieldRead(FlowEvent.FieldRead event) {
    CheckGenerator generator = resolveFieldReadGenerator(event.methodContext(), event.target());
    TypeKind type = TypeKind.fromDescriptor(event.target().descriptor());
    if (generator == null || type.slotSize() != 1) {
      return List.of();
    }

    return List.of(
        new InstrumentationAction.LegacyCheckAction(
            InjectionPoint.afterInstruction(event.location().bytecodeIndex()),
            new ValueAccess.OperandStack(0),
            type,
            generator,
            DiagnosticSpec.of("Read Field '" + event.target().fieldName() + "'")));
  }

  private List<InstrumentationAction> planFieldWrite(FlowEvent.FieldWrite event) {
    CheckGenerator generator = resolveFieldWriteGenerator(event.methodContext(), event.target());
    TypeKind type = TypeKind.fromDescriptor(event.target().descriptor());
    if (generator == null) {
      return List.of();
    }

    String displayName =
        event.isStaticAccess()
            ? "Static Field '" + event.target().fieldName() + "'"
            : "Field '" + event.target().fieldName() + "'";

    return List.of(
        new InstrumentationAction.LegacyCheckAction(
            InjectionPoint.beforeInstruction(event.location().bytecodeIndex()),
            new ValueAccess.OperandStack(0),
            type,
            generator,
            DiagnosticSpec.of(displayName)));
  }

  private List<InstrumentationAction> planArrayLoad(FlowEvent.ArrayLoad event) {
    CheckGenerator generator = strategy.getArrayLoadCheck(TypeKind.REFERENCE);
    if (generator == null) {
      return List.of();
    }

    return List.of(
        new InstrumentationAction.LegacyCheckAction(
            InjectionPoint.afterInstruction(event.location().bytecodeIndex()),
            new ValueAccess.OperandStack(0),
            TypeKind.REFERENCE,
            generator,
            DiagnosticSpec.of("Array Element Read")));
  }

  private List<InstrumentationAction> planArrayStore(FlowEvent.ArrayStore event) {
    CheckGenerator generator = strategy.getArrayStoreCheck(TypeKind.REFERENCE);
    if (generator == null) {
      return List.of();
    }

    return List.of(
        new InstrumentationAction.LegacyCheckAction(
            InjectionPoint.beforeInstruction(event.location().bytecodeIndex()),
            new ValueAccess.OperandStack(0),
            TypeKind.REFERENCE,
            generator,
            DiagnosticSpec.of("Array Element Write")));
  }

  private List<InstrumentationAction> planLocalStore(FlowEvent.LocalStore event) {
    TargetRef.Local target = event.target();
    CheckGenerator generator =
        strategy.getLocalVariableWriteCheck(target.method(), target.slot(), TypeKind.REFERENCE);
    if (generator == null) {
      return List.of();
    }

    return List.of(
        new InstrumentationAction.LegacyCheckAction(
            InjectionPoint.beforeInstruction(event.location().bytecodeIndex()),
            new ValueAccess.OperandStack(0),
            TypeKind.REFERENCE,
            generator,
            DiagnosticSpec.of("Local Variable Assignment (Slot " + target.slot() + ")")));
  }

  private List<InstrumentationAction> planOverrideReturn(FlowEvent.OverrideReturn event) {
    MethodContext methodContext = event.methodContext();
    CheckGenerator generator =
        strategy.getUncheckedOverrideReturnCheck(
            methodContext.classContext().classModel(),
            methodContext.methodModel(),
            loader(methodContext));
    if (generator == null) {
      return List.of();
    }

    return List.of(
        new InstrumentationAction.LegacyCheckAction(
            InjectionPoint.normalReturn(event.location().bytecodeIndex()),
            new ValueAccess.OperandStack(0),
            TypeKind.REFERENCE,
            generator,
            DiagnosticSpec.of(
                "Return value of overridden method "
                    + methodContext.methodModel().methodName().stringValue())));
  }

  private CheckGenerator resolveFieldReadGenerator(MethodContext methodContext, TargetRef.Field target) {
    TypeKind type = TypeKind.fromDescriptor(target.descriptor());
    String ownerInternalName = ownerInternalName(methodContext);
    if (target.ownerInternalName().equals(ownerInternalName)) {
      return resolutionEnvironment
          .findDeclaredField(target.ownerInternalName(), target.fieldName(), loader(methodContext))
          .map(field -> strategy.getFieldReadCheck(field, type))
          .orElse(null);
    }
    return strategy.getBoundaryFieldReadCheck(
        target.ownerInternalName(), target.fieldName(), type, loader(methodContext));
  }

  private CheckGenerator resolveFieldWriteGenerator(
      MethodContext methodContext, TargetRef.Field target) {
    TypeKind type = TypeKind.fromDescriptor(target.descriptor());
    String ownerInternalName = ownerInternalName(methodContext);
    if (target.ownerInternalName().equals(ownerInternalName)) {
      return resolutionEnvironment
          .findDeclaredField(target.ownerInternalName(), target.fieldName(), loader(methodContext))
          .map(field -> strategy.getFieldWriteCheck(field, type))
          .orElse(null);
    }
    return strategy.getBoundaryFieldWriteCheck(
        target.ownerInternalName(), target.fieldName(), type, loader(methodContext));
  }

  private static int parameterSlot(MethodModel method, int parameterIndex) {
    int slotIndex = Modifier.isStatic(method.flags().flagsMask()) ? 0 : 1;
    for (int i = 0; i < parameterIndex; i++) {
      slotIndex += TypeKind.from(method.methodTypeSymbol().parameterList().get(i)).slotSize();
    }
    return slotIndex;
  }

  private static String ownerInternalName(MethodContext methodContext) {
    return methodContext.classContext().classInfo().internalName();
  }

  private static ClassLoader loader(MethodContext methodContext) {
    ClassInfo classInfo = methodContext.classContext().classInfo();
    return classInfo.loader();
  }
}
