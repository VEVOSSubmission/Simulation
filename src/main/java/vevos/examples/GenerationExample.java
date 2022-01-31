package vevos.examples;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import vevos.VEVOS;
import vevos.feature.Variant;
import vevos.feature.config.SimpleConfiguration;
import vevos.feature.sampling.ConstSampler;
import vevos.feature.sampling.FeatureIDESampler;
import vevos.feature.sampling.Sample;
import vevos.feature.sampling.Sampler;
import vevos.functjonal.Lazy;
import vevos.functjonal.Result;
import vevos.functjonal.list.NonEmptyList;
import vevos.io.Resources;
import vevos.util.Logger;
import vevos.util.io.CaseSensitivePath;
import vevos.variability.EvolutionStep;
import vevos.variability.SPLCommit;
import vevos.variability.VariabilityDataset;
import vevos.variability.VariabilityHistory;
import vevos.variability.pc.Artefact;
import vevos.variability.pc.SourceCodeFile;
import vevos.variability.pc.groundtruth.AnnotationGroundTruth;
import vevos.variability.pc.groundtruth.GroundTruth;
import vevos.variability.pc.options.ArtefactFilter;
import vevos.variability.pc.options.VariantGenerationOptions;
import vevos.variability.sequenceextraction.LongestNonOverlappingSequences;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class GenerationExample {
    public static void main(final String[] args) throws Resources.ResourceIOException {
        VEVOS.Initialize();

        final CaseSensitivePath splRepositoryPath = CaseSensitivePath.of("path", "to", "SPL", "git", "repository");
        final CaseSensitivePath groundTruthDatasetPath = CaseSensitivePath.of("path", "to", "datasets");
        final CaseSensitivePath variantsGenerationDir = CaseSensitivePath.of("directory", "to", "put", "generated", "variants");

        /// Load the ground truth dataset extracted with the variability extraction of VEVOS
        final VariabilityDataset dataset =
                Resources.Instance().load(VariabilityDataset.class, groundTruthDatasetPath.path());

        /// Inspect all evolution steps (i.e., commits where the extraction succeeded).
        final Set<EvolutionStep<SPLCommit>> evolutionSteps = dataset.getEvolutionSteps();

        Logger.info("The dataset contains " + dataset.getSuccessCommits().size() + " commits for which the variability extraction succeeded.");
        Logger.info("The dataset contains " + dataset.getErrorCommits().size() + " commits for which the variability extraction failed.");
        Logger.info("The dataset contains " + dataset.getPartialSuccessCommits().size() + " commits that for which the file presence conditions are missing.");
        Logger.info("The dataset contains " + evolutionSteps.size() + " usable pairs of commits.");

        /// Organize all evolution steps into a history for the clone-and-own project.
        final VariabilityHistory history = dataset.getVariabilityHistory(new LongestNonOverlappingSequences());
        /// This yields a list of continuous sub-histories.
        /// The history is divided into sub-histories because for some commits in the SPL, the commit extraction might have failed.
        /// If the extraction fails for a commit c, then we have to exclude c from the variant generation.
        /// This cuts the evolution history into two pieces.
        /// Thus, we divide the history into sub-histories at each failed commit.
        final NonEmptyList<NonEmptyList<SPLCommit>> sequencesInHistory = history.commitSequences();
        Logger.info("The dataset contains " + sequencesInHistory.size() + " sequences.");
        for (int i = 0; i < sequencesInHistory.size(); i++) {
            Logger.info("Sequence " + i + " has " + sequencesInHistory.get(i).size() + " commits.");
        }
        Logger.info("");

        /// Now lets use the variant generator to generate variants.
        /// First, create a sampler that determines which variants to generate at each evolution step.
        Sampler variantsSampler;
        {
            /// Either random sample the set of variants at each evolution step,
            final int numberOfVariantsToGenerate = 42;
            variantsSampler = FeatureIDESampler.CreateRandomSampler(numberOfVariantsToGenerate);
        }
        {
            /// Or use a predefined set of variants for each evolution step.
            final Sample variantsToGenerate = new Sample(List.of(
                    new Variant("Bernard", new SimpleConfiguration(List.of(
                            /// Features selected in variant Bernhard.
                            "A", "B", "D", "E", "N", "R"
                    ))),
                    new Variant("Bianca", new SimpleConfiguration(List.of(
                            /// Features selected in variant Bianca.
                            "A", "B", "C", "I", "N"
                    )))
            ));
            variantsSampler = new ConstSampler(variantsToGenerate);
        }

        /// For the entire history
        for (final NonEmptyList<SPLCommit> subhistory : history.commitSequences()) {
            for (final SPLCommit splCommit : subhistory) {
                /// The spl commit holds the ground truth data of the SPL at the given commit:
                final Lazy<Optional<IFeatureModel>> loadFeatureModel = splCommit.featureModel();
                final Lazy<Optional<Artefact>> loadPresenceConditions = splCommit.presenceConditions();

                /// To generate the variants, we have to load the presence conditions from the ground truth dataset.
                /// Invoking run does exactly that.
                /// We use Lazy, to defer the loading of data until it is required such that
                /// no unwanted data is loaded.
                /// As the extraction of presence condition might have failed, we get an Optional<Artefact>.
                /// Let's assume the extraction succeeded by just invoking orElseThrow here.
                final Artefact pcs = loadPresenceConditions.run().orElseThrow();
                final IFeatureModel featureModel = loadFeatureModel.run().orElseThrow();

                /// Let's sample the set of variants we want to generate for the feature model at this commit.
                /// In case the variantsSampler is actually a ConstSampler, it will ignore the feature model
                /// and will just always return the same set of variants to generate.
                final Sample variants = variantsSampler.sample(featureModel);

                /// Optionally, we might want to filter which files of a variant to generate.
                /// For example, a study could be interested only in generating the changed files in a commit.
                /// In our case, let's just generate all variants.
                /// Moreover, VariantGenerationOptions allow to configure some parameters for the variant generation.
                //Here, we just instruct the generation to exit in case an error happens but we could for example also instruct it to ignore errors and proceed.
                final ArtefactFilter<SourceCodeFile> artefactFilter = ArtefactFilter.KeepAll();
                final VariantGenerationOptions generationOptions = VariantGenerationOptions.ExitOnError(artefactFilter);

                for (final Variant variant : variants) {
                    /// Let's put the variant into our target directory but indexed by commit hash and its name.
                    final CaseSensitivePath variantDir = variantsGenerationDir.resolve(splCommit.id(), variant.getName());

                    /// We can now generate the variant.
                    /// As a result, we get either the ground truth for the generated variant,
                    /// or an exception telling us that something went wrong.
                    final Result<GroundTruth, Exception> result = pcs.generateVariant(variant, splRepositoryPath, variantDir, generationOptions);

                    if (result.isSuccess()) {
                        /// The ground truth of a variant contains:
                        final GroundTruth groundTruth = result.getSuccess();/// 1. the presence conditions.
                        final Artefact presenceConditionsOfVariant = groundTruth.variant();
                        /// 2. a map that stores matchings of blocks for each source code file
                        final Map<CaseSensitivePath, AnnotationGroundTruth> fileMatches = groundTruth.fileMatches();

                        /// We can also export the ground truth PCs of the variant.
                        Resources.Instance().write(Artefact.class, presenceConditionsOfVariant, variantDir.resolve("pcs.variant.csv").path());
                    } else {
                        Logger.error(
                                "Error upon generation of variant "
                                        + variant.getName()
                                        + " at SPL commit " + splCommit.id() + "!",
                                result.getFailure());
                    }
                }
            }
        }
    }
}
