package net.openhft.chronicle.wire;

import net.openhft.chronicle.bytes.Bytes;

public interface BigConverter {
    Object parseObj(CharSequence text);

    long[] parseRaw(CharSequence text);

    void append(StringBuilder text, Number value);

    default void append(Bytes<?> bytes, Number value) {
        final StringBuilder sb = WireInternal.acquireStringBuilder();
        append(sb, value);
        bytes.append(sb);
    }
}
