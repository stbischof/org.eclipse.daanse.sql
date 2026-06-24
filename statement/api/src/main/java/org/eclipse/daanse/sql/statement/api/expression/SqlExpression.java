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
package org.eclipse.daanse.sql.statement.api.expression;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;

/**
 * A scalar SQL expression usable in {@code SELECT}, {@code WHERE}, {@code GROUP BY} and
 * {@code ORDER BY} positions.
 * <p>
 * The model is intentionally dialect-free: it stores <em>structure</em> only. All quoting
 * and spelling happens later, in the renderer. The {@link Raw} variant is an escape hatch
 * for SQL fragments that are already final (e.g. opaque view SQL) and is rendered verbatim.
 */
public sealed interface SqlExpression {

    /**
     * A column reference, optionally qualified by a table alias.
     *
     * @param tableQualifier the table alias (its raw name), or empty for an unqualified column
     * @param name           the column name (unquoted; the renderer quotes it)
     */
    record Column(Optional<String> tableQualifier, String name) implements SqlExpression {
    }

    /**
     * A typed literal value. The {@link Datatype} tells the renderer how to quote it.
     *
     * @param value    the raw value (may be {@code null})
     * @param datatype the value's SQL datatype
     */
    record Literal(Object value, Datatype datatype) implements SqlExpression {
    }

    /**
     * A function or operator call, e.g. {@code COUNT(...)} or {@code UPPER(...)}.
     *
     * @param name      the function name (rendered verbatim, upper-case by convention)
     * @param arguments the argument expressions
     */
    record Function(String name, List<SqlExpression> arguments) implements SqlExpression {
    }

    /**
     * An aggregate-function call that may carry a {@code DISTINCT} qualifier, e.g.
     * {@code COUNT(DISTINCT a, b)} or {@code SUM(amount)}. Distinguished from {@link Function}
     * because the renderer must place {@code DISTINCT} <em>inside</em> the parentheses, and
     * because a multi-argument {@code COUNT(DISTINCT ...)} (compound count-distinct) is its own
     * dialect-gated shape.
     *
     * @param name      the aggregate name (rendered verbatim, upper-case by convention)
     * @param distinct  whether the {@code DISTINCT} qualifier precedes the arguments
     * @param arguments the argument expressions (at least one)
     */
    record Aggregate(String name, boolean distinct, List<SqlExpression> arguments) implements SqlExpression {
        public Aggregate {
            if (arguments == null || arguments.isEmpty()) {
                throw new IllegalArgumentException("Aggregate requires at least one argument");
            }
            arguments = List.copyOf(arguments);
        }
    }

    /**
     * A binary arithmetic expression, e.g. {@code price * quantity}.
     *
     * @param left          left operand
     * @param operator      the arithmetic operator
     * @param right         right operand
     * @param parenthesized whether the rendered expression is wrapped in parentheses; {@code true} for a
     *                      standalone binary (the safe default), {@code false} for an infix fragment that is
     *                      already inside an enclosing context (e.g. {@code sum(a / b)}) and must not gain
     *                      an extra paren pair
     */
    record Binary(SqlExpression left, ArithmeticOperator operator, SqlExpression right, boolean parenthesized)
            implements SqlExpression {
        /** Backward-compatible constructor: parenthesized by default. */
        public Binary(SqlExpression left, ArithmeticOperator operator, SqlExpression right) {
            this(left, operator, right, true);
        }
    }

    /**
     * A searched {@code CASE WHEN condition THEN result ... [ELSE result] END} expression.
     *
     * @param whens      the ordered WHEN/THEN branches (at least one)
     * @param elseResult the ELSE result, if any
     */
    record Case(List<WhenClause> whens, Optional<SqlExpression> elseResult) implements SqlExpression {

        /** One {@code WHEN condition THEN result} branch. */
        public record WhenClause(Predicate condition, SqlExpression result) {
        }
    }

    /**
     * A bind parameter. Renders as a dialect placeholder ({@code ?}, {@code $n}, …) and its
     * value is carried alongside the SQL for the executor to bind — avoiding literal string
     * interpolation.
     *
     * @param value    the value to bind when {@code bound} is true (may be {@code null})
     * @param bound    {@code true} for an immediate value; {@code false} for a positional
     *                 marker whose value is supplied later (e.g. per batch row)
     * @param datatype the parameter's SQL datatype
     */
    record Param(Object value, boolean bound, Datatype datatype) implements SqlExpression {
    }

    /**
     * A pre-rendered SQL fragment, emitted verbatim by the renderer.
     *
     * @param sql the literal SQL text
     */
    record Raw(String sql) implements SqlExpression {
    }

    /**
     * The multi-variant sibling of {@link Raw}: a pre-rendered SQL fragment chosen per dialect at render time.
     * Carries the whole {@code dialect-name -> SQL} map (author-written per-engine fragments — e.g. a computed
     * column / measure expression with several {@code <SQL dialect="..."/>} entries) instead of a single
     * already-picked string, so the expression stays dialect-free and the {@code Statement} stays cache-safe.
     * The renderer resolves the map ({@code dialect.name()} else {@code "generic"}) and emits it verbatim like
     * {@link Raw}.
     *
     * @param byDialectName the {@code dialect-name -> SQL} variants (must contain the live dialect or
     *                      {@code "generic"})
     */
    record RawVariant(java.util.Map<String, String> byDialectName) implements SqlExpression {
    }
}
