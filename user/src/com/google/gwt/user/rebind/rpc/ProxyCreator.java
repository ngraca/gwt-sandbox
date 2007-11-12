/*
 * Copyright 2007 Google Inc.
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
package com.google.gwt.user.rebind.rpc;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JMethod;
import com.google.gwt.core.ext.typeinfo.JPackage;
import com.google.gwt.core.ext.typeinfo.JParameter;
import com.google.gwt.core.ext.typeinfo.JPrimitiveType;
import com.google.gwt.core.ext.typeinfo.JType;
import com.google.gwt.core.ext.typeinfo.TypeOracle;
import com.google.gwt.dev.generator.NameFactory;
import com.google.gwt.dev.util.Util;
import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.client.rpc.impl.RemoteServiceProxy;
import com.google.gwt.user.client.rpc.impl.ClientSerializationStreamReader;
import com.google.gwt.user.client.rpc.impl.ClientSerializationStreamWriter;
import com.google.gwt.user.client.rpc.impl.RequestCallbackAdapter.ResponseReader;
import com.google.gwt.user.rebind.ClassSourceFileComposerFactory;
import com.google.gwt.user.rebind.SourceWriter;
import com.google.gwt.user.server.rpc.SerializationPolicyLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates a client-side proxy for a
 * {@link com.google.gwt.user.client.rpc.RemoteService RemoteService} interface
 * as well as the necessary type and field serializers.
 */
class ProxyCreator {
  private static final String ENTRY_POINT_TAG = "gwt.defaultEntryPoint";

  private static final Map<JPrimitiveType, ResponseReader> JPRIMITIVETYPE_TO_RESPONSEREADER = new HashMap<JPrimitiveType, ResponseReader>();

  private static final String PROXY_SUFFIX = "_Proxy";

  private boolean enforceTypeVersioning;

  private JClassType serviceIntf;

  {
    JPRIMITIVETYPE_TO_RESPONSEREADER.put(JPrimitiveType.BOOLEAN,
        ResponseReader.BOOLEAN);
    JPRIMITIVETYPE_TO_RESPONSEREADER.put(JPrimitiveType.BYTE,
        ResponseReader.BYTE);
    JPRIMITIVETYPE_TO_RESPONSEREADER.put(JPrimitiveType.CHAR,
        ResponseReader.CHAR);
    JPRIMITIVETYPE_TO_RESPONSEREADER.put(JPrimitiveType.DOUBLE,
        ResponseReader.DOUBLE);
    JPRIMITIVETYPE_TO_RESPONSEREADER.put(JPrimitiveType.FLOAT,
        ResponseReader.FLOAT);
    JPRIMITIVETYPE_TO_RESPONSEREADER.put(JPrimitiveType.INT, ResponseReader.INT);
    JPRIMITIVETYPE_TO_RESPONSEREADER.put(JPrimitiveType.LONG,
        ResponseReader.LONG);
    JPRIMITIVETYPE_TO_RESPONSEREADER.put(JPrimitiveType.SHORT,
        ResponseReader.SHORT);
    JPRIMITIVETYPE_TO_RESPONSEREADER.put(JPrimitiveType.VOID,
        ResponseReader.VOID);
  }

  public ProxyCreator(JClassType serviceIntf) {
    assert (serviceIntf.isInterface() != null);
    this.serviceIntf = serviceIntf;
  }

