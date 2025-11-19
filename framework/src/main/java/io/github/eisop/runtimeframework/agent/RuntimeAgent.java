package io.github.eisop.runtimeframework.agent;

import io.github.eisop.runtimeframework.filter.ClassListFilter;
import java.lang.instrument.Instrumentation;
import java.util.List;

public final class RuntimeAgent {

  public static void premain(String args, Instrumentation inst) {
    var filter = new ClassListFilter(List.of("HelloWorld"));
    inst.addTransformer(new RuntimeTransformer(filter), false);
  }
}
