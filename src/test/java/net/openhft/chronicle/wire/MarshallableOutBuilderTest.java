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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.BytesUtil;
import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.OS;
import net.openhft.chronicle.jlbh.JLBH;
import net.openhft.chronicle.jlbh.JLBHOptions;
import net.openhft.chronicle.jlbh.JLBHTask;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MarshallableOutBuilderTest extends net.openhft.chronicle.wire.WireTestCommon {

    @Override
    @Before
    public void threadDump() {
        super.threadDump();
    }

    @Test
    public void fileAppend() throws IOException {
        final String expected = "" +
                "mid: mid\n" +
                "next: 1\n" +
                "echo: echo-1\n" +
                "...\n" +
                "mid2: mid2\n" +
                "next2: word\n" +
                "echo: echo-2\n" +
                "...\n";
        file("?append=true", expected);
    }

    @Test
    public void file() throws IOException {
        final String expected = "" +
                "mid2: mid2\n" +
                "next2: word\n" +
                "echo: echo-2\n" +
                "...\n";
        file("", expected);
    }

    public void file(String query, String expected) throws IOException {
        final File file = new File(OS.getTarget(), "tmp-" + System.nanoTime());
        final URL url = new URL("file://" + file.getAbsolutePath() + query);
        writeMessages(url);
        final Bytes bytes = BytesUtil.readFile(file.getAbsolutePath());
        assertEquals(expected, bytes.toString());
        // can overwrite ok
        writeMessages(url);
    }

    private void writeMessages(URL url) {
        writeMessages(url, null);
    }

    private void writeMessages(URL url, WireType wireType) {
        final MarshallableOut out = MarshallableOut.builder(url).wireType(wireType).get();
        ITop top = out.methodWriter(ITop.class);
        top.mid("mid")
                .next(1)
                .echo("echo-1");
        top.mid2("mid2")
                .next2("word")
                .echo("echo-2");
    }

    @Test
    public void http() throws IOException, InterruptedException {
        int port = 65432;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        server.createContext("/echo", new Handler(queue));
        server.start();
        try {
            final URL url = new URL("http://localhost:" + port + "/echo");
            writeMessages(url);
            assertEquals(
                    "{\"mid\":\"mid\",\"next\":1,\"echo\":\"echo-1\"}\n",
                    queue.poll(1, TimeUnit.SECONDS));
            assertEquals(
                    "{\"mid2\":\"mid2\",\"next2\":\"word\",\"echo\":\"echo-2\"}\n",
                    queue.poll(1, TimeUnit.SECONDS));
            assertNull(queue.poll(1, TimeUnit.MILLISECONDS));
        } finally {
            server.stop(1);
        }
    }

    @Ignore("test was added to work with queue-web-gateway, so work in progress")
    @Test
    public void http2() throws IOException, InterruptedException {
        int port = 51972;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        server.createContext("/echo", new Handler(queue));
        server.start();
        try {
            final URL url = new URL("http://localhost:" + port + "/echo/append");
            writeMessages(url);
            assertEquals(
                    "{\"mid\":\"mid\",\"next\":1,\"echo\":\"echo-1\"}\n",
                    queue.poll(1, TimeUnit.SECONDS));
            assertEquals(
                    "{\"mid2\":\"mid2\",\"next2\":\"word\",\"echo\":\"echo-2\"}\n",
                    queue.poll(1, TimeUnit.SECONDS));
            assertNull(queue.poll(1, TimeUnit.MILLISECONDS));
        } finally {
            server.stop(1);
        }
    }

    //"Only JSON Wire is currently supported"
    @Test(expected = IllegalArgumentException.class)
    public void httpBinary() throws IOException, InterruptedException {
        int port = 65432;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        server.createContext("/echo", new Handler(queue));
        server.start();
        try {
            final URL url = new URL("http://localhost:" + port + "/echo");
            writeMessages(url, WireType.BINARY_LIGHT);
        } finally {
            server.stop(1);
        }

    }

    interface Timed {
        void time(long timeNS);
    }

    static class Handler implements HttpHandler {
        private final BlockingQueue<String> queue;

        public Handler(BlockingQueue<String> queue) {
            this.queue = queue;
        }

        @Override
        public void handle(HttpExchange xchg) throws IOException {
            Bytes bytes = Bytes.allocateElasticOnHeap();
            char ch;
            for (InputStream is = xchg.getRequestBody(); (ch = (char) is.read()) != (char) -1; )
                bytes.writeUnsignedByte(ch);
            if (bytes.readInt(0) < ' ' << 24)
                queue.add(bytes.toHexString());
            else
                queue.add(bytes.toString());
            xchg.sendResponseHeaders(202, 0);
        }
    }

    static class Benchmark implements JLBHTask {
        static final int PORT = 65432;
        static final int THROUGHPUT = Integer.getInteger("throughput", 50);
        private HttpServer server;
        private Timed timed;
        private JLBH jlbh;

        public static void main(String[] args) {
            JLBHOptions jlbhOptions = new JLBHOptions()
                    .warmUpIterations(2000)
                    .iterations(THROUGHPUT * 30)
                    .throughput(THROUGHPUT)
                    .runs(10)
                    .recordOSJitter(false).accountForCoordinatedOmission(false)
                    .jlbhTask(new Benchmark());
            new JLBH(jlbhOptions).start();
        }

        @Override
        public void init(JLBH jlbh) {
            this.jlbh = jlbh;
            try {
                server = HttpServer.create(new InetSocketAddress(PORT), 50);
                server.createContext("/bench", new BenchHandler());
                server.start();
                final URL url = new URL("http://localhost:" + PORT + "/bench");
                MarshallableOut out = MarshallableOut.builder(url).wireType(WireType.JSON_ONLY).get();
                timed = out.methodWriter(Timed.class);
            } catch (IOException ioe) {
                throw Jvm.rethrow(ioe);
            }
        }

        @Override
        public void run(long startTimeNS) {
            timed.time(startTimeNS);
        }

        @Override
        public void complete() {
            server.stop(1);
        }

        class BenchHandler implements HttpHandler {
            Wire wire = WireType.JSON_ONLY.apply(Bytes.allocateElasticOnHeap(128));

            @Override
            public void handle(HttpExchange xchg) {
                try {
                    InputStream is = xchg.getRequestBody();
                    final Bytes<byte[]> bytes2 = (Bytes<byte[]>) wire.bytes();
                    int length = is.available();
                    byte[] bytes = bytes2.underlyingObject();
                    int length2 = is.read(bytes);
                    assert length == length2;
                    bytes2.readPositionRemaining(0, length2);
                    if (wire.bytes().peekUnsignedByte() == '{')
                        wire.bytes().readUnsignedByte();
                    final long time = wire.read("time").int64();
                    jlbh.sample(System.nanoTime() - time);

                    xchg.sendResponseHeaders(202, 0);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }
}

