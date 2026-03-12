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
import io.github.eisop.runtimeframework.planning.ValueAccess;
import io.github.eisop.runtimeframework.policy.ClassClassification;
import io.github.eisop.runtimeframework.semantics.PropertyEmitter;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;

/** A CodeTransform that injects runtime checks based on an {@link EnforcementPlanner}. */
public class EnforcementTransform implements CodeTransform {

  private static final String UNKNOWN_ARRAY_DESCRIPTOR = "[Ljava/lang/Object;";

  private final EnforcementPlanner planner;
  private final PropertyEmitter propertyEmitter;
  private final MethodContext methodContext;
  private final boolean isCheckedScope;
  private boolean entryChecksEmitted;
  private int currentInstructionIndex;
  private int currentSourceLine;

  public EnforcementTransform(
      EnforcementPlanner planner,
      PropertyEmitter propertyEmitter,
      ClassModel classModel,
      MethodModel methodModel,
      boolean isCheckedScope,
      ClassLoader loader) {
    this.planner = planner;
    this.propertyEmitter = propertyEmitter;
    ClassContext classContext =
        new ClassContext(
            new ClassInfo(classModel.thisClass().asInternalName(), loader, null),
            classModel,
            isCheckedScope ? ClassClassification.CHECKED : ClassClassification.UNCHECKED);
    this.methodContext = new MethodContext(classContext, methodModel);
    this.isCheckedScope = isCheckedScope;
    this.entryChecksEmitted = false;
    this.currentInstructionIndex = 0;
    this.currentSourceLine = BytecodeLocation.UNKNOWN_LINE;
  }

  @Override
  public void accept(CodeBuilder builder, CodeElement element) {
    if (element instanceof LineNumber lineNumber) {
      currentSourceLine = lineNumber.line();
    }

    if (maybeEmitEntryChecks(builder, element)) {
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

    if (element instanceof Instruction) {
      currentInstructionIndex++;
    }
  }

  private boolean maybeEmitEntryChecks(CodeBuilder builder, CodeElement element) {
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
    b.with(i);
    if (isCheckedScope) {
      FlowEvent.BoundaryCallReturn event =
          new FlowEvent.BoundaryCallReturn(
              methodContext,
              location,
              new TargetRef.InvokedMethod(
                  i.owner().asInternalName(), i.name().stringValue(), i.typeSymbol()));
      emitPlannedActions(b, event, ActionTiming.AFTER_INSTRUCTION);
    }
  }

  private void handleArrayStore(
      CodeBuilder b, ArrayStoreInstruction a, BytecodeLocation location) {
    if (a.opcode() == Opcode.AASTORE) {
      FlowEvent.ArrayStore event =
          new FlowEvent.ArrayStore(
              methodContext,
              location,
              new TargetRef.ArrayComponent(UNKNOWN_ARRAY_DESCRIPTOR));
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
              new TargetRef.ArrayComponent(UNKNOWN_ARRAY_DESCRIPTOR));
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
                new TargetRef.Local(methodContext.methodModel(), s.slot(), location.bytecodeIndex()));
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
      emitActions(builder, planner.planMethod(methodContext, events), ActionTiming.METHOD_ENTRY, null);
    }
  }

  private boolean isFieldWrite(FieldInstruction f) {
    return f.opcode() == Opcode.PUTFIELD || f.opcode() == Opcode.PUTSTATIC;
  }

  private boolean isFieldRead(FieldInstruction f) {
    return f.opcode() == Opcode.GETFIELD || f.opcode() == Opcode.GETSTATIC;
  }

  private void emitPlannedActions(CodeBuilder builder, FlowEvent event, ActionTiming timing) {
    emitActions(builder, planner.planMethod(methodContext, List.of(event)), timing, event);
  }

  private void emitActions(
      CodeBuilder builder, MethodPlan plan, ActionTiming timing, FlowEvent event) {
    for (InstrumentationAction action : plan.actions()) {
      if (timing.matches(action)) {
        emitAction(builder, action, event);
      }
    }
  }

  private void emitAction(CodeBuilder builder, InstrumentationAction action, FlowEvent event) {
    switch (action) {
      case InstrumentationAction.LegacyCheckAction legacyCheckAction ->
          emitLegacyCheckAction(builder, legacyCheckAction, event);
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
          builder,
          requirement,
          action.valueAccess(),
          action.attribution(),
          action.diagnostic());
    }
  }

  private void emitLegacyCheckAction(
      CodeBuilder builder,
      InstrumentationAction.LegacyCheckAction action,
      FlowEvent event) {
    String diagnosticName = action.diagnostic().displayName();
    switch (action.valueAccess()) {
      case ValueAccess.LocalSlot localSlot -> {
        loadLocal(builder, action.valueType(), localSlot.slot());
        action.generator().generateCheck(builder, action.valueType(), diagnosticName);
      }
      case ValueAccess.ThisReference ignored -> {
        builder.aload(0);
        action.generator().generateCheck(builder, action.valueType(), diagnosticName);
      }
      case ValueAccess.OperandStack operandStack -> {
        if (operandStack.depthFromTop() != 0) {
          throw new IllegalStateException("Only top-of-stack access is currently supported");
        }
        if (event instanceof FlowEvent.FieldWrite fieldWrite) {
          emitFieldWriteStackCheck(
              builder,
              action.valueType(),
              action.generator(),
              diagnosticName,
              fieldWrite.isStaticAccess());
        } else {
          emitTopOfStackCheck(builder, action.valueType(), action.generator(), diagnosticName);
        }
      }
      case ValueAccess.FieldWriteValue ignored ->
          throw new IllegalStateException(
              "Legacy check actions do not support planner-native field-write access");
    }
  }

  private void emitTopOfStackCheck(
      CodeBuilder builder, TypeKind type, io.github.eisop.runtimeframework.core.CheckGenerator generator, String diagnosticName) {
    switch (type.slotSize()) {
      case 1 -> builder.dup();
      case 2 -> builder.dup2();
      default -> throw new IllegalStateException("Unsupported stack size for check emission: " + type);
    }
    generator.generateCheck(builder, type, diagnosticName);
  }

  private void emitFieldWriteStackCheck(
      CodeBuilder builder,
      TypeKind type,
      io.github.eisop.runtimeframework.core.CheckGenerator generator,
      String diagnosticName,
      boolean isStaticAccess) {
    if (isStaticAccess) {
      emitTopOfStackCheck(builder, type, generator, diagnosticName);
      return;
    }

    if (type.slotSize() != 1) {
      throw new IllegalStateException("PUTFIELD check currently expects a single-slot value");
    }
    builder.dup_x1();
    generator.generateCheck(builder, type, diagnosticName);
    builder.swap();
  }

  private void loadLocal(CodeBuilder builder, TypeKind type, int slot) {
    switch (type) {
      case INT, BYTE, CHAR, SHORT, BOOLEAN -> builder.iload(slot);
      case LONG -> builder.lload(slot);
      case FLOAT -> builder.fload(slot);
      case DOUBLE -> builder.dload(slot);
      case REFERENCE -> builder.aload(slot);
      default -> throw new IllegalArgumentException("Unsupported local load type: " + type);
    }
  }

  private BytecodeLocation currentLocation() {
    return BytecodeLocation.at(currentInstructionIndex, currentSourceLine);
  }

  private String ownerInternalName() {
    return methodContext.classContext().classInfo().internalName();
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
