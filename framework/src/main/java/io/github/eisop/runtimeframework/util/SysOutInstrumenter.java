package io.github.eisop.runtimeframework.util;

import io.github.eisop.runtimeframework.core.RuntimeInstrumenter;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

public class SysOutInstrumenter extends RuntimeInstrumenter {

  private static final ClassDesc SYSOUT_SYSTEM = ClassDesc.of("java.lang.System");
  private static final ClassDesc SYSOUT_STREAM = ClassDesc.of("java.io.PrintStream");
  private static final MethodTypeDesc SYSOUT_PRINTLN =
      MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V");

  public SysOutInstrumenter() {
    super();
  }

  private void print(CodeBuilder b, String msg) {
    b.getstatic(SYSOUT_SYSTEM, "out", SYSOUT_STREAM);
    b.ldc(msg);
    b.invokevirtual(SYSOUT_STREAM, "println", SYSOUT_PRINTLN);
  }

  @Override
  protected void generateParamCheck(CodeBuilder b, int slotIndex, TypeKind type) {
    print(b, "   [Param Check] Verifying argument at slot " + slotIndex);
  }

  @Override
  protected void generateFieldWriteCheck(CodeBuilder b, FieldInstruction field) {
    print(b, "   [Field Write] About to write to: " + field.name().stringValue());
  }

  @Override
  protected void generateFieldReadCheck(CodeBuilder b, FieldInstruction field) {
    print(b, "   [Field Read] Just read from: " + field.name().stringValue());
  }

  @Override
  protected void generateReturnCheck(CodeBuilder b, ReturnInstruction ret) {
    print(b, "   [Return Check] Returning from method via opcode: " + ret.opcode().name());
  }
}
