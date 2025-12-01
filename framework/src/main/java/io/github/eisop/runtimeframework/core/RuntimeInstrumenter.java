package io.github.eisop.runtimeframework.core;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassModel;
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

  // UPDATED: Now accepts ClassLoader to pass to the HierarchyResolver
  public ClassTransform asClassTransform(ClassModel classModel, ClassLoader loader) {
    // We use an anonymous class to implement 'atEnd'
    return new ClassTransform() {
      @Override
      public void accept(ClassBuilder classBuilder, ClassElement classElement) {
        if (classElement instanceof MethodModel methodModel && methodModel.code().isPresent()) {
          classBuilder.transformMethod(
              methodModel,
              (methodBuilder, methodElement) -> {
                if (methodElement instanceof CodeAttribute codeModel) {
                  methodBuilder.withCode(
                      codeBuilder -> {
                        instrumentMethodEntry(codeBuilder, methodModel);
                        for (CodeElement element : codeModel) {
                          if (element instanceof FieldInstruction fInst) {
                            if (isFieldWrite(fInst)) {
                              generateFieldWriteCheck(codeBuilder, fInst, classModel);
                              codeBuilder.with(element);
                            } else if (isFieldRead(fInst)) {
                              codeBuilder.with(element);
                              generateFieldReadCheck(codeBuilder, fInst, classModel);
                            }
                          } else if (element instanceof ReturnInstruction rInst) {
                            generateReturnCheck(codeBuilder, rInst, methodModel);
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
      }

      @Override
      public void atEnd(ClassBuilder builder) {
        // Hook to generate bridge methods after all existing methods are processed
        generateBridgeMethods(builder, classModel, loader);
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
      // REFACTOR: generateParameterCheck (was generateParamCheck)
      generateParameterCheck(builder, slotIndex, type, method, i);
      slotIndex += type.slotSize();
    }
  }

  // --- Abstract Hooks ---

  // REFACTOR: Renamed from generateParamCheck
  protected abstract void generateParameterCheck(
      CodeBuilder b, int slotIndex, TypeKind type, MethodModel method, int paramIndex);

  protected abstract void generateFieldWriteCheck(
      CodeBuilder b, FieldInstruction field, ClassModel classModel);

  protected abstract void generateFieldReadCheck(
      CodeBuilder b, FieldInstruction field, ClassModel classModel);

  protected abstract void generateReturnCheck(
      CodeBuilder b, ReturnInstruction ret, MethodModel method);

  // NEW HOOK: Bridge Generation
  protected abstract void generateBridgeMethods(
      ClassBuilder builder, ClassModel model, ClassLoader loader);
}
