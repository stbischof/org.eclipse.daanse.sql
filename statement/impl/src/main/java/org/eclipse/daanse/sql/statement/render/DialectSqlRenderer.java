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
package org.eclipse.daanse.sql.statement.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.daanse.jdbc.db.api.schema.SchemaReference;
import org.eclipse.daanse.jdbc.db.api.schema.TableReference;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType;
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.api.model.DeleteStatement;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.eclipse.daanse.sql.statement.api.model.GroupBy;
import org.eclipse.daanse.sql.statement.api.model.InsertStatement;
import org.eclipse.daanse.sql.statement.api.model.JoinKind;
import org.eclipse.daanse.sql.statement.api.model.OrderKey;
import org.eclipse.daanse.sql.statement.api.model.Projection;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.SetOperation;
import org.eclipse.daanse.sql.statement.api.model.SortSpec;
import org.eclipse.daanse.sql.statement.api.model.Statement;
import org.eclipse.daanse.sql.statement.api.model.UpdateStatement;
import org.eclipse.daanse.sql.statement.api.model.WithStatement;
import org.eclipse.daanse.sql.statement.api.model.NullOrder;
import org.eclipse.daanse.sql.statement.api.model.SortDirection;
import org.eclipse.daanse.sql.statement.api.render.BoundParameter;
import org.eclipse.daanse.sql.statement.api.render.RenderOptions;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.eclipse.daanse.sql.statement.api.render.SqlRenderer;

/**
 * The {@link SqlRenderer} implementation. The single dialect-aware component: it turns the
 * dialect-free query model into SQL, taking every spelling decision (identifier/literal
 * quoting, the {@code AS} keyword, join style, group-by alias-vs-expression, grouping-set
 * support, pagination placement, null ordering) from the {@link Dialect}.
 * <p>
 * Output is single-line; {@link RenderOptions#multiLine()} is accepted but currently
 * rendered the same as compact.
 */
public final class DialectSqlRenderer implements SqlRenderer {

    private final Dialect dialect;

    /**
     * Bind parameters accumulated during a single {@link #render} call, in placeholder order.
     * Not thread-safe: render one statement at a time per instance (renderers are cheap).
     */
    private List<BoundParameter> parameters = new ArrayList<>();

    public DialectSqlRenderer(Dialect dialect) {
        this.dialect = Objects.requireNonNull(dialect, "dialect");
    }

    @Override
    public RenderedSql render(Statement statement, RenderOptions options) {
        this.parameters = new ArrayList<>();
        RenderedSql body = renderInternal(statement, options);
        return new RenderedSql(body.sql(), body.columnTypes(), List.copyOf(parameters));
    }

    /** Dispatch without resetting the parameter accumulator (used for nested statements). */
    private RenderedSql renderInternal(Statement statement, RenderOptions options) {
        if (statement instanceof SelectStatement select) {
            return renderSelect(select, options);
        }
        if (statement instanceof SetOperation set) {
            return renderSet(set);
        }
        if (statement instanceof InsertStatement insert) {
            return renderInsert(insert);
        }
        if (statement instanceof UpdateStatement update) {
            return renderUpdate(update);
        }
        if (statement instanceof DeleteStatement delete) {
            return renderDelete(delete);
        }
        if (statement instanceof WithStatement with) {
            return renderWith(with);
        }
        throw new IllegalArgumentException("unsupported statement: " + statement);
    }

