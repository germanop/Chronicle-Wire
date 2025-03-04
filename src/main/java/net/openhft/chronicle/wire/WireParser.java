/*
 * Copyright 2016-2020 chronicle.software
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
package net.openhft.chronicle.wire;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.io.InvalidMarshallableException;
import net.openhft.chronicle.core.util.InvocationTargetRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Interface to parseOne arbitrary field-value data.
 */
public interface WireParser extends Consumer<WireIn> {

    FieldNumberParselet SKIP_READABLE_BYTES = WireParser::skipReadable;

    @NotNull
    static WireParser wireParser(WireParselet defaultConsumer) {
        return new VanillaWireParser(defaultConsumer, SKIP_READABLE_BYTES);
    }

    @NotNull
    static WireParser wireParser(@NotNull WireParselet defaultConsumer,
                                 @NotNull FieldNumberParselet fieldNumberParselet) {
        return new VanillaWireParser(defaultConsumer, fieldNumberParselet);
    }

    static void skipReadable(long ignoreMethodId, WireIn wire) {
        Bytes<?> bytes = wire.bytes();
        bytes.readPosition(bytes.readLimit());
    }

    WireParselet getDefaultConsumer();

    void parseOne(@NotNull WireIn wireIn) throws InvocationTargetRuntimeException, InvalidMarshallableException;

    @Override
    default void accept(@NotNull WireIn wireIn) {
        wireIn.startEvent();
        Bytes<?> bytes = wireIn.bytes();
        while (bytes.readRemaining() > 0) {
            if (wireIn.isEndEvent())
                break;
            long start = bytes.readPosition();
            parseOne(wireIn);
            wireIn.consumePadding();
            if (bytes.readPosition() == start) {
                Jvm.warn().on(getClass(), "Failed to progress reading " + bytes.readRemaining() + " bytes left.");
                break;
            }
        }
        wireIn.endEvent();
    }

    /**
     * Performs a {@link WireParselet} lookup based on the provided {@code name}. If
     * a {@link WireParselet} is not associated with the provided {@code name}, {@code null}
     * is returned.
     *
     * @return {@link WireParselet} associated with the provided {@code name},
     * {@code null} if no {@link WireParselet} was found.
     */
    WireParselet lookup(CharSequence name);

    @NotNull
    default VanillaWireParser registerOnce(WireKey key, WireParselet valueInConsumer) {
        CharSequence name = key.name();
        if (lookup(name) != null) {
            Jvm.warn().on(getClass(), "Unable to register multiple methods for " + name + " ignoring one.");
        } else {
            register(key, valueInConsumer);
        }
        return (VanillaWireParser) this;
    }

    @NotNull
    default VanillaWireParser register(WireKey key, WireParselet valueInConsumer) {
        return register(key.toString(), valueInConsumer);
    }

    @NotNull
    VanillaWireParser register(String keyName, WireParselet valueInConsumer);

}