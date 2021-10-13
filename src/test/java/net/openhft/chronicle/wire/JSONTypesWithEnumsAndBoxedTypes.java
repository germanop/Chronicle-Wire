package net.openhft.chronicle.wire;

import net.openhft.chronicle.core.pool.ClassAliasPool;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * relates to https://github.com/OpenHFT/Chronicle-Wire/issues/324
 */
@Ignore
@RunWith(value = Parameterized.class)
public class JSONTypesWithEnumsAndBoxedTypes {

    private final boolean useTypes;

    @Parameterized.Parameters(name = "useTypes={0}")
    public static Collection<Object[]> wireTypes() {
        return Arrays.asList(
                new Object[]{true},
                new Object[]{false}
        );
    }

    public JSONTypesWithEnumsAndBoxedTypes(boolean useTypes) {
        this.useTypes = useTypes;
    }

    enum Location {
        PITS, TRACK, GRAVEL
    }

    static class F1 extends AbstractMarshallableCfg {

  

        private String surname;
        private Integer car;
        private Location location;

        public F1(String surname, int car, Location location) {
            this.surname = surname;
            this.car = car;
            this.location = location;
        }
    }

    @Test
    public void test() {
        ClassAliasPool.CLASS_ALIASES.addAlias(F1.class);
        final JSONWire jsonWire = new JSONWire()
                .useTypes(useTypes);

        jsonWire.getValueOut()
                .object(new F1("Hamilton", 44, Location.TRACK));

        final String actual = jsonWire.getValueIn().object().toString();
        Assert.assertTrue(actual.contains("TRACK"));
    }
}