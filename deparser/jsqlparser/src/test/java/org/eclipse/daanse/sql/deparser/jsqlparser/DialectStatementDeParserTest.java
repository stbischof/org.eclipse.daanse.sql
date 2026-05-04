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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.IdentifierQuotingPolicy;
import org.junit.jupiter.api.Test;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

class DialectStatementDeParserTest {

    @Test
    void testSimpleSelect_AnsiDialect() throws JSQLParserException {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        StringBuilder buffer = new StringBuilder();
        BasicDialectStatementDeParser deparser = new BasicDialectStatementDeParser(buffer, dialect);

        Statement stmt = CCJSqlParserUtil.parse("SELECT col1 FROM table1");
        stmt.accept(deparser);

        String result = buffer.toString();
        // Plain canonical-case identifiers are not quoted under WHEN_NEEDED
        assertThat(result).contains("col1");
        assertThat(result).contains("table1");
        assertThat(result).doesNotContain("\"col1\"");
        assertThat(result).doesNotContain("\"table1\"");
    }

    @Test
    void testSimpleSelect_MySqlDialect() throws JSQLParserException {
        Dialect dialect = MockDialectHelper.createMySqlDialect();
        StringBuilder buffer = new StringBuilder();
        BasicDialectStatementDeParser deparser = new BasicDialectStatementDeParser(buffer, dialect);

        Statement stmt = CCJSqlParserUtil.parse("SELECT col1 FROM table1");
        stmt.accept(deparser);

        String result = buffer.toString();
        assertThat(result).contains("col1");
        assertThat(result).contains("table1");
        assertThat(result).doesNotContain("`col1`");
        assertThat(result).doesNotContain("`table1`");
    }

    @Test
    void testSelectWithStringLiteral() throws JSQLParserException {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        StringBuilder buffer = new StringBuilder();
        BasicDialectStatementDeParser deparser = new BasicDialectStatementDeParser(buffer, dialect);

        Statement stmt = CCJSqlParserUtil.parse("SELECT * FROM table1 WHERE name = 'test'");
        stmt.accept(deparser);

        String result = buffer.toString();
        assertThat(result).contains("'test'");
    }

    @Test
    void testSelectWithDateLiteral() throws JSQLParserException {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        StringBuilder buffer = new StringBuilder();
        BasicDialectStatementDeParser deparser = new BasicDialectStatementDeParser(buffer, dialect);

        Statement stmt = CCJSqlParserUtil.parse("SELECT * FROM table1 WHERE created = DATE '2023-12-15'");
        stmt.accept(deparser);

        String result = buffer.toString();
        assertThat(result).contains("DATE '2023-12-15'");
    }

    @Test
    void testSelectWithJoin() throws JSQLParserException {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        StringBuilder buffer = new StringBuilder();
        BasicDialectStatementDeParser deparser = new BasicDialectStatementDeParser(buffer, dialect);

        Statement stmt = CCJSqlParserUtil
                .parse("SELECT t1.col1, t2.col2 FROM table1 t1 JOIN table2 t2 ON t1.id = t2.id");
        stmt.accept(deparser);

        String result = buffer.toString();
        // Aliases used as qualifiers must remain unquoted to match the FROM-clause emission
        assertThat(result).contains("t1.col1");
        assertThat(result).contains("t2.col2");
        assertThat(result).doesNotContain("\"t1\"");
        assertThat(result).doesNotContain("\"t2\"");
    }

    @Test
    void testSelectWithSubquery() throws JSQLParserException {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        StringBuilder buffer = new StringBuilder();
        BasicDialectStatementDeParser deparser = new BasicDialectStatementDeParser(buffer, dialect);

        Statement stmt = CCJSqlParserUtil.parse("SELECT * FROM table1 WHERE id IN (SELECT id FROM table2)");
        stmt.accept(deparser);

        String result = buffer.toString();
        // Plain canonical-case "id" is not quoted under WHEN_NEEDED
        assertThat(result).contains("id");
        assertThat(result).doesNotContain("\"id\"");
    }

    @Test
    void testQualifiedColumnQualifierIsNotQuoted_H2Compatible() throws JSQLParserException {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        StringBuilder buffer = new StringBuilder();
        BasicDialectStatementDeParser deparser = new BasicDialectStatementDeParser(buffer, dialect);

        Statement stmt = CCJSqlParserUtil.parse(
                "SELECT pc.EnglishProductCategoryName, sum(f.OrderQuantity) "
                        + "FROM Fact f JOIN ProductCategory pc ON f.cat = pc.cat");
        stmt.accept(deparser);

        String result = buffer.toString();
        // Mixed-case column names still get quoted under WHEN_NEEDED
        assertThat(result).contains("f.\"OrderQuantity\"");
        assertThat(result).contains("pc.\"EnglishProductCategoryName\"");
        // Aliases as qualifiers stay unquoted
        assertThat(result).contains("AS f");
        assertThat(result).doesNotContain("\"f\".");
        assertThat(result).doesNotContain("\"pc\".");
        // Plain-canonical column does not get quoted
        assertThat(result).contains("f.cat");
        assertThat(result).contains("pc.cat");
    }

