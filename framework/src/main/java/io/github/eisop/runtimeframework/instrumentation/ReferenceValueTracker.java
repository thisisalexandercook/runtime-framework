package io.github.eisop.runtimeframework.instrumentation;

import io.github.eisop.runtimeframework.planning.TargetRef;
import java.lang.classfile.Attributes;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ConvertInstruction;
import java.lang.classfile.instruction.DiscontinuedInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.MonitorInstruction;
import java.lang.classfile.instruction.NewMultiArrayInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Tracks reference descriptors and simple provenance across a method body. */
final class ReferenceValueTracker {

  private final MethodModel methodModel;
  private final int firstNonParameterSlot;
  private final Map<Integer, FrameState> stackMapFrames;
  private FrameState currentState;
  private int currentBytecodeOffset;

  ReferenceValueTracker(String ownerInternalName, MethodModel methodModel) {
    Objects.requireNonNull(ownerInternalName, "ownerInternalName");
    this.methodModel = Objects.requireNonNull(methodModel, "methodModel");
    this.firstNonParameterSlot = firstNonParameterSlot(methodModel);
    this.stackMapFrames = loadStackMapFrames(methodModel, ownerInternalName);
    this.currentState = initialState(ownerInternalName, methodModel);
    this.currentBytecodeOffset = 0;
  }

  void enterBytecode(int bytecodeOffset) {
    currentBytecodeOffset = bytecodeOffset;
    FrameState frameState = stackMapFrames.get(bytecodeOffset);
    if (frameState != null) {
      currentState = frameState.copy();
    }
  }

  Optional<TargetRef.ArrayComponent> arrayComponentTarget(int arrayRefDepthFromTop) {
    if (currentState == null) {
      return Optional.empty();
    }

    TrackedValue arrayRef = currentState.peek(arrayRefDepthFromTop);
    if (arrayRef == null || !arrayRef.isArrayReference()) {
      return Optional.empty();
    }

    return Optional.of(new TargetRef.ArrayComponent(arrayRef.descriptor(), arrayRef.sourceTarget()));
  }

  void acceptInstruction(java.lang.classfile.Instruction instruction) {
    if (currentState == null) {
      return;
    }

    try {
      switch (instruction) {
        case java.lang.classfile.instruction.LoadInstruction load -> simulateLoad(load);
        case StoreInstruction store -> simulateStore(store);
        case ConstantInstruction constant -> simulateConstant(constant);
        case FieldInstruction field -> simulateField(field);
        case InvokeInstruction invoke -> simulateInvoke(invoke.typeSymbol(), hasReceiver(invoke.opcode()), invokeReturnSource(invoke));
        case InvokeDynamicInstruction invokeDynamic -> simulateInvoke(invokeDynamic.typeSymbol(), false, null);
        case ArrayLoadInstruction arrayLoad -> simulateArrayLoad(arrayLoad);
        case ArrayStoreInstruction ignored -> simulateArrayStore();
        case TypeCheckInstruction typeCheck -> simulateTypeCheck(typeCheck);
        case NewObjectInstruction newObject ->
            currentState.push(TrackedValue.reference(newObject.className().asSymbol().descriptorString(), null));
        case NewReferenceArrayInstruction newReferenceArray -> simulateNewReferenceArray(newReferenceArray);
        case NewPrimitiveArrayInstruction newPrimitiveArray -> simulateNewPrimitiveArray(newPrimitiveArray);
        case NewMultiArrayInstruction newMultiArray -> simulateNewMultiArray(newMultiArray);
        case ConvertInstruction convert -> simulateConvert(convert);
        case IncrementInstruction increment -> currentState.store(increment.slot(), TrackedValue.primitive(TypeKind.INT));
        case OperatorInstruction operator -> simulateOperator(operator);
        case StackInstruction stackInstruction -> simulateStack(stackInstruction.opcode());
        case BranchInstruction branch -> simulateBranch(branch);
        case LookupSwitchInstruction ignored -> {
          currentState.pop();
          currentState = null;
        }
        case TableSwitchInstruction ignored -> {
          currentState.pop();
          currentState = null;
        }
        case ReturnInstruction returnInstruction -> simulateReturn(returnInstruction);
        case ThrowInstruction ignored -> {
          currentState.pop();
          currentState = null;
        }
        case MonitorInstruction ignored -> currentState.pop();
        case DiscontinuedInstruction ignored -> currentState = null;
        default -> currentState = null;
      }
    } catch (RuntimeException ignored) {
      currentState = null;
    }
  }

