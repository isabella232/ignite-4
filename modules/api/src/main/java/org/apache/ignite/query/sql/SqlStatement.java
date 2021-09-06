/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.query.sql;

import java.sql.SQLException;

/**
 * SQL statement.
 */
public interface SqlStatement {
    /**
     * @return Statement SQL query.
     */
    String query();

    /**
     * @return Query parameters.
     */
    Object[] parameters();

    /**
     * @param parameters Query parameters.
     * @return {@code this} for chaining.
     */
    SqlStatement parameters(Object... parameters);

    /**
     * @param parameter Query parameter.
     * @return {@code this} for chaining.
     */
    //TODO: method name???
    SqlStatement set(int parameterIndex, Object parameter);

    /**
     * Clears query parameters.
     *
     * @return {@code this} for chaining.
     */
    SqlStatement clearParameters();

    /**
     * Adds a set of parameters to this <code>Statement</code> object's batch of commands.
     *
     * @return {@code this} for chaining.
     */
    //TODO: Multi statement?
    SqlStatement addBatch() throws SQLException;

    /**
     * @param queryTimeout Query timeout.
     * @return {@code this} for chaining.
     */
    SqlStatement queryTimeout(long queryTimeout);

    /**
     * @return Query timeout.
     */
    long queryTimeout();

    //TODO: Do we need some additional parameters? Query memory quota?
}
