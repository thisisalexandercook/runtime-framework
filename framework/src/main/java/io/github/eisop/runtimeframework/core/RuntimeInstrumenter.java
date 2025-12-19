package io.github.eisop.runtimeframework.core;

import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StoreInstruction; // NEW
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Modifier;

public abstract class RuntimeInstrumenter {

  protected final Filter<ClassInfo> scopeFilter;

  protected RuntimeInstrumenter(Filter<ClassInfo> scopeFilter) {
    this.scopeFilter = scopeFilter;
  }

  public ClassTransform asClassTransform(ClassModel classModel, ClassLoader loader) {
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
                        boolean entryChecksDone = !isCheckedScope;

                        for (CodeElement element : codeModel) {
                          // Inject entry checks after first LineNumber to ensure valid stack traces
                          if (!entryChecksDone && element instanceof LineNumber) {
                            codeBuilder.with(element);
                            instrumentMethodEntry(codeBuilder, methodModel);
                            entryChecksDone = true;
                            continue;
                          }
                          if (!entryChecksDone && element instanceof Instruction) {
                            instrumentMethodEntry(codeBuilder, methodModel);
                            entryChecksDone = true;
                          }

                          if (element instanceof FieldInstruction fInst) {
                            if (isFieldWrite(fInst)) {
                              generateFieldWriteCheck(codeBuilder, fInst, classModel);
                              codeBuilder.with(element);
                            } else if (isFieldRead(fInst)) {
                              codeBuilder.with(element);
                              if (isCheckedScope) {
                                // generateFieldReadCheck(codeBuilder, fInst, classModel);
                                // Currently disabling field read checks as the GETFIELD
                                // and GETSTATIC instructions are not actually dangerous
                                // on their own. Its when we STORE a field we read from
                                // that an issue could arise
                              }
                            }
                          } else if (element instanceof ReturnInstruction rInst) {
                            if (isCheckedScope) {
                              generateReturnCheck(codeBuilder, rInst, methodModel);
                            } else {
                              generateUncheckedReturnCheck(
                                  codeBuilder, rInst, methodModel, classModel, loader);
                            }
                            codeBuilder.with(element);
                          } else if (element instanceof InvokeInstruction invoke) {
                            codeBuilder.with(element);
                            if (isCheckedScope) {
                              generateMethodCallCheck(codeBuilder, invoke);
                            }
                          } else if (element instanceof ArrayStoreInstruction astore) {
                            generateArrayStoreCheck(codeBuilder, astore);
                            codeBuilder.with(element);
                          } else if (element instanceof ArrayLoadInstruction aload) {
                            codeBuilder.with(element);
                            if (isCheckedScope) {
                              generateArrayLoadCheck(codeBuilder, aload);
                            }
                          } else if (element
                              instanceof StoreInstruction store) { // NEW: Store Check
                            // GATE: Only check local vars in Checked Code
                            if (isCheckedScope) {
                              generateStoreCheck(codeBuilder, store, methodModel);
                            }
                            codeBuilder.with(element);
                          } else {
                            codeBuilder.with(element);
                          }
                        }

                        if (!entryChecksDone && isCheckedScope) {
                          instrumentMethodEntry(codeBuilder, methodModel);
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

  protected abstract void generateUncheckedReturnCheck(
      CodeBuilder b,
      ReturnInstruction ret,
      MethodModel method,
      ClassModel classModel,
      ClassLoader loader);

  protected abstract void generateMethodCallCheck(CodeBuilder b, InvokeInstruction invoke);

  protected abstract void generateBridgeMethods(
      ClassBuilder builder, ClassModel model, ClassLoader loader);

  protected abstract void generateArrayStoreCheck(CodeBuilder b, ArrayStoreInstruction instruction);

  protected abstract void generateArrayLoadCheck(CodeBuilder b, ArrayLoadInstruction instruction);

  protected abstract void generateStoreCheck(
      CodeBuilder b, StoreInstruction instruction, MethodModel method);
}