  private void simulateLoad(java.lang.classfile.instruction.LoadInstruction load) {
    TrackedValue local = currentState.load(load.slot());
    if (local == null) {
      local = TrackedValue.ofKind(load.typeKind());
    } else if (load.typeKind() == TypeKind.REFERENCE && load.slot() >= firstNonParameterSlot) {
      local =
          TrackedValue.reference(
              local.descriptor(),
              new TargetRef.Local(methodModel, load.slot(), currentBytecodeOffset));
    }
    currentState.push(local);
  }

  private void simulateStore(StoreInstruction store) {
    TrackedValue value = currentState.pop();
    if (value == null) {
      value = TrackedValue.ofKind(store.typeKind());
    }
    currentState.store(store.slot(), value);
  }

  private void simulateConstant(ConstantInstruction constant) {
    if (constant.typeKind() != TypeKind.REFERENCE) {
      currentState.push(TrackedValue.primitive(constant.typeKind()));
      return;
    }

    if (constant.opcode() == Opcode.ACONST_NULL) {
      currentState.push(TrackedValue.reference(null, null));
      return;
    }

    Object constantValue = constant.constantValue();
    String descriptor =
        switch (constantValue) {
          case String ignored -> "Ljava/lang/String;";
          case ClassDesc ignored -> "Ljava/lang/Class;";
          case MethodTypeDesc ignored -> "Ljava/lang/invoke/MethodType;";
          case DirectMethodHandleDesc ignored -> "Ljava/lang/invoke/MethodHandle;";
          default -> null;
        };
    currentState.push(TrackedValue.reference(descriptor, null));
  }

  private void simulateField(FieldInstruction field) {
    String descriptor = field.typeSymbol().descriptorString();
    TargetRef.Field sourceTarget =
        new TargetRef.Field(
            field.owner().asInternalName(), field.name().stringValue(), descriptor);

    switch (field.opcode()) {
      case GETSTATIC -> currentState.push(TrackedValue.fromDescriptor(descriptor, sourceTarget));
      case GETFIELD -> {
        currentState.pop();
        currentState.push(TrackedValue.fromDescriptor(descriptor, sourceTarget));
      }
      case PUTSTATIC -> currentState.pop();
      case PUTFIELD -> {
        currentState.pop();
        currentState.pop();
      }
      default -> currentState = null;
    }
  }

  private void simulateInvoke(
      MethodTypeDesc descriptor, boolean hasReceiver, TargetRef.InvokedMethod returnSource) {
    for (int i = descriptor.parameterList().size() - 1; i >= 0; i--) {
      currentState.pop();
    }
    if (hasReceiver) {
      currentState.pop();
    }

    String returnDescriptor = descriptor.returnType().descriptorString();
    if (!"V".equals(returnDescriptor)) {
      currentState.push(TrackedValue.fromDescriptor(returnDescriptor, returnSource));
    }
  }

  private void simulateArrayLoad(ArrayLoadInstruction arrayLoad) {
    currentState.pop();
    TrackedValue arrayRef = currentState.pop();

    if (arrayLoad.typeKind() != TypeKind.REFERENCE) {
      currentState.push(TrackedValue.primitive(arrayLoad.typeKind()));
      return;
    }

    currentState.push(componentValue(arrayRef));
  }

  private void simulateArrayStore() {
    currentState.pop();
    currentState.pop();
    currentState.pop();
  }

  private void simulateTypeCheck(TypeCheckInstruction typeCheck) {
    currentState.pop();
    if (typeCheck.opcode() == Opcode.INSTANCEOF) {
      currentState.push(TrackedValue.primitive(TypeKind.INT));
      return;
    }

    if (typeCheck.opcode() == Opcode.CHECKCAST) {
      currentState.push(TrackedValue.reference(typeCheck.type().asSymbol().descriptorString(), null));
      return;
    }

    currentState = null;
  }

