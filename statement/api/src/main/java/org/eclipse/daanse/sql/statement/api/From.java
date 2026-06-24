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

import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.api.schema.SchemaReference;
import org.eclipse.daanse.jdbc.db.api.schema.TableReference;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;

/** Factory methods for {@link FromClause} nodes. */
public final class From {

    private From() {
    }

    /** Builds a (optionally schema-qualified) {@link TableReference}. */
    public static TableReference tableRef(String schema, String table) {
        return schema == null ? new TableReference(table)
                : new TableReference(Optional.of(new SchemaReference(schema)), table);
    }

    public static FromClause.FromTable table(String table, TableAlias alias) {
        return table(new TableReference(table), alias);
    }

    public static FromClause.FromTable table(String schema, String table, TableAlias alias) {
        return table(tableRef(schema, table), alias);
    }

    public static FromClause.FromTable table(TableReference table, TableAlias alias) {
        return new FromClause.FromTable(table, alias, Optional.empty(), Map.of());
    }

    public static FromClause.FromTable table(String schema, String table, TableAlias alias, Predicate filter,
            Map<String, String> hints) {
        return table(tableRef(schema, table), alias, filter, hints);
    }

    public static FromClause.FromTable table(TableReference table, TableAlias alias, Predicate filter,
            Map<String, String> hints) {
        return new FromClause.FromTable(table, alias, Optional.ofNullable(filter), Map.copyOf(hints));
    }

    public static FromClause.FromSubquery subquery(SelectStatement query, TableAlias alias) {
        return new FromClause.FromSubquery(query, alias);
    }

    /** A derived table from a pre-rendered SQL fragment (view / inline {@code VALUES}). */
    public static FromClause.FromRaw raw(String sql, TableAlias alias) {
        return new FromClause.FromRaw(sql, alias);
    }

    /**
     * A comma product of two-or-more from-clauses with no join predicate of its own — the caller
     * adds the join conditions to {@code WHERE} (see {@link FromClause.FromProduct}).
     */
    public static FromClause.FromProduct product(FromClause first, FromClause second, FromClause... rest) {
        java.util.List<FromClause> items = new java.util.ArrayList<>();
        items.add(first);
        items.add(second);
        java.util.Collections.addAll(items, rest);
        return new FromClause.FromProduct(items);
    }
}
