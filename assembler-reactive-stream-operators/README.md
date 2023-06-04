# assembler-reactive-stream-operators

[![Maven Central](https://img.shields.io/maven-central/v/io.github.pellse/assembler-reactive-stream-operators.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.pellse%22%20AND%20a:%22assembler-reactive-stream-operators%22)
[![Javadocs](http://javadoc.io/badge/io.github.pellse/assembler-reactive-stream-operators.svg)](http://javadoc.io/doc/io.github.pellse/assembler-reactive-stream-operators)

## Usage Example

```java
import static io.github.pellse.assembler.AssemblerBuilder.assemblerOf;
import static io.github.pellse.query.io.github.pellse.util.MapperUtils.oneToManyAsList;
import static io.github.pellse.query.io.github.pellse.util.MapperUtils.oneToOne;

import static io.github.pellse.assembler.microprofile.PublisherBuilderAdapter.publisherBuilderAdapter;

Assembler<Customer, PublisherBuilder<Transaction>> assembler = assemblerOf(Transaction.class)
    .withIdResolver(Customer::getCustomerId)
    .withAssemblerRules(
        oneToOne(this::getBillingInfo, BillingInfo::getCustomerId, BillingInfo::new),
        oneToManyAsList(this::getAllOrders, OrderItem::getCustomerId),
        Transaction::new)
    .using(publisherBuilderAdapter());

Flux<Transaction> transactionFlux = Flux.fromIterable(getCustomers())
    .buffer(3)
    .concatMap(customers -> assembler.assemble(customers)
        .filter(transaction -> transaction.getOrderItems().size() > 1)
        .buildRs());
```
In the above example we create an `Assembler` that returns an Eclipse MicroProfile Reactive Stream Operator [PublisherBuilder](https://download.eclipse.org/microprofile/microprofile-reactive-streams-operators-1.0/apidocs/org/eclipse/microprofile/reactive/streams/operators/PublisherBuilder.html), we can then see the integration and interoperability with other implementations of the [Reactive Streams Specification](https://www.reactive-streams.org), Project Reactor in this example, everything inside the Project Reactor's `Flux ` `concatMap()` method is Eclipse MicroProfile Reactive Stream Operator code, the `buildRs()` method returns a standardized [org.reactivestreams.Publisher](https://www.reactive-streams.org/reactive-streams-1.0.3-javadoc/org/reactivestreams/Publisher.html) which can then act as an integration point with 3rd party libraries or be used standalone in [Eclipse MicroProfile](https://microprofile.io/) compliant code.

When no further reactive stream manipulation is required from the `PublisherBuilder` returned from the `Assembler`, we can directly return a `Publisher` from the `Assembler` by using the `publishAdapter()` static factory method instead of `publisherBuilderAdapter()` (the example below also demonstrates the interoperability with RxJava `Flowable` type):
```java
import static io.github.pellse.assembler.AssemblerBuilder.assemblerOf;
import static io.github.pellse.query.io.github.pellse.util.MapperUtils.oneToManyAsList;
import static io.github.pellse.query.io.github.pellse.util.MapperUtils.oneToOne;

import static io.github.pellse.assembler.microprofile.PublisherBuilderAdapter.publisherAdapter;

Assembler<Customer, Publisher<Transaction>> assembler = assemblerOf(Transaction.class)
    .withIdResolver(Customer::getCustomerId)
    .withAssemblerRules(
        oneToOne(this::getBillingInfo, BillingInfo::getCustomerId, BillingInfo::new),
        oneToManyAsList(this::getAllOrders, OrderItem::getCustomerId),
        Transaction::new)
    .using(publisherAdapter());

Flowable<Transaction> transactionFlowable = Flowable.fromIterable(getCustomers())
    .buffer(3)
    .concatMap(assembler::assemble);
```