    private RenderedSql renderWith(WithStatement with) {
        // CTE bodies render first, so their bind parameters precede the body's, in order.
        List<org.eclipse.daanse.jdbc.db.dialect.api.generator.CteGenerator.Cte> ctes = new ArrayList<>();
        for (org.eclipse.daanse.sql.statement.api.model.CommonTableExpression cte : with.ctes()) {
            String body = renderInternal(cte.query(), RenderOptions.compact()).sql();
            // Quote the CTE name (so body references via From.table(name, ...) match the casing);
            // append an explicit quoted column list when given (required for recursive CTEs on H2).
            String name = dialect.quoteIdentifier(cte.name());
            if (!cte.columns().isEmpty()) {
                name += "(" + cte.columns().stream().map(dialect::quoteIdentifier).collect(Collectors.joining(", "))
                        + ")";
            }
            ctes.add(new org.eclipse.daanse.jdbc.db.dialect.api.generator.CteGenerator.Cte(name, body));
        }
        String withClause = dialect.cteGenerator().withClause(ctes, with.recursive());
        RenderedSql body = renderInternal(with.body(), RenderOptions.compact());
        return RenderedSql.of(withClause + body.sql(), body.columnTypes());
    }

    // ---- DML -------------------------------------------------------------------

    private void appendQualifiedTable(StringBuilder sb, TableReference table) {
        String schema = table.schema().map(SchemaReference::name).orElse(null);
        sb.append(dialect.quoteIdentifier(schema, table.name()));
    }

    private RenderedSql renderInsert(InsertStatement ins) {
        StringBuilder sb = new StringBuilder("insert into ");
        appendQualifiedTable(sb, ins.table());
        if (!ins.columns().isEmpty()) {
            sb.append(" (")
                    .append(ins.columns().stream().map(c -> dialect.quoteIdentifier(c))
                            .collect(Collectors.joining(", ")))
                    .append(")");
        }
        if (ins.source().isPresent()) {
            sb.append(' ').append(renderInternal(ins.source().get(), RenderOptions.compact()).sql());
        } else {
            sb.append(" values ");
            sb.append(ins.rows().stream()
                    .map(row -> "(" + row.stream().map(this::renderExpression).collect(Collectors.joining(", ")) + ")")
                    .collect(Collectors.joining(", ")));
        }
        return RenderedSql.of(sb.toString(), List.of());
    }

    private RenderedSql renderUpdate(UpdateStatement upd) {
        StringBuilder sb = new StringBuilder("update ");
        appendQualifiedTable(sb, upd.table());
        sb.append(" set ");
        sb.append(upd.assignments().stream()
                .map(a -> dialect.quoteIdentifier(a.column()) + " = " + renderExpression(a.value()))
                .collect(Collectors.joining(", ")));
        if (!upd.filters().isEmpty()) {
            sb.append(" where ").append(renderPredicateList(upd.filters(), " and "));
        }
        return RenderedSql.of(sb.toString(), List.of());
    }

    private RenderedSql renderDelete(DeleteStatement del) {
        StringBuilder sb = new StringBuilder("delete from ");
        appendQualifiedTable(sb, del.table());
        if (!del.filters().isEmpty()) {
            sb.append(" where ").append(renderPredicateList(del.filters(), " and "));
        }
        return RenderedSql.of(sb.toString(), List.of());
    }

    // ---- SELECT ----------------------------------------------------------------

    /**
     * Formats a clause keyword/separator for the requested mode, byte-compatible with the legacy
     * {@code SqlQuery.ClauseList.formatClauseKeyword}: compact returns {@code s} unchanged; formatted maps a
     * leading-space keyword (e.g. {@code " from "}, {@code " and "}) to {@code NL + keyword.trimLeading} and a
     * trailing-space separator (e.g. {@code ", "}) to {@code keyword.trimTrailing + NL + indent} (a keyword
     * with both, like {@code " from "}, gets both → {@code "\nfrom\n<indent>"}); a keyword ending in
     * {@code "("} gets {@code "(" + NL + indent}. Top-level prefix is empty.
     */
    private static String fmtKw(String s, RenderOptions options) {
        if (!options.formatted()) {
            return s;
        }
        String nl = System.lineSeparator();
        String r = s;
        if (r.startsWith(" ")) {
            r = nl + r.substring(1);
        }
        if (r.endsWith(" ")) {
            r = r.substring(0, r.length() - 1) + nl + options.indent();
        } else if (r.endsWith("(")) {
            r = r + nl + options.indent();
        }
        return r;
    }

