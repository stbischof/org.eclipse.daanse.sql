/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.sql.statement.api;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.sql.statement.api.expression.ArithmeticOperator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;

/** Factory methods for {@link SqlExpression}s (house style: static helpers over values). */
public final class Expressions {

    private Expressions() {
    }

    /** An unqualified column reference. */
    public static SqlExpression column(String name) {
        return new SqlExpression.Column(Optional.empty(), name);
    }

    /** A column reference qualified by a table alias. */
    public static SqlExpression column(TableAlias table, String name) {
        return new SqlExpression.Column(Optional.of(table.name()), name);
    }

    /**
     * A column from a jdbc.db {@link org.eclipse.daanse.jdbc.db.api.schema.ColumnReference},
     * qualified by the given query alias. Only the column's {@code name()} is used; the
     * reference's own table is metadata and is <em>not</em> the in-query qualifier.
     */
    public static SqlExpression column(TableAlias table,
            org.eclipse.daanse.jdbc.db.api.schema.ColumnReference column) {
        return new SqlExpression.Column(Optional.of(table.name()), column.name());
    }

    /**
     * An unqualified column from a jdbc.db
     * {@link org.eclipse.daanse.jdbc.db.api.schema.ColumnReference} (uses {@code name()} only).
     */
    public static SqlExpression column(org.eclipse.daanse.jdbc.db.api.schema.ColumnReference column) {
        return new SqlExpression.Column(Optional.empty(), column.name());
    }

    /** A typed literal value. */
    public static SqlExpression literal(Object value, Datatype datatype) {
        return new SqlExpression.Literal(value, datatype);
    }

    /** A verbatim SQL fragment. */
    public static SqlExpression raw(String sql) {
        return new SqlExpression.Raw(sql);
    }

    /** A bind parameter carrying an immediate value (rendered as a placeholder, bound by the executor). */
    public static SqlExpression param(Object value, Datatype datatype) {
        return new SqlExpression.Param(value, true, datatype);
    }

    /** A positional parameter marker whose value is supplied at execute time (e.g. per batch row). */
    public static SqlExpression paramMarker(Datatype datatype) {
        return new SqlExpression.Param(null, false, datatype);
    }

    /** A function call, e.g. {@code function("UPPER", column("name"))}. */
    public static SqlExpression function(String name, SqlExpression... arguments) {
        return new SqlExpression.Function(name, List.of(arguments));
    }

    /** The {@code COUNT(*)} aggregate. */
    public static SqlExpression countStar() {
        return new SqlExpression.Function("COUNT", List.of(new SqlExpression.Raw("*")));
    }

    /** A non-distinct aggregate call, e.g. {@code aggregate("SUM", column("amount"))}. */
    public static SqlExpression aggregate(String name, SqlExpression... arguments) {
        requireArgs(arguments, "aggregate");
        return new SqlExpression.Aggregate(name, false, List.of(arguments));
    }

    /**
     * A {@code DISTINCT} aggregate call, e.g. {@code SUM(DISTINCT amount)} or
     * {@code COUNT(DISTINCT a, b)} (the multi-argument form is compound count-distinct, which
     * not every dialect supports — gate at the call site on {@code allowsCompoundCountDistinct}).
     */
    public static SqlExpression aggregateDistinct(String name, SqlExpression... arguments) {
        requireArgs(arguments, "aggregateDistinct");
        return new SqlExpression.Aggregate(name, true, List.of(arguments));
    }

    /** {@code COUNT(DISTINCT arg0, arg1, ...)}. */
    public static SqlExpression countDistinct(SqlExpression... arguments) {
        requireArgs(arguments, "countDistinct");
        return new SqlExpression.Aggregate("COUNT", true, List.of(arguments));
    }

    private static void requireArgs(SqlExpression[] arguments, String method) {
        if (arguments == null || arguments.length == 0) {
            throw new IllegalArgumentException(method + " requires at least one argument");
        }
    }

    /** {@code UPPER(expression)}. */
    public static SqlExpression upper(SqlExpression expression) {
        return new SqlExpression.Function("UPPER", List.of(expression));
    }

    /** {@code LOWER(expression)}. */
    public static SqlExpression lower(SqlExpression expression) {
        return new SqlExpression.Function("LOWER", List.of(expression));
    }

    /** {@code COALESCE(arg0, arg1, ...)}. */
    public static SqlExpression coalesce(SqlExpression... arguments) {
        return new SqlExpression.Function("COALESCE", List.of(arguments));
    }

    /** A binary arithmetic expression {@code left <op> right}. */
    public static SqlExpression arithmetic(SqlExpression left, ArithmeticOperator operator, SqlExpression right) {
        return new SqlExpression.Binary(left, operator, right);
    }

    /**
     * A binary arithmetic expression that renders WITHOUT enclosing parentheses — for infix fragments already
     * inside an enclosing context (e.g. the {@code a / b} inside {@code sum(a / b)}), where the standalone
     * {@link #arithmetic} form's outer parens would be incorrect.
     */
    public static SqlExpression infix(SqlExpression left, ArithmeticOperator operator, SqlExpression right) {
        return new SqlExpression.Binary(left, operator, right, false);
    }

    public static SqlExpression add(SqlExpression left, SqlExpression right) {
        return arithmetic(left, ArithmeticOperator.ADD, right);
    }

    public static SqlExpression subtract(SqlExpression left, SqlExpression right) {
        return arithmetic(left, ArithmeticOperator.SUBTRACT, right);
    }

    public static SqlExpression multiply(SqlExpression left, SqlExpression right) {
        return arithmetic(left, ArithmeticOperator.MULTIPLY, right);
    }

    public static SqlExpression divide(SqlExpression left, SqlExpression right) {
        return arithmetic(left, ArithmeticOperator.DIVIDE, right);
    }

    public static SqlExpression modulo(SqlExpression left, SqlExpression right) {
        return arithmetic(left, ArithmeticOperator.MODULO, right);
    }

    /** A single {@code WHEN condition THEN result} branch for {@link #caseExpr}. */
    public static SqlExpression.Case.WhenClause when(Predicate condition, SqlExpression result) {
        return new SqlExpression.Case.WhenClause(condition, result);
    }

    /** A {@code CASE WHEN ... THEN ... END} with no ELSE. */
    public static SqlExpression caseExpr(List<SqlExpression.Case.WhenClause> whens) {
        return new SqlExpression.Case(List.copyOf(whens), Optional.empty());
    }

    /** A {@code CASE WHEN ... THEN ... ELSE elseResult END}. */
    public static SqlExpression caseExpr(List<SqlExpression.Case.WhenClause> whens, SqlExpression elseResult) {
        return new SqlExpression.Case(List.copyOf(whens), Optional.ofNullable(elseResult));
    }
}