  private void simulateNewReferenceArray(NewReferenceArrayInstruction newReferenceArray) {
    currentState.pop();
    currentState.push(
        TrackedValue.reference(arrayDescriptor(newReferenceArray.componentType()), null));
  }

  private void simulateNewPrimitiveArray(NewPrimitiveArrayInstruction newPrimitiveArray) {
    currentState.pop();
    currentState.push(
        TrackedValue.reference("[" + primitiveDescriptor(newPrimitiveArray.typeKind()), null));
  }

  private void simulateNewMultiArray(NewMultiArrayInstruction newMultiArray) {
    for (int i = 0; i < newMultiArray.dimensions(); i++) {
      currentState.pop();
    }
    currentState.push(
        TrackedValue.reference(newMultiArray.arrayType().asSymbol().descriptorString(), null));
  }

  private void simulateConvert(ConvertInstruction convert) {
    currentState.pop();
    currentState.push(TrackedValue.primitive(convert.toType()));
  }

  private void simulateOperator(OperatorInstruction operator) {
    switch (operator.opcode()) {
      case ARRAYLENGTH -> {
        currentState.pop();
        currentState.push(TrackedValue.primitive(TypeKind.INT));
      }
      case INEG, FNEG -> {
        currentState.pop();
        currentState.push(TrackedValue.primitive(operator.typeKind()));
      }
      case LNEG, DNEG -> {
        currentState.pop();
        currentState.push(TrackedValue.primitive(operator.typeKind()));
      }
      case IADD, ISUB, IMUL, IDIV, IREM, IAND, IOR, IXOR, ISHL, ISHR, IUSHR,
          FADD, FSUB, FMUL, FDIV, FREM -> {
        currentState.pop();
        currentState.pop();
        currentState.push(TrackedValue.primitive(operator.typeKind()));
      }
      case LADD, LSUB, LMUL, LDIV, LREM, LAND, LOR, LXOR -> {
        currentState.pop();
        currentState.pop();
        currentState.push(TrackedValue.primitive(TypeKind.LONG));
      }
      case LSHL, LSHR, LUSHR -> {
        currentState.pop();
        currentState.pop();
        currentState.push(TrackedValue.primitive(TypeKind.LONG));
      }
      case DADD, DSUB, DMUL, DDIV, DREM -> {
        currentState.pop();
        currentState.pop();
        currentState.push(TrackedValue.primitive(TypeKind.DOUBLE));
      }
      case LCMP, FCMPL, FCMPG, DCMPL, DCMPG -> {
        currentState.pop();
        currentState.pop();
        currentState.push(TrackedValue.primitive(TypeKind.INT));
      }
      default -> currentState = null;
    }
  }

  private void simulateStack(Opcode opcode) {
    switch (opcode) {
      case POP -> currentState.pop();
      case POP2 -> popCategory2Aware();
      case DUP -> {
        TrackedValue value = currentState.pop();
        requireCategory1(value);
        currentState.push(value);
        currentState.push(value);
      }
      case DUP_X1 -> {
        TrackedValue value1 = currentState.pop();
        TrackedValue value2 = currentState.pop();
        requireCategory1(value1);
        requireCategory1(value2);
        currentState.push(value1);
        currentState.push(value2);
        currentState.push(value1);
      }
      case DUP_X2 -> simulateDupX2();
      case DUP2 -> simulateDup2();
      case DUP2_X1 -> simulateDup2X1();
      case DUP2_X2 -> simulateDup2X2();
      case SWAP -> {
        TrackedValue value1 = currentState.pop();
        TrackedValue value2 = currentState.pop();
        requireCategory1(value1);
        requireCategory1(value2);
        currentState.push(value1);
        currentState.push(value2);
      }
      default -> currentState = null;
    }
  }

  private void simulateDupX2() {
    TrackedValue value1 = currentState.pop();
    requireCategory1(value1);
    TrackedValue value2 = currentState.pop();
    if (value2 == null) {
      throw new IllegalStateException();
    }
    if (value2.isCategory2()) {
      currentState.push(value1);
      currentState.push(value2);
      currentState.push(value1);
      return;
    }

    TrackedValue value3 = currentState.pop();
    requireCategory1(value2);
    requireCategory1(value3);
    currentState.push(value1);
    currentState.push(value3);
    currentState.push(value2);
    currentState.push(value1);
  }

