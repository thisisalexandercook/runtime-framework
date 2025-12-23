package io.github.eisop.runtimeframework.util;

import io.github.eisop.runtimeframework.core.RuntimeChecker;
import io.github.eisop.runtimeframework.core.RuntimeInstrumenter;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;

public class SysOutRuntimeChecker extends RuntimeChecker {

  @Override
  public String getName() {
    return "SysOut Debug Checker";
  }

  @Override
  public RuntimeInstrumenter getInstrumenter(Filter<ClassInfo> filter) {
    // SysOut instrumenter ignores the filter (it logs everything)
    return new SysOutInstrumenter();
  }
}
