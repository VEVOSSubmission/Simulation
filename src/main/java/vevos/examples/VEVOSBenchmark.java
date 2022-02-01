package vevos.examples;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import vevos.VEVOS;
import vevos.feature.Variant;
import vevos.feature.sampling.FeatureIDESampler;
import vevos.feature.sampling.Sample;
import vevos.feature.sampling.Sampler;
import vevos.functjonal.Cast;
import vevos.functjonal.Lazy;
import vevos.functjonal.Result;
import vevos.io.Resources;
import vevos.io.TextIO;
import vevos.repository.BusyboxRepository;
import vevos.repository.SPLRepository;
import vevos.util.Clock;
import vevos.util.Logger;
import vevos.util.io.CaseSensitivePath;
import vevos.variability.EvolutionStep;
import vevos.variability.SPLCommit;
import vevos.variability.VariabilityDataset;
import vevos.variability.pc.Artefact;
import vevos.variability.pc.SourceCodeFile;
import vevos.variability.pc.groundtruth.GroundTruth;
import vevos.variability.pc.options.ArtefactFilter;
import vevos.variability.pc.options.VariantGenerationOptions;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class VEVOSBenchmark {
    private record Repo(
            CaseSensitivePath splRepositoryPath,
            CaseSensitivePath groundTruthDatasetPath,
            CaseSensitivePath variantsGenerationDir,
            Function<CaseSensitivePath, SPLRepository> createRepo,
            Consumer<SPLRepository> cleanup) {}

    /// TODO: Specify your paths here.
    private static final CaseSensitivePath SPL_REPOS_DIR = CaseSensitivePath.of("path/to/SPL/repos");
    private static final CaseSensitivePath DATASETS_DIR = CaseSensitivePath.of("path/to/datasets/");
    private static final CaseSensitivePath VARIANT_GENERATION_DIR = CaseSensitivePath.of("path/to/datasets/");

    private static final Repo LINUX = new Repo(
            SPL_REPOS_DIR.resolve("linux"),
            DATASETS_DIR.resolve("linux"),
            VARIANT_GENERATION_DIR.resolve("linux"),
            path -> new SPLRepository(path.path()),
            s -> {}
    );
    private static final Repo BUSYBOX = new Repo(
            SPL_REPOS_DIR.resolve("busybox"),
            DATASETS_DIR.resolve("busybox"),
            VARIANT_GENERATION_DIR.resolve("busybox"),
            path -> new BusyboxRepository(path.path()),
            s -> {
                BusyboxRepository b = Cast.unchecked(s);
                try {
                    b.postprocess();
                } catch (Exception e) {
                    Logger.error("Error in Busybox cleanup", e);
                }
            }
    );
    private final static int NUMBER_OF_VARIANTS_TO_GENERATE = 5;
    private final static int MAX_COMMITS_TO_ANALYZE = 20;

    private static String logTime(final String task, final double seconds) {
        final String msg = task + " took " + seconds + "s.";
        Logger.info(msg);
        return msg;
    }

    private static void resultEntry(final StringBuilder builder, final String entry) {
        builder.append(entry).append("\r\n");
    }

    public static void benchmark(final Repo repo) throws Exception {
        VEVOS.Initialize();
//        Logger.setLogLevel(LogLevel.INFO);

        final StringBuilder timeData = new StringBuilder();

        final SPLRepository splRepo = repo.createRepo().apply(repo.splRepositoryPath);

        final Clock clock = new Clock();
        clock.start();
        final VariabilityDataset dataset =
                Resources.Instance().load(VariabilityDataset.class, repo.groundTruthDatasetPath().path());
        final double timeDatasetLoading = clock.getPassedSeconds();
        resultEntry(timeData, logTime("Loading dataset", timeDatasetLoading));

        clock.start();
        final Set<EvolutionStep<SPLCommit>> evolutionSteps = dataset.getEvolutionSteps();
        final double timeEvolutionStepCreation = clock.getPassedSeconds();
        resultEntry(timeData, logTime("Creating evolution steps", timeEvolutionStepCreation));

        Logger.info("The dataset contains " + dataset.getSuccessCommits().size() + " commits for which the variability extraction succeeded.");
        Logger.info("The dataset contains " + dataset.getErrorCommits().size() + " commits for which the variability extraction failed.");
        Logger.info("The dataset contains " + dataset.getPartialSuccessCommits().size() + " commits that for which the file presence conditions are missing.");
        Logger.info("The dataset contains " + evolutionSteps.size() + " usable pairs of commits.");

        final List<SPLCommit> subhistory = dataset.getSuccessCommits();

        final Sampler variantsSampler =
                FeatureIDESampler.CreateRandomSampler(NUMBER_OF_VARIANTS_TO_GENERATE);
//                new FeatureIDESampler(NUMBER_OF_VARIANTS_TO_GENERATE, cnf -> new VEVOSRandomSampling(cnf, NUMBER_OF_VARIANTS_TO_GENERATE));
        double timeLoadPCsAverage = 0;
        double timeSampleAverage = 0;
        double timeGenVariantsAverage = 0;

        int analyzedCommits = 0;
        for (final SPLCommit splCommit : subhistory) {
            Logger.info("-- Processing commit " + splCommit.id() + " --");
            splRepo.checkoutCommit(splCommit);

            clock.start();
            final Lazy<Optional<IFeatureModel>> loadFeatureModel = splCommit.featureModel();
            final Lazy<Optional<Artefact>> loadPresenceConditions = splCommit.presenceConditions();

            if (loadPresenceConditions.run().isEmpty()) {
                Logger.info("has no PCs");
                continue;
            }
            if (loadFeatureModel.run().isEmpty()) {
                Logger.info("has no FM");
                continue;
            }

            final Artefact pcs = loadPresenceConditions.run().orElseThrow();
            final IFeatureModel featureModel = loadFeatureModel.run().orElseThrow();
            final double timeLoadPCs = clock.getPassedSeconds();
            timeLoadPCsAverage += timeLoadPCs;
            logTime("Loading PCs and FM", timeLoadPCs);

//            Logger.info("#features = " + featureModel.getFeatures().size());
//            Logger.info("Feature model is valid = " + FeatureModelUtils.isValid(featureModel));

            clock.start();
            final Sample variants = variantsSampler.sample(featureModel);
            final double timeSample = clock.getPassedSeconds();
            timeSampleAverage += timeSample;
            logTime("Sampling " + variants.size() + " variants", timeSample);

            final ArtefactFilter<SourceCodeFile> artefactFilter = ArtefactFilter.KeepAll();
            final VariantGenerationOptions generationOptions = VariantGenerationOptions.ExitOnError(artefactFilter);

            clock.start();
            for (final Variant variant : variants) {
                final CaseSensitivePath variantDir = repo.variantsGenerationDir().resolve(splCommit.id(), "_", analyzedCommits + "" , "_", variant.getName());
                final Result<GroundTruth, Exception> result = pcs.generateVariant(variant, repo.splRepositoryPath(), variantDir, generationOptions);

//                final FeatureIDEConfiguration config = (FeatureIDEConfiguration) variant.getConfiguration();
//                Logger.info("Generating variant with configuration:\r\n" + config.toAssignment());

                if (result.isSuccess()) {
                    final GroundTruth groundTruth = result.getSuccess();
                    final Artefact presenceConditionsOfVariant = groundTruth.variant();
                    Resources.Instance().write(Artefact.class, presenceConditionsOfVariant, variantDir.resolve("pcs.variant.csv").path());
                } else {
                    throw result.getFailure();
                }
            }
            final double timeGenVariants = clock.getPassedSeconds();
            timeGenVariantsAverage += timeGenVariants;
            logTime("Generating " + variants.size() + " variants", timeGenVariants);

            repo.cleanup.accept(splRepo);

            ++analyzedCommits;
            if (analyzedCommits > MAX_COMMITS_TO_ANALYZE) {
                break;
            }
        }

        timeLoadPCsAverage = timeLoadPCsAverage / ((double)analyzedCommits);
        timeSampleAverage = timeSampleAverage / ((double)analyzedCommits);
        timeGenVariantsAverage = timeGenVariantsAverage / (analyzedCommits * NUMBER_OF_VARIANTS_TO_GENERATE);

        resultEntry(timeData, logTime("Loading PCs Average", timeLoadPCsAverage));
        resultEntry(timeData, logTime("Sampling " + NUMBER_OF_VARIANTS_TO_GENERATE + " variants Average", timeSampleAverage));
        resultEntry(timeData, logTime("Generating " + NUMBER_OF_VARIANTS_TO_GENERATE + " variants Average", timeGenVariantsAverage));
        resultEntry(timeData, "Commits: " + analyzedCommits);

        final String result = timeData.toString();
        Logger.info("Done\r\n" + result);
        TextIO.write(repo.variantsGenerationDir.resolve("benchmarkdata.txt").path(), result);
    }

    public static void benchmarkLinux() throws Exception {
        benchmark(LINUX);
    }

    public static void benchmarkBusybox() throws Exception {
        benchmark(BUSYBOX);
    }

    public static void main(final String[] args) throws Exception {
        if (args.length < 1) {
            Logger.error("Expected exactly one argument that either is \"busybox\" or \"linux\"!");
        }

        final String repoName = args[0];
        if ("linux".equalsIgnoreCase(repoName)) {
            benchmarkLinux();
        } else if ("busybox".equalsIgnoreCase(repoName)) {
            benchmarkBusybox();
        } else {
            Logger.error("Unknown repository " + repoName + "! Expected \"busybox\" or \"linux\"!");
        }
    }
}
