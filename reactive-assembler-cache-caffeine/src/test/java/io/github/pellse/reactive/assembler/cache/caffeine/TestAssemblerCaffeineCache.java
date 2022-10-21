package io.github.pellse.reactive.assembler.cache.caffeine;

import io.github.pellse.assembler.BillingInfo;
import io.github.pellse.assembler.Customer;
import io.github.pellse.assembler.OrderItem;
import io.github.pellse.assembler.Transaction;
import io.github.pellse.reactive.assembler.caching.CacheEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.github.benmanes.caffeine.cache.Caffeine.newBuilder;
import static io.github.pellse.assembler.AssemblerTestUtils.*;
import static io.github.pellse.reactive.assembler.AssemblerBuilder.assemblerOf;
import static io.github.pellse.reactive.assembler.Mapper.rule;
import static io.github.pellse.reactive.assembler.RuleMapper.oneToMany;
import static io.github.pellse.reactive.assembler.RuleMapper.oneToOne;
import static io.github.pellse.reactive.assembler.cache.caffeine.CaffeineCacheFactory.caffeineCache;
import static io.github.pellse.reactive.assembler.caching.AutoCacheFactory.autoCache;
import static io.github.pellse.reactive.assembler.caching.AutoCacheFactory.toCacheEvent;
import static io.github.pellse.reactive.assembler.caching.CacheEvent.removed;
import static io.github.pellse.reactive.assembler.caching.CacheEvent.updated;
import static io.github.pellse.reactive.assembler.caching.CacheFactory.cached;
import static java.time.Duration.ofMillis;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static reactor.core.scheduler.Schedulers.parallel;

public class TestAssemblerCaffeineCache {

    private final AtomicInteger billingInvocationCount = new AtomicInteger();
    private final AtomicInteger ordersInvocationCount = new AtomicInteger();

    private Publisher<BillingInfo> getBillingInfo(List<Long> customerIds) {
        return Flux.just(billingInfo1, billingInfo3)
                .filter(billingInfo -> customerIds.contains(billingInfo.customerId()))
                .doOnComplete(billingInvocationCount::incrementAndGet);
    }

    private Publisher<OrderItem> getAllOrders(List<Long> customerIds) {
        return Flux.just(orderItem11, orderItem12, orderItem13, orderItem21, orderItem22)
                .filter(orderItem -> customerIds.contains(orderItem.customerId()))
                .doOnComplete(ordersInvocationCount::incrementAndGet);
    }

    private Flux<Customer> getCustomers() {
        return Flux.just(customer1, customer2, customer3, customer1, customer2, customer3, customer1, customer2, customer3);
    }

    @BeforeEach
    void setup() {
        billingInvocationCount.set(0);
        ordersInvocationCount.set(0);
    }

    @Test
    public void testReusableAssemblerBuilderWithCaffeineCache() {

        var assembler = assemblerOf(Transaction.class)
                .withCorrelationIdExtractor(Customer::customerId)
                .withAssemblerRules(
                        rule(BillingInfo::customerId, oneToOne(cached(this::getBillingInfo, caffeineCache()), BillingInfo::new)),
                        rule(OrderItem::customerId, oneToMany(OrderItem::id, cached(this::getAllOrders, caffeineCache(newBuilder().maximumSize(10))))),
                        Transaction::new)
                .build();

        StepVerifier.create(getCustomers()
                        .window(3)
                        .delayElements(ofMillis(100))
                        .flatMapSequential(assembler::assemble))
                .expectSubscription()
                .expectNext(transaction1, transaction2, transaction3, transaction1, transaction2, transaction3, transaction1, transaction2, transaction3)
                .expectComplete()
                .verify();

        assertEquals(1, billingInvocationCount.get());
        assertEquals(1, ordersInvocationCount.get());
    }

    @Test
    public void testReusableAssemblerBuilderWithCaffeineCache2() {

        var assembler = assemblerOf(Transaction.class)
                .withCorrelationIdExtractor(Customer::customerId)
                .withAssemblerRules(
                        rule(BillingInfo::customerId, oneToOne(cached(this::getBillingInfo, caffeineCache(b -> b.maximumSize(10))), BillingInfo::new)),
                        rule(OrderItem::customerId, oneToMany(OrderItem::id, cached(this::getAllOrders, caffeineCache(newBuilder().maximumSize(10))))),
                        Transaction::new)
                .build();

        StepVerifier.create(getCustomers()
                        .window(2)
                        .delayElements(ofMillis(100))
                        .flatMapSequential(assembler::assemble))
                .expectSubscription()
                .expectNext(transaction1, transaction2, transaction3, transaction1, transaction2, transaction3, transaction1, transaction2, transaction3)
                .expectComplete()
                .verify();

        assertEquals(2, billingInvocationCount.get());
        assertEquals(2, ordersInvocationCount.get());
    }

