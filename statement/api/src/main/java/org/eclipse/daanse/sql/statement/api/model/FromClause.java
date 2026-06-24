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
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.api.schema.TableReference;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;

/**
 * The {@code FROM} clause as a tree of table references, sub-queries and joins.
 * <p>
 * Whether a {@link FromJoin} renders as an ANSI {@code JOIN ... ON} or as an old-style
 * comma join with the condition pushed into {@code WHERE} is decided by the renderer from
 * the dialect's capabilities — there is a single representation here.
 */
public sealed interface FromClause {

    /**
     * A base table reference.
     *
     * @param table  the (optionally schema-qualified) table, as the shared jdbc.db identifier
     * @param alias  the query-local table alias
     * @param filter an optional per-table filter to add to {@code WHERE}
     * @param hints  optimizer hints (dialect-specific; may be empty)
     */
    record FromTable(TableReference table, TableAlias alias, Optional<Predicate> filter,
            Map<String, String> hints) implements FromClause {
    }

    /**
     * A derived table (sub-query) reference.
     *
     * @param query the sub-query
     * @param alias the derived-table alias
     */
    record FromSubquery(SelectStatement query, TableAlias alias) implements FromClause {
    }

    /**
     * A derived table from a pre-rendered, dialect-specific SQL fragment (e.g. a mapping view's
     * chosen SQL, or an inline {@code VALUES} table). Unlike {@link FromSubquery} the body is not a
     * structured {@link SelectStatement} — it is the SQL the source already produces for the target
     * dialect, wrapped as {@code (sql) as alias}.
     *
     * @param sql   the derived-table body SQL (without surrounding parentheses)
     * @param alias the derived-table alias
     */
    record FromRaw(String sql, TableAlias alias) implements FromClause {
    }

    /**
     * A join of two from-clauses.
     *
     * @param left  the left input
     * @param kind  the join kind
     * @param right the right input
     * @param on    the join condition (ignored for {@link JoinKind#CROSS})
     */
    record FromJoin(FromClause left, JoinKind kind, FromClause right, Predicate on) implements FromClause {
    }

    /**
     * A comma-separated product of from-clauses with <em>no</em> join predicate of its own —
     * the caller is responsible for adding the join conditions to {@code WHERE} (via the
     * builder's {@code where(...)}). This lets a mapper reproduce the exact {@code WHERE}-conjunct
     * order of a legacy query (where join and constraint predicates were interleaved by call
     * order), which a {@link FromJoin}'s deferred predicate-push cannot.
     *
     * @param items the comma-joined inputs (at least two)
     */
    record FromProduct(List<FromClause> items) implements FromClause {
        public FromProduct {
            if (items == null || items.size() < 2) {
                throw new IllegalArgumentException("FromProduct requires at least two items");
            }
            items = List.copyOf(items);
        }
    }
}
