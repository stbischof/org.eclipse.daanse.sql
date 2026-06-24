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

/**
 * A renderable, immutable SQL statement. May be a read ({@link SelectStatement}, or a
 * {@link SetOperation} such as {@code UNION ALL}) or a write
 * ({@link InsertStatement}/{@link UpdateStatement}/{@link DeleteStatement}).
 */
public sealed interface Statement
        permits SelectStatement, SetOperation, InsertStatement, UpdateStatement, DeleteStatement, WithStatement {
}