    private RenderedSql renderSelect(SelectStatement s, RenderOptions options) {
        final String itemSep = fmtKw(", ", options);
        StringBuilder sb = new StringBuilder();
        List<BestFitColumnType> types = new ArrayList<>();

        // WHERE accumulates the explicit filters plus anything pushed down while rendering
        // the FROM clause (non-ANSI comma joins, per-table filters).
        List<Predicate> where = new ArrayList<>(s.filters());
        String fromSql = s.from().map(f -> renderFrom(f, where, options)).orElse(null);

        sb.append(fmtKw(s.distinct() ? "select distinct " : "select ", options));
        s.rowLimit().ifPresent(rl -> dialect.paginationGenerator().selectPrefix(rl.maxRows(), rl.offset())
                .ifPresent(prefix -> sb.append(prefix).append(' ')));

        boolean first = true;
        for (int i = 0; i < s.projections().size(); i++) {
            Projection p = s.projections().get(i);
            if (!first) {
                sb.append(itemSep);
            }
            first = false;
            String exprSql = renderExpression(p.expression());
            sb.append(exprSql);
            String alias = effectiveAlias(p, i);
            // A "*" projection can never be aliased ("select * as c0" is invalid SQL).
            if (alias != null && !"*".equals(exprSql)) {
                sb.append(" as ").append(dialect.quoteIdentifier(alias));
            }
            types.add(p.columnType());
        }
        // GROUPING(...) super-aggregate columns are appended as extra select items.
        int g = 0;
        for (GroupBy.GroupingFunction gf : s.groupBy().groupingFunctions()) {
            sb.append(itemSep).append(dialect.functionGenerator().generateGrouping(renderExpression(gf.argument())));
            sb.append(" as ").append(dialect.quoteIdentifier("g" + g++));
            types.add(null);
        }

        if (fromSql != null) {
            sb.append(fmtKw(" from ", options)).append(fromSql);
        }
        if (!where.isEmpty()) {
            sb.append(fmtKw(" where ", options)).append(renderPredicateList(where, fmtKw(" and ", options)));
        }
        renderGroupBy(s.groupBy(), s.projections(), sb, options);
        if (!s.having().isEmpty()) {
            sb.append(fmtKw(" having ", options)).append(renderPredicateList(s.having(), fmtKw(" and ", options)));
        }
        if (!s.orderKeys().isEmpty()) {
            sb.append(fmtKw(" order by ", options));
            sb.append(s.orderKeys().stream().map(k -> renderOrderKey(k, s.projections()))
                    .collect(Collectors.joining(itemSep)));
        }
        s.rowLimit().ifPresent(rl -> sb.append(dialect.paginationGenerator().paginate(rl.maxRows(), rl.offset())));

        return RenderedSql.of(sb.toString(), Collections.unmodifiableList(types));
    }

    private String effectiveAlias(Projection p, int ordinal) {
        if (p.alias().isPresent()) {
            return p.alias().get().name();
        }
        if (dialect.allowsFieldAlias()) {
            return "c" + ordinal;
        }
        return null;
    }

    // ---- FROM ------------------------------------------------------------------

