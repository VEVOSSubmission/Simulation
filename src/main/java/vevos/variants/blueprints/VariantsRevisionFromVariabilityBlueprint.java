package vevos.variants.blueprints;

import org.eclipse.jgit.api.errors.GitAPIException;
import vevos.feature.Variant;
import vevos.feature.sampling.Sample;
import vevos.functjonal.Lazy;
import vevos.functjonal.Result;
import vevos.repository.AbstractSPLRepository;
import vevos.repository.AbstractVariantsRepository;
import vevos.repository.Branch;
import vevos.util.Logger;
import vevos.util.io.CaseSensitivePath;
import vevos.variability.SPLCommit;
import vevos.variability.pc.Artefact;
import vevos.variability.pc.groundtruth.GroundTruth;
import vevos.variability.pc.options.ArtefactFilter;
import vevos.variability.pc.options.VariantGenerationOptions;
import vevos.variants.VariantCommit;
import vevos.variants.VariantsRevision;
import vevos.variants.sampling.SamplingStrategy;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Default blueprint to generate variants from the software product line at a certain commit.
 */
public class VariantsRevisionFromVariabilityBlueprint extends VariantsRevisionBlueprint {
    private final SPLCommit splCommit;
    private final SamplingStrategy sampler;

    /**
     * Creates a new blueprint that can generate variants from the given splCommit that contains
     * the variability information of a specific SPLCommit.
     * @param splCommit The variability commit from which a VariantsRevision should be created.
     * @param predecessor The predecessor blueprint that generates the VariantsRevision that has to be generated before
     *                    the revision of this blueprint. May be null, if this is the first blueprint to generate.
     */
    public VariantsRevisionFromVariabilityBlueprint(
            final SPLCommit splCommit,
            final VariantsRevisionFromVariabilityBlueprint predecessor,
            final SamplingStrategy sampler)
    {
        super(predecessor);
        this.splCommit = splCommit;
        this.sampler = sampler;
    }

    public SPLCommit getSPLCommit() {
        return splCommit;
    }

    @Override
    protected Lazy<Sample> computeSample() {
        return splCommit.featureModel().map(featureModel -> sampler.sampleForRevision(featureModel, this));
    }

    @Override
    public Lazy<VariantsRevision.Branches> generateArtefactsFor(final VariantsRevision revision) {
        return splCommit.presenceConditions().and(getSample()).map(ts -> {
            // TODO: Should we implement handling of an empty optional, or do we consider this to be a fundamental error?
            final Artefact traces = ts.first().orElseThrow();
            final Sample sample = ts.second();
            final AbstractSPLRepository splRepo = revision.getSPLRepo();
            final AbstractVariantsRepository variantsRepo = revision.getVariantsRepo();

            final Map<Branch, VariantCommit> commits = new HashMap<>(sample.size());
            for (final Variant variant : sample.variants()) {
                final Branch branch = variantsRepo.getBranchByName(variant.getName());

                try {
                    variantsRepo.checkoutBranch(branch);
                } catch (final IOException | GitAPIException e) {
                    throw new RuntimeException("Failed checkout of branch " + branch + " in variants repository.");
                }

                try {
                    splRepo.checkoutCommit(splCommit);
                } catch (final IOException | GitAPIException e) {
                    throw new RuntimeException("Failed checkout of commit " + splCommit.id() + " in SPL Repository.");
                }

                // Generate the code
                final Result<GroundTruth, Exception> result = traces.generateVariant(
                        variant,
                        new CaseSensitivePath(splRepo.getPath()),
                        new CaseSensitivePath(variantsRepo.getPath()),
                        VariantGenerationOptions.ExitOnErrorButAllowNonExistentFiles(ArtefactFilter.KeepAll()));
                Logger.log(result.map(u -> "Generating variant " + variant + " was successful!"));

                // Commit the generated variant with the corresponding spl commit has as message.
                final String commitMessage = splCommit.id() + " || " + splCommit.message() + " || " + variant.getName();
                final Optional<VariantCommit> variantCommit;

                try {
                    variantCommit = variantsRepo.commit(commitMessage);
                } catch (final GitAPIException | IOException e) {
                    throw new RuntimeException("Failed to commit " + commitMessage + " to VariantsRepository.");
                }

                variantCommit.ifPresent(commit -> commits.put(branch, commit));
            }

            return new VariantsRevision.Branches(commits);
        });
    }
}
