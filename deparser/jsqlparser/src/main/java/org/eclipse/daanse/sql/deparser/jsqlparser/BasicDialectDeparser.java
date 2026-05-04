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
import org.eclipse.daanse.sql.deparser.api.DialectDeparser;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.metatype.annotations.Designate;

import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.deparser.StatementDeParser;

/**
 * Factory implementation for creating dialect-aware SQL deparsers.
 */
@Component(service = DialectDeparser.class, scope = ServiceScope.SINGLETON)
@Designate(ocd = BasicDialectDeparserConfig.class)
public class BasicDialectDeparser implements DialectDeparser {

    private final IdentifierQuotingPolicy configuredPolicy;

    public BasicDialectDeparser() {
        this((IdentifierQuotingPolicy) null);
    }

    public BasicDialectDeparser(IdentifierQuotingPolicy quotingPolicy) {
        this.configuredPolicy = quotingPolicy == null ? BasicDialectStatementDeParser.DEFAULT_QUOTING_POLICY
                : quotingPolicy;
    }

    @Activate
    public BasicDialectDeparser(BasicDialectDeparserConfig config) {
        this(config == null ? null : config.quotingPolicy());
    }

    @Override
    public String deparse(Statement statement, Dialect dialect) {
        return deparse(statement, dialect, configuredPolicy);
    }

    @Override
    public String deparse(Statement statement, Dialect dialect, IdentifierQuotingPolicy quotingPolicy) {
        StringBuilder buffer = new StringBuilder();
        IdentifierQuotingPolicy effective = quotingPolicy == null ? configuredPolicy : quotingPolicy;
        StatementDeParser deparser = new BasicDialectStatementDeParser(buffer, dialect, effective);
        statement.accept(deparser);
        return buffer.toString();
    }
}
