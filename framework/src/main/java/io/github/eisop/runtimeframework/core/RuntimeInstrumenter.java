package io.github.eisop.runtimeframework.core;

import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Modifier;

/** The "Worker" that visits methods and injects bytecode. */
public abstract class RuntimeInstrumenter {

  // We will use this later to decide *if* we check a parameter
  // protected final RuntimeCheckPolicy policy;

  // For now, let's just get the traversal working without the policy complexity
  public RuntimeInstrumenter() {}

  /** The main entry point. This returns the function we pass to 'transformClass'. */
  public ClassTransform asClassTransform() {
    return (classBuilder, classElement) -> {

      // 1. Filter: Is this element a Method?
      if (classElement instanceof MethodModel methodModel) {

        // 2. Filter: Does it have a body? (Abstract/Native methods don't)
        if (methodModel.code().isPresent()) {

          // 3. Rebuild the method
          // We recreate the method structure so we can modify its contents
          classBuilder.withMethod(
              methodModel.methodName(),
              methodModel.methodType(),
              methodModel.flags().flagsMask(),
              methodBuilder -> {
                // Iterate over the method's internals (Annotations, Code, etc.)
                for (var element : methodModel) {
                  if (element instanceof CodeAttribute code) {
                    // FOUND THE CODE! -> Rewrite it
                    methodBuilder.withCode(
                        codeBuilder -> {

                          // A. Inject our custom checks at the very top
                          instrumentMethodEntry(codeBuilder, methodModel);

                          // B. Copy the original instructions
                          for (CodeElement ce : code) {
                            codeBuilder.with(ce);
                          }
                        });
                  } else {
                    // Copy annotations/attributes as-is
                    methodBuilder.with(element);
                  }
                }
              });
        } else {
          // It's abstract or native -> Just copy it
          classBuilder.with(classElement);
        }
      } else {
        // It's a Field or Class Attribute -> Just copy it
        classBuilder.with(classElement);
      }
    };
  }

  /** Helper to calculate slot indices and delegate to the specific checker. */
  protected void instrumentMethodEntry(CodeBuilder builder, MethodModel method) {
    // Calculate slot index (Static methods start at 0, Instance methods at 1 for 'this')
    boolean isStatic = (method.flags().flagsMask() & Modifier.STATIC) != 0;
    int slotIndex = isStatic ? 0 : 1;

    MethodTypeDesc methodDesc = method.methodTypeSymbol();
    int paramCount = methodDesc.parameterList().size();

    for (int i = 0; i < paramCount; i++) {
      TypeKind type = TypeKind.from(methodDesc.parameterList().get(i));

      // Call the abstract method to let the subclass insert checks
      generateCheck(builder, slotIndex, type);

      // Advance slot (Double/Long take 2 slots)
      slotIndex += type.slotSize();
    }
  }

  /** The "Hole" to be filled by the specific checker (e.g. NullnessChecker). */
  protected abstract void generateCheck(CodeBuilder builder, int slotIndex, TypeKind type);
}