    // -------------------------------------------------------------------------
    // Configurable IdentifierQuotingPolicy
    // -------------------------------------------------------------------------

    @Test
    void testQuotingPolicy_WhenNeeded_isDefault_andLeavesPlainNamesUnquoted() throws JSQLParserException {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        StringBuilder buffer = new StringBuilder();
        // Default constructor → WHEN_NEEDED
        BasicDialectStatementDeParser deparser = new BasicDialectStatementDeParser(buffer, dialect);

        Statement stmt = CCJSqlParserUtil
                .parse("SELECT t1.col1, t1.OrderQuantity FROM table1 t1");
        stmt.accept(deparser);

        String result = buffer.toString();
        // table1, col1, t1: plain lowercase → not quoted
        assertThat(result).contains("table1");
        assertThat(result).contains("t1.col1");
        // OrderQuantity: mixed case → quoted
        assertThat(result).contains("t1.\"OrderQuantity\"");
        assertThat(result).doesNotContain("\"col1\"");
        assertThat(result).doesNotContain("\"table1\"");
        assertThat(result).doesNotContain("\"t1\"");
    }

    @Test
    void testQuotingPolicy_Always_quotesEveryIdentifierExceptAliases() throws JSQLParserException {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        StringBuilder buffer = new StringBuilder();
        BasicDialectStatementDeParser deparser = new BasicDialectStatementDeParser(buffer, dialect,
                IdentifierQuotingPolicy.ALWAYS);

        Statement stmt = CCJSqlParserUtil
                .parse("SELECT t1.col1, t1.OrderQuantity FROM table1 t1");
        stmt.accept(deparser);

        String result = buffer.toString();
        // ALWAYS → table1 and column names quoted
        assertThat(result).contains("\"table1\"");
        assertThat(result).contains("\"col1\"");
        assertThat(result).contains("\"OrderQuantity\"");
        // Aliases used as qualifiers stay unquoted (this is the bug-fix invariant,
        // independent of the quoting policy)
        assertThat(result).contains("AS t1");
        assertThat(result).contains("t1.\"col1\"");
        assertThat(result).contains("t1.\"OrderQuantity\"");
        assertThat(result).doesNotContain("\"t1\".");
    }

    @Test
    void testQuotingPolicy_Always_passedThroughDeparserApi() throws JSQLParserException {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        BasicDialectDeparser api = new BasicDialectDeparser();

        Statement stmt = CCJSqlParserUtil.parse("SELECT t1.col1 FROM table1 t1");

        String whenNeeded = api.deparse(stmt, dialect);
        String always = api.deparse(stmt, dialect, IdentifierQuotingPolicy.ALWAYS);

        // WHEN_NEEDED leaves plain lowercase names alone
        assertThat(whenNeeded).contains("table1");
        assertThat(whenNeeded).contains("t1.col1");
        assertThat(whenNeeded).doesNotContain("\"col1\"");
        assertThat(whenNeeded).doesNotContain("\"table1\"");

        // ALWAYS quotes them
        assertThat(always).contains("\"table1\"");
        assertThat(always).contains("\"col1\"");
        assertThat(always).contains("AS t1");
        assertThat(always).doesNotContain("\"t1\".");
    }

    @Test
    void testQuotingPolicy_Never_emitsAllIdentifiersBare() throws JSQLParserException {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        StringBuilder buffer = new StringBuilder();
        BasicDialectStatementDeParser deparser = new BasicDialectStatementDeParser(buffer, dialect,
                IdentifierQuotingPolicy.NEVER);

        Statement stmt = CCJSqlParserUtil
                .parse("SELECT t1.col1, t1.OrderQuantity FROM table1 t1");
        stmt.accept(deparser);

        String result = buffer.toString();
        assertThat(result).contains("table1");
        assertThat(result).contains("t1.col1");
        assertThat(result).contains("t1.OrderQuantity");
        assertThat(result).doesNotContain("\"");
    }

    // -------------------------------------------------------------------------
    // Behavior must be the same shape for every dialect — only the quote
    // character changes. The alias-as-qualifier-stays-unquoted invariant is
    // dialect-independent.
    // -------------------------------------------------------------------------

    @Test
    void testQuotingPolicy_AcrossDialects_WhenNeeded() throws JSQLParserException {
        for (DialectFlavor flavor : DialectFlavor.values()) {
            Dialect dialect = flavor.create();
            String result = new BasicDialectDeparser().deparse(
                    CCJSqlParserUtil.parse("SELECT t1.col1, t1.OrderQuantity FROM table1 t1"),
                    dialect, IdentifierQuotingPolicy.WHEN_NEEDED);

            // Plain canonical-case identifiers stay bare under WHEN_NEEDED, regardless of dialect
            assertThat(result).as("dialect=%s", flavor).contains("table1");
            assertThat(result).as("dialect=%s", flavor).contains("t1.col1");
            // Mixed-case column gets quoted with the dialect's quote character
            assertThat(result).as("dialect=%s", flavor)
                    .contains("t1." + flavor.q("OrderQuantity"));
            // Alias remains unquoted as a qualifier — the bug fix invariant
            assertThat(result).as("dialect=%s", flavor).doesNotContain(flavor.q("t1") + ".");
            // The plain identifiers are NOT wrapped in this dialect's quote chars
            assertThat(result).as("dialect=%s", flavor).doesNotContain(flavor.q("col1"));
            assertThat(result).as("dialect=%s", flavor).doesNotContain(flavor.q("table1"));
        }
    }