  private void simulateDup2() {
    TrackedValue value1 = currentState.pop();
    if (value1 == null) {
      throw new IllegalStateException();
    }
    if (value1.isCategory2()) {
      currentState.push(value1);
      currentState.push(value1);
      return;
    }

    TrackedValue value2 = currentState.pop();
    requireCategory1(value1);
    requireCategory1(value2);
    currentState.push(value2);
    currentState.push(value1);
    currentState.push(value2);
    currentState.push(value1);
  }

  private void simulateDup2X1() {
    TrackedValue value1 = currentState.pop();
    if (value1 == null) {
      throw new IllegalStateException();
    }
    if (value1.isCategory2()) {
      TrackedValue value2 = currentState.pop();
      requireCategory1(value2);
      currentState.push(value1);
      currentState.push(value2);
      currentState.push(value1);
      return;
    }

    TrackedValue value2 = currentState.pop();
    TrackedValue value3 = currentState.pop();
    requireCategory1(value1);
    requireCategory1(value2);
    requireCategory1(value3);
    currentState.push(value2);
    currentState.push(value1);
    currentState.push(value3);
    currentState.push(value2);
    currentState.push(value1);
  }

  private void simulateDup2X2() {
    TrackedValue value1 = currentState.pop();
    if (value1 == null) {
      throw new IllegalStateException();
    }
    TrackedValue value2 = value1.isCategory2() ? null : currentState.pop();
    if (!value1.isCategory2()) {
      requireCategory1(value1);
      requireCategory1(value2);
    }

    TrackedValue value3 = currentState.pop();
    if (value3 == null) {
      throw new IllegalStateException();
    }
    if (value1.isCategory2()) {
      if (value3.isCategory2()) {
        currentState.push(value1);
        currentState.push(value3);
        currentState.push(value1);
        return;
      }
      TrackedValue value4 = currentState.pop();
      requireCategory1(value3);
      requireCategory1(value4);
      currentState.push(value1);
      currentState.push(value4);
      currentState.push(value3);
      currentState.push(value1);
      return;
    }

    if (value3.isCategory2()) {
      currentState.push(value2);
      currentState.push(value1);
      currentState.push(value3);
      currentState.push(value2);
      currentState.push(value1);
      return;
    }

    TrackedValue value4 = currentState.pop();
    requireCategory1(value3);
    requireCategory1(value4);
    currentState.push(value2);
    currentState.push(value1);
    currentState.push(value4);
    currentState.push(value3);
    currentState.push(value2);
    currentState.push(value1);
  }

  private void simulateBranch(BranchInstruction branch) {
    switch (branch.opcode()) {
      case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IFNULL, IFNONNULL -> currentState.pop();
      case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
          IF_ACMPEQ, IF_ACMPNE -> {
        currentState.pop();
        currentState.pop();
      }
      case GOTO, GOTO_W, JSR, JSR_W -> {
        currentState = null;
        return;
      }
      default -> {
        currentState = null;
        return;
      }
    }
  }

  private void simulateReturn(ReturnInstruction returnInstruction) {
    switch (returnInstruction.opcode()) {
      case RETURN -> currentState = null;
      case ARETURN, IRETURN, FRETURN, LRETURN, DRETURN -> {
        currentState.pop();
        currentState = null;
      }
      default -> currentState = null;
    }
  }

  private void popCategory2Aware() {
    TrackedValue value = currentState.pop();
    if (value == null) {
      throw new IllegalStateException();
    }
    if (!value.isCategory2()) {
      currentState.pop();
    }
  }

  private void requireCategory1(TrackedValue value) {
    if (value == null || value.isCategory2()) {
      throw new IllegalStateException();
    }
  }

  private TargetRef.InvokedMethod invokeReturnSource(InvokeInstruction invoke) {
    return new TargetRef.InvokedMethod(
        invoke.owner().asInternalName(), invoke.name().stringValue(), invoke.typeSymbol());
  }

