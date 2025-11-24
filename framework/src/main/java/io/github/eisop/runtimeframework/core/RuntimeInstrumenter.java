package io.github.eisop.runtimeframework.core;

import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Modifier;

public abstract class RuntimeInstrumenter {

  public RuntimeInstrumenter() {}

  public ClassTransform asClassTransform() {
    return (classBuilder, classElement) -> {
      if (classElement instanceof MethodModel methodModel && methodModel.code().isPresent()) {
        classBuilder.transformMethod(
            methodModel,
            (methodBuilder, methodElement) -> {
              if (methodElement instanceof CodeAttribute codeModel) {
                methodBuilder.withCode(
                    codeBuilder -> {

                      // PHASE 1: Method Entry
                      instrumentMethodEntry(codeBuilder, methodModel);

                      // PHASE 2: Instruction Stream
                      for (CodeElement element : codeModel) {

                        if (element instanceof FieldInstruction fInst) {
                          if (isFieldWrite(fInst)) {
                            // WRITE: Check BEFORE (Value is on stack)
                            generateFieldWriteCheck(codeBuilder, fInst);
                            codeBuilder.with(element);
                          } else if (isFieldRead(fInst)) {
                            // READ: Check AFTER (Value has just landed on stack)
                            codeBuilder.with(element);
                            generateFieldReadCheck(codeBuilder, fInst);
                          }
                        } else if (element instanceof ReturnInstruction rInst) {
                          // RETURN: Check BEFORE (Value is on stack)
                          generateReturnCheck(codeBuilder, rInst);
                          codeBuilder.with(element);
                        } else {
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
    };
  }

  private boolean isFieldWrite(FieldInstruction f) {
    return f.opcode() == Opcode.PUTFIELD || f.opcode() == Opcode.PUTSTATIC;
  }

  private boolean isFieldRead(FieldInstruction f) {
    return f.opcode() == Opcode.GETFIELD || f.opcode() == Opcode.GETSTATIC;
  }

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

  protected abstract void generateParamCheck(CodeBuilder b, int slotIndex, TypeKind type);

  protected abstract void generateFieldWriteCheck(CodeBuilder b, FieldInstruction field);

  protected abstract void generateFieldReadCheck(CodeBuilder b, FieldInstruction field);

  protected abstract void generateReturnCheck(CodeBuilder b, ReturnInstruction ret);
}