    @Test
    void testQuotingPolicy_AcrossDialects_Always() throws JSQLParserException {
        for (DialectFlavor flavor : DialectFlavor.values()) {
            Dialect dialect = flavor.create();
            String result = new BasicDialectDeparser().deparse(
                    CCJSqlParserUtil.parse("SELECT t1.col1, t1.OrderQuantity FROM table1 t1"),
                    dialect, IdentifierQuotingPolicy.ALWAYS);

            // ALWAYS quotes every identifier with the dialect's quote character
            assertThat(result).as("dialect=%s", flavor).contains(flavor.q("table1"));
            assertThat(result).as("dialect=%s", flavor).contains(flavor.q("col1"));
            assertThat(result).as("dialect=%s", flavor).contains(flavor.q("OrderQuantity"));
            // Alias stays unquoted even with ALWAYS — invariant holds across all dialects
            assertThat(result).as("dialect=%s", flavor).contains("AS t1");
            assertThat(result).as("dialect=%s", flavor).doesNotContain(flavor.q("t1") + ".");
        }
    }

    // -------------------------------------------------------------------------
    // OSGi metatype wiring
    // -------------------------------------------------------------------------

    @Test
    void testOsgiConfig_ConstructorInjection_setsQuotingPolicy() throws JSQLParserException {
        BasicDialectDeparser component = new BasicDialectDeparser(configWithPolicy(IdentifierQuotingPolicy.ALWAYS));

        // The implicit (no-policy) deparse() follows the configured policy
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        Statement stmt = CCJSqlParserUtil.parse("SELECT col1 FROM table1");
        String result = component.deparse(stmt, dialect);
        assertThat(result).contains("\"col1\"");
        assertThat(result).contains("\"table1\"");
    }

    @Test
    void testOsgiConfig_NoArgConstructor_usesDefaultPolicy() throws JSQLParserException {
        BasicDialectDeparser component = new BasicDialectDeparser();
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        Statement stmt = CCJSqlParserUtil.parse("SELECT col1 FROM table1");

        // Default = WHEN_NEEDED → plain canonical names stay unquoted
        String result = component.deparse(stmt, dialect);
        assertThat(result).contains("col1");
        assertThat(result).contains("table1");
        assertThat(result).doesNotContain("\"col1\"");
        assertThat(result).doesNotContain("\"table1\"");
    }

    @Test
    void testOsgiConfig_NullConfig_fallsBackToDefault() throws JSQLParserException {
        BasicDialectDeparser component = new BasicDialectDeparser((BasicDialectDeparserConfig) null);
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        Statement stmt = CCJSqlParserUtil.parse("SELECT col1 FROM table1");

        // Null config → falls back to WHEN_NEEDED behavior
        String result = component.deparse(stmt, dialect);
        assertThat(result).doesNotContain("\"col1\"");
        assertThat(result).doesNotContain("\"table1\"");
    }

    @Test
    void testOsgiConfig_ExplicitPolicyOverridesConfigured() throws JSQLParserException {
        BasicDialectDeparser component = new BasicDialectDeparser(configWithPolicy(IdentifierQuotingPolicy.ALWAYS));

        Dialect dialect = MockDialectHelper.createAnsiDialect();
        Statement stmt = CCJSqlParserUtil.parse("SELECT col1 FROM table1");

        // Explicit policy on the call wins over the configured one
        String never = component.deparse(stmt, dialect, IdentifierQuotingPolicy.NEVER);
        assertThat(never).doesNotContain("\"");
        assertThat(never).contains("col1");
        assertThat(never).contains("table1");
    }

    private static BasicDialectDeparserConfig configWithPolicy(IdentifierQuotingPolicy policy) {
        BasicDialectDeparserConfig cfg = mock(BasicDialectDeparserConfig.class);
        when(cfg.quotingPolicy()).thenReturn(policy);
        return cfg;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private enum DialectFlavor {
        ANSI("\"", "\""), MYSQL("`", "`"), SQLSERVER("[", "]");

        final String open;
        final String close;

        DialectFlavor(String open, String close) {
            this.open = open;
            this.close = close;
        }

        Dialect create() {
            return switch (this) {
            case ANSI -> MockDialectHelper.createAnsiDialect();
            case MYSQL -> MockDialectHelper.createMySqlDialect();
            case SQLSERVER -> MockDialectHelper.createSqlServerDialect();
            };
        }

        String q(String name) {
            return open + name + close;
        }
    }

}
