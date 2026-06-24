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

/**
 * A boolean condition usable in {@code WHERE}/{@code HAVING}/join-{@code ON} positions.
 * <p>
 * Like {@link SqlExpression}, predicates carry structure only; the renderer performs all
 * quoting and spelling. {@link Raw} is the verbatim escape hatch.
 */
public sealed interface Predicate {

    /** {@code left <op> right}. */
    record Comparison(SqlExpression left, ComparisonOperator operator, SqlExpression right) implements Predicate {
    }

    /** {@code expression IN (values...)}. */
    record In(SqlExpression expression, List<SqlExpression> values) implements Predicate {
    }

    /**
     * Row-value / tuple {@code IN}: {@code (c1, c2, ...) IN ((v11, v12, ...), (v21, v22, ...), ...)}.
     * Each row in {@code rows} must have the same arity as {@code columns}. Gate construction at the
     * call site on {@code dialect.supportsMultiValueInExpr()}; dialects without it need an OR-of-ANDs
     * expansion instead.
     */
    record InTuple(List<SqlExpression> columns, List<List<SqlExpression>> rows) implements Predicate {
    }

    /** {@code expression IS [NOT] NULL}. */
    record IsNull(SqlExpression expression, boolean negated) implements Predicate {
    }

    /** {@code expression [NOT] LIKE pattern [ESCAPE c]}. */
    record Like(SqlExpression expression, SqlExpression pattern, boolean negated,
            Optional<Character> escape) implements Predicate {
    }

    /** {@code expression [NOT] BETWEEN low AND high}. */
    record Between(SqlExpression expression, SqlExpression low, SqlExpression high,
            boolean negated) implements Predicate {
    }

    /** Negation: {@code NOT (operand)}. */
    record Not(Predicate operand) implements Predicate {
    }

    /** Conjunction of operands ({@code AND}). An empty list is treated as always-true. */
    record And(List<Predicate> operands) implements Predicate {
    }

    /** Disjunction of operands ({@code OR}). An empty list is treated as always-false. */
    record Or(List<Predicate> operands) implements Predicate {
    }

    /** A pre-rendered predicate fragment, emitted verbatim. */
    record Raw(String sql) implements Predicate {
    }
}
