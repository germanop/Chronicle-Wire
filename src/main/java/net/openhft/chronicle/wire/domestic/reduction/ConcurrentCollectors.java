/*
 * Copyright 2016-2022 chronicle.software
 *
 *       https://chronicle.software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.wire.domestic.reduction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toConcurrentMap;
import static net.openhft.chronicle.core.util.ObjectUtils.requireNonNull;

public final class ConcurrentCollectors {

    // Suppresses default constructor, ensuring non-instantiability.
    private ConcurrentCollectors() {
    }

    /**
     * Returns a concurrent {@code Collector} that reduces the input elements into a
     * new {@code List}.
     *
     * @param <T> the type of the input elements
     * @return a {@code Collector} which collects all the input elements into a
     * {@code List}, in encounter order
     */
    @NotNull
    public static <T>
    Collector<T, ?, List<T>> toConcurrentList() {
        return Collector.of(
                () -> Collections.synchronizedList(new ArrayList<>()),
                List::add,
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                Collector.Characteristics.CONCURRENT
        );
    }

    /**
     * Returns a concurrent {@code Collector} that reduces the input elements into a
     * new {@code Set}.
     *
     * <p>This is an {@link java.util.stream.Collector.Characteristics#UNORDERED unordered}
     * Collector.
     *
     * @param <T> the type of the input elements
     * @return a {@code Collector} which collects all the input elements into a
     * {@code Set}
     */
    @NotNull
    public static <T>
    Collector<T, ?, Set<T>> toConcurrentSet() {
        return collectingAndThen(
                toConcurrentMap(Function.identity(),
                        t -> Boolean.TRUE,
                        retainingMerger()),
                Map::keySet);
    }

    /**
     * Returns a {@link java.util.stream.Collector} which performs a concurrent reduction of its
     * input elements under a specified {@code BinaryOperator} using the
     * provided identity.
     *
     * @param <T>      element type for the input and output of the reduction
     * @param identity the identity value for the reduction (also, the value
     *                 that is returned when there are no input elements)
     * @param op       a {@code BinaryOperator<T>} used to reduce the input elements
     * @return a {@code Collector} which implements the reduction operation
     * @apiNote The {@code reducing()} collectors are most useful when used in a
     * multi-level reduction, downstream of {@code groupingBy} or
     * {@code partitioningBy}.
     * @see Collectors#reducing(Object, BinaryOperator)
     */
    public static <T>
    Collector<T, ?, T> reducingConcurrent(final T identity,
                                          @NotNull final BinaryOperator<T> op) {
        requireNonNull(op);

        return Collector.of(
                () -> new AtomicReference<>(identity),
                (AtomicReference<T> ar, T e) -> ar.accumulateAndGet(e, op),
                (AtomicReference<T> t1, AtomicReference<T> t2) -> {
                    t1.accumulateAndGet(t2.get(), op);
                    return t1;
                },
                AtomicReference::get,
                Collector.Characteristics.CONCURRENT);
    }

    /**
     * Returns a {@code Collector} which performs a concurrent reduction of its
     * input elements under a specified {@code BinaryOperator}.  The result
     * is described as an {@code Optional<T>}.
     *
     * <p>For example, given a stream of {@code Person}, to calculate tallest
     * person in each city:
     * <pre>{@code
     *     Comparator<Person> byHeight = Comparator.comparing(Person::getHeight);
     *     Map<City, Person> tallestByCity
     *         = people.stream().collect(groupingBy(Person::getCity, reducing(BinaryOperator.maxBy(byHeight))));
     * }</pre>
     *
     * @param <T> element type for the input and output of the reduction
     * @param op  a {@code BinaryOperator<T>} used to reduce the input elements
     * @return a {@code Collector} which implements the reduction operation
     * @see Collectors#reducing(BinaryOperator)
     */
    @NotNull
    public static <T>
    Collector<T, ?, Optional<T>> reducingConcurrent(@NotNull final BinaryOperator<T> op) {
        requireNonNull(op);

        final BinaryOperator<T> internalAccumulator = (a, b) -> {
            if (a == null) {
                return b;
            }
            return op.apply(a, b);
        };

        return Collector.of(
                AtomicReference::new,
                (AtomicReference<T> ar, T e) -> ar.accumulateAndGet(e, internalAccumulator),
                (AtomicReference<T> t1, AtomicReference<T> t2) -> {
                    t1.accumulateAndGet(t2.get(), internalAccumulator);
                    return t1;
                },
                (AtomicReference<T> ar) -> Optional.ofNullable(ar.get()),
                Collector.Characteristics.CONCURRENT);
    }

    /**
     * Returns a {@code Collector} which performs a concurrent reduction of its
     * input elements under a specified mapping function and
     * {@code BinaryOperator}. This is a generalization of
     * {@link Collectors#reducing(Object, BinaryOperator)} which allows a transformation
     * of the elements before reduction.
     *
     * @param <T>      the type of the input elements
     * @param <R>      the type of the mapped values
     * @param identity the identity value for the reduction (also, the value
     *                 that is returned when there are no input elements)
     * @param mapper   a mapping function to apply to each input value
     * @param op       a {@code BinaryOperator<U>} used to reduce the mapped values
     * @return a {@code Collector} implementing the map-reduce operation
     * @apiNote The {@code reducing()} collectors are most useful when used in a
     * multi-level reduction, downstream of {@code groupingBy} or
     * {@code partitioningBy}.  To perform a simple map-reduce on a stream,
     * use {@link Stream#map(Function)} and {@link Stream#reduce(Object, BinaryOperator)}
     * instead.
     *
     * <p>For example, given a stream of {@code Person}, to calculate the longest
     * last name of residents in each city:
     * <pre>{@code
     *     Comparator<String> byLength = Comparator.comparing(String::length);
     *     Map<City, String> longestLastNameByCity
     *         = people.stream().collect(groupingBy(Person::getCity,
     *                                              reducing(Person::getLastName, BinaryOperator.maxBy(byLength))));
     * }</pre>
     * @see Collectors#reducing(Object, Function, BinaryOperator)
     */
    @NotNull
    public static <T, R>
    Collector<T, ?, R> reducingConcurrent(@Nullable R identity,
                                          @NotNull final Function<? super T, ? extends R> mapper,
                                          @NotNull final BinaryOperator<R> op) {
        requireNonNull(mapper);
        requireNonNull(op);

        return Collector.of(
                () -> new AtomicReference<>(identity),
                (AtomicReference<R> ar, T t) -> ar.accumulateAndGet(mapper.apply(t), op),
                (AtomicReference<R> t1, AtomicReference<R> t2) -> {
                    t1.accumulateAndGet(t2.get(), op);
                    return t1;
                },
                AtomicReference::get,
                Collector.Characteristics.CONCURRENT
        );
    }

    /**
     * Returns a merger that will replace an existing value with the latest value.
     *
     * @param <V> value type
     * @return a merger that will replace values
     */
    public static <V> BinaryOperator<V> replacingMerger() {
        return (u, v) -> v;
    }

    /**
     * Returns a merger that will retain an existing value and discard the latest value.
     *
     * @param <V> value type
     * @return a merger that will retain values
     */
    public static <V> BinaryOperator<V> retainingMerger() {
        return (u, v) -> u;
    }

    /**
     * Returns a merger that will throw an Exception if duplicate keys are detected.
     *
     * @param <V> value type
     * @return a merger that will throw an Exception if duplicate keys are detected
     */
    public static <V> BinaryOperator<V> throwingMerger() {
        return (u, v) -> {
            throw new IllegalStateException(String.format("Duplicate value for %s", u));
        };
    }

}