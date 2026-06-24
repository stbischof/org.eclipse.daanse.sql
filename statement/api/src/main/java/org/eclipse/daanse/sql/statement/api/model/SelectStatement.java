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
package org.eclipse.daanse.sql.statement.api.model;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.statement.api.expression.Predicate;

/**
 * An immutable {@code SELECT} statement. Holds <em>structure</em> only — no dialect, no
 * pre-quoted SQL — so it has value semantics and is safe to use as a cache key. Render it
 * with a {@code SqlRenderer} to obtain dialect-specific SQL.
 *
 * @param distinct    whether {@code DISTINCT} is set
 * @param projections the {@code SELECT} list (also carries the column types, in order)
 * @param from        the {@code FROM} clause, if any
 * @param filters     {@code WHERE} predicates, combined with {@code AND}
 * @param groupBy     the {@code GROUP BY} part (may be empty)
 * @param having      {@code HAVING} predicates, combined with {@code AND}
 * @param orderKeys   the {@code ORDER BY} items
 * @param rowLimit    the row limit/offset, if any
 */
public record SelectStatement(boolean distinct, List<Projection> projections, Optional<FromClause> from,
        List<Predicate> filters, GroupBy groupBy, List<Predicate> having, List<OrderKey> orderKeys,
        Optional<RowLimit> rowLimit) implements Statement {
}