  private boolean hasReceiver(Opcode opcode) {
    return switch (opcode) {
      case INVOKESTATIC -> false;
      case INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE -> true;
      default -> true;
    };
  }

  private TrackedValue componentValue(TrackedValue arrayRef) {
    if (arrayRef == null || arrayRef.descriptor() == null || !arrayRef.descriptor().startsWith("[")) {
      return TrackedValue.reference(null, null);
    }

    String componentDescriptor = arrayRef.descriptor().substring(1);
    if (isReferenceDescriptor(componentDescriptor)) {
      return TrackedValue.reference(
          componentDescriptor,
          new TargetRef.ArrayComponent(arrayRef.descriptor(), arrayRef.sourceTarget()));
    }
    return TrackedValue.fromDescriptor(componentDescriptor, null);
  }

  private static FrameState initialState(String ownerInternalName, MethodModel methodModel) {
    FrameState state = new FrameState();
    int slot = 0;
    if (!methodModel.flags().has(java.lang.reflect.AccessFlag.STATIC)) {
      state.store(
          slot++,
          TrackedValue.reference(
              "L" + ownerInternalName + ";",
              new TargetRef.Receiver(ownerInternalName, methodModel)));
    }

    for (int i = 0; i < methodModel.methodTypeSymbol().parameterList().size(); i++) {
      String descriptor = methodModel.methodTypeSymbol().parameterList().get(i).descriptorString();
      state.store(
          slot,
          TrackedValue.fromDescriptor(
              descriptor, new TargetRef.MethodParameter(ownerInternalName, methodModel, i)));
      slot += slotSize(descriptor);
    }
    return state;
  }

  private static Map<Integer, FrameState> loadStackMapFrames(
      MethodModel methodModel, String ownerInternalName) {
    Map<Integer, FrameState> frames = new HashMap<>();
    methodModel
        .code()
        .ifPresent(
            codeModel -> {
              if (!(codeModel instanceof CodeAttribute codeAttribute)) {
                return;
              }
              codeModel
                  .findAttribute(Attributes.stackMapTable())
                  .ifPresent(
                      attribute -> {
                        for (StackMapFrameInfo frame : attribute.entries()) {
                          frames.put(
                              codeAttribute.labelToBci(frame.target()),
                              frameStateFromStackMap(frame, ownerInternalName));
                        }
                      });
            });
    return frames;
  }

  private static FrameState frameStateFromStackMap(
      StackMapFrameInfo frame, String ownerInternalName) {
    FrameState state = new FrameState();
    for (int slot = 0; slot < frame.locals().size(); slot++) {
      TrackedValue value = fromVerificationType(frame.locals().get(slot), ownerInternalName);
      if (value != null) {
        state.store(slot, value);
      }
    }
    for (StackMapFrameInfo.VerificationTypeInfo stackValue : frame.stack()) {
      TrackedValue value = fromVerificationType(stackValue, ownerInternalName);
      if (value != null) {
        state.push(value);
      }
    }
    return state;
  }

  private static TrackedValue fromVerificationType(
      StackMapFrameInfo.VerificationTypeInfo typeInfo, String ownerInternalName) {
    return switch (typeInfo) {
      case StackMapFrameInfo.SimpleVerificationTypeInfo.TOP -> null;
      case StackMapFrameInfo.SimpleVerificationTypeInfo.INTEGER -> TrackedValue.primitive(TypeKind.INT);
      case StackMapFrameInfo.SimpleVerificationTypeInfo.FLOAT -> TrackedValue.primitive(TypeKind.FLOAT);
      case StackMapFrameInfo.SimpleVerificationTypeInfo.LONG -> TrackedValue.primitive(TypeKind.LONG);
      case StackMapFrameInfo.SimpleVerificationTypeInfo.DOUBLE -> TrackedValue.primitive(TypeKind.DOUBLE);
      case StackMapFrameInfo.SimpleVerificationTypeInfo.NULL -> TrackedValue.reference(null, null);
      case StackMapFrameInfo.SimpleVerificationTypeInfo.UNINITIALIZED_THIS ->
          TrackedValue.reference("L" + ownerInternalName + ";", null);
      case StackMapFrameInfo.ObjectVerificationTypeInfo objectType ->
          TrackedValue.reference(objectType.classSymbol().descriptorString(), null);
      case StackMapFrameInfo.UninitializedVerificationTypeInfo ignored ->
          TrackedValue.reference(null, null);
    };
  }

