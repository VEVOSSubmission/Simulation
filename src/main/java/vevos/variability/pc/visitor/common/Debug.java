package vevos.variability.pc.visitor.common;

import vevos.util.StringUtils;
import vevos.variability.pc.visitor.ArtefactVisitor;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collection of visitors for debug purposes.
 */
public class Debug {
    /**
     * Creates a visitor that prints trees in a simplistic way.
     */
    public static ArtefactVisitor createSimpleTreePrinter() {
        final AtomicInteger indent = new AtomicInteger();
        return new CallbackArtefactVisitor(
                artefact -> {
                    System.out.println(StringUtils.genIndent(indent.get()) + artefact);
                    indent.incrementAndGet();
                },
                artefact -> indent.decrementAndGet()
        );
    }
}
