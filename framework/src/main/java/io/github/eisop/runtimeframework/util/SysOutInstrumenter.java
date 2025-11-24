package io.github.eisop.runtimeframework.util;

import io.github.eisop.runtimeframework.core.RuntimeInstrumenter;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.FieldInstruction;
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

  @Override
  protected void generateParamCheck(CodeBuilder b, int slotIndex, TypeKind type) {
    b.getstatic(SYSOUT_SYSTEM, "out", SYSOUT_STREAM);
    b.ldc("   [Param Check] Verifying argument at slot " + slotIndex);
    b.invokevirtual(SYSOUT_STREAM, "println", SYSOUT_PRINTLN);
  }

  @Override
  protected void generateFieldWriteCheck(CodeBuilder b, FieldInstruction field) {
    // 1. Load System.out
    b.getstatic(SYSOUT_SYSTEM, "out", SYSOUT_STREAM);

    // 2. Load the message string
    b.ldc("   [Field Check] About to write to field: " + field.name().stringValue());

    // 3. Print (Stack is now clean, original value for PUTFIELD is underneath)
    b.invokevirtual(SYSOUT_STREAM, "println", SYSOUT_PRINTLN);
  }
}
