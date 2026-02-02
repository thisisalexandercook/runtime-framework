package io.github.eisop.runtimeframework.util;

import io.github.eisop.runtimeframework.core.RuntimeInstrumenter;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StoreInstruction;
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

  @Override
  protected CodeTransform createCodeTransform(
      ClassModel classModel, MethodModel methodModel, boolean isCheckedScope, ClassLoader loader) {
    return new SysOutTransform();
  }

  @Override
  protected void generateBridgeMethods(ClassBuilder builder, ClassModel model, ClassLoader loader) {
    // Debug instrumenter does not generate bridges
  }

  private static void print(CodeBuilder b, String msg) {
    b.getstatic(SYSOUT_SYSTEM, "out", SYSOUT_STREAM);
    b.ldc(msg);
    b.invokevirtual(SYSOUT_STREAM, "println", SYSOUT_PRINTLN);
  }

  private static class SysOutTransform implements CodeTransform {

    @Override
    public void accept(CodeBuilder b, CodeElement element) {
      if (element instanceof FieldInstruction f) {
        if (isWrite(f)) {
          print(b, "   [Field Write] About to write to: " + f.name().stringValue());
          b.with(element);
        } else {
          b.with(element);
          print(b, "   [Field Read] Just read from: " + f.name().stringValue());
        }
      } else if (element instanceof ReturnInstruction r) {
        print(b, "   [Return Check] Returning from method via opcode: " + r.opcode().name());
        b.with(element);
      } else if (element instanceof InvokeInstruction i) {
        b.with(element);
        print(
            b,
            "   [Call Site] Just called: "
                + i.owner().asInternalName()
                + "."
                + i.name().stringValue());
      } else if (element instanceof ArrayLoadInstruction) {
        b.with(element);
        print(b, "   [Array Load] Reading from array");
      } else if (element instanceof ArrayStoreInstruction) {
        print(b, "   [Array Store] Writing to array");
        b.with(element);
      } else if (element instanceof StoreInstruction s) {
        print(b, "   [Local Store] Writing to slot " + s.slot());
        b.with(element);
      } else {
        b.with(element);
      }
    }

    private boolean isWrite(FieldInstruction f) {
      return f.opcode().name().startsWith("PUT");
    }
  }
}
