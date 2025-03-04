package net.openhft.chronicle.wire.internal;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.core.pool.ClassLookup;
import net.openhft.chronicle.core.util.ClassNotFoundRuntimeException;
import net.openhft.chronicle.wire.Validate;
import net.openhft.chronicle.wire.Wire;
import net.openhft.chronicle.wire.WireType;
import org.jetbrains.annotations.NotNull;

public class WireTypeConverterInternal {
    private static final Validate NO_OP = x -> {
    };
    private final Bytes bytes = Bytes.allocateElasticOnHeap();
    private final Wire yamlWire = WireType.YAML_ONLY.apply(bytes);
    private final Wire jsonWire = WireType.JSON_ONLY.apply(bytes);

    private final Validate validate;
    private Exception e;

    public WireTypeConverterInternal(@NotNull Validate validate) {
        this.validate = validate;
        replaceClassLookup(jsonWire);
        replaceClassLookup(yamlWire);
    }

    public WireTypeConverterInternal() {
        this.validate = NO_OP;
        replaceClassLookup(jsonWire);
        replaceClassLookup(yamlWire);
    }

    public CharSequence jsonToYaml(CharSequence json) throws Exception {
        e = null;
        jsonWire.reset();
        jsonWire.bytes().append(json);

        Object object = jsonWire.getValueIn().object();
        if (e != null)
            throw e;
        validate.validate(object);
        yamlWire.reset();
        yamlWire.getValueOut().object(object);
        if (e != null)
            throw e;

        return yamlWire.bytes();
    }

    public CharSequence yamlToJson(CharSequence yaml) throws Exception {
        e = null;
        yamlWire.reset();
        yamlWire.bytes().clear().append(yaml);
        Object object = yamlWire.getValueIn().object();

        if (e != null)
            throw e;

        validate.validate(object);
        jsonWire.reset();
        jsonWire.bytes().clear();
        jsonWire.getValueOut().object(object);
        if (e != null)
            throw e;
        return jsonWire.bytes();
    }

    private void replaceClassLookup(Wire wire) {
        final ClassLookup delegate = wire.classLookup();
        wire.classLookup(new ClassLookup() {

            @Override
            public Class<?> forName(CharSequence name) throws ClassNotFoundRuntimeException {
                try {
                    return delegate.forName(name);
                } catch (Exception e) {
                    WireTypeConverterInternal.this.e = e;
                    throw e;
                }
            }

            @Override
            public String nameFor(Class<?> clazz) throws IllegalArgumentException {
                try {
                    return delegate.nameFor(clazz);
                } catch (Exception e) {
                    WireTypeConverterInternal.this.e = e;
                    throw e;
                }
            }

            @Override
            public void addAlias(Class<?>... classes) {
                delegate.addAlias(classes);
            }

            @Override
            public void addAlias(Class<?> clazz, String names) {
                delegate.addAlias(clazz, names);
            }
        });
    }

    /**
     * Explicit support for leniency on different types.
     *
     * @param newClass    to use instead
     * @param oldTypeName to support
     */
    public void addAlias(Class newClass, String oldTypeName) {
        jsonWire.classLookup().addAlias(newClass, oldTypeName);
        yamlWire.classLookup().addAlias(newClass, oldTypeName);
    }
}
