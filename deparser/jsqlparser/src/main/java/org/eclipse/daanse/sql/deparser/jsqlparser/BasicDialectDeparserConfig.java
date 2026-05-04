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

import org.eclipse.daanse.jdbc.db.dialect.api.IdentifierQuotingPolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi metatype configuration for {@link BasicDialectDeparser}.
 *
 * <p>
 * Lets a deployer choose how aggressively the deparser quotes identifiers
 * (column, table, schema, catalog) without changing the underlying dialect's
 * own quoting policy. Aliases used as qualifiers in column references are
 * always emitted unquoted regardless of this setting — that is a correctness
 * invariant, not a styling choice.
 */
@ObjectClassDefinition(name = "%ocd.deparser.name", description = "%ocd.deparser.description", localization = BasicDialectDeparserConfig.OCD_LOCALIZATION)
public @interface BasicDialectDeparserConfig {

    String OCD_LOCALIZATION = "OSGI-INF/l10n/org.eclipse.daanse.sql.deparser.jsqlparser.ocd";

    /**
     * Identifier quoting policy applied by the deparser when emitting column,
     * table, schema and catalog names.
     *
     * <ul>
     * <li>{@code WHEN_NEEDED} (default) — quote only when the dialect's case
     * folding, reserved-word list, or character set demands it.</li>
     * <li>{@code ALWAYS} — wrap every identifier in the dialect's quote
     * character.</li>
     * <li>{@code NEVER} — never wrap identifiers; rely on the dialect's case
     * folding rules.</li>
     * </ul>
     */
    @AttributeDefinition(name = "%quotingPolicy.name", description = "%quotingPolicy.description")
    IdentifierQuotingPolicy quotingPolicy() default IdentifierQuotingPolicy.WHEN_NEEDED;
}
