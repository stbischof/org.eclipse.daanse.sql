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
package org.eclipse.daanse.sql.statement.api.render;

/**
 * Options controlling SQL output.
 *
 * @param formatted whether to format the SQL onto multiple indented lines
 * @param indent    the indent unit used when {@code formatted} is true
 */
public record RenderOptions(boolean formatted, String indent) {

    public static RenderOptions compact() {
        return new RenderOptions(false, "    ");
    }

    public static RenderOptions multiLine() {
        return new RenderOptions(true, "    ");
    }
}