  /**
   * Creates the client-side proxy class.
   * 
   * @throws UnableToCompleteException
   */
  public String create(TreeLogger logger, GeneratorContext context)
      throws UnableToCompleteException {
    TypeOracle typeOracle = context.getTypeOracle();

    JClassType serviceAsync = typeOracle.findType(serviceIntf.getQualifiedSourceName()
        + "Async");
    if (serviceAsync == null) {
      logger.branch(TreeLogger.ERROR,
          "Could not find an asynchronous version for the service interface "
              + serviceIntf.getQualifiedSourceName(), null);
      RemoteServiceAsyncValidator.logValidAsyncInterfaceDeclaration(logger,
          serviceIntf);
      throw new UnableToCompleteException();
    }

    SourceWriter srcWriter = getSourceWriter(logger, context, serviceAsync);
    if (srcWriter == null) {
      return getProxyQualifiedName();
    }

    // Make sure that the async and synchronous versions of the RemoteService
    // agree with one another
    //
    RemoteServiceAsyncValidator rsav = new RemoteServiceAsyncValidator(logger,
        typeOracle);
    Map<JMethod, JMethod> syncMethToAsyncMethMap = rsav.validate(logger,
        serviceIntf, serviceAsync);

    // Determine the set of serializable types
    SerializableTypeOracleBuilder stob = new SerializableTypeOracleBuilder(
        logger, typeOracle);
    SerializableTypeOracle sto = stob.build(context.getPropertyOracle(),
        serviceIntf);

    TypeSerializerCreator tsc = new TypeSerializerCreator(logger, sto, context,
        serviceIntf);
    tsc.realize(logger);

    enforceTypeVersioning = Shared.shouldEnforceTypeVersioning(logger,
        context.getPropertyOracle());

    String serializationPolicyStrongName = writeSerializationPolicyFile(logger,
        context, sto);

    generateProxyFields(srcWriter, sto, serializationPolicyStrongName);

    String typeSerializerName = sto.getTypeSerializerQualifiedName(serviceIntf);
    generateProxyContructor(srcWriter, sto, serializationPolicyStrongName,
        typeSerializerName);

    generateProxyMethods(srcWriter, sto, syncMethToAsyncMethMap);

    srcWriter.commit(logger);

    return getProxyQualifiedName();
  }

  /**
   * Generate the proxy constructor and delegate to the superclass constructor
   * using the default address for the
   * {@link com.google.gwt.user.client.rpc.RemoteService RemoteService}.
   */
  private void generateProxyContructor(SourceWriter srcWriter,
      SerializableTypeOracle serializableTypeOracle,
      String serializationPolicyStrongName, String typeSerializerName) {
    srcWriter.println("public " + getProxySimpleName() + "() {");
    srcWriter.indent();
    srcWriter.println("super(GWT.getModuleBaseURL(),");
    srcWriter.indent();
    srcWriter.println(getDefaultServiceDefName() + ",");
    srcWriter.println("SERIALIZATION_POLICY, ");
    srcWriter.println("REMOTE_SERVICE_INTERFACE_NAME, ");
    srcWriter.println("SERIALIZER);");
    srcWriter.outdent();
    srcWriter.outdent();
    srcWriter.println("}");
  }

  /**
   * Generate any fields required by the proxy.
   */
  private void generateProxyFields(SourceWriter srcWriter,
      SerializableTypeOracle serializableTypeOracle,
      String serializationPolicyStrongName) {
    // Initialize a field with binary name of the remote service interface
    srcWriter.println("private static final String REMOTE_SERVICE_INTERFACE_NAME = \""
        + serializableTypeOracle.getSerializedTypeName(serviceIntf) + "\";");
    srcWriter.println("private static final String SERIALIZATION_POLICY =\""
        + serializationPolicyStrongName + "\";");
    String typeSerializerName = serializableTypeOracle.getTypeSerializerQualifiedName(serviceIntf);
    srcWriter.println("private static final " + typeSerializerName
        + " SERIALIZER = new " + typeSerializerName + "();");
    srcWriter.println();
  }