    private String renderFrom(FromClause from, List<Predicate> whereSink, RenderOptions options) {
        if (from instanceof FromClause.FromTable t) {
            StringBuilder b = new StringBuilder();
            appendQualifiedTable(b, t.table());
            b.append(dialect.allowsFromAlias() ? " as " : " ").append(dialect.quoteIdentifier(t.alias().name()));
            if (!t.hints().isEmpty()) {
                dialect.hintGenerator().appendHintsAfterFromClause(b, t.hints());
            }
            t.filter().ifPresent(whereSink::add);
            return b.toString();
        }
        if (from instanceof FromClause.FromSubquery sq) {
            String inner = renderSelect(sq.query(), RenderOptions.compact()).sql();
            return "(" + inner + ")" + (dialect.allowsFromAlias() ? " as " : " ")
                    + dialect.quoteIdentifier(sq.alias().name());
        }
        if (from instanceof FromClause.FromRaw r) {
            return "(" + r.sql() + ")" + (dialect.allowsFromAlias() ? " as " : " ")
                    + dialect.quoteIdentifier(r.alias().name());
        }
        if (from instanceof FromClause.FromVariant v) {
            // Resolve the per-dialect map here (the one render-time pick), then render exactly like FromRaw.
            return "(" + chooseVariant(v.byDialectName()) + ")" + (dialect.allowsFromAlias() ? " as " : " ")
                    + dialect.quoteIdentifier(v.alias().name());
        }
        if (from instanceof FromClause.FromProduct prod) {
            // Comma product: no predicate of its own — the caller put any join conditions in WHERE.
            return prod.items().stream().map(item -> renderFrom(item, whereSink, options))
                    .collect(Collectors.joining(fmtKw(", ", options)));
        }
        if (from instanceof FromClause.FromJoin j) {
            String left = renderFrom(j.left(), whereSink, options);
            String right = renderFrom(j.right(), whereSink, options);
            if (j.kind() == JoinKind.CROSS) {
                return left + " cross join " + right;
            }
            // LEFT joins must use ANSI ON; INNER joins may fall back to comma + WHERE.
            boolean ansi = dialect.allowsJoinOn() || j.kind() == JoinKind.LEFT;
            if (ansi) {
                String keyword = j.kind() == JoinKind.LEFT ? " left join " : " join ";
                return left + keyword + right + " on " + renderPredicate(j.on());
            }
            whereSink.add(j.on());
            return left + fmtKw(", ", options) + right;
        }
        throw new IllegalArgumentException("unsupported from clause: " + from);
    }

    /**
     * Resolve a per-dialect SQL-fragment map to the fragment for this renderer's dialect. The one render-time
     * dialect pick for {@link FromClause.FromVariant} (and any future expression variant) — a 1:1 lift of the
     * legacy {@code ViewCodeSet.chooseQuery} fallback: the live dialect's entry, else {@code "generic"}, else
     * an error.
     */
    private String chooseVariant(java.util.Map<String, String> byDialectName) {
        String picked = byDialectName.get(dialect.name());
        if (picked != null) {
            return picked;
        }
        String generic = byDialectName.get("generic");
        if (generic == null) {
            throw new IllegalArgumentException(
                    "no SQL variant for dialect '" + dialect.name() + "' and no 'generic' fallback");
        }
        return generic;
    }

    // ---- GROUP BY --------------------------------------------------------------

    private void renderGroupBy(GroupBy gb, List<Projection> projections, StringBuilder sb, RenderOptions options) {
        if (gb.isEmpty()) {
            return;
        }
        if (!gb.groupingSets().isEmpty() && dialect.supportsGroupingSets()) {
            sb.append(fmtKw(" group by grouping sets (", options));
            sb.append(gb.groupingSets().stream()
                    .map(set -> "(" + set.keys().stream().map(this::renderExpression)
                            .collect(Collectors.joining(", ")) + ")")
                    .collect(Collectors.joining(", ")));
            sb.append(")");
            return;
        }
        // Plain GROUP BY: explicit keys, plus (when grouping sets are unsupported) the distinct
        // union of all grouping-set keys as a best-effort fallback.
        List<String> rendered = new ArrayList<>();
        for (GroupBy.GroupKey key : gb.keys()) {
            rendered.add(renderGroupKey(key, projections));
        }
        Set<String> flattened = new LinkedHashSet<>(rendered);
        for (GroupBy.GroupingSet set : gb.groupingSets()) {
            for (SqlExpression e : set.keys()) {
                flattened.add(renderExpression(e));
            }
        }
        if (!flattened.isEmpty()) {
            sb.append(fmtKw(" group by ", options)).append(String.join(fmtKw(", ", options), flattened));
        }
    }

