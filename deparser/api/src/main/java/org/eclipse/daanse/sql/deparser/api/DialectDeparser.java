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
package org.eclipse.daanse.sql.deparser.api;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.IdentifierQuotingPolicy;

/**
 * Dialect-aware SQL deparsers.
 */
public interface DialectDeparser {

    /**
     * Deparses a SQL statement using the given dialect with the implementation's
     * default identifier-quoting policy ({@link IdentifierQuotingPolicy#WHEN_NEEDED}).
     *
     * @param statement the JSqlParser statement to deparse
     * @param dialect   the database dialect to use for SQL generation
     * @return the generated SQL string
     */
    String deparse(net.sf.jsqlparser.statement.Statement statement, Dialect dialect);

    /**
     * Deparses a SQL statement using the given dialect and explicit quoting policy.
     * Default routes back to {@link #deparse(net.sf.jsqlparser.statement.Statement, Dialect)}
     * for implementations that don't honor an explicit policy.
     *
     * @param statement     the JSqlParser statement to deparse
     * @param dialect       the database dialect to use for SQL generation
     * @param quotingPolicy controls when identifiers (column, table, schema, catalog)
     *                      are wrapped in the dialect's quote character; {@code null}
     *                      uses the implementation default
     * @return the generated SQL string
     */
    default String deparse(net.sf.jsqlparser.statement.Statement statement, Dialect dialect,
            IdentifierQuotingPolicy quotingPolicy) {
        return deparse(statement, dialect);
    }
}
