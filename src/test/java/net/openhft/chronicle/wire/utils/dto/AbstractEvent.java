/*
 * Copyright (c) 2016-2019 Chronicle Software Ltd
 */

package net.openhft.chronicle.wire.utils.dto;

import net.openhft.chronicle.wire.*;
import net.openhft.chronicle.wire.converter.Base85;
import net.openhft.chronicle.wire.converter.NanoTime;

public class AbstractEvent<E extends AbstractEvent<E>> extends SelfDescribingMarshallable {
    @Base85
    private long sender;
    @Base85
    private long target;
    // client sending time
    @NanoTime
    private long sendingTime;

    public long sender() {
        return sender;
    }

    public E sender(long sender) {
        this.sender = sender;
        return (E) this;
    }

    public long target() {
        return target;
    }

    public E target(long target) {
        this.target = target;
        return (E) this;
    }

    public long sendingTime() {
        return sendingTime;
    }

    public E sendingTime(long sendingTime) {
        this.sendingTime = sendingTime;
        return (E) this;
    }
}
