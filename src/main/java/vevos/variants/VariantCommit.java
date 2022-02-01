package vevos.variants;

import vevos.repository.Branch;
import vevos.repository.Commit;

/**
 * Represents a commit to an AbstractVariantsRepository.
 */
public class VariantCommit extends Commit {
    private final Branch branch;

    public VariantCommit(final String commitId, final Branch branch) {
        super(commitId);
        this.branch = branch;
    }

    public Branch branch() {
        return branch;
    }
}
