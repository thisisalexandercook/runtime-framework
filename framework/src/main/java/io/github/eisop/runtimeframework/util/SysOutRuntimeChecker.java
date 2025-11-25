package io.github.eisop.runtimeframework.util;

import io.github.eisop.runtimeframework.core.RuntimeChecker;
import io.github.eisop.runtimeframework.core.RuntimeInstrumenter;

/**
 * A basic checker for testing and debugging the framework infrastructure.
 *
 * <p>Instead of enforcing safety properties, this checker injects System.out.println statements at
 * every instrumentation point. Use this to verify that the Agent is correctly intercepting and
 * rewriting classes.
 */
public class SysOutRuntimeChecker extends RuntimeChecker {

  @Override
  public String getName() {
    return "SysOut Debug Checker";
  }

  @Override
  public RuntimeInstrumenter getInstrumenter() {
    return new SysOutInstrumenter();
  }
}
