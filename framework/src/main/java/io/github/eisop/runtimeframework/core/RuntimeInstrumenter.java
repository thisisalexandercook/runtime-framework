package io.github.eisop.runtimeframework.core;

import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Modifier;

public abstract class RuntimeInstrumenter {

  public RuntimeInstrumenter() {}

  public ClassTransform asClassTransform() {
    return (classBuilder, classElement) -> {
      // 1. Only process Methods
      if (classElement instanceof MethodModel methodModel) {

        // 2. Only process methods with Code (skips abstract/native)
        if (methodModel.code().isPresent()) {
          classBuilder.transformMethod(
              methodModel,
              (methodBuilder, methodElement) -> {

                // 3. Only process the Code attribute
                if (methodElement instanceof CodeAttribute codeModel) {
                  methodBuilder.withCode(
                      codeBuilder -> {

                        // PHASE 1: Inject Entry Checks (Parameters)
                        instrumentMethodEntry(codeBuilder, methodModel);

                        // PHASE 2: Stream Instructions (Field Checks)
                        for (CodeElement element : codeModel) {
                          if (element instanceof FieldInstruction fInst && isFieldWrite(fInst)) {

                            // A. Inject check BEFORE the write
                            generateFieldWriteCheck(codeBuilder, fInst);

                            // B. Write the original instruction
                            codeBuilder.with(element);

                          } else {
                            // Pass everything else through unchanged
                            codeBuilder.with(element);
                          }
                        }
                      });
                } else {
                  methodBuilder.with(methodElement);
                }
              });
        } else {
          classBuilder.with(classElement);
        }
      } else {
        classBuilder.with(classElement);
      }
    };
  }

  private boolean isFieldWrite(FieldInstruction f) {
    return f.opcode() == Opcode.PUTFIELD || f.opcode() == Opcode.PUTSTATIC;
  }

  // --- Helper for Method Entry ---
  protected void instrumentMethodEntry(CodeBuilder builder, MethodModel method) {
    boolean isStatic = (method.flags().flagsMask() & Modifier.STATIC) != 0;
    int slotIndex = isStatic ? 0 : 1;

    MethodTypeDesc methodDesc = method.methodTypeSymbol();
    int paramCount = methodDesc.parameterList().size();

    for (int i = 0; i < paramCount; i++) {
      TypeKind type = TypeKind.from(methodDesc.parameterList().get(i));
      generateParamCheck(builder, slotIndex, type);
      slotIndex += type.slotSize();
    }
  }

  // --- Abstract Hooks for Subclasses ---

  /** Called at the start of the method for every parameter. */
  protected abstract void generateParamCheck(CodeBuilder b, int slotIndex, TypeKind type);

  /** Called immediately before a PUTFIELD or PUTSTATIC instruction. */
  protected abstract void generateFieldWriteCheck(CodeBuilder b, FieldInstruction field);
}
