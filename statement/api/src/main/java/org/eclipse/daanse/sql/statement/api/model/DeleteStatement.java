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

import org.eclipse.daanse.jdbc.db.api.schema.TableReference;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;

/**
 * A {@code DELETE FROM table [WHERE ...]} statement.
 *
 * @param table   the target table (shared jdbc.db identifier)
 * @param filters {@code WHERE} predicates, combined with {@code AND} (empty = delete all)
 */
public record DeleteStatement(TableReference table, List<Predicate> filters) implements Statement {
}
