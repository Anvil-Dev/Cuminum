package dev.anvilcraft.resource.cuminum.processor;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

/**
 * Convenience wrappers around {@link TreeMaker} and {@link Names} for building
 * javac AST nodes. All methods use the fully-qualified name chain pattern
 * ({@code Select(Select(Ident("com"), "mojang"), ...)}) so no import statements
 * need to be added to the compilation unit.
 */
public final class AstHelper {

    private final TreeMaker maker;
    private final Names names;

    public AstHelper(TreeMaker maker, Names names) {
        this.maker = maker;
        this.names = names;
    }

    // ── Names ──────────────────────────────────────────────────────────

    /** Create a {@link Name} from a string. */
    public Name name(String s) {
        return names.fromString(s);
    }

    // ── Identifiers ─────────────────────────────────────────────────────

    /** Create a simple identifier node. */
    public JCIdent ident(String s) {
        return maker.Ident(name(s));
    }

    /**
     * Build a chain-select for a fully-qualified name.
     * {@code "com.mojang.serialization.Codec"} becomes
     * {@code Select(Select(Select(Ident("com"), "mojang"), "serialization"), "Codec")}.
     */
    public JCExpression chainIdent(String fqn) {
        String[] parts = fqn.split("\\.");
        JCExpression e = ident(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            e = maker.Select(e, name(parts[i]));
        }
        return e;
    }

    // ── Method calls ────────────────────────────────────────────────────

    /** Build {@code receiver.method()}. */
    public JCMethodInvocation call(JCExpression receiver, String method) {
        return maker.Apply(
            List.nil(),
            maker.Select(receiver, name(method)),
            List.nil()
        );
    }

    /** Build {@code receiver.method(arg)}. */
    public JCMethodInvocation call(JCExpression receiver, String method, JCExpression arg) {
        return maker.Apply(
            List.nil(),
            maker.Select(receiver, name(method)),
            List.of(arg)
        );
    }

    /** Build {@code receiver.method(arg1, arg2)}. */
    public JCMethodInvocation call(JCExpression receiver, String method,
                                   JCExpression arg1, JCExpression arg2) {
        return maker.Apply(
            List.nil(),
            maker.Select(receiver, name(method)),
            List.of(arg1, arg2)
        );
    }

    /** Build {@code receiver.method(args...)} for varargs. */
    public JCMethodInvocation call(JCExpression receiver, String method, JCExpression... args) {
        return maker.Apply(
            List.nil(),
            maker.Select(receiver, name(method)),
            List.from(java.util.Arrays.asList(args))
        );
    }

    // ── Literals ────────────────────────────────────────────────────────

    /** Create a string literal. */
    public JCLiteral literal(String value) {
        return maker.Literal(value);
    }

    /** Create a null literal. */
    public JCLiteral nullLiteral() {
        return maker.Literal(com.sun.tools.javac.code.TypeTag.BOT, null);
    }

    // ── Member references ───────────────────────────────────────────────

    /**
     * Build a method reference {@code owner::method}.
     * {@code owner} is usually the simple class name.
     */
    public JCMemberReference methodRef(String owner, String method) {
        return maker.Reference(
            JCMemberReference.ReferenceMode.INVOKE,
            name(method),
            ident(owner),
            List.nil()
        );
    }

    /** Build a constructor reference {@code owner::new}. */
    public JCMemberReference constructorRef(String owner) {
        return maker.Reference(
            JCMemberReference.ReferenceMode.NEW,
            name("<init>"),
            ident(owner),
            List.nil()
        );
    }

    // ── Lambda ──────────────────────────────────────────────────────────

    /**
     * Build a lambda expression {@code paramName -> body}.
     * The parameter type is left for javac to infer.
     * Use {@link #typedLambda} when inference fails with raw AST nodes.
     */
    public JCLambda lambda(String paramName, JCExpression body) {
        JCVariableDecl param = maker.VarDef(
            maker.Modifiers(Flags.PARAMETER),
            name(paramName),
            null,  // type: inferred
            null   // initializer: none
        );
        return maker.Lambda(List.of(param), body);
    }

    /**
     * Build an explicitly-typed lambda {@code (ParamType paramName) -> body}.
     * Needed when javac cannot infer the parameter type from context,
     * which is common with manually-constructed AST nodes.
     */
    public JCLambda typedLambda(String paramName, JCExpression paramType, JCExpression body) {
        JCVariableDecl param = maker.VarDef(
            maker.Modifiers(Flags.PARAMETER),
            name(paramName),
            paramType,
            null
        );
        return maker.Lambda(List.of(param), body);
    }

    // ── Types ───────────────────────────────────────────────────────────

    /** Build a parameterized type {@code BaseType<Arg>}. */
    public JCTypeApply typeApply(JCExpression baseType, JCExpression arg) {
        return maker.TypeApply(baseType, List.of(arg));
    }

    /** Build a parameterized type {@code BaseType<Arg1, Arg2>}. */
    public JCTypeApply typeApply(JCExpression baseType, JCExpression arg1, JCExpression arg2) {
        return maker.TypeApply(baseType, List.of(arg1, arg2));
    }

    /** Build {@code Codec.STRING}, {@code Codec.INT}, etc. */
    public JCFieldAccess codecConst(String constant) {
        return maker.Select(
            chainIdent("com.mojang.serialization.Codec"),
            name(constant)
        );
    }

    // ── Fields ──────────────────────────────────────────────────────────

    /** Create a field declaration with initializer. */
    public JCVariableDecl createField(long modifiers, String fieldName,
                                      JCExpression type, JCExpression init) {
        return maker.VarDef(
            maker.Modifiers(modifiers),
            name(fieldName),
            type,
            init
        );
    }

    // ── Convenience modifiers ───────────────────────────────────────────

    /** PUBLIC | STATIC | FINAL — typical for generated CODEC fields. */
    public long publicStaticFinal() {
        return Flags.PUBLIC | Flags.STATIC | Flags.FINAL;
    }

    // ── Accessors ──────────────────────────────────────────────────────

    public TreeMaker maker() { return maker; }
    public Names names() { return names; }
}
