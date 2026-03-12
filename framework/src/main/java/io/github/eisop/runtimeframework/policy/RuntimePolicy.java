package io.github.eisop.runtimeframework.policy;

import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.planning.FlowEvent;
import java.lang.classfile.ClassModel;

/** Defines runtime policy decisions for class classification and instrumentation scope. */
public interface RuntimePolicy {

  ClassClassification classify(ClassInfo info);

  ClassClassification classify(ClassInfo info, ClassModel model);

  boolean isGlobalMode();

  default boolean shouldTransform(ClassInfo info) {
    return classify(info) != ClassClassification.SKIP;
  }

  default boolean shouldTransform(ClassInfo info, ClassModel model) {
    return classify(info, model) != ClassClassification.SKIP;
  }

  default boolean isChecked(ClassInfo info) {
    return classify(info) == ClassClassification.CHECKED;
  }

  default boolean isChecked(ClassInfo info, ClassModel model) {
    return classify(info, model) == ClassClassification.CHECKED;
  }

  default boolean allows(FlowEvent event) {
    return true;
  }
}
