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

package net.openhft.chronicle.wire;

import net.openhft.chronicle.bytes.Bytes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;

import static net.openhft.chronicle.wire.VanillaMethodWriterBuilder.DISABLE_WRITER_PROXY_CODEGEN;
import static net.openhft.chronicle.wire.WireType.BINARY;
import static org.junit.Assume.assumeFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@RunWith(Parameterized.class)
public class UpdateInterceptorReturnTypeTest extends WireTestCommon {
    @Parameterized.Parameter
    public boolean disableProxyCodegen;

    @Parameterized.Parameters(name = DISABLE_WRITER_PROXY_CODEGEN + "={0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[]{false}, new Object[]{true});
    }

    static Wire createWire() {
        final Wire wire = BINARY.apply(Bytes.allocateElasticOnHeap());
        return wire;
    }

    @Before
    public void setUp() {
        System.setProperty(DISABLE_WRITER_PROXY_CODEGEN, String.valueOf(disableProxyCodegen));
        if (disableProxyCodegen)
            expectException("Falling back to proxy method writer");
    }

    @After
    public void cleanUp() {
        System.clearProperty(DISABLE_WRITER_PROXY_CODEGEN);
    }

    @Test
    public void testUpdateInterceptorNoReturnType() {

        final Wire wire = createWire();
        wire
                .methodWriterBuilder(NoReturnType.class)
                .updateInterceptor((methodName, t) -> true)
                .build()
                .x("hello world");
        assertEquals("" +
                        "--- !!data #binary\n" +
                        "x: hello world\n",
                Wires.fromSizePrefixedBlobs(wire));
    }

    @Test
    public void testUpdateInterceptorWithIntReturnType() {
        final Wire wire = createWire();
        int value = wire
                .methodWriterBuilder(WithIntReturnType.class)
                .updateInterceptor((methodName, t) -> true)
                .build()
                .x("hello world");
        assertEquals(0, value);
        assertEquals("" +
                        "--- !!data #binary\n" +
                        "x: hello world\n",
                Wires.fromSizePrefixedBlobs(wire));
    }

    @Test
    public void testUpdateInterceptorWithObjectReturnType() {
        final Wire wire = createWire();
        final WithObjectReturnType mw = wire
                .methodWriterBuilder(WithObjectReturnType.class)
                .updateInterceptor((methodName, t) -> true)
                .build();
        Object value = mw.x("hello world");
        assertSame(mw, value);
        assertEquals(disableProxyCodegen, Proxy.isProxyClass(mw.getClass()));
        assumeFalse(disableProxyCodegen);
        // data is written but on hold until the end of message is written.
        // WireDumper no longer scans data that is written but not ready
        assertEquals("" +
                        "--- !!not-ready-data\n" +
                        "...\n" +
                        "# 15 bytes remaining\n",
                Wires.fromSizePrefixedBlobs(wire));

        mw.y("good byte");
        assertEquals("" +
                        "--- !!data #binary\n" +
                        "x: hello world\n" +
                        "y: good byte\n",
                Wires.fromSizePrefixedBlobs(wire));
    }

    @Test
    public void testUpdateInterceptorWithLadderByQtyListener() {
        final Wire wire = createWire();
        wire
                .methodWriterBuilder(LadderByQtyListener.class)
                .updateInterceptor((methodName, t) -> true)
                .build()
                .ladderByQty("a ladder");
        assertEquals("" +
                        "--- !!data #binary\n" +
                        "ladderByQty: a ladder\n",
                Wires.fromSizePrefixedBlobs(wire));
    }

    public interface LadderByQtyListener {
        void ladderByQty(String ladder);

        default void lbq(String name, String ladder) {
            ladderByQty(ladder);
        }

        default boolean ignoreMethodBasedOnFirstArg(String methodName, String ladderDefinitionName) {
            return false;
        }
    }

    interface NoReturnType {
        void x(String x);
    }

    interface WithIntReturnType {
        int x(String x);
    }

    interface WithObjectReturnType {
        Object x(String x);

        void y(String y);
    }

    interface WithObjectVoidReturnType {
        Void x(String x);
    }
}