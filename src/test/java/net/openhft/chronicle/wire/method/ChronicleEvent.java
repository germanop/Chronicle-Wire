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

package net.openhft.chronicle.wire.method;

import net.openhft.chronicle.wire.Base64LongConverter;
import net.openhft.chronicle.wire.BytesInBinaryMarshallable;
import net.openhft.chronicle.wire.LongConversion;
import net.openhft.chronicle.wire.NanoTimestampLongConverter;

public class ChronicleEvent extends BytesInBinaryMarshallable implements Event {
    @LongConversion(NanoTimestampLongConverter.class)
    private long sendingTimeNS;
    @LongConversion(NanoTimestampLongConverter.class)
    private long transactTimeNS;

    @LongConversion(Base64LongConverter.class)
    private long text1;
    private String text3;


    @Override
    public void sendingTimeNS(long sendingTimeNS) {
        this.sendingTimeNS = sendingTimeNS;
    }

    @Override
    public long sendingTimeNS() {
        return sendingTimeNS;
    }

    @Override
    public void transactTimeNS(long transactTimeNS) {
        this.transactTimeNS = transactTimeNS;
    }

    @Override
    public long transactTimeNS() {
        return transactTimeNS;
    }
}
