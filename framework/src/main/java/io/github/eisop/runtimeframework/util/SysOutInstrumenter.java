package io.github.eisop.runtimeframework.util;

import io.github.eisop.runtimeframework.core.RuntimeInstrumenter;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

public class SysOutInstrumenter extends RuntimeInstrumenter {

  private static final ClassDesc SYSOUT_SYSTEM = ClassDesc.of("java.lang.System");
  private static final ClassDesc SYSOUT_STREAM = ClassDesc.of("java.io.PrintStream");
  private static final MethodTypeDesc SYSOUT_PRINTLN =
      MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V");

  public SysOutInstrumenter() {
    // Pass a filter that accepts everything for debug purposes
    super(info -> true);
  }

  private void print(CodeBuilder b, String msg) {
    b.getstatic(SYSOUT_SYSTEM, "out", SYSOUT_STREAM);
    b.ldc(msg);
    b.invokevirtual(SYSOUT_STREAM, "println", SYSOUT_PRINTLN);
  }

  @Override
  protected void generateParameterCheck(
      CodeBuilder b, int slotIndex, TypeKind type, MethodModel method, int paramIndex) {
    print(b, "   [Param Check] Verifying argument at slot " + slotIndex);
  }

  @Override
  protected void generateFieldWriteCheck(
      CodeBuilder b, FieldInstruction field, ClassModel classModel) {
    print(b, "   [Field Write] About to write to: " + field.name().stringValue());
  }

  @Override
  protected void generateFieldReadCheck(
      CodeBuilder b, FieldInstruction field, ClassModel classModel) {
    print(b, "   [Field Read] Just read from: " + field.name().stringValue());
  }

  @Override
  protected void generateReturnCheck(CodeBuilder b, ReturnInstruction ret, MethodModel method) {
    print(b, "   [Return Check] Returning from method via opcode: " + ret.opcode().name());
  }

  @Override
  protected void generateUncheckedReturnCheck(
      CodeBuilder b,
      ReturnInstruction ret,
      MethodModel method,
      ClassModel classModel,
      ClassLoader loader) {
    print(
        b,
        "   [Unchecked Override Return Check] Checking return of overridden method: "
            + method.methodName().stringValue());
  }

  @Override
  protected void generateMethodCallCheck(CodeBuilder b, InvokeInstruction invoke) {
    print(
        b,
        "   [Call Site] Just called: "
            + invoke.owner().asInternalName()
            + "."
            + invoke.name().stringValue());
  }

  @Override
  protected void generateBridgeMethods(ClassBuilder builder, ClassModel model, ClassLoader loader) {
    // Debug instrumenter does not generate bridges, but we can log that the hook was hit
    // System.out.println("[SysOutInstrumenter] Bridge hook triggered for: " +
    // model.thisClass().asInternalName());
  }

  @Override
  protected void generateArrayLoadCheck(CodeBuilder b, ArrayLoadInstruction instruction) {
    print(b, "   [Array Load] Reading from array");
  }

  @Override
  protected void generateArrayStoreCheck(CodeBuilder b, ArrayStoreInstruction instruction) {
    print(b, "   [Array Store] Writing to array");
  }
}
