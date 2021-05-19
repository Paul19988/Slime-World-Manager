package com.grinderwolf.swm.clsm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

public final class NMSTransformer implements ClassFileTransformer {

  private static final boolean DEBUG = Boolean.getBoolean("clsmDebug");

  private static final Pattern PATTERN = Pattern.compile("^(\\w+)\\s*\\((.*?)\\)\\s*@(.+?\\.txt)$");

  private static final Map<String, Change[]> changes = new HashMap<>();

  public static void premain(@NotNull final String agentArgs, @NotNull final Instrumentation instrumentation) {
    instrumentation.addTransformer(new NMSTransformer());
    try (final InputStream fileStream = NMSTransformer.class.getResourceAsStream("/list.yml")) {
      if (fileStream == null) {
        System.err.println("Failed to find change list.");
        System.exit(1);
        return;
      }
      final Yaml yaml = new Yaml();
      try (final InputStreamReader reader = new InputStreamReader(fileStream)) {
        final Map<String, Object> data = yaml.load(reader);
        for (final String originalClazz : data.keySet()) {
          final boolean optional = originalClazz.startsWith("__optional__");
          final String clazz = originalClazz.substring(optional ? 12 : 0);
          if (!(data.get(originalClazz) instanceof ArrayList)) {
            System.err.println("Invalid change list for class " + clazz + ".");
            continue;
          }
          final List<String> changeList = (List<String>) data.get(originalClazz);
          final Change[] changeArray = new Change[changeList.size()];
          for (int i = 0; i < changeList.size(); i++) {
            final Matcher matcher = NMSTransformer.PATTERN.matcher(changeList.get(i));
            if (!matcher.find()) {
              System.err.println("Invalid change '" + changeList.get(i) + "' on class " + clazz + ".");
              System.exit(1);
            }
            final String methodName = matcher.group(1);
            final String paramsString = matcher.group(2).trim();
            final String[] parameters;
            if (paramsString.isEmpty()) {
              parameters = new String[0];
            } else {
              parameters = matcher.group(2).split(",");
            }
            final String location = matcher.group(3);
            final String content;
            try (final InputStream changeStream = NMSTransformer.class.getResourceAsStream("/" + location)) {
              if (changeStream == null) {
                System.err.println("Failed to find data for change " + changeList.get(i) + " on class " + clazz + ".");
                System.exit(1);
              }
              final byte[] contentByteArray = NMSTransformer.readAllBytes(changeStream);
              content = new String(contentByteArray, StandardCharsets.UTF_8);
            }
            changeArray[i] = new Change(content, methodName, optional, parameters);
          }
          if (NMSTransformer.DEBUG) {
            System.out.println("Loaded " + changeArray.length + " changes for class " + clazz + ".");
          }
          final Change[] oldChanges = NMSTransformer.changes.get(clazz);
          if (oldChanges == null) {
            NMSTransformer.changes.put(clazz, changeArray);
          } else {
            final Change[] newChanges = new Change[oldChanges.length + changeArray.length];
            System.arraycopy(oldChanges, 0, newChanges, 0, oldChanges.length);
            System.arraycopy(changeArray, 0, newChanges, oldChanges.length, changeArray.length);
            NMSTransformer.changes.put(clazz, newChanges);
          }
        }
      }
    } catch (final IOException ex) {
      System.err.println("Failed to load class list.");
      ex.printStackTrace();
    }
  }

  private static byte[] readAllBytes(@NotNull final InputStream stream) throws IOException {
    final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    final byte[] buffer = new byte[4096];
    int readLen;
    while ((readLen = stream.read(buffer)) != -1) {
      byteStream.write(buffer, 0, readLen);
    }
    return byteStream.toByteArray();
  }

  @Override
  public byte @Nullable [] transform(final ClassLoader loader, final String className,
                                     final Class<?> classBeingRedefined, final ProtectionDomain protectionDomain,
                                     final byte[] classfileBuffer) {
    if (className == null) {
      return null;
    }
    if (!NMSTransformer.changes.containsKey(className)) {
      return null;
    }
    final String fixedClassName = className.replace("/", ".");
    if (NMSTransformer.DEBUG) {
      System.out.println("Applying changes for class " + fixedClassName);
    }
    try {
      final ClassPool pool = ClassPool.getDefault();
      pool.appendClassPath(new LoaderClassPath(loader));
      final CtClass ctClass = pool.get(fixedClassName);
      for (final Change change : NMSTransformer.changes.get(className)) {
        try {
          final CtMethod[] methods = ctClass.getDeclaredMethods(change.getMethodName());
          boolean found = false;
          main:
          for (final CtMethod method : methods) {
            final CtClass[] params = method.getParameterTypes();
            if (params.length != change.getParams().length) {
              continue;
            }
            for (int i = 0; i < params.length; i++) {
              if (!change.getParams()[i].trim().equals(params[i].getName())) {
                continue main;
              }
            }
            found = true;
            try {
              method.insertBefore(change.getContent());
            } catch (final CannotCompileException ex) {
              if (!change.isOptional()) { // If it's an optional change we can ignore it
                throw ex;
              }
            }
            break;
          }
          if (!found && !change.isOptional()) {
            throw new NotFoundException("Unknown method " + change.getMethodName());
          }
        } catch (final CannotCompileException ex) {
          throw new CannotCompileException("Method " + change.getMethodName(), ex);
        }
      }
      return ctClass.toBytecode();
    } catch (final NotFoundException | CannotCompileException | IOException ex) {
      System.err.println("Failed to override methods from class " + fixedClassName + ".");
      ex.printStackTrace();
    }
    return null;
  }

  @Getter
  @RequiredArgsConstructor
  private static class Change {

    @NotNull
    private final String content;

    @NotNull
    private final String methodName;

    private final boolean optional;

    private final String @NotNull [] params;
  }
}