    @Test
    public void testReusableAssemblerBuilderWithDoubleCaching() {

        var assembler = assemblerOf(Transaction.class)
                .withCorrelationIdExtractor(Customer::customerId)
                .withAssemblerRules(
                        rule(BillingInfo::customerId, oneToOne(cached(this::getBillingInfo, caffeineCache()), BillingInfo::new)),
                        rule(OrderItem::customerId, oneToMany(OrderItem::id, cached(cached(this::getAllOrders, caffeineCache())))),
                        Transaction::new)
                .build();

        StepVerifier.create(getCustomers()
                        .window(3)
                        .delayElements(ofMillis(100))
                        .flatMapSequential(assembler::assemble))
                .expectSubscription()
                .expectNext(transaction1, transaction2, transaction3, transaction1, transaction2, transaction3, transaction1, transaction2, transaction3)
                .expectComplete()
                .verify();

        assertEquals(1, billingInvocationCount.get());
        assertEquals(1, ordersInvocationCount.get());
    }

    @Test
    public void testReusableAssemblerBuilderWithAutoCaching() {

        Flux<BillingInfo> dataSource1 = Flux.just(billingInfo1, billingInfo2, billingInfo3);
        Flux<OrderItem> dataSource2 = Flux.just(
                orderItem11, orderItem12, orderItem13, orderItem21, orderItem22, orderItem31, orderItem32, orderItem33);

        Transaction transaction2 = new Transaction(customer2, billingInfo2, List.of(orderItem21, orderItem22));
        Transaction transaction3 = new Transaction(customer3, billingInfo3, List.of(orderItem31, orderItem32, orderItem33));

        var assembler = assemblerOf(Transaction.class)
                .withCorrelationIdExtractor(Customer::customerId)
                .withAssemblerRules(
                        rule(BillingInfo::customerId, oneToOne(cached(this::getBillingInfo, caffeineCache(), autoCache(toCacheEvent(dataSource1))))),
                        rule(OrderItem::customerId, oneToMany(OrderItem::id, cached(this::getAllOrders, caffeineCache(), autoCache(toCacheEvent(dataSource2))))),
                        Transaction::new)
                .build();

        StepVerifier.create(getCustomers()
                        .window(5)
                        .delayElements(ofMillis(100))
                        .flatMapSequential(assembler::assemble))
                .expectSubscription()
                .expectNext(transaction1, transaction2, transaction3, transaction1, transaction2, transaction3, transaction1, transaction2, transaction3)
                .expectComplete()
                .verify();

        assertEquals(0, billingInvocationCount.get());
        assertEquals(0, ordersInvocationCount.get());
    }

    @Test
    public void testReusableAssemblerBuilderWithAutoCachingEvents() {

        interface CDC {
            OrderItem item();
        }

        record CDCAdd(OrderItem item) implements CDC {
        }

        record CDCDelete(OrderItem item) implements CDC {
        }

        Function<List<Long>, Publisher<OrderItem>> getAllOrders = customerIds -> {
            assertEquals(List.of(3L), customerIds);
            return Flux.just(orderItem11, orderItem12, orderItem13, orderItem21, orderItem22)
                    .filter(orderItem -> customerIds.contains(orderItem.customerId()))
                    .doOnComplete(ordersInvocationCount::incrementAndGet);
        };

        BillingInfo updatedBillingInfo2 = new BillingInfo(2L, 2L, "4540111111111111");

        Flux<CacheEvent<BillingInfo>> billingInfoEventFlux = Flux.just(
                        updated(billingInfo1), updated(billingInfo2), updated(billingInfo3), updated(updatedBillingInfo2))
                .subscribeOn(parallel());

        var orderItemFlux = Flux.just(
                        new CDCAdd(orderItem11), new CDCAdd(orderItem12), new CDCAdd(orderItem13),
                        new CDCAdd(orderItem21), new CDCAdd(orderItem22),
                        new CDCAdd(orderItem31), new CDCAdd(orderItem32), new CDCAdd(orderItem33),
                        new CDCDelete(orderItem31), new CDCDelete(orderItem32))
                .map(e -> e instanceof CDCAdd ? updated(e.item()) : removed(e.item()))
                .subscribeOn(parallel());

        Transaction transaction2 = new Transaction(customer2, updatedBillingInfo2, List.of(orderItem21, orderItem22));
        Transaction transaction3 = new Transaction(customer3, billingInfo3, List.of(orderItem33));

        var assembler = assemblerOf(Transaction.class)
                .withCorrelationIdExtractor(Customer::customerId)
                .withAssemblerRules(
                        rule(BillingInfo::customerId, oneToOne(cached(this::getBillingInfo, caffeineCache(), autoCache(billingInfoEventFlux, 3)))),
                        rule(OrderItem::customerId, oneToMany(OrderItem::id, cached(getAllOrders, caffeineCache(), autoCache(orderItemFlux, 3)))),
                        Transaction::new)
                .build();

        StepVerifier.create(getCustomers()
                        .window(3)
                        .delayElements(ofMillis(100))
                        .flatMapSequential(assembler::assemble))
                .expectSubscription()
                .expectNext(transaction1, transaction2, transaction3, transaction1, transaction2, transaction3, transaction1, transaction2, transaction3)
                .expectComplete()
                .verify();

        assertEquals(0, billingInvocationCount.get());
        assertEquals(0, ordersInvocationCount.get());
    }
}
