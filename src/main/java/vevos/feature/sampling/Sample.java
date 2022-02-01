package vevos.feature.sampling;

import org.jetbrains.annotations.NotNull;
import vevos.feature.Variant;

import java.util.Iterator;
import java.util.List;

public record Sample(List<Variant> variants) implements Iterable<Variant> {
    public static Sample of(final List<Variant> variants) {
        return new Sample(variants);
    }

    public int size() {
        return variants.size();
    }

    @NotNull
    @Override
    public Iterator<Variant> iterator() {
        return variants.iterator();
    }
}
