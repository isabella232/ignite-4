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

package org.apache.ignite.internal.schema.configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;
import org.apache.ignite.configuration.NamedListView;
import org.apache.ignite.configuration.schemas.table.TableValidator;
import org.apache.ignite.configuration.schemas.table.TableView;
import org.apache.ignite.configuration.validation.ValidationContext;
import org.apache.ignite.configuration.validation.ValidationIssue;
import org.apache.ignite.configuration.validation.Validator;
import org.apache.ignite.internal.schema.definition.TableDefinitionImpl;
import org.apache.ignite.internal.schema.definition.builder.TableSchemaBuilderImpl;
import org.apache.ignite.schema.definition.ColumnDefinition;

/**
 * Table schema configuration validator implementation.
 */
public class TableValidatorImpl implements Validator<TableValidator, NamedListView<TableView>> {
    /** Static instance. */
    public static final TableValidatorImpl INSTANCE = new TableValidatorImpl();

    /** {@inheritDoc} */
    @Override public void validate(TableValidator annotation, ValidationContext<NamedListView<TableView>> ctx) {
        NamedListView<TableView> list = ctx.getNewValue();
        
        for (String key : list.namedListKeys()) {
            TableView view = list.get(key);
            
            try {
                TableDefinitionImpl tbl = SchemaConfigurationConverter.convert(view);

                assert !tbl.keyColumns().isEmpty();
                assert !tbl.affinityColumns().isEmpty();

                Collection<ColumnDefinition> allColumns = new ArrayList<>(tbl.keyColumns());
                allColumns.addAll(tbl.valueColumns());

                TableSchemaBuilderImpl.validateIndices(tbl.indices(), allColumns, tbl.affinityColumns().stream().map(ColumnDefinition::name).collect(Collectors.toSet()));
            }
            catch (IllegalArgumentException e) {
                ctx.addIssue(new ValidationIssue("Validator works success by key " + ctx.currentKey() + ". Found "
                    + view.columns().size() + " columns"));
            }
        }

    }

    /** Private constructor. */
    private TableValidatorImpl() {
    }
}
