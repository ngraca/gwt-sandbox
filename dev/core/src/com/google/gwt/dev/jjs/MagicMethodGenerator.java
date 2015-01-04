/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.gwt.dev.jjs;


import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.dev.jjs.ast.Context;
import com.google.gwt.dev.jjs.ast.JExpression;
import com.google.gwt.dev.jjs.ast.JMethod;
import com.google.gwt.dev.jjs.ast.JMethodCall;

/**
 * This is the expected type signature for magic method injectors;
 * you do not need to explicitly implement this interface,
 * or the suggested method name; however, implementing this interface
 * allows you shorter gwt module xml, since you are not required to supply
 * the full method name.
 *
 *  The full, brutal syntax to add magic method injection is as follows:
 <pre>
  &lt;extend-configuration-property name="gwt.magic.methods"
    value="jsni.method.ref.To.replace(Ljsni/param/Signature;)Ljsni/returned/Signature; *= type.of.magic.method.Generator::staticGenerateMethod"/>
 </pre>
 * You may omit the ::staticGenerateMethod if your type implements {@link MagicMethodGenerator}.
 *
 * The first half of the value is the fully qualified jsni signature of the method you wish to overwrite:
 * com.package.Clazz.method(Ljsni/param/Signatures;)Ljsni/return/Type;
 * If your parameters or return type are generic, supply the erased type (whatever the jsni syntax would be)
 *
 * In the middle, we use " *= ", without quotes, as the delimiter between signatures.
 *
 * The second half of the value is the generator for the AST injection,
 * either a Fully Qualified Class Name that does implement MagicMethodGenerator
 * or a fully qualified static method that has the same parameter signature as
 * {@link #injectMagic(TreeLogger, JMethodCall, JMethod, Context, UnifyAstView)}
 *
 * com.package.Clazz   // implements MagicMethodGenerator
 * or
 * com.package.Clazz::staticMethodName  // static method, same param signature as injectMagic
 *
 * This type signature will be maintained for gwt.magic.method configuration property,
 * and if we change the api to be more concise or to give you access to more runtime data,
 * we will provide a new configuration property (and possible make the old one emit deprecation warnings).
 *
 * @author James X. Nelson (james@wetheinter.net, @james)
 *
 */
public interface MagicMethodGenerator{
  /**
   * This is the magic method type signature used to replace a given JMethodCall
   * with generated AST expressions or generated classes using StandardGeneratorContext
   * obtained using {@link UnifyAstView#getRebindPermutationOracle()}.
   * <p>
   * Note that magic methods suffer from the same requirement as GWT.create:
   * if you want to know what a class is at generate time, you must accept a
   * class literal, and not a class reference.
   * <p>
   * If you wish to generate code that accepts class references, you must
   * generate some kind of collection-backed factory method which can accept
   * your class ref and do something useful based on runtime analysis.
   * <p>
   * A generator that accepts ONLY class literals will be capable of inlining
   * all requested code at the call site,
   * rather than monolithic-compiling a bunch of code into a given factory.
   * <p>
   * If you absolutely must send a class reference and need type data at generation time,
   * consider annotating the methods calling your magic method,
   * since class literals in parameter annotations are simple to extract
   * (you are given the encapsulating method call).
   * <p>
   * NOTE THAT MAGIC METHOD INJECTION DOES NOT WORK IN REGULAR GWT DEV MODE,
   * (or any pure java runtime).
   * <p>
   * It is a production-mode only enhancement,
   * and combined with other xapi modules,
   * can allow you to use reflection or other jvm-only features from shared code.
   * <p>
   * A typical cross-platform use case is to have the method body use GWT.isClient,
   * let the jvm do standard reflection,
   * (i.e. getting a class name from system properties, and reflectively calling a method).
   * while gwt dev can use GWT.create (and share generator code w/ magic method injector).
   * <p>
   * You may not put code that is uncompilable in gwt behind a magic method call;
   * dev mode and ast reader still has to choke it down.
   * If you want to use reflection, include
   * net.wetheinter:xapi-gwt-api (or xapi-gwt uber jar),
   * which include emulated support for the java reflection api.
   * <p>
   * You are recommended to use super dev mode;
   * Given that dev mode performance doesn't affect production mode performance,
   * we personally only ever start dev mode during testing and to use the java debugger.
   * <p>
   * @param logger - The logger to log to.
   * @param methodCall - The method call we are overwriting
   * @param enclosingMethod - The method encapsulating the method call we are replacing.
   * @param context - The method call context, so you can insert clinits / whatnot
   * <p>
   * DO NOT CALL context.replaceMe() or context.removeMe(); return a value instead.
   * @param ast - A view over UnifyAst, exposing our basic needs
   * @return - A JExpression to replace the method call with
   *
   * @see {@link MagicMethodTestGenerator} for a reference implementation.
   */
  JExpression injectMagic(TreeLogger logger, JMethodCall methodCall,
      JMethod enclosingMethod, Context context, UnifyAstView ast)
        throws UnableToCompleteException;
}