package io.github.eisop.runtimeframework.instrumentation;

import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.planning.EnforcementPlanner;
import io.github.eisop.runtimeframework.planning.BridgePlan;
import io.github.eisop.runtimeframework.planning.ClassContext;
import io.github.eisop.runtimeframework.planning.InstrumentationAction;
import io.github.eisop.runtimeframework.planning.StrategyBackedEnforcementPlanner;
import io.github.eisop.runtimeframework.planning.ValueAccess;
import io.github.eisop.runtimeframework.policy.ClassClassification;
import io.github.eisop.runtimeframework.resolution.HierarchyResolver;
import io.github.eisop.runtimeframework.resolution.ParentMethod;
import io.github.eisop.runtimeframework.strategy.InstrumentationStrategy;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Modifier;
import java.util.List;

public class EnforcementInstrumenter extends RuntimeInstrumenter {

  private final EnforcementPlanner planner;
  private final HierarchyResolver hierarchyResolver;

  public EnforcementInstrumenter(
      InstrumentationStrategy strategy, HierarchyResolver hierarchyResolver) {
    this(new StrategyBackedEnforcementPlanner(strategy), hierarchyResolver);
  }

  public EnforcementInstrumenter(EnforcementPlanner planner, HierarchyResolver hierarchyResolver) {
    this.planner = planner;
    this.hierarchyResolver = hierarchyResolver;
  }

  @Override
  protected CodeTransform createCodeTransform(
      ClassModel classModel, MethodModel methodModel, boolean isCheckedScope, ClassLoader loader) {
    return new EnforcementTransform(planner, classModel, methodModel, isCheckedScope, loader);
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

  private void emitBridgeActions(
      CodeBuilder builder, BridgePlan plan, BridgeActionTiming timing) {
    for (InstrumentationAction action : plan.actions()) {
      if (timing.matches(action)) {
        emitBridgeAction(builder, action);
      }
    }
  }

  private void emitBridgeAction(CodeBuilder builder, InstrumentationAction action) {
    switch (action) {
      case InstrumentationAction.LegacyCheckAction legacyCheckAction ->
          emitLegacyBridgeCheck(builder, legacyCheckAction);
      case InstrumentationAction.ValueCheckAction ignored ->
          throw new IllegalStateException("ValueCheckAction emission is not implemented yet");
      case InstrumentationAction.LifecycleHookAction ignored ->
          throw new IllegalStateException("LifecycleHookAction emission is not implemented yet");
    }
  }

  private void emitLegacyBridgeCheck(
      CodeBuilder builder, InstrumentationAction.LegacyCheckAction action) {
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
        emitTopOfStackCheck(builder, action.valueType(), action);
      }
    }
  }

  private void emitTopOfStackCheck(
      CodeBuilder builder, TypeKind type, InstrumentationAction.LegacyCheckAction action) {
    switch (type.slotSize()) {
      case 1 -> builder.dup();
      case 2 -> builder.dup2();
      default -> throw new IllegalStateException("Unsupported stack size for check emission: " + type);
    }
    action.generator().generateCheck(builder, type, action.diagnostic().displayName());
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
