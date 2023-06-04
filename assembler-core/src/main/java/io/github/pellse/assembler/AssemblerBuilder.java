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

package io.github.pellse.assembler;

import io.github.pellse.util.function.*;
import io.github.pellse.util.function.checked.CheckedSupplier;
import io.github.pellse.util.function.checked.UncheckedException;
import io.github.pellse.util.query.Mapper;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface AssemblerBuilder {

    static <R> withIdResolverBuilder<R> assemblerOf(Class<R> outputClass) {
        return new withIdResolverBuilderImpl<>();
    }

    @FunctionalInterface
    interface withIdResolverBuilder<R> {

        <T, ID> WithAssemblerRulesBuilder<T, ID, R> withIdResolver(Function<T, ID> idResolver);
    }

    @FunctionalInterface
    interface WithAssemblerRulesBuilder<T, ID, R> {

        @SuppressWarnings("unchecked")
        default <E1> AssembleUsingBuilder<T, ID, R> withAssemblerRules(
                Mapper<ID, E1, ?> mapper,
                BiFunction<T, E1, R> assemblerFunction) {

            return withAssemblerRules(List.of(mapper), (t, s) -> assemblerFunction.apply(t, (E1) s[0]));
        }

        @SuppressWarnings("unchecked")
        default <E1, E2>
        AssembleUsingBuilder<T, ID, R> withAssemblerRules(
                Mapper<ID, E1, ?> mapper1,
                Mapper<ID, E2, ?> mapper2,
                Function3<T, E1, E2, R> assemblerFunction) {

            return withAssemblerRules(List.of(mapper1, mapper2), (t, s) -> assemblerFunction.apply(t, (E1) s[0], (E2) s[1]));
        }

        @SuppressWarnings("unchecked")
        default <E1, E2, E3>
        AssembleUsingBuilder<T, ID, R> withAssemblerRules(
                Mapper<ID, E1, ?> mapper1,
                Mapper<ID, E2, ?> mapper2,
                Mapper<ID, E3, ?> mapper3,
                Function4<T, E1, E2, E3, R> assemblerFunction) {

            return withAssemblerRules(List.of(mapper1, mapper2, mapper3),
                    (t, s) -> assemblerFunction.apply(t, (E1) s[0], (E2) s[1], (E3) s[2]));
        }

        @SuppressWarnings("unchecked")
        default <E1, E2, E3, E4>
        AssembleUsingBuilder<T, ID, R> withAssemblerRules(
                Mapper<ID, E1, ?> mapper1,
                Mapper<ID, E2, ?> mapper2,
                Mapper<ID, E3, ?> mapper3,
                Mapper<ID, E4, ?> mapper4,
                Function5<T, E1, E2, E3, E4, R> assemblerFunction) {

            return withAssemblerRules(List.of(mapper1, mapper2, mapper3, mapper4),
                    (t, s) -> assemblerFunction.apply(t, (E1) s[0], (E2) s[1], (E3) s[2], (E4) s[3]));
        }

        @SuppressWarnings("unchecked")
        default <E1, E2, E3, E4, E5>
        AssembleUsingBuilder<T, ID, R> withAssemblerRules(
                Mapper<ID, E1, ?> mapper1,
                Mapper<ID, E2, ?> mapper2,
                Mapper<ID, E3, ?> mapper3,
                Mapper<ID, E4, ?> mapper4,
                Mapper<ID, E5, ?> mapper5,
                Function6<T, E1, E2, E3, E4, E5, R> assemblerFunction) {

            return withAssemblerRules(List.of(mapper1, mapper2, mapper3, mapper4, mapper5),
                    (t, s) -> assemblerFunction.apply(t, (E1) s[0], (E2) s[1], (E3) s[2], (E4) s[3], (E5) s[4]));
        }

        @SuppressWarnings("unchecked")
        default <E1, E2, E3, E4, E5, E6>
        AssembleUsingBuilder<T, ID, R> withAssemblerRules(
                Mapper<ID, E1, ?> mapper1,
                Mapper<ID, E2, ?> mapper2,
                Mapper<ID, E3, ?> mapper3,
                Mapper<ID, E4, ?> mapper4,
                Mapper<ID, E5, ?> mapper5,
                Mapper<ID, E6, ?> mapper6,
                Function7<T, E1, E2, E3, E4, E5, E6, R> assemblerFunction) {

            return withAssemblerRules(List.of(mapper1, mapper2, mapper3, mapper4, mapper5, mapper6),
                    (t, s) -> assemblerFunction.apply(t, (E1) s[0], (E2) s[1], (E3) s[2], (E4) s[3], (E5) s[4], (E6) s[5]));
        }

        @SuppressWarnings("unchecked")
        default <E1, E2, E3, E4, E5, E6, E7>
        AssembleUsingBuilder<T, ID, R> withAssemblerRules(
                Mapper<ID, E1, ?> mapper1,
                Mapper<ID, E2, ?> mapper2,
                Mapper<ID, E3, ?> mapper3,
                Mapper<ID, E4, ?> mapper4,
                Mapper<ID, E5, ?> mapper5,
                Mapper<ID, E6, ?> mapper6,
                Mapper<ID, E7, ?> mapper7,
                Function8<T, E1, E2, E3, E4, E5, E6, E7, R> assemblerFunction) {

            return withAssemblerRules(List.of(mapper1, mapper2, mapper3, mapper4, mapper5, mapper6, mapper7),
                    (t, s) -> assemblerFunction.apply(
                            t, (E1) s[0], (E2) s[1], (E3) s[2], (E4) s[3], (E5) s[4], (E6) s[5], (E7) s[6]));
        }

        @SuppressWarnings("unchecked")
        default <E1, E2, E3, E4, E5, E6, E7, E8>
        AssembleUsingBuilder<T, ID, R> withAssemblerRules(
                Mapper<ID, E1, ?> mapper1,
                Mapper<ID, E2, ?> mapper2,
                Mapper<ID, E3, ?> mapper3,
                Mapper<ID, E4, ?> mapper4,
                Mapper<ID, E5, ?> mapper5,
                Mapper<ID, E6, ?> mapper6,
                Mapper<ID, E7, ?> mapper7,
                Mapper<ID, E8, ?> mapper8,
                Function9<T, E1, E2, E3, E4, E5, E6, E7, E8, R> assemblerFunction) {

            return withAssemblerRules(List.of(mapper1, mapper2, mapper3, mapper4, mapper5, mapper6, mapper7, mapper8),
                    (t, s) -> assemblerFunction.apply(
                            t, (E1) s[0], (E2) s[1], (E3) s[2], (E4) s[3], (E5) s[4], (E6) s[5], (E7) s[6], (E8) s[7]));
        }

        @SuppressWarnings("unchecked")
        default <E1, E2, E3, E4, E5, E6, E7, E8, E9>
        AssembleUsingBuilder<T, ID, R> withAssemblerRules(
                Mapper<ID, E1, ?> mapper1,
                Mapper<ID, E2, ?> mapper2,
                Mapper<ID, E3, ?> mapper3,
                Mapper<ID, E4, ?> mapper4,
                Mapper<ID, E5, ?> mapper5,
                Mapper<ID, E6, ?> mapper6,
                Mapper<ID, E7, ?> mapper7,
                Mapper<ID, E8, ?> mapper8,
                Mapper<ID, E9, ?> mapper9,
                Function10<T, E1, E2, E3, E4, E5, E6, E7, E8, E9, R> assemblerFunction) {

            return withAssemblerRules(List.of(mapper1, mapper2, mapper3, mapper4, mapper5, mapper6, mapper7, mapper8, mapper9),
                    (t, s) -> assemblerFunction.apply(
                            t, (E1) s[0], (E2) s[1], (E3) s[2], (E4) s[3], (E5) s[4], (E6) s[5], (E7) s[6], (E8) s[7], (E9) s[8]));
        }

        @SuppressWarnings("unchecked")
        default <E1, E2, E3, E4, E5, E6, E7, E8, E9, E10>
        AssembleUsingBuilder<T, ID, R> withAssemblerRules(
                Mapper<ID, E1, ?> mapper1,
                Mapper<ID, E2, ?> mapper2,
                Mapper<ID, E3, ?> mapper3,
                Mapper<ID, E4, ?> mapper4,
                Mapper<ID, E5, ?> mapper5,
                Mapper<ID, E6, ?> mapper6,
                Mapper<ID, E7, ?> mapper7,
                Mapper<ID, E8, ?> mapper8,
                Mapper<ID, E9, ?> mapper9,
                Mapper<ID, E10, ?> mapper10,
                Function11<T, E1, E2, E3, E4, E5, E6, E7, E8, E9, E10, R> assemblerFunction) {

            return withAssemblerRules(List.of(mapper1, mapper2, mapper3, mapper4, mapper5, mapper6, mapper7, mapper8, mapper9, mapper10),
                    (t, s) -> assemblerFunction.apply(
                            t, (E1) s[0], (E2) s[1], (E3) s[2], (E4) s[3], (E5) s[4], (E6) s[5], (E7) s[6], (E8) s[7], (E9) s[8], (E10) s[9]));
        }

        @SuppressWarnings("unchecked")
        default <E1, E2, E3, E4, E5, E6, E7, E8, E9, E10, E11>
        AssembleUsingBuilder<T, ID, R> withAssemblerRules(
                Mapper<ID, E1, ?> mapper1,
                Mapper<ID, E2, ?> mapper2,
                Mapper<ID, E3, ?> mapper3,
                Mapper<ID, E4, ?> mapper4,
                Mapper<ID, E5, ?> mapper5,
                Mapper<ID, E6, ?> mapper6,
                Mapper<ID, E7, ?> mapper7,
                Mapper<ID, E8, ?> mapper8,
                Mapper<ID, E9, ?> mapper9,
                Mapper<ID, E10, ?> mapper10,
                Mapper<ID, E11, ?> mapper11,
                Function12<T, E1, E2, E3, E4, E5, E6, E7, E8, E9, E10, E11, R> assemblerFunction) {

            return withAssemblerRules(List.of(mapper1, mapper2, mapper3, mapper4, mapper5, mapper6, mapper7, mapper8, mapper9, mapper10, mapper11),
                    (t, s) -> assemblerFunction.apply(
                            t, (E1) s[0], (E2) s[1], (E3) s[2], (E4) s[3], (E5) s[4], (E6) s[5], (E7) s[6], (E8) s[7], (E9) s[8], (E10) s[9], (E11) s[10]));
        }

        AssembleUsingBuilder<T, ID, R> withAssemblerRules(List<Mapper<ID, ?, ?>> mappers,
                                                          BiFunction<T, Object[], R> aggregationFunction);
    }

    interface AssembleUsingBuilder<T, ID, R> {

        AssembleUsingBuilder<T, ID, R> withErrorConverter(Function<Throwable, RuntimeException> errorConverter);

        <RC> Assembler<T, RC> using(AssemblerAdapter<T, ID, R, RC> adapter);
    }

    class withIdResolverBuilderImpl<R> implements withIdResolverBuilder<R> {

        private withIdResolverBuilderImpl() {
        }

        @Override
        public <T, ID> WithAssemblerRulesBuilder<T, ID, R> withIdResolver(Function<T, ID> idResolver) {
            return new WithAssemblerRulesBuilderImpl<>(idResolver);
        }
    }

    class WithAssemblerRulesBuilderImpl<T, ID, R> implements WithAssemblerRulesBuilder<T, ID, R> {

        private final Function<T, ID> idResolver;

        private WithAssemblerRulesBuilderImpl(Function<T, ID> idResolver) {

            this.idResolver = idResolver;
        }

        @Override
        public AssembleUsingBuilder<T, ID, R> withAssemblerRules(List<Mapper<ID, ?, ?>> mappers,
                                                                 BiFunction<T, Object[], R> aggregationFunction) {
            return new AssembleUsingBuilderImpl<>(idResolver, mappers, aggregationFunction);
        }
    }

    class AssembleUsingBuilderImpl<T, ID, R> implements AssembleUsingBuilder<T, ID, R> {

        private final Function<T, ID> idResolver;
        private final BiFunction<T, Object[], R> aggregationFunction;
        private final List<Mapper<ID, ?, ?>> mappers;

        private Function<Throwable, RuntimeException> errorConverter = UncheckedException::new;

        private AssembleUsingBuilderImpl(Function<T, ID> idResolver,
                                         List<Mapper<ID, ?, ?>> mappers,
                                         BiFunction<T, Object[], R> aggregationFunction) {

            this.idResolver = idResolver;

            this.aggregationFunction = aggregationFunction;
            this.mappers = mappers;
        }

        @Override
        public AssembleUsingBuilder<T, ID, R> withErrorConverter(Function<Throwable, RuntimeException> errorConverter) {
            this.errorConverter = errorConverter;
            return this;
        }

        @Override
        public <RC> Assembler<T, RC> using(AssemblerAdapter<T, ID, R, RC> assemblerAdapter) {

            return new AssemblerImpl<>(idResolver, mappers, aggregationFunction, errorConverter, assemblerAdapter);
        }
    }

    class AssemblerImpl<T, ID, R, RC> implements Assembler<T, RC> {

        private final Function<T, ID> idResolver;
        private final List<Mapper<ID, ?, ?>> mappers;
        private final BiFunction<T, Object[], R> aggregationFunction;

        private final Function<Throwable, RuntimeException> errorConverter;
        private final AssemblerAdapter<T, ID, R, RC> assemblerAdapter;

        private AssemblerImpl(Function<T, ID> idResolver,
                              List<Mapper<ID, ?, ?>> mappers,
                              BiFunction<T, Object[], R> aggregationFunction,
                              Function<Throwable, RuntimeException> errorConverter,
                              AssemblerAdapter<T, ID, R, RC> assemblerAdapter) {
            this.idResolver = idResolver;
            this.aggregationFunction = aggregationFunction;
            this.mappers = mappers;
            this.errorConverter = errorConverter;
            this.assemblerAdapter = assemblerAdapter;
        }

        @Override
        public RC assembleFromSupplier(CheckedSupplier<Iterable<T>, Throwable> topLevelEntitiesProvider) {
            return Assembler.assembleFromSupplier(topLevelEntitiesProvider, idResolver, mappers, aggregationFunction, assemblerAdapter, errorConverter);
        }
    }
}