    private String renderGroupKey(GroupBy.GroupKey key, List<Projection> projections) {
        if (key instanceof GroupBy.GroupKey.Ref ref) {
            int ordinal = ref.projection().ordinal();
            Projection p = projections.get(ordinal);
            if (dialect.requiresGroupByAlias()) {
                String alias = effectiveAlias(p, ordinal);
                if (alias != null) {
                    return dialect.quoteIdentifier(alias);
                }
            }
            return renderExpression(p.expression());
        }
        GroupBy.GroupKey.Expr expr = (GroupBy.GroupKey.Expr) key;
        return renderExpression(expr.expression());
    }

    // ---- ORDER BY --------------------------------------------------------------

    private String renderOrderKey(OrderKey key, List<Projection> projections) {
        String exprSql;
        if (key.projectionRef().isPresent() && dialect.requiresOrderByAlias()) {
            int ordinal = key.projectionRef().get().ordinal();
            String alias = effectiveAlias(projections.get(ordinal), ordinal);
            exprSql = alias != null ? dialect.quoteIdentifier(alias) : renderExpression(key.expression());
        } else {
            exprSql = renderExpression(key.expression());
        }
        SortSpec spec = key.sort();
        boolean ascending = spec.direction() == SortDirection.ASC;
        boolean collateNullsLast = spec.nullOrder() != NullOrder.FIRST;
        if (spec.nullSortValue() != null) {
            // Order nulls as if they held nullSortValue (e.g. a parent-child hierarchy nullParentValue).
            return dialect.orderByGenerator().generateOrderItemForOrderValue(
                    exprSql, spec.nullSortValue(), spec.nullSortDatatype(), ascending, collateNullsLast).toString();
        }
        return dialect.orderByGenerator().generateOrderItem(exprSql, spec.nullable(), ascending, collateNullsLast)
                .toString();
    }

    // ---- expressions & predicates ----------------------------------------------

    private String renderExpression(SqlExpression e) {
        if (e instanceof SqlExpression.Column c) {
            String name = dialect.quoteIdentifier(c.name());
            return c.tableQualifier().map(q -> dialect.quoteIdentifier(q) + "." + name).orElse(name);
        }
        if (e instanceof SqlExpression.Literal l) {
            StringBuilder b = new StringBuilder();
            dialect.quote(b, l.value(), l.datatype());
            return b.toString();
        }
        if (e instanceof SqlExpression.Function f) {
            return f.name() + "("
                    + f.arguments().stream().map(this::renderExpression).collect(Collectors.joining(", ")) + ")";
        }
        if (e instanceof SqlExpression.Aggregate a) {
            return a.name() + "(" + (a.distinct() ? "distinct " : "")
                    + a.arguments().stream().map(this::renderExpression).collect(Collectors.joining(", ")) + ")";
        }
        if (e instanceof SqlExpression.Binary b) {
            String rendered = renderExpression(b.left()) + " " + b.operator().symbol() + " "
                    + renderExpression(b.right());
            return b.parenthesized() ? "(" + rendered + ")" : rendered;
        }
        if (e instanceof SqlExpression.Case c) {
            StringBuilder sb = new StringBuilder("case");
            for (SqlExpression.Case.WhenClause w : c.whens()) {
                sb.append(" when ").append(renderPredicate(w.condition())).append(" then ")
                        .append(renderExpression(w.result()));
            }
            c.elseResult().ifPresent(er -> sb.append(" else ").append(renderExpression(er)));
            return sb.append(" end").toString();
        }
        if (e instanceof SqlExpression.Param pm) {
            parameters.add(new BoundParameter(pm.value(), pm.bound(), pm.datatype()));
            return dialect.parameterPlaceholderGenerator().placeholder(parameters.size());
        }
        if (e instanceof SqlExpression.Raw r) {
            return r.sql();
        }
        if (e instanceof SqlExpression.RawVariant v) {
            return chooseVariant(v.byDialectName());
        }
        throw new IllegalArgumentException("unsupported expression: " + e);
    }

