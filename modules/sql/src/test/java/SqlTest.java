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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import org.apache.ignite.internal.schema.Column;
import org.apache.ignite.internal.schema.NativeTypes;
import org.apache.ignite.internal.schema.SchemaDescriptor;
import org.apache.ignite.internal.schema.SchemaRegistry;
import org.apache.ignite.internal.table.TableImpl;
import org.apache.ignite.internal.util.Constants;
import org.apache.ignite.query.sql.IgniteSql;
import org.apache.ignite.query.sql.SqlResultSet;
import org.apache.ignite.query.sql.SqlResultSetMeta;
import org.apache.ignite.query.sql.SqlRow;
import org.apache.ignite.query.sql.SqlSession;
import org.apache.ignite.query.sql.SqlTx;
import org.apache.ignite.query.sql.reactive.ReactiveSqlResultSet;
import org.apache.ignite.schema.ColumnType;
import org.apache.ignite.table.Table;
import org.apache.ignite.table.Tuple;
import org.apache.ignite.tx.IgniteTransactions;
import org.apache.ignite.tx.Transaction;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SqlTest {
    @Mock
    IgniteSql queryMgr;

    @Mock
    private IgniteTransactions igniteTx;

    @Mock
    private Transaction tx;

    @BeforeEach
    void setUp() {
        initMock();
    }

    @Test
    public void testSynchronousSql() {
        igniteTx.runInTransaction(tx -> {
            SqlSession sess = queryMgr.session();

            sess.defaultTimeout(10_000); // Set default timeout.
            sess.setParameter("memoryQuota", 10 * Constants.MiB); // Set default timeout.

            // Execute outside TX.
            SqlResultSet rs = sess.execute("INSERT INTO table VALUES (?, ?)", 10, "str");

            assertEquals(1, rs.updateCount());

            // Execute in TX.
            SqlTx sqlTx = queryMgr.session().withTransaction(tx);

            rs = sqlTx.execute("SELECT id, val FROM table WHERE id < {};", 10);

            for (SqlRow r : rs) {
                assertTrue(10 > r.longValue("id"));
                assertTrue((r.stringValue("val")).startsWith("str"));
            }

            sqlTx.commit();
        });

        Mockito.verify(tx).commit();
    }

    @Test
    public void testSynchronousSql2() {
        Table tbl = getTable();

        // Starts new TX.
        SqlTx sqlTx = queryMgr.session().withNewTransaction();

        SqlResultSet rs = sqlTx.execute("SELECT id, val FROM table WHERE id < {};", 10);
        SqlRow row = rs.iterator().next();

        tbl.withTransaction(sqlTx.transaction())
            .insertAsync(Tuple.create().set("val", "NewValue"))
            .thenAccept(r -> sqlTx.transaction().rollback());

        Mockito.verify(tx, Mockito.times(1)).rollback();
    }

    @NotNull private Table getTable() {
        SchemaDescriptor schema = new SchemaDescriptor(UUID.randomUUID(), 42,
            new Column[]{new Column("id", NativeTypes.INT64, false)},
            new Column[]{new Column("val", NativeTypes.STRING, true)}
        );

        SchemaRegistry schemaReg = Mockito.mock(SchemaRegistry.class);
        Mockito.when(schemaReg.schema()).thenReturn(schema);

        Table tbl = Mockito.mock(Table.class);
        Mockito.when(tbl.insertAsync(Mockito.any())).thenReturn(CompletableFuture.completedFuture(null));
        Mockito.when(tbl.withTransaction(Mockito.any())).thenAnswer(Answers.RETURNS_SELF);

        return tbl;
    }

    @Test
    public void testSynchronousSql3() throws ExecutionException, InterruptedException {
        SqlSession sess = queryMgr.session();

        CompletableFuture<SqlResultSet> f = sess.executeMultiAsync(
            "CREATE TABLE tbl(id INTEGER PRIMARY KEY, val VARCHAR);" +
                "INSERT INTO  tbl VALUES (1, 2);" +
                "INSERT INTO  tbl VALUES (1, 2);" +
                "INSERT INTO  tbl VALUES (1, 2);" +
                "INSERT INTO  tbl VALUES (1, 2);" +
                "INSERT INTO  tbl VALUES (1, 2);" +
                "INSERT INTO  tbl VALUES (1, 2);" +
                "INSERT INTO  tbl VALUES (1, 2);" +
                "CREATE INDEX IDX_0 ON tbl (val);" +

                "CREATE TABLE tbl2(id INTEGER PRIMARY KEY, val VARCHAR);" +
//                    "SELECT id, val FROM tbl WHERE id == {};" +
//                    "DROP TABLE tbl", tx, 10)


                "CREATE TABLE tbl(id INTEGER PRIMARY KEY, val VARCHAR);" +
                "INSERT INTO  tbl VALUES (1, 2)" +
                "SELECT id, val FROM tbl WHERE id == {};" +
                "DROP TABLE tbl", tx, 10)
                                                .thenCompose(multiRs -> {
                                                    SqlResultSet rs = multiRs.iterator().next();

                                                    String str = rs.iterator().next().stringValue("val");

                                                    return sess.executeAsync("SELECT val FROM table where val LIKE {};", tx, str);
                                                });

        SqlResultSet rs = f.get();

        rs.iterator().next();
    }

    @Test
    public void testAsyncSql() {
        igniteTx.beginAsync().thenCompose(tx0 -> {
            SqlSession sess = queryMgr.session();

            return sess.executeAsync(
                "CREATE TABLE tbl(id INTEGER PRI<ARY KEY, val VARCHAR);" +
                    "SELECT id, val FROM tbl WHERE id == {};" +
                    "DROP TABLE tbl", 10)

                       .thenCompose(rs -> {
                           String str = rs.iterator().next().stringValue("val");

                           return sess.executeAsync("SELECT val FROM table where val LIKE {};", str);
                       })
                       .thenApply(ignore -> tx0);
        }).thenAccept(Transaction::commitAsync);

        Mockito.verify(tx).commitAsync();
    }

    @Test
    public void testReactiveSql() {
        SqlRowSubscriber subscriber = new SqlRowSubscriber(row -> {
            assertTrue(10 > row.longValue("id"));
            assertTrue(row.stringValue("val").startsWith("str"));
        });

        igniteTx.beginAsync().thenApply(tx -> queryMgr.session())
            .thenCompose(session -> {
                session.executeQueryReactive("SELECT id, val FROM table WHERE id < {} AND val LIKE {};", tx, 10, "str%")
                    .subscribe(subscriber);

                return subscriber.exceptionally(th -> {
                    tx.rollbackAsync();
                    return null;
                }).thenApply(ignore -> tx.commitAsync());
            });

        Mockito.verify(tx).commitAsync();
    }

    @Disabled
    @Test
    public void testMetadata() {
        SqlResultSet rs = queryMgr.session().execute("SELECT id, val FROM table WHERE id < {} AND val LIKE {}; ", null, 10, "str%");

        SqlRow row = rs.iterator().next();

        SqlResultSetMeta meta = rs.metadata();

        assertEquals(rs.metadata().columnsCount(), row.columnCount());

        assertEquals(0, meta.indexOf("id"));
        assertEquals(1, meta.indexOf("val"));

        assertEquals("id", meta.column(0).name());
        assertEquals("val", meta.column(1).name());

        assertEquals(ColumnType.INT64, meta.column(0).columnType());
        assertEquals(ColumnType.string(), meta.column(1).columnType());

        assertFalse(meta.column(0).nullable());
        assertTrue(meta.column(1).nullable());
    }

    private void initMock() {
        SqlSession session = Mockito.mock(SqlSession.class);

        Mockito.when(queryMgr.session()).thenReturn(session);

        SqlTx sqlTx = Mockito.mock(SqlTx.class);

        Mockito.when(sqlTx.transaction()).thenReturn(tx);
        Mockito.when(session.withTransaction(Mockito.same(tx))).thenReturn(sqlTx);
        Mockito.when(session.withNewTransaction()).thenReturn(sqlTx);
        Mockito.doAnswer(ans -> AdditionalAnswers.delegatesTo(session).answer(ans)).when(sqlTx).execute(Mockito.any(), Mockito.any());
        Mockito.doAnswer(ans -> AdditionalAnswers.delegatesTo(tx).answer(ans)).when(sqlTx).commit();
        Mockito.doAnswer(ans -> AdditionalAnswers.delegatesTo(tx).answer(ans)).when(sqlTx).rollback();

        Mockito.when(session.execute(Mockito.eq("SELECT id, val FROM table WHERE id < {};"), Mockito.any()))
            .thenAnswer(ans -> Mockito.when(Mockito.mock(SqlResultSet.class).iterator())
                                   .thenReturn(List.of(
                                       new TestRow().set("id", 1L).set("val", "string 1").build(),
                                       new TestRow().set("id", 2L).set("val", "string 2").build(),
                                       new TestRow().set("id", 5L).set("val", "string 3").build()
                                   ).iterator()).getMock());

        Mockito.when(session.execute(Mockito.eq("INSERT INTO table VALUES (?, ?)"), Mockito.any(), Mockito.any()))
            .thenAnswer(ans -> Mockito.when(Mockito.mock(SqlResultSet.class).updateCount())
                                   .thenReturn(1).getMock());

        Mockito.when(session.executeAsync(Mockito.eq("SELECT id, val FROM table WHERE id == {};"), Mockito.any()))
            .thenAnswer(ans -> {
                Object mock = Mockito.when(Mockito.mock(SqlResultSet.class).iterator())
                                  .thenReturn(List.of(new TestRow().set("id", 1L).set("val", "string 1").build()).iterator())
                                  .getMock();

                return CompletableFuture.completedFuture(mock);
            });

        Mockito.when(session.executeAsync(Mockito.eq("SELECT val FROM table where val LIKE {};"), Mockito.any()))
            .thenAnswer(ans -> {
                Object mock = Mockito.when(Mockito.mock(SqlResultSet.class).iterator())
                                  .thenReturn(List.of(new TestRow().set("id", 10L).set("val", "string 10").build()).iterator())
                                  .getMock();

                return CompletableFuture.completedFuture(mock);
            });

        Mockito.when(session.executeQueryReactive(Mockito.startsWith("SELECT id, val FROM table WHERE id < {} AND val LIKE {};"), Mockito.any(), Mockito.any()))
            .thenAnswer(invocation -> {
                ReactiveSqlResultSet mock = Mockito.mock(ReactiveSqlResultSet.class);

                Mockito.doAnswer(ans -> {
                    Flow.Subscriber subscrber = ans.getArgument(0);

                    subscrber.onSubscribe(Mockito.mock(Flow.Subscription.class));

                    List.of(
                        new TestRow().set("id", 1L).set("val", "string 1").build(),
                        new TestRow().set("id", 2L).set("val", "string 2").build(),
                        new TestRow().set("id", 5L).set("val", "string 3").build()
                    ).forEach(i -> subscrber.onNext(i));

                    subscrber.onComplete();

                    return ans;
                }).when(mock).subscribe(Mockito.any(Flow.Subscriber.class));

                return mock;
            });

        Mockito.doAnswer(invocation -> {
            Consumer<Transaction> argument = invocation.getArgument(0);

            argument.accept(tx);

            return null;
        }).when(igniteTx).runInTransaction(Mockito.any());

        Mockito.when(igniteTx.beginAsync()).thenReturn(CompletableFuture.completedFuture(tx));
    }

    /**
     * Dummy subsctiber for test purposes.
     */
    static class SqlRowSubscriber extends CompletableFuture<Void> implements Flow.Subscriber<SqlRow> {
        private Consumer<SqlRow> rowConsumer;

        SqlRowSubscriber(Consumer<SqlRow> rowConsumer) {
            this.rowConsumer = rowConsumer;
        }

        @Override public void onSubscribe(Flow.Subscription subscription) {
            whenCompleteAsync((res, th) -> {
                if (th != null)
                    subscription.cancel();
            });

            subscription.request(Long.MAX_VALUE); // Unbounded.
        }

        @Override public void onNext(SqlRow row) {
            rowConsumer.accept(row);
        }

        @Override public void onError(Throwable throwable) {
            completeExceptionally(throwable);
        }

        @Override public void onComplete() {
            complete(null);
        }
    }
}
