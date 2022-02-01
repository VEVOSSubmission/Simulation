package vevos.variability;

import vevos.repository.Commit;

public record EvolutionStep<T extends Commit>(T parent, T child) {
    @Override
    public String toString() {
        return "(" + parent  + ", " + child + ")";
    }
}
