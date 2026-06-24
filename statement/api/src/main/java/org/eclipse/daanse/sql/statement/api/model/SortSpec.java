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
 * How a single {@link OrderKey} is sorted.
 *
 * @param direction         ascending or descending
 * @param nullable          whether the expression may be {@code null} (enables null-ordering logic)
 * @param nullOrder         where nulls sort
 * @param prepend           whether this key is prepended (vs appended) to the order list
 * @param nullSortValue     when non-null, nulls are ordered <em>as if</em> they held this value (the
 *                          renderer uses the dialect's order-value generator instead of plain
 *                          null-ordering) — e.g. a parent-child hierarchy's {@code nullParentValue}
 * @param nullSortDatatype  the datatype of {@code nullSortValue} (required when it is non-null)
 */
public record SortSpec(SortDirection direction, boolean nullable, NullOrder nullOrder, boolean prepend,
        String nullSortValue, org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype nullSortDatatype) {

    /** Convenience constructor for the common case with no null-sort-value. */
    public SortSpec(SortDirection direction, boolean nullable, NullOrder nullOrder, boolean prepend) {
        this(direction, nullable, nullOrder, prepend, null, null);
    }

    public static SortSpec asc() {
        return new SortSpec(SortDirection.ASC, false, NullOrder.DEFAULT, false);
    }

    public static SortSpec desc() {
        return new SortSpec(SortDirection.DESC, false, NullOrder.DEFAULT, false);
    }

    /** @return a copy of this spec marked nullable with the given null ordering */
    public SortSpec withNulls(NullOrder order) {
        return new SortSpec(direction, true, order, prepend, nullSortValue, nullSortDatatype);
    }

    /** @return a copy of this spec marked as prepended */
    public SortSpec prepended() {
        return new SortSpec(direction, nullable, nullOrder, true, nullSortValue, nullSortDatatype);
    }

    /**
     * @return a copy ordering nulls as if they held {@code value} (of type {@code datatype}), via the
     * dialect's order-value generator.
     */
    public SortSpec withNullSortValue(String value, org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype datatype) {
        return new SortSpec(direction, nullable, nullOrder, prepend, value, datatype);
    }
}
