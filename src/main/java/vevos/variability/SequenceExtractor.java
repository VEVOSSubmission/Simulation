package vevos.variability;

import vevos.functjonal.list.NonEmptyList;

import java.util.Collection;
import java.util.List;

@FunctionalInterface
public interface SequenceExtractor {
    List<NonEmptyList<SPLCommit>> extract(final Collection<SPLCommit> commits);
}
