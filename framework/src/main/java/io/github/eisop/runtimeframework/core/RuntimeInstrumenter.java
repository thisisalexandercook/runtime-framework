package io.github.eisop.runtimeframework.core;

import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;
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
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Modifier;

public abstract class RuntimeInstrumenter {

  protected final Filter<ClassInfo> scopeFilter;

  protected RuntimeInstrumenter(Filter<ClassInfo> scopeFilter) {
    this.scopeFilter = scopeFilter;
  }

  public ClassTransform asClassTransform(ClassModel classModel, ClassLoader loader) {
    // 1. Determine Scope for this class
    boolean isCheckedScope =
        scopeFilter.test(new ClassInfo(classModel.thisClass().asInternalName(), loader, null));

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

                        // GATE: Only check parameters if we are in Checked Code
                        if (isCheckedScope) {
                          instrumentMethodEntry(codeBuilder, methodModel);
                        }

                        for (CodeElement element : codeModel) {
                          if (element instanceof FieldInstruction fInst) {
                            if (isFieldWrite(fInst)) {
                              // Always check writes (Handles Global Write Protection)
                              generateFieldWriteCheck(codeBuilder, fInst, classModel);
                              codeBuilder.with(element);
                            } else if (isFieldRead(fInst)) {
                              codeBuilder.with(element);
                              // GATE: Only check reads if we are in Checked Code
                              if (isCheckedScope) {
                                generateFieldReadCheck(codeBuilder, fInst, classModel);
                              }
                            }
                          } else if (element instanceof ReturnInstruction rInst) {
                            // SPLIT: Checked Return vs. Unchecked Override Return
                            if (isCheckedScope) {
                              generateReturnCheck(codeBuilder, rInst, methodModel);
                            } else {
                              // Pass ClassLoader to help resolve hierarchy
                              generateUncheckedReturnCheck(
                                  codeBuilder, rInst, methodModel, classModel, loader);
                            }
                            codeBuilder.with(element);
                          } else if (element instanceof InvokeInstruction invoke) {
                            codeBuilder.with(element);
                            // GATE: Only check calls if we are in Checked Code
                            if (isCheckedScope) {
                              generateMethodCallCheck(codeBuilder, invoke);
                            }
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
        // GATE: Only generate bridges if we are in Checked Code
        if (isCheckedScope) {
          generateBridgeMethods(builder, classModel, loader);
        }
      }
    };
  }

  protected boolean isFieldWrite(FieldInstruction f) {
    return f.opcode() == Opcode.PUTFIELD || f.opcode() == Opcode.PUTSTATIC;
  }

  protected boolean isFieldRead(FieldInstruction f) {
    return f.opcode() == Opcode.GETFIELD || f.opcode() == Opcode.GETSTATIC;
  }

  protected void instrumentMethodEntry(CodeBuilder builder, MethodModel method) {
    boolean isStatic = (method.flags().flagsMask() & Modifier.STATIC) != 0;
    int slotIndex = isStatic ? 0 : 1;
    MethodTypeDesc methodDesc = method.methodTypeSymbol();
    int paramCount = methodDesc.parameterList().size();

    for (int i = 0; i < paramCount; i++) {
      TypeKind type = TypeKind.from(methodDesc.parameterList().get(i));
      generateParameterCheck(builder, slotIndex, type, method, i);
      slotIndex += type.slotSize();
    }
  }

  // --- Abstract Hooks ---

  protected abstract void generateParameterCheck(
      CodeBuilder b, int slotIndex, TypeKind type, MethodModel method, int paramIndex);

  protected abstract void generateFieldWriteCheck(
      CodeBuilder b, FieldInstruction field, ClassModel classModel);

  protected abstract void generateFieldReadCheck(
      CodeBuilder b, FieldInstruction field, ClassModel classModel);

  protected abstract void generateReturnCheck(
      CodeBuilder b, ReturnInstruction ret, MethodModel method);

  // NEW: Hook for Unchecked classes overriding Checked methods
  protected abstract void generateUncheckedReturnCheck(
      CodeBuilder b,
      ReturnInstruction ret,
      MethodModel method,
      ClassModel classModel,
      ClassLoader loader);

  protected abstract void generateMethodCallCheck(CodeBuilder b, InvokeInstruction invoke);

  protected abstract void generateBridgeMethods(
      ClassBuilder builder, ClassModel model, ClassLoader loader);
}
