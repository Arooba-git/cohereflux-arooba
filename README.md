# Assembler
Efficient implementation of the [API Composition Pattern](https://microservices.io/patterns/data/api-composition.html) for querying/merging data from multiple datasources/services, with a specific focus on solving the N + 1 query problem.

## Native Reactive Support

As of version 0.3.0, a new implementation [reactive-assembler-core](https://github.com/pellse/assembler/tree/master/reactive-assembler-core) was added to natively support [Reactive Streams](http://www.reactive-streams.org) specification:

[![Maven Central](https://img.shields.io/maven-central/v/io.github.pellse/reactive-assembler-core.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.pellse%22%20AND%20a:%22reactive-assembler-core%22) [![Javadocs](http://javadoc.io/badge/io.github.pellse/reactive-assembler-core.svg)](http://javadoc.io/doc/io.github.pellse/reactive-assembler-core)

The internals of this new implementation is based on [Project Reactor](https://projectreactor.io), which means the Assembler library through the [reactive-assembler-core](https://github.com/pellse/assembler/tree/master/reactive-assembler-core) module can participate in a end to end reactive streams chain (e.g. from a REST endpoint to the database) and keep all reactive streams properties as defined by the [Reactive Manifesto](https://www.reactivemanifesto.org) (Responsive, Resillient, Elastic, Message Driven with back-pressure, non-blocking, etc.)

This is the only module still actively maintained, all the other ones (see below) are still available but deprecated in favor of this one.

## Use Cases

One interesting use case would be for example to build a materialized view in a microservice architecture supporting Event Sourcing and Command Query Responsibility Segregation (CQRS). In this context, if we have an incoming stream of events where each event needs to be enriched with some sort of external data before being stored, it would be convenient to be able to easily batch those events instead of hitting those external services for every single event.

## Usage Example for Native Reactive Support
Assuming the following data model of a very simplified online store, and api to access different services:
```java
public record Customer(Long customerId, String name) {}
public record BillingInfo(Long customerId, String creditCardNumber) {}
public record OrderItem(Long customerId, String orderDescription, Double price) {}
public record Transaction(Customer customer, BillingInfo billingInfo, List<OrderItem> orderItems) {}

Flux<Customer> customers(); // REST call to a separate microservice (no query filters for brevity)
Publisher<BillingInfo> billingInfo(List<Long> customerIds); // Connects to MongoDB
Publisher<OrderItem> allOrders(List<Long> customerIds); // Connects to a relational database
```

If `customers()` returns 50 customers, instead of having to make one additional call per *customerId* to retrieve each customer's associated `BillingInfo` (which would result in 50 additional network calls, thus the N + 1 queries issue) we can only make 1 additional call to retrieve all at once all `BillingInfo` for all `Customer` returned by `customers()`, same for `OrderItem`. Since we are working with 3 different and independent datasources, joining data from `Customer`, `BillingInfo` and `OrderItem` into `Transaction` (using *customerId* as a correlation id between all those entities) has to be done at the application level, which is what this library was implemented for.

When using [reactive-assembler-core](https://github.com/pellse/assembler/tree/master/reactive-assembler-core), the code to aggregate different reactive datasources will typically look like this:

```java
import static io.github.pellse.reactive.assembler.AssemblerBuilder.assemblerOf;
import static io.github.pellse.reactive.assembler.Mapper.*;
import reactor.core.publisher.Flux;

Assembler<Customer, Flux<Transaction>> assembler = assemblerOf(Transaction.class)
    .withIdExtractor(Customer::customerId)
    .withAssemblerRules(
        oneToOne(this::billingInfo, BillingInfo::customerId),
        oneToMany(this::allOrders, OrderItem::customerId),
        Transaction::new)
    .build();

Flux<Transaction> transactionFlux = assembler.assemble(customers());
```
In the scenario where we deal with an infinite stream of data, since the Assembler needs to completely drain the upstream from `customers()` to gather all the correlation ids (*customerId*), the example above will trigger resource exhaustion. The solution is to split the stream into multiple smaller streams and batch the processing of those individual smaller streams. Most reactive libraries (Project Reactor, RxJava, Akka Streams, etc.) already support that concept, below is an example using Project Reactor:
```java
Flux<Transaction> transactionFlux = customers()
    .windowTimeout(100, ofSeconds(5))
    .flatMapSequential(assembler::assemble);
```
## Caching
In addition to providing helper functions to define mapping semantics (e.g. `oneToOne()`, `OneToMany()`), `Mapper` also provides a simple caching/memoization mechanism through the `cached()` wrapper method.

```java
import static io.github.pellse.reactive.assembler.AssemblerBuilder.assemblerOf;
import static io.github.pellse.reactive.assembler.Mapper.*;
import reactor.core.publisher.Flux;

var assembler = assemblerOf(Transaction.class)
    .withIdExtractor(Customer::customerId)
    .withAssemblerRules(
        cached(oneToOne(this::getBillingInfos, BillingInfo::customerId)),
        cached(oneToMany(this::getAllOrders, OrderItem::customerId)),
        Transaction::new)
    .build();
    
var transactionFlux = customers()
    .window(3)
    .flatMapSequential(assembler::assemble);
```
This can be useful for aggregating dynamic data with static data or data we know doesn't change often (or on a predefined schedule e.g. data that is refreshed by a batch job once a day).

The `cached()` method internally uses the list of correlation ids from the upstream (list of customer ids in the above example) as the cache key. The result of the consumed downstream is cached, not each separate item from the downstream. Concretely, if we take the line `cached(oneToOne(this::getBillingInfos, BillingInfo::customerId))`, from the example above `window(3)` would generate windows of 3 `Customer` e.g. ( (C1, C2, C3), (C1, C4, C7), (C4, C5, C6), (C2, C8, C9) ), at that moment in time the cache would like this:

| Key [correlation id list] | Cached BillingInfo Downstream |
| --- | --- |
| [1, 2, 3] | (B1, B2, B3) |
| [1, 4, 7] | (B1, B4, B7) |
| [4, 5, 6] | (B4, B5, B6) |
| [2, 8, 9] | (B2, B8, B9) |

Here B1, B2 and B4 are each cached twice as each are part of 2 different streams. This is because we effectively cache the query itself vs. separate individual entities, so when caching downstreams we need to be careful to have predictable results otherwise the number of different combinations (of correlation id lists) might not justify to use caching.

Note that an overloaded version of the `cached()` method is also defined to allow plugging your own cache implementation.

## Other Supported Technologies
Currently the following legacy implementations are still available, but it is strongly recommended to switch to [reactive-assembler-core](https://github.com/pellse/assembler/tree/master/reactive-assembler-core) as any future development will be focused on the Native Reactive implementation described above:

1. [![Maven Central](https://img.shields.io/maven-central/v/io.github.pellse/assembler-core.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.pellse%22%20AND%20a:%22assembler-core%22)
[![Javadocs](http://javadoc.io/badge/io.github.pellse/assembler-core.svg)](http://javadoc.io/doc/io.github.pellse/assembler-core) [Java 8 Stream (synchronous and parallel)](https://github.com/pellse/assembler/tree/master/assembler-core)
2. [![Maven Central](https://img.shields.io/maven-central/v/io.github.pellse/assembler-core.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.pellse%22%20AND%20a:%22assembler-core%22)
[![Javadocs](http://javadoc.io/badge/io.github.pellse/assembler-core.svg)](http://javadoc.io/doc/io.github.pellse/assembler-core) [CompletableFuture](https://github.com/pellse/assembler/tree/master/assembler-core)
3. [![Maven Central](https://img.shields.io/maven-central/v/io.github.pellse/assembler-flux.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.pellse%22%20AND%20a:%22assembler-flux%22)
[![Javadocs](http://javadoc.io/badge/io.github.pellse/assembler-flux.svg)](http://javadoc.io/doc/io.github.pellse/assembler-flux) [Flux](https://github.com/pellse/assembler/tree/master/assembler-flux)
4. [![Maven Central](https://img.shields.io/maven-central/v/io.github.pellse/assembler-rxjava.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.pellse%22%20AND%20a:%22assembler-rxjava%22)
[![Javadocs](http://javadoc.io/badge/io.github.pellse/assembler-rxjava.svg)](http://javadoc.io/doc/io.github.pellse/assembler-rxjava) [RxJava](https://github.com/pellse/assembler/tree/master/assembler-rxjava)
5. [![Maven Central](https://img.shields.io/maven-central/v/io.github.pellse/assembler-akka-stream.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.pellse%22%20AND%20a:%22assembler-akka-stream%22)
[![Javadocs](http://javadoc.io/badge/io.github.pellse/assembler-akka-stream.svg)](http://javadoc.io/doc/io.github.pellse/assembler-akka-stream) [Akka Stream](https://github.com/pellse/assembler/tree/master/assembler-akka-stream)
6. [![Maven Central](https://img.shields.io/maven-central/v/io.github.pellse/assembler-reactive-stream-operators.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.pellse%22%20AND%20a:%22assembler-reactive-stream-operators%22)
[![Javadocs](http://javadoc.io/badge/io.github.pellse/assembler-reactive-stream-operators.svg)](http://javadoc.io/doc/io.github.pellse/assembler-reactive-stream-operators) [Eclipse MicroProfile Reactive Stream Operators](https://github.com/pellse/assembler/tree/master/assembler-reactive-stream-operators)

You only need to include in your project's build file (maven, gradle) the lib that corresponds to the type of reactive (or non reactive) support needed (Java 8 stream, CompletableFuture, Flux, RxJava, Akka Stream, Eclipse MicroProfile Reactive Stream Operators).

All modules above have dependencies on the following modules:
1. [![Maven Central](https://img.shields.io/maven-central/v/io.github.pellse/assembler-core.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.pellse%22%20AND%20a:%22assembler-core%22)
[![Javadocs](http://javadoc.io/badge/io.github.pellse/assembler-core.svg)](http://javadoc.io/doc/io.github.pellse/assembler-core) [assembler-core](https://github.com/pellse/assembler/tree/master/assembler-core)
2. [![Maven Central](https://img.shields.io/maven-central/v/io.github.pellse/assembler-util.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.pellse%22%20AND%20a:%22assembler-util%22)
[![Javadocs](http://javadoc.io/badge/io.github.pellse/assembler-util.svg)](http://javadoc.io/doc/io.github.pellse/assembler-util) [assembler-util](https://github.com/pellse/assembler/tree/master/assembler-util)

### [Java 8 Stream (synchronous and parallel)](https://github.com/pellse/assembler/tree/master/assembler-core)
To build the `Transaction` entity we can simply combine the invocation of the methods declared above using (from [StreamAssemblerTest](https://github.com/pellse/assembler/blob/master/assembler-core/src/test/java/io/github/pellse/assembler/stream/StreamAssemblerTest.java)):
```java
import static io.github.pellse.assembler.AssemblerBuilder.assemblerOf;
import static io.github.pellse.util.query.MapperUtils.oneToOne;
import static io.github.pellse.util.query.MapperUtils.oneToManyAsList;

import static io.github.pellse.assembler.stream.StreamAdapter.streamAdapter;

List<Transaction> transactions = assemblerOf(Transaction.class)
    .withIdExtractor(Customer::getCustomerId)
    .withAssemblerRules(
        oneToOne(this::getBillingInfos, BillingInfo::getCustomerId, BillingInfo::new), // Default BillingInfo for null values
        oneToManyAsList(this::getAllOrders, OrderItem::getCustomerId),
        Transaction::new)
    .using(streamAdapter())
    .assembleFromSupplier(this::getCustomers)
    .collect(toList());
```
### [CompletableFuture](https://github.com/pellse/assembler/tree/master/assembler-core)
It is also possible to bind to a different execution engine (e.g. for parallel processing) just by switching to a different `AssemblerAdapter` implementation. For example, to support the aggregation process through `CompletableFuture`, just plug a `CompletableFutureAdapter` instead (from [CompletableFutureAssemblerTest](https://github.com/pellse/assembler/blob/master/assembler-core/src/test/java/io/github/pellse/assembler/future/CompletableFutureAssemblerTest.java)):
```java
import static io.github.pellse.assembler.AssemblerBuilder.assemblerOf;
import static io.github.pellse.util.query.MapperUtils.oneToOne;
import static io.github.pellse.util.query.MapperUtils.oneToManyAsList;

import static io.github.pellse.assembler.future.CompletableFutureAdapter.completableFutureAdapter;

CompletableFuture<List<Transaction>> transactions = assemblerOf(Transaction.class)
    .withIdExtractor(Customer::getCustomerId)
    .withAssemblerRules(
        oneToOne(this::getBillingInfos, BillingInfo::getCustomerId, BillingInfo::new),
        oneToManyAsList(this::getAllOrders, OrderItem::getCustomerId),
        Transaction::new)
    .using(completableFutureAdapter())
    .assembleFromSupplier(this::getCustomers);
```
### [Flux](https://github.com/pellse/assembler/tree/master/assembler-flux)
Reactive support is also provided through the [Spring Project Reactor](https://projectreactor.io/) to asynchronously retrieve all data to be aggregated, for example (from [FluxAssemblerTest]( https://github.com/pellse/assembler/blob/master/assembler-flux/src/test/java/io/github/pellse/assembler/flux/FluxAssemblerTest.java)):
```java
import static io.github.pellse.assembler.AssemblerBuilder.assemblerOf;
import static io.github.pellse.util.query.MapperUtils.oneToOne;
import static io.github.pellse.util.query.MapperUtils.oneToManyAsList;

import static io.github.pellse.assembler.flux.FluxAdapter.fluxAdapter;

Flux<Transaction> transactionFlux = assemblerOf(Transaction.class)
    .withIdExtractor(Customer::getCustomerId)
    .withAssemblerRules(
        oneToOne(this::getBillingInfos, BillingInfo::getCustomerId),
        oneToManyAsList(this::getAllOrders, OrderItem::getCustomerId),
        Transaction::new)
    .using(fluxAdapter(elastic()))
    .assembleFromSupplier(this::getCustomers))
```
or by reusing the same `Assembler` instance as a transformation step within a `Flux`: 
```java
Assembler<Customer, Flux<Transaction>> assembler = assemblerOf(Transaction.class)
    .withIdExtractor(Customer::getCustomerId)
    .withAssemblerRules(
         oneToOne(this::getBillingInfos, BillingInfo::getCustomerId),
         oneToManyAsList(this::getAllOrders, OrderItem::getCustomerId),
         Transaction::new)
    .using(fluxAdapter()); // Parallel scheduler used by default

Flux<Transaction> transactionFlux = Flux.fromIterable(getCustomers()) // or just getCustomerFlux()
    .bufferTimeout(10, ofSeconds(5)) // every 5 seconds or max of 10 customers, returns Flux<List<Customer>>
    .flatMap(assembler::assemble) // flatMap(customerList -> assembler.assemble(customerList)))
```
### [RxJava](https://github.com/pellse/assembler/tree/master/assembler-rxjava)
In addition to the Flux implementation, [RxJava](https://github.com/ReactiveX/RxJava) is also supported through `Observable`s and `Flowable`s.

With Observable support (from [ObservableAssemblerTest]( https://github.com/pellse/assembler/blob/master/assembler-rxjava/src/test/java/io/github/pellse/assembler/rxjava/ObservableAssemblerTest.java)):
```java
import static io.github.pellse.assembler.AssemblerBuilder.assemblerOf;
import static io.github.pellse.util.query.MapperUtils.oneToOne;
import static io.github.pellse.util.query.MapperUtils.oneToManyAsList;

import static io.github.pellse.assembler.rxjava.ObservableAdapter.observableAdapter;

Observable<Transaction> transactionObservable = assemblerOf(Transaction.class)
    .withIdExtractor(Customer::getCustomerId)
    .withAssemblerRules(
         oneToOne(this::getBillingInfos, BillingInfo::getCustomerId),
         oneToManyAsList(this::getAllOrders, OrderItem::getCustomerId),
         Transaction::new)
     .using(observableAdapter(single()))
     .assembleFromSupplier(this::getCustomers);
```
With Flowable support (from [FlowableAssemblerTest]( https://github.com/pellse/assembler/blob/master/assembler-rxjava/src/test/java/io/github/pellse/assembler/rxjava/FlowableAssemblerTest.java)):
```java
import static io.github.pellse.assembler.AssemblerBuilder.assemblerOf;
import static io.github.pellse.util.query.MapperUtils.oneToOne;
import static io.github.pellse.util.query.MapperUtils.oneToManyAsList;

import static io.github.pellse.assembler.rxjava.FlowableAdapter.flowableAdapter;

Flowable<Transaction> transactionFlowable = assemblerOf(Transaction.class)
    .withIdExtractor(Customer::getCustomerId)
    .withAssemblerRules(
        oneToOne(this::getBillingInfos, BillingInfo::getCustomerId),
        oneToManyAsList(this::getAllOrders, OrderItem::getCustomerId),
        Transaction::new)
    .using(flowableAdapter(single()))
    .assembleFromSupplier(this::getCustomers);
```
### [Akka Stream](https://github.com/pellse/assembler/tree/master/assembler-akka-stream)
By using an `AkkaSourceAdapter` we can support the [Akka Stream](https://akka.io/) framework by creating instances of Akka `Source` (from [AkkaSourceAssemblerTest]( https://github.com/pellse/assembler/blob/master/assembler-akka-stream/src/test/java/io/github/pellse/assembler/akkastream/AkkaSourceAssemblerTest.java)):
```java
import static io.github.pellse.assembler.AssemblerBuilder.assemblerOf;
import static io.github.pellse.util.query.MapperUtils.oneToOne;
import static io.github.pellse.util.query.MapperUtils.oneToManyAsList;

import static io.github.pellse.assembler.akkastream.AkkaSourceAdapter.akkaSourceAdapter;

ActorSystem system = ActorSystem.create();
Materializer mat = ActorMaterializer.create(system);

Source<Transaction, NotUsed> transactionSource = assemblerOf(Transaction.class)
    .withIdExtractor(Customer::getCustomerId)
    .withAssemblerRules(
        oneToOne(this::getBillingInfos, BillingInfo::getCustomerId),
        oneToManyAsList(this::getAllOrders, OrderItem::getCustomerId),
        Transaction::new)
    .using(akkaSourceAdapter())) // Sequential
    .assembleFromSupplier(this::getCustomers);

transactionSource.runWith(Sink.foreach(System.out::println), mat)
    .toCompletableFuture().get();
```
or
```java
ActorSystem system = ActorSystem.create();
Materializer mat = ActorMaterializer.create(system);

Assembler<Customer, Source<Transaction, NotUsed>> assembler = assemblerOf(Transaction.class)
    .withIdExtractor(Customer::getCustomerId)
    .withAssemblerRules(
        oneToOne(this::getBillingInfos, BillingInfo::getCustomerId),
        oneToManyAsList(this::getAllOrders, OrderItem::getCustomerId),
        Transaction::new)
    .using(akkaSourceAdapter(true)); // Parallel

Source<Transaction, NotUsed> transactionSource = Source.from(getCustomers())
    .groupedWithin(3, ofSeconds(5))
    .flatMapConcat(assembler::assemble)

transactionSource.runWith(Sink.foreach(System.out::println), mat)
    .toCompletableFuture().get();
```
It is also possible to create an Akka `Flow` from the Assembler DSL:
```java
ActorSystem system = ActorSystem.create();
Materializer mat = ActorMaterializer.create(system);

Assembler<Customer, Source<Transaction, NotUsed>> assembler = assemblerOf(Transaction.class)
    .withIdExtractor(Customer::getCustomerId)
    .withAssemblerRules(
        oneToOne(this::getBillingInfos, BillingInfo::getCustomerId),
        oneToManyAsList(this::getAllOrders, OrderItem::getCustomerId),
        Transaction::new)
    .using(akkaSourceAdapter(Source::async)); // Custom underlying sources configuration

Source<Customer, NotUsed> customerSource = Source.from(getCustomers());

Flow<Customer, Transaction, NotUsed> transactionFlow = Flow.<Customer>create()
    .grouped(3)
    .flatMapConcat(assembler::assemble);
        
customerSource.via(transactionFlow)
    .runWith(Sink.foreach(System.out::println), mat)
    .toCompletableFuture().get();
```
### [Eclipse MicroProfile Reactive Stream Operators](https://github.com/pellse/assembler/tree/master/assembler-reactive-stream-operators)
Since MicroProfile specifications are not allowed to depend on any dependencies other than the JDK and other MicroProfile specifications, a new initiative [Eclipse MicroProfile Reactive Stream Operators](https://github.com/eclipse/microprofile-reactive-streams-operators) was created to define an API to support reactive stream manipulation and control. This is supported by the Assembler library with the `PublisherBuilderAdapter` (from [PublisherBuilderAssemblerTest](https://github.com/pellse/assembler/blob/master/assembler-reactive-stream-operators/src/test/java/io/github/pellse/assembler/microprofile/PublisherBuilderAssemblerTest.java)):
```java
import static io.github.pellse.assembler.AssemblerBuilder.assemblerOf;
import static io.github.pellse.util.query.MapperUtils.oneToManyAsList;
import static io.github.pellse.util.query.MapperUtils.oneToOne;

import static io.github.pellse.assembler.microprofile.PublisherBuilderAdapter.publisherBuilderAdapter;

Assembler<Customer, PublisherBuilder<Transaction>> assembler = assemblerOf(Transaction.class)
    .withIdExtractor(Customer::getCustomerId)
    .withAssemblerRules(
        oneToOne(this::getBillingInfos, BillingInfo::getCustomerId, BillingInfo::new),
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
import static io.github.pellse.util.query.MapperUtils.oneToManyAsList;
import static io.github.pellse.util.query.MapperUtils.oneToOne;

import static io.github.pellse.assembler.microprofile.PublisherBuilderAdapter.publisherAdapter;

Assembler<Customer, Publisher<Transaction>> assembler = assemblerOf(Transaction.class)
    .withIdExtractor(Customer::getCustomerId)
    .withAssemblerRules(
        oneToOne(this::getBillingInfos, BillingInfo::getCustomerId, BillingInfo::new),
        oneToManyAsList(this::getAllOrders, OrderItem::getCustomerId),
        Transaction::new)
    .using(publisherAdapter());

Flowable<Transaction> transactionFlowable = Flowable.fromIterable(getCustomers())
    .buffer(3)
    .concatMap(assembler::assemble);
```

## What's Next?
See the [list of issues](https://github.com/pellse/assembler/issues) for planned improvements in a near future.
