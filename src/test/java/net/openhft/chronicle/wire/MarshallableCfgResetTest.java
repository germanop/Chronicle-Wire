package net.openhft.chronicle.wire;

import net.openhft.chronicle.bytes.Bytes;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MarshallableCfgResetTest extends net.openhft.chronicle.wire.WireTestCommon {

    public static class Engine extends AbstractMarshallableCfg {
        public boolean isItElectric;

        public Engine(boolean isItElectric) {
            this.isItElectric = isItElectric;
        }
    }

    public static class Boat extends SelfDescribingMarshallable {

        Engine engine;

        public Boat(Engine engine) {
            this.engine = engine;
        }
    }


    /**
     * tests that objects that extends AbstractMarshallableCfg are reset when calling {@link ValueIn#object(Object, Class)}
     */
    @Test
    public void test() {
        Boat k9f = new Boat(new Engine(false));

        Wire w = new JSONWire();
        w.getValueOut().object(k9f);
        // isItElectric isn't written as it has the default value
        assertEquals("{\"engine\":{}}", w.toString());

        Boat using = new Boat(new Engine(true));

        // isItElectric is set to true and then the fields set are overridden.
        Boat boat = w.getValueIn().object(using, Boat.class);
        Assert.assertTrue(boat.engine.isItElectric);

        new JSONWire(Bytes.from("{\"engine\":{\"isItElectric\":false}}")).getValueIn().object(using, Boat.class);
        Assert.assertFalse(boat.engine.isItElectric);
    }

}
