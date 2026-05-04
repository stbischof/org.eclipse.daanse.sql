/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.deparser.jsqlparser;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.IdentifierQuotingPolicy;

import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.TimeValue;
import net.sf.jsqlparser.expression.TimestampValue;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.SelectVisitor;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;

public class BasicDialectExpressionDeParser extends ExpressionDeParser {

    public static final IdentifierQuotingPolicy DEFAULT_QUOTING_POLICY = IdentifierQuotingPolicy.WHEN_NEEDED;

    private final Dialect dialect;
    private final IdentifierQuotingPolicy quotingPolicy;

    public BasicDialectExpressionDeParser(Dialect dialect) {
        this(dialect, DEFAULT_QUOTING_POLICY);
    }

    public BasicDialectExpressionDeParser(Dialect dialect, IdentifierQuotingPolicy quotingPolicy) {
        super();
        this.builder = new StringBuilder();
        this.dialect = dialect;
        this.quotingPolicy = quotingPolicy == null ? DEFAULT_QUOTING_POLICY : quotingPolicy;
    }

    public BasicDialectExpressionDeParser(SelectVisitor<StringBuilder> selectVisitor, StringBuilder buffer,
            Dialect dialect) {
        this(selectVisitor, buffer, dialect, DEFAULT_QUOTING_POLICY);
    }

    public BasicDialectExpressionDeParser(SelectVisitor<StringBuilder> selectVisitor, StringBuilder buffer,
            Dialect dialect, IdentifierQuotingPolicy quotingPolicy) {
        super(selectVisitor, buffer);
        this.dialect = dialect;
        this.quotingPolicy = quotingPolicy == null ? DEFAULT_QUOTING_POLICY : quotingPolicy;
    }

    // Identifier Quoting

    @Override
    public <S> StringBuilder visit(Column tableColumn, S context) {
        final Table table = tableColumn.getTable();
        String tableName = null;

        if (table != null) {
            if (table.getAlias() != null) {
                tableName = table.getAlias().getName();
            } else {
                tableName = table.getFullyQualifiedName();
            }
        }

        if (tableName != null && !tableName.isEmpty()) {
            builder.append(tableName).append('.');
        }
        dialect.quoteIdentifierWith(tableColumn.getColumnName(), builder, quotingPolicy);

        if (tableColumn.getArrayConstructor() != null) {
            tableColumn.getArrayConstructor().accept(this, context);
        }

        if (tableColumn.getCommentText() != null) {
            builder.append(" /* ").append(tableColumn.getCommentText()).append(" */");
        }

        return builder;
    }

    // String Literal Quoting

    @Override
    public <S> StringBuilder visit(StringValue stringValue, S context) {
        if (stringValue.getPrefix() != null) {
            builder.append(stringValue.getPrefix());
        }
        dialect.quoteStringLiteral(builder, stringValue.getValue());
        return builder;
    }

    // Date/Time/Timestamp Literal Quoting

    @Override
    public <S> StringBuilder visit(DateValue dateValue, S context) {
        dialect.quoteDateLiteral(builder, dateValue.getValue().toString());
        return builder;
    }

    @Override
    public <S> StringBuilder visit(TimeValue timeValue, S context) {
        dialect.quoteTimeLiteral(builder, timeValue.getValue().toString());
        return builder;
    }

    @Override
    public <S> StringBuilder visit(TimestampValue timestampValue, S context) {
        dialect.quoteTimestampLiteral(builder, timestampValue.getValue().toString());
        return builder;
    }

    // Numeric Literal Formatting

    @Override
    public <S> StringBuilder visit(LongValue longValue, S context) {
        dialect.quoteNumericLiteral(builder, longValue.getStringValue());
        return builder;
    }

    @Override
    public <S> StringBuilder visit(DoubleValue doubleValue, S context) {
        String valueString = doubleValue.toString();
        if (dialect.needsExponent(doubleValue.getValue(), valueString)) {
            valueString += "E0";
        }
        dialect.quoteNumericLiteral(builder, valueString);
        return builder;
    }
}