  /**
   * Generates the client's asynchronous proxy method.
   */
  private void generateProxyMethod(SourceWriter w,
      SerializableTypeOracle serializableTypeOracle, JMethod syncMethod,
      JMethod asyncMethod) {

    w.println();

    // Write the method signature
    JType asyncReturnType = asyncMethod.getReturnType();
    w.print("public ");
    w.print(asyncReturnType.getQualifiedSourceName());
    w.print(" ");
    w.print(asyncMethod.getName() + "(");

    boolean needsComma = false;
    boolean needsTryCatchBlock = false;
    NameFactory nameFactory = new NameFactory();
    JParameter[] asyncParams = asyncMethod.getParameters();
    for (int i = 0; i < asyncParams.length; ++i) {
      JParameter param = asyncParams[i];

      if (needsComma) {
        w.print(", ");
      } else {
        needsComma = true;
      }

      /*
       * Ignoring the AsyncCallback parameter, if any method requires a call to
       * SerializationStreamWriter.writeObject we need a try catch block
       */
      JType paramType = param.getType();
      if (i < asyncParams.length - 1
          && paramType.isPrimitive() == null
          && !paramType.getQualifiedSourceName().equals(
              String.class.getCanonicalName())) {
        needsTryCatchBlock = true;
      }

      w.print(paramType.getParameterizedQualifiedSourceName());
      w.print(" ");

      String paramName = param.getName();
      nameFactory.addName(paramName);
      w.print(paramName);
    }

    w.println(") {");

    w.indent();

    w.print(ClientSerializationStreamWriter.class.getSimpleName());
    w.print(" ");
    String streamWriterName = nameFactory.createName("streamWriter");
    w.println(streamWriterName + " = createStreamWriter();");
    w.println("// createStreamWriter() prepared the stream and wrote the name of RemoteService");
    if (needsTryCatchBlock) {
      w.println("try {");
      w.indent();
    }

    if (!shouldEnforceTypeVersioning()) {
      w.println(streamWriterName + ".addFlags("
          + ClientSerializationStreamReader.class.getName()
          + ".SERIALIZATION_STREAM_FLAGS_NO_TYPE_VERSIONING);");
    }

    // Write the method name
    w.println(streamWriterName + ".writeString(\"" + syncMethod.getName()
        + "\");");

    // Write the parameter count followed by the parameter values
    JParameter[] syncParams = syncMethod.getParameters();
    w.println(streamWriterName + ".writeInt(" + syncParams.length + ");");
    for (JParameter param : syncParams) {
      w.println(streamWriterName + ".writeString(\""
          + serializableTypeOracle.getSerializedTypeName(param.getType())
          + "\");");
    }

    // Encode the arguments.
    //
    for (JParameter param : syncParams) {
      JType paramType = param.getType();
      w.print(streamWriterName + ".");
      w.print(Shared.getStreamWriteMethodNameFor(paramType));
      w.println("(" + param.getName() + ");");
    }

    JParameter callbackParam = asyncParams[asyncParams.length - 1];
    String callbackName = callbackParam.getName();
    if (needsTryCatchBlock) {
      w.outdent();
      w.print("} catch (SerializationException ");
      String exceptionName = nameFactory.createName("ex");
      w.println(exceptionName + ") {");
      w.indent();
      w.println(callbackName + ".onFailure(" + exceptionName + ");");
      w.outdent();
      w.println("}");
    }

    w.println();
    if (asyncReturnType != JPrimitiveType.VOID) {
      w.print("return ");
    }

    // Call the doInvoke method to actually send the request.
    w.print("doInvoke(");
    JType returnType = syncMethod.getReturnType();
    w.print("ResponseReader." + getResponseReaderFor(returnType).name());
    w.print(", " + streamWriterName + ".toString(), ");
    w.println(callbackName + ");");
    w.outdent();
    w.println("}");
  }

  private void generateProxyMethods(SourceWriter w,
      SerializableTypeOracle serializableTypeOracle,
      Map<JMethod, JMethod> syncMethToAsyncMethMap) {
    JMethod[] syncMethods = serviceIntf.getOverridableMethods();
    for (JMethod syncMethod : syncMethods) {
      JMethod asyncMethod = syncMethToAsyncMethMap.get(syncMethod);
      assert (asyncMethod != null);

      generateProxyMethod(w, serializableTypeOracle, syncMethod, asyncMethod);
    }
  }

  private String getDefaultServiceDefName() {
    String[][] metaData = serviceIntf.getMetaData(ENTRY_POINT_TAG);
    if (metaData.length == 0) {
      return null;
    }

    return serviceIntf.getMetaData(ENTRY_POINT_TAG)[0][0];
  }

