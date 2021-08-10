package com.github.mkouba;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.ReceiverParameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.LiteralStringValueExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.PatternExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.SuperExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.AssertStmt;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.EmptyStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.LabeledStmt;
import com.github.javaparser.ast.stmt.LocalClassDeclarationStmt;
import com.github.javaparser.ast.stmt.LocalRecordDeclarationStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.SynchronizedStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.UnparsableStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.stmt.YieldStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.IntersectionType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.type.UnionType;
import com.github.javaparser.ast.type.UnknownType;
import com.github.javaparser.ast.type.VarType;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.type.WildcardType;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * The following registrations are needed to make javaparser work with native image.
 */
@RegisterForReflection(targets = {
        ImportDeclaration.class,
        PackageDeclaration.class,

        // Body
        AnnotationDeclaration.class,
        AnnotationMemberDeclaration.class,
        BodyDeclaration.class,
        CallableDeclaration.class,
        ClassOrInterfaceDeclaration.class,
        CompactConstructorDeclaration.class,
        ConstructorDeclaration.class,
        EnumConstantDeclaration.class,
        EnumDeclaration.class,
        FieldDeclaration.class,
        InitializerDeclaration.class,
        MethodDeclaration.class,
        Parameter.class,
        ReceiverParameter.class,
        RecordDeclaration.class,
        TypeDeclaration.class,
        VariableDeclarator.class,
        FieldDeclaration.class,

        // Types
        ArrayType.class,
        ClassOrInterfaceType.class,
        IntersectionType.class,
        PrimitiveType.class,
        ReferenceType.class,
        Type.class,
        TypeParameter.class,
        UnionType.class,
        UnknownType.class,
        VarType.class,
        VoidType.class,
        WildcardType.class,

        // Statement
        AssertStmt.class,
        BlockStmt.class,
        BreakStmt.class,
        CatchClause.class,
        ContinueStmt.class,
        DoStmt.class,
        EmptyStmt.class,
        ExplicitConstructorInvocationStmt.class,
        ExpressionStmt.class,
        ForEachStmt.class,
        ForStmt.class,
        IfStmt.class,
        LabeledStmt.class,
        LocalClassDeclarationStmt.class,
        LocalRecordDeclarationStmt.class,
        ReturnStmt.class,
        Statement.class,
        SwitchEntry.class,
        SwitchStmt.class,
        SynchronizedStmt.class,
        ThrowStmt.class,
        TryStmt.class,
        UnparsableStmt.class,
        WhileStmt.class,
        YieldStmt.class,

        // Expr
        AnnotationExpr.class,
        ArrayAccessExpr.class,
        ArrayCreationExpr.class,
        ArrayInitializerExpr.class,
        AssignExpr.class,
        BinaryExpr.class,
        BooleanLiteralExpr.class,
        CastExpr.class,
        CharLiteralExpr.class,
        ClassExpr.class,
        ConditionalExpr.class,
        DoubleLiteralExpr.class,
        EnclosedExpr.class,
        Expression.class,
        FieldAccessExpr.class,
        InstanceOfExpr.class,
        IntegerLiteralExpr.class,
        LambdaExpr.class,
        LiteralExpr.class,
        LiteralStringValueExpr.class,
        LongLiteralExpr.class,
        MarkerAnnotationExpr.class,
        MemberValuePair.class,
        MethodCallExpr.class,
        MethodReferenceExpr.class,
        Name.class,
        NameExpr.class,
        NormalAnnotationExpr.class,
        NullLiteralExpr.class,
        ObjectCreationExpr.class,
        PatternExpr.class,
        SimpleName.class,
        SingleMemberAnnotationExpr.class,
        StringLiteralExpr.class,
        SuperExpr.class,
        SwitchExpr.class,
        TextBlockLiteralExpr.class,
        ThisExpr.class,
        TypeExpr.class,
        UnaryExpr.class,
        VariableDeclarationExpr.class })
public class ReflectionRegistrations {

}
