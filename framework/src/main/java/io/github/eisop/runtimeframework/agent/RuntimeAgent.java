package io.github.eisop.runtimeframework.agent;

import java.lang.instrument.Instrumentation;

public class RuntimeAgent {
    public static void premain(String args, java.lang.instrument.Instrumentation inst) {
	System.out.println("Agent Initialized");
    }
}
