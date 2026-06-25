package dev.anvilcraft.resource.cuminum.processor;

import com.google.auto.service.AutoService;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCImport;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;
import dev.anvilcraft.resource.cuminum.CodecIgnore;
import dev.anvilcraft.resource.cuminum.UseCodec;
import dev.anvilcraft.resource.cuminum.codec.AutoCodec;
import dev.anvilcraft.resource.cuminum.codec.CodecField;
import org.checkerframework.checker.nullness.qual.NonNull;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Annotation processor that injects {@code CODEC}, {@code MAP_CODEC} fields
 * directly into classes annotated with {@link AutoCodec} — Lombok-style.
 *
 * <p><b>Strategy</b>: generate the field source code as a string, parse it with
 * javac's own parser (which correctly handles type attribution), then inject the
 * resulting {@link JCVariableDecl} nodes into the target class's AST. This avoids
 * the type-inference issues that arise from manually constructing generic-heavy
 * {@code RecordCodecBuilder} chains with raw {@link TreeMaker} nodes.</p>
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("dev.anvilcraft.resource.cuminum.codec.AutoCodec")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class AutoCodecProcessor extends AbstractProcessor {

    // ── javac internals (lazy-init) ──────────────────────────────────────
    private JavacTrees trees;
    private TreeMaker maker;
    private Names names;
    private AstHelper ast;
    private ParserFactory parserFactory;

    // ── Entry point ─────────────────────────────────────────────────────

    @Override
    public synchronized void init(javax.annotation.processing.ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        JavaWorkaround.init();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, @NonNull RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        if (trees == null) initJavacInternals();
        if (trees == null) return false;

        for (Element element : roundEnv.getElementsAnnotatedWith(AutoCodec.class)) {
            AutoCodec annotation = element.getAnnotation(AutoCodec.class);
            if (element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.RECORD) {
                try {
                    injectCodecFields(
                        (TypeElement) element,
                        annotation != null ? annotation.value() : AutoCodec.CodecType.CODEC
                    );
                } catch (Exception e) {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Cuminum: failed for " + element + ": " + e + "\n" + sw
                    );
                }
            }
        }
        return true;
    }

    // ── Javac internals init ────────────────────────────────────────────

    private void initJavacInternals() {
        try {
            Context context = getJavacContext();
            trees = JavacTrees.instance(context);
            maker = TreeMaker.instance(context);
            names = Names.instance(context);
            ast = new AstHelper(maker, names);
            parserFactory = ParserFactory.instance(context);
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                "Cuminum: javac internals initialized successfully");
        } catch (Exception e) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.WARNING,
                "Cuminum: cannot access javac internals. Error: " + e.getMessage()
            );
        }
    }

    private Context getJavacContext() {
        if (processingEnv instanceof JavacProcessingEnvironment jpe) {
            return jpe.getContext();
        }
        try {
            var field = processingEnv.getClass().getDeclaredField("context");
            field.setAccessible(true);
            return (Context) field.get(processingEnv);
        } catch (Exception e) {
            throw new RuntimeException("Not running on javac", e);
        }
    }

    // ── Core injection logic ────────────────────────────────────────────

    private void injectCodecFields(TypeElement typeElement, AutoCodec.CodecType codecType) {
        if (!(typeElement instanceof Symbol.ClassSymbol classSymbol)) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                "Cuminum: not a ClassSymbol: " + typeElement);
            return;
        }

        JCClassDecl classDecl = (JCClassDecl) trees.getTree(classSymbol);
        if (classDecl == null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                "Cuminum: null classDecl for " + typeElement.getQualifiedName());
            return;
        }

        // Duplicate guard
        boolean hasCodec = false, hasMapCodec = false;
        for (JCTree def : classDecl.defs) {
            if (def instanceof JCVariableDecl vd) {
                if (vd.name.contentEquals("CODEC")) hasCodec = true;
                if (vd.name.contentEquals("MAP_CODEC")) hasMapCodec = true;
            }
        }

        String packageName = processingEnv.getElementUtils()
            .getPackageOf(typeElement).getQualifiedName().toString();
        String className = typeElement.getSimpleName().toString();
        boolean isRecord = typeElement.getKind() == ElementKind.RECORD;

        // Build source code for the fields, parse, and inject directly
        String source = generateFieldSource(packageName, className, typeElement,
            codecType, isRecord);
        if (source.isEmpty()) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                "Cuminum: empty source for " + className);
            return;
        }

        JCCompilationUnit parsed = parseSource(source);
        if (parsed == null) return;

        // Add necessary imports to the target class's compilation unit
        ensureImports(classSymbol);

        // Extract the generated wrapper class, then its members, and inject.
        // We inject both the CODEC/MAP_CODEC field declarations and, for
        // records, the `static {}` initializer block (a JCBlock) — without the
        // block the fields would be declared but never assigned.
        //
        // Position handling (see anchorPositions): nodes parsed from the
        // generated string carry positions foreign to the target file. For the
        // record case (uninitialized fields + a static block) every injected
        // node is anchored to the class's position so flow analysis orders the
        // field declarations and their assignments correctly. A field WITH an
        // initializer (the non-record lambda case) is left untouched, because
        // anchoring a field-initializer lambda corrupts javac's type inference
        // and breaks forGetter.
        int anchorPos = classDecl.pos;
        for (JCTree topDef : parsed.defs) {
            if (topDef instanceof JCClassDecl wrapperClass) {
                for (JCTree member : wrapperClass.defs) {
                    if (member instanceof JCVariableDecl vd) {
                        String name = vd.name.toString();
                        if ("CODEC".equals(name) && hasCodec) continue;
                        if ("MAP_CODEC".equals(name) && hasMapCodec) continue;
                        if (vd.init == null) anchorPositions(vd, anchorPos);
                        classDecl.defs = classDecl.defs.append(vd);
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                            "Cuminum: injected field " + name + " into " + className);
                    } else if (member instanceof JCBlock block && block.isStatic()) {
                        // The static initializer that assigns the record's
                        // CODEC/MAP_CODEC fields. Skip entirely when both
                        // fields already exist (nothing left to assign).
                        if (hasCodec && hasMapCodec) continue;
                        anchorPositions(block, anchorPos);
                        classDecl.defs = classDecl.defs.append(block);
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                            "Cuminum: injected static initializer into " + className);
                    }
                }
            }
        }
    }

    // ── Source code generation ──────────────────────────────────────────

    /**
     * Generate a complete Java class source string containing only the
     * CODEC/MAP_CODEC fields. This is parsed by javac to get properly
     * attributed AST nodes, then injected into the target class.
     */
    private String generateFieldSource(String pkg, String className,
                                        TypeElement typeElement,
                                        AutoCodec.CodecType codecType,
                                        boolean isRecord) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n");
        sb.append("import com.mojang.serialization.Codec;\n");
        sb.append("import com.mojang.serialization.MapCodec;\n");
        sb.append("import com.mojang.serialization.codecs.RecordCodecBuilder;\n");
        sb.append("public final class _CuminumGen_").append(className).append(" {\n");

        String fieldBlocks = generateFieldBlocksSource(typeElement, className, isRecord);
        if (fieldBlocks.isEmpty()) return "";

        if (isRecord) {
            // For records: a lambda inside a field initializer crashes javac's
            // definite-assignment analyzer (Bits.incl / Flow.visitLambda — a JDK
            // bug). A static init block sidesteps it, so declare the fields
            // uninitialized and assign them in a `static {}` block.
            sb.append("  public static final MapCodec<").append(className).append("> MAP_CODEC;\n");
            sb.append("  public static final Codec<").append(className).append("> CODEC;\n");
            sb.append("  static {\n");

            if (codecType == AutoCodec.CodecType.CODEC) {
                sb.append("    CODEC = RecordCodecBuilder.create(instance -> instance.group(\n");
                sb.append(fieldBlocks);
                sb.append("    ).apply(instance, ").append(className).append("::new));\n");
            } else if (codecType == AutoCodec.CodecType.MAP_CODEC) {
                sb.append("    MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(\n");
                sb.append(fieldBlocks);
                sb.append("    ).apply(instance, ").append(className).append("::new));\n");
            } else { // BOTH
                sb.append("    MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(\n");
                sb.append(fieldBlocks);
                sb.append("    ).apply(instance, ").append(className).append("::new));\n");
                sb.append("    CODEC = MAP_CODEC.codec();\n");
            }
            sb.append("  }\n");
        } else {
            // Non-record classes: a field initializer lambda works fine.
            if (codecType == AutoCodec.CodecType.CODEC) {
                sb.append("  public static final Codec<").append(className)
                    .append("> CODEC = RecordCodecBuilder.create(instance -> instance.group(\n");
                sb.append(fieldBlocks);
                sb.append("  ).apply(instance, ").append(className).append("::new));\n");
            } else {
                sb.append("  public static final MapCodec<").append(className)
                    .append("> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(\n");
                sb.append(fieldBlocks);
                sb.append("  ).apply(instance, ").append(className).append("::new));\n");
                if (codecType == AutoCodec.CodecType.BOTH) {
                    sb.append("  public static final Codec<").append(className)
                        .append("> CODEC = MAP_CODEC.codec();\n");
                }
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Generate the comma-separated field blocks for the {@code group()} call.
     */
    private String generateFieldBlocksSource(TypeElement typeElement,
                                              String className, boolean isRecord) {
        StringBuilder sb = new StringBuilder();
        List<? extends Element> members = isRecord
            ? typeElement.getRecordComponents()
            : typeElement.getEnclosedElements();

        boolean first = true;
        for (Element member : members) {
            if (!isRecord && member.getKind() != ElementKind.FIELD) continue;
            if (member.getModifiers().contains(Modifier.STATIC)) continue;
            if (member.getAnnotation(CodecIgnore.class) != null) continue;

            TypeMirror typeMirror = member.asType();
            String fieldName = member.getSimpleName().toString();

            // JSON key
            CodecField anno = member.getAnnotation(CodecField.class);
            String jsonKey = (anno != null && !anno.value().isEmpty())
                ? anno.value() : fieldName;

            // Codec reference source
            String codecRef;
            if (anno != null && (anno.codec() instanceof UseCodec useCodec)) {
                codecRef = resolveUseCodecSource(useCodec);
                if (codecRef == null) codecRef = resolveCodecSource(typeMirror);
            } else {
                codecRef = resolveCodecSource(typeMirror);
            }

            // Getter source
            String getter = buildGetterSource(className, member, isRecord);

            // Build the chain
            boolean isOptionalType = isRawTypeString(typeMirror, "java.util.Optional");
            boolean isNullable = hasNullableAnnotation(member);

            if (!first) sb.append(",\n");
            first = false;

            if (isOptionalType) {
                sb.append("    ").append(codecRef)
                    .append(".optionalFieldOf(\"").append(jsonKey).append("\")");
            } else if (isNullable) {
                sb.append("    ").append(codecRef)
                    .append(".optionalFieldOf(\"").append(jsonKey).append("\", null)");
            } else {
                sb.append("    ").append(codecRef)
                    .append(".fieldOf(\"").append(jsonKey).append("\")");
            }
            sb.append(".forGetter(").append(getter).append(")");
        }

        if (!first) sb.append("\n");
        return sb.toString();
    }

    // ── Getter source ───────────────────────────────────────────────────

    private String buildGetterSource(String className, Element member,
                                      boolean isRecord) {
        String fieldName = member.getSimpleName().toString();

        if (isRecord) {
            return className + "::" + fieldName;
        }

        if (!member.getModifiers().contains(Modifier.PRIVATE)) {
            // Public/package-private field → obj -> obj.field
            return "obj -> obj." + fieldName;
        }

        // Private field → assume getter
        String prefix = member.asType().getKind().name().equals("BOOLEAN")
            ? "is" : "get";
        String getterName = prefix + capitalize(fieldName);
        return className + "::" + getterName;
    }

    // ── Codec source resolver ───────────────────────────────────────────

    private String resolveUseCodecSource(UseCodec useCodec) {
        String canonicalName;
        try {
            canonicalName = useCodec.value().getCanonicalName();
        } catch (MirroredTypeException e) {
            canonicalName = e.getTypeMirror().toString();
        }
        if (canonicalName.isEmpty() || canonicalName.equals("java.lang.Void")) {
            return null;
        }
        String memberName = useCodec.member().isEmpty() ? "CODEC" : useCodec.member();
        return canonicalName + "." + memberName;
    }

    private String resolveCodecSource(TypeMirror type) {
        if (isRawTypeString(type, "java.util.Optional")) {
            return resolveCodecSource(getGenericArgument(type, 0));
        }
        if (isRawTypeString(type, "java.util.List")) {
            return resolveCodecSource(getGenericArgument(type, 0)) + ".listOf()";
        }
        if (isRawTypeString(type, "java.util.Map")) {
            String keyCodec = resolveCodecSource(getGenericArgument(type, 0));
            String valueCodec = resolveCodecSource(getGenericArgument(type, 1));
            return "Codec.unboundedMap(" + keyCodec + ", " + valueCodec + ")";
        }

        String typeStr = type.toString();
        return switch (typeStr) {
            case "byte", "java.lang.Byte"       -> "Codec.BYTE";
            case "short", "java.lang.Short"     -> "Codec.SHORT";
            case "char", "java.lang.Character"  -> "Codec.CHAR";
            case "int", "java.lang.Integer"     -> "Codec.INT";
            case "long", "java.lang.Long"       -> "Codec.LONG";
            case "float", "java.lang.Float"     -> "Codec.FLOAT";
            case "double", "java.lang.Double"   -> "Codec.DOUBLE";
            case "boolean", "java.lang.Boolean" -> "Codec.BOOL";
            case "java.lang.String"             -> "Codec.STRING";
            case "net.minecraft.core.BlockPos"  -> "net.minecraft.core.BlockPos.CODEC";
            default -> resolveCodecSourceFallback(type);
        };
    }

    private String resolveCodecSourceFallback(TypeMirror type) {
        Element element = processingEnv.getTypeUtils().asElement(type);
        if (element != null && element.getAnnotation(AutoCodec.class) != null) {
            String pkg = processingEnv.getElementUtils()
                .getPackageOf(element).getQualifiedName().toString();
            String name = element.getSimpleName().toString();
            String fqn = pkg.isEmpty() ? name : pkg + "." + name;
            return fqn + ".CODEC";
        }
        if (type instanceof DeclaredType dt) {
            Element elem = dt.asElement();
            if (elem instanceof TypeElement te) {
                return te.getQualifiedName() + ".CODEC";
            }
        }
        return type.toString() + ".CODEC";
    }

    // ── Source parsing ──────────────────────────────────────────────────

    /**
     * Parse a Java source string into a {@link JCCompilationUnit}.
     * The returned AST is fully attributed by javac.
     */
    private JCCompilationUnit parseSource(String source) {
        try {
            JavaFileObject fileObject = new SimpleJavaFileObject(
                URI.create("string:///_CuminumGen_.java"),
                JavaFileObject.Kind.SOURCE
            ) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return source;
                }
            };

            var parser = parserFactory.newParser(
                source, true, false, false);
            return parser.parseCompilationUnit();
        } catch (Exception e) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "Cuminum: failed to parse generated source: " + e.getMessage()
            );
            return null;
        }
    }

    /**
     * Recursively set every node's position to {@code anchorPos} — a valid
     * offset inside the target source file (the annotated class's position).
     *
     * <p>The injected nodes were parsed from a generated wrapper string, so
     * they carry positions that point nowhere in the target file. Two things
     * go wrong if left alone: (1) javac's definite-assignment analyzer mis-
     * orders the injected {@code static} block against the record's own
     * members and reports spurious "variable might already be assigned"
     * errors; (2) diagnostics map to wrong lines.</p>
     *
     * <p>Crucially we anchor to a real position rather than {@code NOPOS}
     * (-1): {@code NOPOS} breaks {@code Flow}'s lambda analysis (the bit-set
     * logic in {@code Bits}), which silently corrupts {@code forGetter} type
     * inference. This mirrors Lombok's {@code setGeneratedBy}, which sets
     * {@code node.pos = sourceNode.getStartPos()}.</p>
     *
     * <p>Implemented as a manual recursive walk rather than a
     * {@code TreeScanner} subclass: subclassing a {@code jdk.compiler} class
     * from the unnamed module fails the JVM superclass access check (the
     * package is opened reflectively at runtime, but the check happens at
     * class load).</p>
     */
    private static void anchorPositions(JCTree node, int anchorPos) {
        if (node == null) return;
        node.pos = anchorPos;

        if (node instanceof JCVariableDecl vd) {
            if (vd.mods != null) vd.mods.pos = anchorPos;
            anchorPositions(vd.vartype, anchorPos);
            anchorPositions(vd.init, anchorPos);
        } else if (node instanceof com.sun.tools.javac.tree.JCTree.JCFieldAccess fa) {
            anchorPositions(fa.selected, anchorPos);
        } else if (node instanceof com.sun.tools.javac.tree.JCTree.JCMethodInvocation mi) {
            anchorPositions(mi.meth, anchorPos);
            if (mi.typeargs != null) for (JCTree a : mi.typeargs) anchorPositions(a, anchorPos);
            if (mi.args != null) for (JCTree a : mi.args) anchorPositions(a, anchorPos);
        } else if (node instanceof com.sun.tools.javac.tree.JCTree.JCLambda lambda) {
            // Anchor the lambda node itself but NOT its body: re-anchoring the
            // body's positions corrupts javac's lambda type inference (the
            // forGetter argument collapses to a wrong type, breaking group()).
            // The body keeps its parsed positions, which attribution handles.
            return;
        } else if (node instanceof com.sun.tools.javac.tree.JCTree.JCMemberReference mr) {
            anchorPositions(mr.expr, anchorPos);
            if (mr.typeargs != null) for (JCTree a : mr.typeargs) anchorPositions(a, anchorPos);
        } else if (node instanceof com.sun.tools.javac.tree.JCTree.JCTypeApply ta) {
            anchorPositions(ta.clazz, anchorPos);
            if (ta.arguments != null) for (JCTree a : ta.arguments) anchorPositions(a, anchorPos);
        } else if (node instanceof com.sun.tools.javac.tree.JCTree.JCClassDecl cd) {
            if (cd.defs != null) for (JCTree d : cd.defs) anchorPositions(d, anchorPos);
        } else if (node instanceof com.sun.tools.javac.tree.JCTree.JCBlock block) {
            if (block.stats != null) for (JCTree s : block.stats) anchorPositions(s, anchorPos);
        } else if (node instanceof com.sun.tools.javac.tree.JCTree.JCExpressionStatement es) {
            anchorPositions(es.expr, anchorPos);
        } else if (node instanceof com.sun.tools.javac.tree.JCTree.JCAssign assign) {
            anchorPositions(assign.lhs, anchorPos);
            anchorPositions(assign.rhs, anchorPos);
        } else if (node instanceof com.sun.tools.javac.tree.JCTree.JCReturn ret) {
            anchorPositions(ret.expr, anchorPos);
        } else if (node instanceof com.sun.tools.javac.tree.JCTree.JCNewClass nc) {
            if (nc.args != null) for (JCTree a : nc.args) anchorPositions(a, anchorPos);
            anchorPositions(nc.clazz, anchorPos);
        } else if (node instanceof com.sun.tools.javac.tree.JCTree.JCAnnotation ann) {
            anchorPositions(ann.annotationType, anchorPos);
            if (ann.args != null) for (JCTree a : ann.args) anchorPositions(a, anchorPos);
        }
    }

    // ── Utility methods ─────────────────────────────────────────────────

    private boolean isRawTypeString(TypeMirror type, String rawCanonicalName) {
        if (type instanceof DeclaredType declaredType) {
            Element element = declaredType.asElement();
            if (element instanceof TypeElement typeElement) {
                return typeElement.getQualifiedName().contentEquals(rawCanonicalName);
            }
        }
        return false;
    }

    private boolean hasNullableAnnotation(@NonNull Element element) {
        return element.getAnnotationMirrors().stream().anyMatch(mirror ->
            mirror.getAnnotationType().toString().endsWith(".Nullable"));
    }

    private TypeMirror getGenericArgument(TypeMirror type, int index) {
        if (type instanceof DeclaredType declaredType) {
            List<? extends TypeMirror> args = declaredType.getTypeArguments();
            if (args.size() > index) return args.get(index);
        }
        return processingEnv.getElementUtils()
            .getTypeElement("java.lang.Object").asType();
    }

    /**
     * Ensure the target class's compilation unit imports the types needed
     * by the injected CODEC fields: {@code Codec}, {@code MapCodec},
     * {@code RecordCodecBuilder}.
     */
    private void ensureImports(Symbol.ClassSymbol classSymbol) {
        JCCompilationUnit unit = (JCCompilationUnit)
            trees.getPath(classSymbol).getCompilationUnit();
        if (unit == null) return;

        String[] neededImports = {
            "com.mojang.serialization.Codec",
            "com.mojang.serialization.MapCodec",
            "com.mojang.serialization.codecs.RecordCodecBuilder",
        };

        // Separate the package declaration from the rest of defs.
        // The package declaration must remain the first element,
        // otherwise JCCompilationUnit.getPackage() returns null and
        // the class loses its package in the generated bytecode.
        com.sun.tools.javac.util.List<JCTree> body;
        com.sun.tools.javac.util.List<JCTree> head;
        if (unit.defs != null
            && !unit.defs.isEmpty()
            && unit.defs.head.hasTag(com.sun.tools.javac.tree.JCTree.Tag.PACKAGEDEF)) {
            head = com.sun.tools.javac.util.List.of(unit.defs.head);
            body = unit.defs.tail;
        } else {
            head = com.sun.tools.javac.util.List.nil();
            body = unit.defs;
        }

        for (String fqn : neededImports) {
            boolean alreadyImported = false;
            if (unit.defs != null) {
                for (JCTree def : unit.defs) {
                    if (def instanceof JCImport imp) {
                        // qualid is a chain-select like com.mojang.serialization.Codec
                        if (imp.qualid != null && fqn.equals(imp.qualid.toString())) {
                            alreadyImported = true;
                            break;
                        }
                    }
                }
            }
            if (!alreadyImported) {
                var qualidExpr = ast.chainIdent(fqn);
                // chainIdent always returns JCFieldAccess for multi-part FQNs
                JCImport imp = maker.Import(
                    (com.sun.tools.javac.tree.JCTree.JCFieldAccess) qualidExpr, false);
                imp.pos = com.sun.tools.javac.util.Position.NOPOS;
                body = body.prepend(imp);
            }
        }

        // Reconstruct: package declaration + new imports + original content
        unit.defs = head.appendList(body);
    }

    private static String capitalize(@NonNull String str) {
        if (str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
