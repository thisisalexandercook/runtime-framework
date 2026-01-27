package io.github.eisop.runtimeframework.resolution;

import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;

/**
 * Represents a method found in a parent class during hierarchy resolution. Wraps the ClassModel of
 * the parent and the MethodModel of the method.
 */
public record ParentMethod(ClassModel owner, MethodModel method) {}