  private static String arrayDescriptor(ClassEntry componentType) {
    return "[" + componentType.asSymbol().descriptorString();
  }

  private static boolean isReferenceDescriptor(String descriptor) {
    return descriptor != null && (descriptor.startsWith("L") || descriptor.startsWith("["));
  }

  private static String primitiveDescriptor(TypeKind typeKind) {
    return switch (typeKind) {
      case BYTE -> "B";
      case CHAR -> "C";
      case DOUBLE -> "D";
      case FLOAT -> "F";
      case INT -> "I";
      case LONG -> "J";
      case SHORT -> "S";
      case BOOLEAN -> "Z";
      default -> throw new IllegalArgumentException("Unsupported primitive kind: " + typeKind);
    };
  }

  private static int slotSize(String descriptor) {
    return "J".equals(descriptor) || "D".equals(descriptor) ? 2 : 1;
  }

  private static int firstNonParameterSlot(MethodModel methodModel) {
    int slot = methodModel.flags().has(java.lang.reflect.AccessFlag.STATIC) ? 0 : 1;
    for (int i = 0; i < methodModel.methodTypeSymbol().parameterList().size(); i++) {
      slot += slotSize(methodModel.methodTypeSymbol().parameterList().get(i).descriptorString());
    }
    return slot;
  }

  private static final class FrameState {
    private final Map<Integer, TrackedValue> locals;
    private final List<TrackedValue> stack;

    private FrameState() {
      this(new HashMap<>(), new ArrayList<>());
    }

    private FrameState(Map<Integer, TrackedValue> locals, List<TrackedValue> stack) {
      this.locals = locals;
      this.stack = stack;
    }

    FrameState copy() {
      return new FrameState(new HashMap<>(locals), new ArrayList<>(stack));
    }

    void push(TrackedValue value) {
      stack.add(value);
    }

    TrackedValue pop() {
      if (stack.isEmpty()) {
        throw new IllegalStateException();
      }
      return stack.remove(stack.size() - 1);
    }

    TrackedValue peek(int depthFromTop) {
      int index = stack.size() - 1 - depthFromTop;
      if (index < 0 || index >= stack.size()) {
        return null;
      }
      return stack.get(index);
    }

    TrackedValue load(int slot) {
      return locals.get(slot);
    }

    void store(int slot, TrackedValue value) {
      locals.put(slot, value);
      if (value != null && value.isCategory2()) {
        locals.remove(slot + 1);
      }
    }
  }

  private record TrackedValue(TypeKind kind, String descriptor, TargetRef sourceTarget) {

    static TrackedValue primitive(TypeKind kind) {
      return new TrackedValue(kind, null, null);
    }

    static TrackedValue reference(String descriptor, TargetRef sourceTarget) {
      return new TrackedValue(TypeKind.REFERENCE, descriptor, sourceTarget);
    }

    static TrackedValue ofKind(TypeKind kind) {
      return kind == TypeKind.REFERENCE ? reference(null, null) : primitive(kind);
    }

    static TrackedValue fromDescriptor(String descriptor, TargetRef sourceTarget) {
      if (isReferenceDescriptor(descriptor)) {
        return reference(descriptor, sourceTarget);
      }
      return primitive(fromPrimitiveDescriptor(descriptor));
    }

    boolean isCategory2() {
      return kind == TypeKind.LONG || kind == TypeKind.DOUBLE;
    }

    boolean isArrayReference() {
      return kind == TypeKind.REFERENCE && descriptor != null && descriptor.startsWith("[");
    }

    private static TypeKind fromPrimitiveDescriptor(String descriptor) {
      return switch (descriptor) {
        case "B", "C", "I", "S", "Z" -> TypeKind.INT;
        case "D" -> TypeKind.DOUBLE;
        case "F" -> TypeKind.FLOAT;
        case "J" -> TypeKind.LONG;
        default -> throw new IllegalArgumentException("Unsupported descriptor: " + descriptor);
      };
    }
  }
}
