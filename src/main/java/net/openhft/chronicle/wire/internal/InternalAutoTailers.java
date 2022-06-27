package net.openhft.chronicle.wire.internal;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.threads.InvalidEventHandlerException;
import net.openhft.chronicle.threads.Pauser;
import net.openhft.chronicle.wire.ExcerptListener;
import net.openhft.chronicle.wire.MarshallableIn;
import net.openhft.chronicle.wire.internal.reduction.ReductionUtil;
import net.openhft.chronicle.wire.domestic.AutoTailers;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

import static net.openhft.chronicle.core.util.ObjectUtils.requireNonNull;

public final class InternalAutoTailers {

    private InternalAutoTailers() {
    }

    public static final class RunnablePoller extends AbstractPoller implements AutoTailers.CloseableRunnable {

        private final Pauser pauser;

        public RunnablePoller(@NotNull final Supplier<? extends MarshallableIn> tailerSupplier,
                              @NotNull final ExcerptListener excerptListener,
                              @NotNull final Supplier<Pauser> pauserSupplier) {
            super(tailerSupplier, excerptListener);
            requireNonNull(pauserSupplier);
            this.pauser = requireNonNull(pauserSupplier.get());
        }

        @Override
        public void run() {
            try {
                while (running()) {
                    if (ReductionUtil.accept(tailer(), excerptListener()) != -1) {
                        pauser.pause();
                    }
                }
            } finally {
                closer().run();
            }
        }
    }

    public static final class EventHandlerPoller extends AbstractPoller implements AutoTailers.CloseableEventHandler {

        public EventHandlerPoller(@NotNull final Supplier<? extends MarshallableIn> tailerSupplier,
                                  @NotNull final ExcerptListener excerptListener) {
            super(tailerSupplier, excerptListener);
        }

        @Override
        public boolean action() throws InvalidEventHandlerException {
            if (!running()) {
                closer().run();
                throw InvalidEventHandlerException.reusable();
            }
            return ReductionUtil.accept(tailer(), excerptListener()) != -1;
        }
    }

    private abstract static class AbstractPoller implements AutoCloseable {

        private final ExcerptListener excerptListener;
        private final MarshallableIn tailer;
        private final Runnable closer;
        private volatile boolean running = true;

        protected AbstractPoller(@NotNull final Supplier<? extends MarshallableIn> tailerSupplier,
                                 @NotNull final ExcerptListener excerptListener) {
            requireNonNull(tailerSupplier);
            this.excerptListener = requireNonNull(excerptListener);
            this.tailer = requireNonNull(tailerSupplier.get());
            this.closer = closer(tailer);
        }

        protected ExcerptListener excerptListener() {
            return excerptListener;
        }

        protected MarshallableIn tailer() {
            return tailer;
        }

        protected Runnable closer() {
            return closer;
        }

        protected boolean running() {
            return running;
        }

        @Override
        public final void close() {
            running = false;
        }

        private static Runnable closer(MarshallableIn tailer) {
            if (tailer instanceof AutoCloseable) {
                final AutoCloseable ac = (AutoCloseable) tailer;
                return () -> {
                    try {
                        ac.close();
                    } catch (Exception e) {
                        throw Jvm.rethrow(e);
                    }
                };
            }
            return () -> {
            };
        }
    }

}