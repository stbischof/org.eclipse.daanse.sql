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

import net.sf.jsqlparser.util.deparser.StatementDeParser;

public class BasicDialectStatementDeParser extends StatementDeParser {

    public static final IdentifierQuotingPolicy DEFAULT_QUOTING_POLICY = IdentifierQuotingPolicy.WHEN_NEEDED;

    public BasicDialectStatementDeParser(StringBuilder buffer, Dialect dialect) {
        this(buffer, dialect, DEFAULT_QUOTING_POLICY);
    }

    public BasicDialectStatementDeParser(StringBuilder buffer, Dialect dialect, IdentifierQuotingPolicy quotingPolicy) {

        super(createExpressionDeParser(dialect, buffer, quotingPolicy),
                createSelectDeParser(buffer, dialect, quotingPolicy), buffer);

        BasicDialectSelectDeParser selectDeParser = (BasicDialectSelectDeParser) getSelectDeParser();
        BasicDialectExpressionDeParser expressionDeParser = (BasicDialectExpressionDeParser) getExpressionDeParser();
        selectDeParser.setExpressionVisitor(expressionDeParser);
        expressionDeParser.setSelectVisitor(selectDeParser);
    }

    private static BasicDialectExpressionDeParser createExpressionDeParser(Dialect dialect, StringBuilder buffer,
            IdentifierQuotingPolicy quotingPolicy) {
        return new BasicDialectExpressionDeParser(dialect, quotingPolicy);
    }

    private static BasicDialectSelectDeParser createSelectDeParser(StringBuilder buffer, Dialect dialect,
            IdentifierQuotingPolicy quotingPolicy) {
        return new BasicDialectSelectDeParser(buffer, dialect, quotingPolicy);
    }

}
