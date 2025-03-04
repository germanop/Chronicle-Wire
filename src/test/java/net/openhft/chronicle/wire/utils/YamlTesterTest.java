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

package net.openhft.chronicle.wire.utils;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.time.SetTimeProvider;
import net.openhft.chronicle.core.time.SystemTimeProvider;
import net.openhft.chronicle.wire.TextMethodTester;
import net.openhft.chronicle.wire.WireTestCommon;
import net.openhft.chronicle.wire.YamlMethodTester;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static net.openhft.chronicle.core.time.SystemTimeProvider.CLOCK;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;


public class YamlTesterTest extends WireTestCommon {
    private static TestImpl testImpl;

    @Before
    public void setUp() {
        CLOCK = new SetTimeProvider("2022-05-17T20:26:00")
                .autoIncrement(1, TimeUnit.MICROSECONDS);
    }

    @Override
    protected void preAfter() {
        super.preAfter();
        if (testImpl != null)
            assertTrue(testImpl.isClosed());
    }

    @After
    public void tearDown() {
        CLOCK = SystemTimeProvider.INSTANCE;
    }

    @Test
    public void t1() {
        YamlTester yt = new YamlMethodTester<>(
                "yaml-tester/t1/in.yaml",
                newTestImplFunction(),
                TestOut.class,
                "yaml-tester/t1/out.yaml")
                .setup("yaml-tester/t1/setup.yaml")
                .inputFunction(s -> s.replace("# Replace comment", ""));
        assertEquals(yt.expected(), yt.actual());
    }

    @Test
    public void t2() {
        final YamlTester yt = YamlTester.runTest(newTestImplFunction(), TestOut.class, "yaml-tester/t2");
        assertEquals(yt.expected(), yt.actual());
    }

    @Test
    public void t3() {
        final YamlTester yt = YamlTester.runTest(newTestImplFunction(), TestOut.class, "yaml-tester/t3");
        assertEquals(yt.expected(), yt.actual());
    }

    @NotNull
    private static Function<TestOut, Object> newTestImplFunction() {
        return out -> testImpl = new TestImpl(out);
    }

    @Test
    public void t2error() {
        System.err.println("### The Following NullPointerException Are Expected ###");
        expectException("java.lang.NullPointerException");
        CLOCK = null;
        final YamlTester yt = YamlTester.runTest(newTestImplFunction(), TestOut.class, "yaml-tester/t2");
        assertEquals("" +
                        "---\n" +
                        "---\n" +
                        "---",
                yt.actual());
        assertTrue(testImpl.isClosed());
    }

    @Test
    public void mismatched() {
        assumeFalse(YamlTester.REGRESS_TESTS);
        expectException("setup.yaml not found");
        final YamlTester yt = YamlTester.runTest(TestImpl.class, "yaml-tester/mismatch");
        assertNotEquals("This tests an inconsistency was found, so they shouldn't be the same", yt.expected(), yt.actual());
    }

    @Test
    public void comments() {
        // Note using YamlWire instead of TextWire moves comment 8
        final YamlTester yt = YamlTester.runTest(newTestImplFunction(), TestOut.class, "yaml-tester/comments");
        assertEquals(yt.expected(), yt.actual());
    }

    @Test
    public void direct() throws IOException {
        assumeFalse(Jvm.getBoolean("regress.tests"));
        YamlTester yt = new TextMethodTester<>(
                "=" +
                        "# comment 1\n" +
                        "---\n" +
                        "# comment 2\n" +
                        "time: 2022-05-17T20:25:02.002\n" +
                        "# comment 3\n" +
                        "...\n" +
                        "# comment 4\n" +
                        "---\n" +
                        "# comment 5\n" +
                        "testEvent: {\n" +
                        "  # comment 6\n" +
                        "  eventTime: 2022-05-17T20:25:01.001\n" +
                        "  # comment 7\n" +
                        "}\n" +
                        "# comment 8\n" +
                        "...\n" +
                        "# comment 9\n",
                newTestImplFunction(),
                TestOut.class,
                "=" +
                        "# comment 1\n" +
                        "# comment 2\n" +
                        "---\n" +
                        "# comment 3\n" +
                        "# comment 4\n" +
                        "# comment 5\n" +
                        "# comment 6\n" +
                        "# comment 7\n" +
                        "# comment 8\n" +
                        "---\n" +
                        "testEvent: {\n" +
                        "  eventTime: 2022-05-17T20:25:01.001,\n" +
                        "  processedTime: 2022-05-17T20:25:02.002,\n" +
                        "  currentTime: 2022-05-17T20:26:00\n" +
                        "}\n" +
                        "...\n" +
                        "# comment 9\n")
                .run();
        assertEquals(yt.expected(), yt.actual());
    }
}
