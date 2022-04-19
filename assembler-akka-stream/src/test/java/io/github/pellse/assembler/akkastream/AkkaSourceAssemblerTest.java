/*
 * Copyright 2018 Sebastien Pelletier
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.pellse.assembler.akkastream;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;
import io.github.pellse.assembler.*;
import io.github.pellse.util.function.checked.UncheckedException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static io.github.pellse.assembler.AssemblerBuilder.assemblerOf;
import static io.github.pellse.assembler.AssemblerTestUtils.*;
import static io.github.pellse.assembler.akkastream.AkkaSourceAdapter.akkaSourceAdapter;
import static io.github.pellse.util.query.MapperUtils.oneToManyAsList;
import static io.github.pellse.util.query.MapperUtils.oneToOne;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AkkaSourceAssemblerTest {

    private final ActorSystem system = ActorSystem.create();
    private final Materializer mat = ActorMaterializer.create(system);

    private List<Customer> getCustomers() {
        return asList(customer1, customer2, customer3, customer1, customer2);
    }

    @Test
    public void testAssemblerBuilderWithAkkaSource() throws Exception {

        TestKit probe = new TestKit(system);

        Source<Transaction, ?> transactionSource = assemblerOf(Transaction.class)
                .withIdExtractor(Customer::customerId)
                .withAssemblerRules(
                        oneToOne(AssemblerTestUtils::getBillingInfo, BillingInfo::customerId, BillingInfo::new),
                        oneToManyAsList(AssemblerTestUtils::getAllOrders, OrderItem::customerId),
                        Transaction::new)
                .using(akkaSourceAdapter())
                .assembleFromSupplier(this::getCustomers);

        final CompletionStage<Done> future = transactionSource.runWith(
                Sink.foreach(elem -> probe.getRef().tell(elem, ActorRef.noSender())), mat);

        probe.expectMsgAllOf(transaction1, transaction2, transaction3, transaction1, transaction2);

        future.toCompletableFuture().get();
    }

    @Test
    public void testAssemblerBuilderWithAkkaSourceWithError() {

        assertThrows(UncheckedException.class, () -> {
            Source<Transaction, ?> transactionSource = assemblerOf(Transaction.class)
                    .withIdExtractor(Customer::customerId)
                    .withAssemblerRules(
                            oneToOne(AssemblerTestUtils::throwSQLException, BillingInfo::customerId, BillingInfo::new),
                            oneToManyAsList(AssemblerTestUtils::throwSQLException, OrderItem::customerId),
                            Transaction::new)
                    .using(akkaSourceAdapter())
                    .assembleFromSupplier(this::getCustomers); // Sequential

            final CompletionStage<Done> future = transactionSource.runWith(Sink.ignore(), mat);

            try {
                future.toCompletableFuture().get();
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        });
    }

    @Test
    public void testAssemblerBuilderWithAkkaSourceAsync() throws Exception {

        TestKit probe = new TestKit(system);

        Source<Transaction, ?> transactionSource = assemblerOf(Transaction.class)
                .withIdExtractor(Customer::customerId)
                .withAssemblerRules(
                        oneToOne(AssemblerTestUtils::getBillingInfo, BillingInfo::customerId, BillingInfo::new),
                        oneToManyAsList(AssemblerTestUtils::getAllOrders, OrderItem::customerId),
                        Transaction::new)
                .using(akkaSourceAdapter(true))
                .assembleFromSupplier(this::getCustomers); // Parallel

        final CompletionStage<Done> future = transactionSource.runWith(
                Sink.foreach(elem -> probe.getRef().tell(elem, ActorRef.noSender())), mat);

        probe.expectMsgAllOf(transaction1, transaction2, transaction3, transaction1, transaction2);

        future.toCompletableFuture().get();
    }

    @Test
    public void testAssemblerBuilderWithAkkaSourceAsyncWithBuffering() throws Exception {

        TestKit probe = new TestKit(system);

        Source<Transaction, NotUsed> transactionSource = Source.from(getCustomers())
                .groupedWithin(3, ofSeconds(5))
                .flatMapConcat(customerList ->
                        assemblerOf(Transaction.class)
                                .withIdExtractor(Customer::customerId)
                                .withAssemblerRules(
                                        oneToOne(AssemblerTestUtils::getBillingInfo, BillingInfo::customerId, BillingInfo::new),
                                        oneToManyAsList(AssemblerTestUtils::getAllOrders, OrderItem::customerId),
                                        Transaction::new)
                                .using(akkaSourceAdapter(Source::async))
                                .assemble(customerList)); // Custom source configuration

        final CompletionStage<Done> future = transactionSource.runWith(
                Sink.foreach(elem -> probe.getRef().tell(elem, ActorRef.noSender())), mat);

        probe.expectMsgAllOf(transaction1, transaction2, transaction3, transaction1, transaction2);

        future.toCompletableFuture().get();
    }

    @Test
    public void testReusableAssemblerBuilderWithAkkaSourceAsyncWithBuffering() throws Exception {

        TestKit probe = new TestKit(system);

        Assembler<Customer, Source<Transaction, ?>> assembler = assemblerOf(Transaction.class)
                .withIdExtractor(Customer::customerId)
                .withAssemblerRules(
                        oneToOne(AssemblerTestUtils::getBillingInfo, BillingInfo::customerId, BillingInfo::new),
                        oneToManyAsList(AssemblerTestUtils::getAllOrders, OrderItem::customerId),
                        Transaction::new)
                .using(akkaSourceAdapter(Source::async));

        Source<Transaction, NotUsed> transactionSource = Source.from(getCustomers())
                .groupedWithin(3, ofSeconds(5))
                .flatMapConcat(assembler::assemble); // Custom source configuration

        final CompletionStage<Done> future = transactionSource.runWith(
                Sink.foreach(elem -> probe.getRef().tell(elem, ActorRef.noSender())), mat);

        probe.expectMsgAllOf(transaction1, transaction2, transaction3, transaction1, transaction2);

        future.toCompletableFuture().get();
    }
}
