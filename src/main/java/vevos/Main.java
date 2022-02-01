package vevos;

import vevos.feature.sampling.ConstSampler;
import vevos.feature.sampling.FeatureIDESampler;
import vevos.functjonal.Functjonal;
import vevos.functjonal.Lazy;
import vevos.functjonal.Unit;
import vevos.functjonal.category.MonadTransformer;
import vevos.functjonal.list.NonEmptyList;
import vevos.io.Resources;
import vevos.io.data.VariabilityDatasetLoader;
import vevos.repository.AbstractSPLRepository;
import vevos.util.Logger;
import vevos.variability.EvolutionStep;
import vevos.variability.SPLCommit;
import vevos.variability.VariabilityDataset;
import vevos.variability.VariabilityHistory;
import vevos.variability.sequenceextraction.LongestNonOverlappingSequences;
import vevos.variants.VariantsRepository;
import vevos.variants.VariantsRevision;
import vevos.variants.sampling.SampleOnceAtBeginStrategy;
import vevos.variants.sampling.SamplingStrategy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public class Main {
    private static final File PROPERTIES_FILE = new File("src/main/resources/user.properties");
    private static final String VARIABILITY_DATASET = "variability_dataset";
    private static final String SPL_REPO = "spl_repo";
    private static final String VARIANTS_REPO = "variants_repo";

    public static void main(final String[] args) throws IOException, Resources.ResourceIOException {
        VEVOS.Initialize();

        // Debug variability repo
        final Properties properties = new Properties();
        try (final FileInputStream inputStream = new FileInputStream(PROPERTIES_FILE)) {
            properties.load(inputStream);
        } catch (final IOException e) {
            Logger.error("Failed to open properties file: ", e);
            return;
        }
        /*
        Directory to which dataset was downloaded to
         */
        final Path variabilityDatasetDir = Paths.get(properties.getProperty(VARIABILITY_DATASET));

        // Check directory where the variability dataset is located
        if (variabilityDatasetDir.toFile().exists() && Files.list(variabilityDatasetDir).count() > 0) {
            Logger.debug("Variability dataset found under " + variabilityDatasetDir);
        } else {
            Logger.error("Was not able to find directory with variability dataset under " + variabilityDatasetDir);
        }

        // Directory to which https://github.com/torvalds/linux was cloned to
        final File splRepoDir = new File(properties.getProperty(SPL_REPO));

        Logger.info("variabilityDatasetDir: " + variabilityDatasetDir);
        Logger.info("splRepoDir: " + splRepoDir);
        final VariabilityDataset variabilityDataset;

        final VariabilityDatasetLoader datasetLoader = new VariabilityDatasetLoader();
        assert datasetLoader.canLoad(variabilityDatasetDir);
        variabilityDataset = datasetLoader.load(variabilityDatasetDir).getSuccess();
        final Set<EvolutionStep<SPLCommit>> evolutionSteps = variabilityDataset.getEvolutionSteps();
        Logger.info("The dataset contains " + variabilityDataset.getSuccessCommits().size() + " commits for which the variability extraction succeeded.");
        Logger.info("The dataset contains " + variabilityDataset.getErrorCommits().size() + " commits for which the variability extraction failed.");
        Logger.info("The dataset contains " + variabilityDataset.getPartialSuccessCommits().size() + " commits that for which the file presence conditions are missing.");
        Logger.info("The dataset contains " + evolutionSteps.size() + " usable pairs.");
        for (final EvolutionStep<SPLCommit> pair : evolutionSteps) {
            Logger.debug("<<CHILD> " + pair.child().id() + "> -- <<PARENT> " + pair.parent().id() + ">");
            Logger.debug("<<CHILD> " + pair.child().id() + "> -- <<SPL_COMMIT> " + pair.child().id() + ">");
            Logger.debug("<<PARENT> " + pair.parent().id() + "> -- <<SPL_COMMIT> " + pair.parent().id() + ">");
            Logger.debug("");
        }
        final VariabilityHistory history = variabilityDataset.getVariabilityHistory(new LongestNonOverlappingSequences());
        final NonEmptyList<NonEmptyList<SPLCommit>> sequencesInHistory = history.commitSequences();
        Logger.info("The dataset contains " + sequencesInHistory.size() + " sequences.");
        for (int i = 0; i < sequencesInHistory.size(); i++) {
            Logger.info("Sequence " + i + " has " + sequencesInHistory.get(i).size() + " commits.");
        }
        Logger.info("");

        // How to use variant generator
        {
            // Setup
            final AbstractSPLRepository splRepository = null;
            final SamplingStrategy samplingForBusybox = new SampleOnceAtBeginStrategy(
                    FeatureIDESampler.CreateRandomSampler(5)
            );
            final SamplingStrategy samplingForLinux = new SampleOnceAtBeginStrategy(
                            Resources.Instance().load(ConstSampler.class, Path.of("linuxConfigs.txt"))
            );
            final VariantsRepository variantsRepo = new VariantsRepository(
                    Path.of(properties.getProperty(VARIANTS_REPO)),
                    splRepository,
                    history.toBlueprints(samplingForBusybox)
                    );

            // Let's generate revisions for all variability commits here ...
            final Optional<VariantsRevision> firstRevisionToGenerate = variantsRepo.getStartRevision();

            // First possible way is to just run all revisions at once.
            {
                // This lazy holds the computation that will run everything.
                final Lazy<Unit> genAll = Functjonal.match(
                        firstRevisionToGenerate,
                        VariantsRevision::evolveAll, // If there is a first revision to generate, then generate all subsequent revision.
                        () -> Lazy.pure(Unit.Instance())); // If there was nothing to generate, return an empty computation.
                // Now run the generation process.
                // Only from this point on, we will see the program interact with the file system and git.
                genAll.run();
            }

            // Alternatively, we can also generate just a few steps if we like.
            {
                // First, let's build the necessary computations.
                final Lazy<Optional<VariantsRevision>> revision0 = Lazy.pure(firstRevisionToGenerate);
                final Lazy<Optional<VariantsRevision>> genRevision0 = MonadTransformer.bind(revision0, VariantsRevision::evolve);
                final Lazy<Optional<VariantsRevision>> genRevision1 = MonadTransformer.bind(genRevision0, VariantsRevision::evolve);
                final Lazy<Optional<VariantsRevision>> genRevision2 = MonadTransformer.bind(genRevision1, VariantsRevision::evolve);

                // Second, run them! Only from this point on, we will see the program interact with the file system and git.
                genRevision0.run(); // This would generate revision0.
                genRevision1.run(); // This would generate revision0 and then revision1.
                // This would generate revision0 and then revision1 and then revision2.
                // This returns a handle for revision3 which is not yet generated.
                final Optional<VariantsRevision> revision3 = genRevision2.run();
                // Because Lazy caches intermediate results, revision0 and revision1 have only been generated exactly once.
            }
        }
    }
}