  private String getProxyQualifiedName() {
    String[] name = Shared.synthesizeTopLevelClassName(serviceIntf,
        PROXY_SUFFIX);
    return name[0].length() == 0 ? name[1] : name[0] + "." + name[1];
  }

  private String getProxySimpleName() {
    String[] name = Shared.synthesizeTopLevelClassName(serviceIntf,
        PROXY_SUFFIX);
    return name[1];
  }

  private ResponseReader getResponseReaderFor(JType returnType) {
    if (returnType.isPrimitive() != null) {
      return JPRIMITIVETYPE_TO_RESPONSEREADER.get(returnType.isPrimitive());
    }

    if (returnType.getQualifiedSourceName().equals(
        String.class.getCanonicalName())) {
      return ResponseReader.STRING;
    }

    return ResponseReader.OBJECT;
  }

  private SourceWriter getSourceWriter(TreeLogger logger, GeneratorContext ctx,
      JClassType serviceAsync) {
    JPackage serviceIntfPkg = serviceAsync.getPackage();
    String packageName = serviceIntfPkg == null ? "" : serviceIntfPkg.getName();
    PrintWriter printWriter = ctx.tryCreate(logger, packageName,
        getProxySimpleName());
    if (printWriter == null) {
      return null;
    }

    ClassSourceFileComposerFactory composerFactory = new ClassSourceFileComposerFactory(
        packageName, getProxySimpleName());

    String[] imports = new String[] {
        RemoteServiceProxy.class.getCanonicalName(),
        ClientSerializationStreamWriter.class.getCanonicalName(),
        GWT.class.getCanonicalName(), ResponseReader.class.getCanonicalName(),
        SerializationException.class.getCanonicalName()};
    for (String imp : imports) {
      composerFactory.addImport(imp);
    }

    composerFactory.setSuperclass(RemoteServiceProxy.class.getSimpleName());
    composerFactory.addImplementedInterface(serviceAsync.getParameterizedQualifiedSourceName());

    return composerFactory.createSourceWriter(ctx, printWriter);
  }

  private boolean shouldEnforceTypeVersioning() {
    return enforceTypeVersioning;
  }

  private String writeSerializationPolicyFile(TreeLogger logger,
      GeneratorContext ctx, SerializableTypeOracle sto)
      throws UnableToCompleteException {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      OutputStreamWriter osw = new OutputStreamWriter(baos,
          SerializationPolicyLoader.SERIALIZATION_POLICY_FILE_ENCODING);
      PrintWriter pw = new PrintWriter(osw);

      JType[] serializableTypes = sto.getSerializableTypes();
      for (int i = 0; i < serializableTypes.length; ++i) {
        JType serializableType = serializableTypes[i];
        String binaryTypeName = sto.getSerializedTypeName(serializableType);
        boolean maybeInstantiated = sto.maybeInstantiated(serializableType);
        pw.println(binaryTypeName + ", " + Boolean.toString(maybeInstantiated));
      }

      // Closes the wrapped streams.
      pw.close();

      byte[] serializationPolicyFileContents = baos.toByteArray();
      String serializationPolicyName = Util.computeStrongName(serializationPolicyFileContents);

      String serializationPolicyFileName = SerializationPolicyLoader.getSerializationPolicyFileName(serializationPolicyName);
      OutputStream os = ctx.tryCreateResource(logger,
          serializationPolicyFileName);
      if (os != null) {
        os.write(serializationPolicyFileContents);
        ctx.commitResource(logger, os);
      } else {
        logger.log(TreeLogger.TRACE,
            "SerializationPolicy file for RemoteService '"
                + serviceIntf.getQualifiedSourceName()
                + "' already exists; no need to rewrite it.", null);
      }

      return serializationPolicyName;
    } catch (UnsupportedEncodingException e) {
      logger.log(TreeLogger.ERROR,
          SerializationPolicyLoader.SERIALIZATION_POLICY_FILE_ENCODING
              + " is not supported", e);
      throw new UnableToCompleteException();
    } catch (IOException e) {
      logger.log(TreeLogger.ERROR, null, e);
      throw new UnableToCompleteException();
    }
  }
}
