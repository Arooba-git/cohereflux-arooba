package io.github.pellse.reactive.assembler.caching;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.github.pellse.util.ObjectUtils.then;
import static io.github.pellse.util.collection.CollectionUtil.*;
import static java.util.Map.of;
import static java.util.Optional.ofNullable;
import static reactor.core.publisher.Mono.defer;
import static reactor.core.publisher.Mono.just;

@FunctionalInterface
interface CacheUpdater<ID, R> {
    Mono<?> updateCache(Cache<ID, R> cache, Map<ID, List<R>> cacheQueryResults, Map<ID, List<R>> incomingChanges);
}

public interface Cache<ID, R> {

    static <ID, R> Cache<ID, R> adapterCache(
            BiFunction<Iterable<ID>, Boolean, Mono<Map<ID, List<R>>>> getAll,
            Function<Map<ID, List<R>>, Mono<?>> putAll,
            Function<Map<ID, List<R>>, Mono<?>> removeAll) {
        return adapterCache(getAll, putAll, removeAll, null);
    }

    static <ID, R> Cache<ID, R> adapterCache(
            BiFunction<Iterable<ID>, Boolean, Mono<Map<ID, List<R>>>> getAll,
            Function<Map<ID, List<R>>, Mono<?>> putAll,
            Function<Map<ID, List<R>>, Mono<?>> removeAll,
            BiFunction<Map<ID, List<R>>, Map<ID, List<R>>, Mono<?>> updateAll) {
        return new Cache<>() {

            @Override
            public Mono<Map<ID, List<R>>> getAll(Iterable<ID> ids, boolean computeIfAbsent) {
                return getAll.apply(ids, computeIfAbsent);
            }

            @Override
            public Mono<?> putAll(Map<ID, List<R>> map) {
                return putAll.apply(map);
            }

            @Override
            public Mono<?> removeAll(Map<ID, List<R>> map) {
                return removeAll.apply(map);
            }

            @Override
            public Mono<?> updateAll(Map<ID, List<R>> mapToAdd, Map<ID, List<R>> mapToRemove) {
                return ofNullable(updateAll)
                        .orElse(Cache.super::updateAll)
                        .apply(mapToAdd, mapToRemove);
            }
        };
    }

    static <ID, EID, R> Cache<ID, R> mergeStrategyAwareCache(
            Function<R, EID> idExtractor,
            Cache<ID, R> delegateCache) {

        var optimizedCache = adapterCache(
                emptyOr(delegateCache::getAll),
                emptyOr(delegateCache::putAll),
                emptyOr(delegateCache::removeAll)
        );

        return adapterCache(
                optimizedCache::getAll,
                applyMergeStrategy(
                        optimizedCache,
                        (cacheQueryResults, incomingChanges) -> mergeMaps(incomingChanges, cacheQueryResults, idExtractor),
                        Cache::putAll),
                applyMergeStrategy(
                        optimizedCache,
                        (cache, cacheQueryResults, incomingChanges) ->
                                then(subtractFromMap(incomingChanges, cacheQueryResults, idExtractor),
                                        updatedMap -> cache.updateAll(updatedMap, diff(cacheQueryResults, updatedMap))))
        );
    }

    private static <ID, R> Function<Map<ID, List<R>>, Mono<?>> applyMergeStrategy(
            Cache<ID, R> delegateCache,
            MergeStrategy<ID, R> mergeStrategy,
            BiFunction<Cache<ID, R>, Map<ID, List<R>>, Mono<?>> cacheUpdater) {

        return applyMergeStrategy(
                delegateCache,
                (cache, cacheQueryResults, incomingChanges) ->
                        cacheUpdater.apply(cache, mergeStrategy.merge(cacheQueryResults, incomingChanges)));
    }

    private static <ID, R> Function<Map<ID, List<R>>, Mono<?>> applyMergeStrategy(
            Cache<ID, R> delegateCache,
            CacheUpdater<ID, R> cacheUpdater) {

        return incomingChanges -> isEmpty(incomingChanges) ? just(of()) : defer(() ->
                delegateCache.getAll(incomingChanges.keySet(), false)
                        .flatMap(cacheQueryResults ->
                                cacheUpdater.updateCache(delegateCache, cacheQueryResults, incomingChanges)));
    }

    private static <ID, R> Function<Map<ID, List<R>>, Mono<?>> emptyOr(
            Function<Map<ID, List<R>>, Mono<?>> mappingFunction) {
        return map -> isEmpty(map) ? just(of()) : mappingFunction.apply(map);
    }

    private static <ID, R> BiFunction<Iterable<ID>, Boolean, Mono<Map<ID, List<R>>>> emptyOr(
            BiFunction<Iterable<ID>, Boolean, Mono<Map<ID, List<R>>>> mappingFunction) {
        return (ids, computeIfAbsent) -> isEmpty(ids) ? just(of()) : mappingFunction.apply(ids, computeIfAbsent);
    }

    Mono<Map<ID, List<R>>> getAll(Iterable<ID> ids, boolean computeIfAbsent);

    Mono<?> putAll(Map<ID, List<R>> map);

    Mono<?> removeAll(Map<ID, List<R>> map);

    default Mono<?> updateAll(Map<ID, List<R>> mapToAdd, Map<ID, List<R>> mapToRemove) {
        return putAll(mapToAdd).then(removeAll(mapToRemove));
    }
}
