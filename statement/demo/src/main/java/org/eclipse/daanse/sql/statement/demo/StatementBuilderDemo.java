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
package org.eclipse.daanse.sql.statement.demo;

import java.util.List;

import org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.ColumnAlias;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.SetOperation;
import org.eclipse.daanse.sql.statement.api.model.SortSpec;
import org.eclipse.daanse.sql.statement.api.model.Statement;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;

/**
 * Runnable demonstration of the dialect-agnostic SQL query builder.
 * <p>
 * Each {@code build*} method assembles an immutable {@link Statement} using only the
 * dialect-free API; {@link #main(String[])} renders them with a concrete dialect. The same
 * model renders differently per dialect (e.g. row limits) — proof that "structure" and
 * "spelling" are cleanly separated.
 *
 * <h2>How a real adapter would use this</h2> A ROLAP or ORM adapter never touches SQL text.
 * It only translates its own model into the builder primitives, e.g.:
 *
 * <pre>{@code
 * // ORM: load Customer (entity -> table, field -> column, ref -> join)
 * var q = SelectStatementBuilder.create();
 * TableAlias c = TableAlias.of("c");
 * q.from(From.table(customerEntity.table(), c));            // entity -> FROM table
 * for (Attribute a : customerEntity.attributes()) {
 *     q.project(Expressions.column(c, a.columnName()), a.columnType());   // field -> column
 * }
 * q.where(criteria.toPredicate(c));                          // criteria -> Predicate
 * RenderedSql sql = renderer.render(q.build());              // dialect spells it
 * // ... then map each ResultSet row back into the EObject using the same field<->column map.
 * }</pre>
 *
 * The builder/renderer is reusable; only the translation above is domain-specific.
 */
public final class StatementBuilderDemo {

    private StatementBuilderDemo() {
    }

    /**
     * {@code sales} joined to {@code product}, counted per product name, filtered and ordered.
     * Demonstrates projections, a join, a WHERE (comparison), GROUP BY by handle, and ORDER BY.
     */
    public static SelectStatement salesByProduct() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias s = TableAlias.of("s");
        TableAlias p = TableAlias.of("p");

        q.from(From.table("sales", s));
        q.innerJoin(From.table("product", p),
                Predicates.eq(Expressions.column(s, "product_id"), Expressions.column(p, "id")));

        var nameRef = q.project(Expressions.column(p, "name"), BestFitColumnType.STRING);
        q.project(Expressions.countStar(), BestFitColumnType.LONG, ColumnAlias.of("cnt"));

        q.where(Predicates.gt(Expressions.column(s, "amount"), Expressions.literal(100, Datatype.INTEGER)));
        q.groupOn(nameRef);
        q.orderOn(nameRef, SortSpec.asc());
        return q.build();
    }

    /**
     * Top-N product names. Demonstrates a row limit, which renders as a trailing
     * {@code FETCH NEXT} on ANSI dialects but as a leading {@code TOP} on SQL Server.
     */
    public static SelectStatement topProducts() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias p = TableAlias.of("p");
        q.from(From.table("product", p));
        var nameRef = q.project(Expressions.column(p, "name"), BestFitColumnType.STRING);
        q.orderOn(nameRef, SortSpec.asc());
        q.rowLimit(10);
        return q.build();
    }

    /** A {@code UNION ALL} of two simple selects (e.g. table-per-class style). */
    public static Statement productNameUnion() {
        SelectStatementBuilder a = SelectStatementBuilder.create();
        TableAlias ta = TableAlias.of("a");
        a.from(From.table("product_a", ta));
        a.project(Expressions.column(ta, "name"), BestFitColumnType.STRING);

        SelectStatementBuilder b = SelectStatementBuilder.create();
        TableAlias tb = TableAlias.of("b");
        b.from(From.table("product_b", tb));
        b.project(Expressions.column(tb, "name"), BestFitColumnType.STRING);

        return SetOperation.unionAll(List.of(a.build(), b.build()));
    }

    public static void main(String[] args) {
        DialectSqlRenderer renderer = new DialectSqlRenderer(new AnsiDialect());
        print(renderer, "salesByProduct", salesByProduct());
        print(renderer, "topProducts", topProducts());
        print(renderer, "productNameUnion", productNameUnion());
    }

    private static void print(DialectSqlRenderer renderer, String label, Statement statement) {
        // Using System.out is intentional for a runnable demo.
        System.out.println("-- " + label);
        System.out.println(renderer.render(statement).sql());
        System.out.println();
    }
}