    private String renderPredicateList(List<Predicate> predicates, String separator) {
        return predicates.stream().map(this::renderPredicate).collect(Collectors.joining(separator));
    }

    /**
     * Renders a single predicate to its dialect SQL fragment (no surrounding SELECT context). Used by
     * callers that need a standalone predicate string, e.g. segment-cache-key construction.
     */
    public String renderPredicate(Predicate p) {
        if (p instanceof Predicate.Comparison c) {
            return renderExpression(c.left()) + " " + symbol(c.operator()) + " " + renderExpression(c.right());
        }
        if (p instanceof Predicate.In in) {
            return renderExpression(in.expression()) + " in ("
                    + in.values().stream().map(this::renderExpression).collect(Collectors.joining(", ")) + ")";
        }
        if (p instanceof Predicate.InTuple it) {
            String cols = it.columns().stream().map(this::renderExpression).collect(Collectors.joining(", "));
            String rows = it.rows().stream()
                    .map(row -> "(" + row.stream().map(this::renderExpression).collect(Collectors.joining(", ")) + ")")
                    .collect(Collectors.joining(", "));
            return "(" + cols + ") in (" + rows + ")";
        }
        if (p instanceof Predicate.IsNull n) {
            return renderExpression(n.expression()) + (n.negated() ? " is not null" : " is null");
        }
        if (p instanceof Predicate.Like l) {
            String base = renderExpression(l.expression()) + (l.negated() ? " not like " : " like ")
                    + renderExpression(l.pattern());
            return base + l.escape().map(c -> " escape '" + c + "'").orElse("");
        }
        if (p instanceof Predicate.Between b) {
            return renderExpression(b.expression()) + (b.negated() ? " not between " : " between ")
                    + renderExpression(b.low()) + " and " + renderExpression(b.high());
        }
        if (p instanceof Predicate.Not not) {
            return "not (" + renderPredicate(not.operand()) + ")";
        }
        if (p instanceof Predicate.And a) {
            if (a.operands().isEmpty()) {
                return "1 = 1";
            }
            return "(" + renderPredicateList(a.operands(), " and ") + ")";
        }
        if (p instanceof Predicate.Or o) {
            if (o.operands().isEmpty()) {
                return "1 = 0";
            }
            return "(" + renderPredicateList(o.operands(), " or ") + ")";
        }
        if (p instanceof Predicate.Raw r) {
            return r.sql();
        }
        throw new IllegalArgumentException("unsupported predicate: " + p);
    }

    private static String symbol(ComparisonOperator op) {
        return op.symbol();
    }

    // ---- set operations --------------------------------------------------------

    private RenderedSql renderSet(SetOperation so) {
        if (so.inputs().size() < 2) {
            throw new IllegalArgumentException("set operation needs at least two inputs");
        }
        // Render each input exactly once (so its parameters accumulate once, in order).
        List<RenderedSql> rendered = new ArrayList<>();
        for (Statement in : so.inputs()) {
            rendered.add(renderInternal(in, RenderOptions.compact()));
        }
        StringBuilder sb = new StringBuilder(rendered.stream().map(RenderedSql::sql)
                .collect(Collectors.joining(" " + so.op().keyword() + " ")));
        if (!so.orderKeys().isEmpty()) {
            List<Projection> none = List.of();
            sb.append(" order by ").append(
                    so.orderKeys().stream().map(k -> renderOrderKey(k, none)).collect(Collectors.joining(", ")));
        }
        so.rowLimit().ifPresent(rl -> sb.append(dialect.paginationGenerator().paginate(rl.maxRows(), rl.offset())));
        // Column types are those of the first input.
        return RenderedSql.of(sb.toString(), rendered.get(0).columnTypes());
    }
}
