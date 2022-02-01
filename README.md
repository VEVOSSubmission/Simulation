# VEVOS - Variant Simulation

VEVOS is a tool suite for the simulation of the evolution of clone-and-own projects and consists of two main components: The ground truth extraction, called VEVOS/Extraction and the variant simulation called VEVOS/Simulation.

This repository contains VEVOS/Simulation and thus the second part of the replication package for the paper _Simulating the Evolution of Clone-and-Own Projects with VEVOS_ submitted to the International Conference on Evaluation and Assessment in Software Engineering (EASE) 2022.
VEVOS/Simulation is a java library for generating variants with ground truth from an input software product line and dataset extracted with VEVOS/Extraction.

![Simulation Overview](docs/generation.png)

## Example Usage and Main Features

VEVOS/Simulation is supposed to be used by your research prototype on clone-and-own or variability in software systems.
In the following we give a step by step example in how the library can be used to 
  - parse the ground truth dataset extracted by VEVOS/Extraction,
  - traverse the datasets' evolution history,
  - sample variants randomly, or use a predefined set of variants for simulation,
  - generate variants for each step in the evolution history,
  - obtain the ground truth of generated variants.

The example's source code can also be found in [GenerationExample.java](src/main/java/vevos/examples/GenerationExample.java).
A similar and executable version of this example can be found in [VEVOSBenchmark.java](src/main/java/vevos/examples/VEVOSBenchmark.java), which gathers some rudimentary data on runtime performance for Linux and Busybox variant simulation. 

At the very begin of your program, you have to initialize the library:
```java
VEVOS.Initialize();
```
This initializes the libraries logging and binding to FeatureIDE.
You may also set a log level for the library here via `Logger::setLogLevel`.

We can then start by specifying the necessary paths to (1) the git repository of the input software product line, (2) the directory of the extracted ground truth dataset, (3) and a directory to which we want to generate variants. (We use case sensitive paths to also allow the generation of Linux variants under Windows).
```java
final CaseSensitivePath splRepositoryPath = CaseSensitivePath.of("path", "to", "SPL", "git", "repository");
final CaseSensitivePath groundTruthDatasetPath = CaseSensitivePath.of("path", "to", "datasets");
final CaseSensitivePath variantsGenerationDir = CaseSensitivePath.of("directory", "to", "put", "generated", "variants");
```
We can now load the extracted ground truth dataset:
```java
final VariabilityDataset dataset = Resources.Instance()
        .load(VariabilityDataset.class, groundTruthDatasetPath.path());
```
For loading data, VEVOS/Simulation uses a central service for resource loading and writing called `Resources`.
`Resources` provides a unified interface for reading and writing data of any type.
Above, we use the resources to load a `VariabilityDataset` from the given path.
Internally, `Resources` stores `ResourceLoader` and `ResourceWriter` objects that perform the file system interaction.
This central interface allows users to add loaders and writers for further or custom data types as well as to replace existing loaders.
Currently, `Resources` support IO of CSV files, feature models (KernelHaven `json`, and FeatureIDE `dimacs`, `xml`), variant configurations (FeatureIDE `xml`), and presence conditions of product lines and variants.

From the loaded `dataset`, we can obtain the available evolution steps.
An evolution step describes a commit-sized change to the input software product line, and is defined by a (child) commit performing a change to a previous (parent) commit.
Note that the evolution steps are not ordered because commits in the input product-line repository might not have been ordered as the commits might have been extracted from different branches.
If we require an order, we can request a continuous history of evolution steps instead of an unordered set.
Therefore, a `SequenceExtractor` is used to determine how the successfully extracted commits should be ordered.
In this example, we use the `LongestNonOverlappingSequences` extractor to sort the commits into one single continuous history.
Nevertheless, merge commits and error commits (where VEVOS/Extraction failed) are excluded from the history and thus, the returned list of commits has gaps.
Because of these gaps, we obtain a list of sub-histories, where each sub-history is continuous but sub-histories are divided by merge and error commits.
```java
final Set<EvolutionStep<SPLCommit>> evolutionSteps = dataset.getEvolutionSteps();

/// Organize all evolution steps into a history for the clone-and-own project.
final VariabilityHistory history = dataset.getVariabilityHistory(new LongestNonOverlappingSequences());
/// This yields a list of continuous sub-histories.
/// The history is divided into sub-histories because for some commits in the SPL, the commit extraction might have failed.
/// If the extraction fails for a commit c, then we have to exclude c from the variant generation.
/// This cuts the evolution history into two pieces.
/// Thus, we divide the history into sub-histories at each failed commit.
final NonEmptyList<NonEmptyList<SPLCommit>> sequencesInHistory = history.commitSequences();
```
A sequence extraction might fail, for example if all commits are unrelated and no continuous history could be derived.
In case this happens, as an alternative, you can either iterate over all `evolutionSteps` or iterate over all success commits in isolation:
```java
final List<SPLCommit> successCommits = dataset.getSuccessCommits();
```
In particular, the `VariabilityDataset` provides:
- _success commits_ for which the extraction of feature mappings and feature model succeeded,
- _partial success_ commits for which part of the extraction failed; Usually, a partial success commit has feature mappings but no file presence condition and no feature model,
- _error commits_ for which the extraction failed.

To generate variants, we have to specify which variants should be generated.
Therefore, a `Sampler` is used that returns the set of variants to use for a certain feature model.
The set of desired variants is encapsulated in samplers because the set of valid variants of the input product line may change when the feature model changes over time (i.e., commits).
Thus, the sampler can be invoked during each step of the variant simulation.
Apart from the possibility of introducing custom samplers, VEVOS/Simulation comes with two built-in ways for sampling:
Random configuration sampling using the FeatureIDE library, and constant sampling.

[Random sampling](src/main/java/vevos/feature/sampling/FeatureIDESampler.java) returns a random set of valid configurations from a given feature model:
```java
/// Either use random sampling, ...
final int numberOfVariantsToGenerate = 42;
Sampler variantsSampler = FeatureIDESampler.CreateRandomSampler(numberOfVariantsToGenerate);
```
[Constant sampling](src/main/java/vevos/feature/sampling/ConstSampler.java) uses a pre-defined set of variants and ignores the feature model (it can easily be extended though to for example crash if a configuration violates a feature model at any commit):
```java
/// ... or use a predefined set of variants.
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

Sampler variantsSampler = new ConstSampler(variantsToGenerate);
```
For the generation of variants we have to be able to access the repository of the input software product line to retrieve its source code.
We reference the repository with an instance of `SPLRepository`:
```java
/// in general:
final SPLRepository splRepository = new SPLRepository(splRepositoryPath.path());
/// for Busybox:
final SPLRepository splRepository = new BusyboxRepository(splRepositoryPath.path());
```
Note that Busybox has a special subclass called `BusyboxRepository` that performs some necessary pre- and postprocessing on the product line's source code.

We are now ready to traverse the evolution history to generate variants:
```java
for (final NonEmptyList<SPLCommit> subhistory : history.commitSequences()) {
    for (final SPLCommit splCommit : subhistory) {
        final Lazy<Optional<IFeatureModel>> loadFeatureModel = splCommit.featureModel();
        final Lazy<Optional<Artefact>> loadPresenceConditions = splCommit.presenceConditions();
```
The history we retrieved earlier is structured into sub-histories. For each sub-history we can get the commits (as objects of type `SPLCommit`) from the input software product line that was analysed by VEVOS/Extraction.
Through an `SPLCommit`, we can access the feature model and the presence condition of the software product line at the respective commit.
However, both types of data are not directly accessible but have to be loaded first.
This is what the `Lazy` type is used for: It defers the loading of data until it is actually required.
This makes accessing the possibly huge (93GB for 13k commits of Linux, yikes!) ground truth dataset faster and memory-friendly as only required data is loaded into memory.
We can start the loading process by invoking `Lazy::run` that returns a value of the loaded type (i.e., `Optional<IFeatureModel>` or `Optional<Artefact>`).
A `Lazy` caches its loaded value, so loading is only performed once: Subsequent calls to `Lazy::run` return the cached value directly.
(Loaded data that is not required anymore can and should be freed by invoking `Lazy::forget`.)
As the extraction of feature model or presence condition might have failed, both types are again wrapped in an `Optional` that contains a value if extraction was successful.
Let's assume the extraction succeeded by just invoking `orElseThrow` here.
(However, if also partial success commits are considered, one might need a more careful procedure here.)
```java
        final Artefact pcs = loadPresenceConditions.run().orElseThrow();
        final IFeatureModel featureModel = loadFeatureModel.run().orElseThrow();
```
Having the feature model at hand, we can now sample the variants we want to generate for the current `splCommit`.
In case the `variantsSampler` is actually a `ConstSampler` (see above), it will ignore the feature model and will just always return the same set of variants you specified earlier in the `ConstSampler`.
```java
        final Sample variants = variantsSampler.sample(featureModel);
```
Optionally, we might want to filter which files of a variant to generate.
For example, a study on evolution of code in variable software systems could be interested only in generating the changed files of a commit.
In our case, let's just generate the entire code base of each variant.
Moreover, `VariantGenerationOptions` allow to configure some parameters for the variant generation.
Here, we just instruct the generation to exit in case an error happens but we could for example also instruct it to ignore errors and proceed.
```java
        final ArtefactFilter<SourceCodeFile> artefactFilter = ArtefactFilter.KeepAll();
        final VariantGenerationOptions generationOptions = VariantGenerationOptions.ExitOnError(artefactFilter);
```
To generate variants, we have to access the source code of the input software product line at the currently inspected commit.
We thus checkout the current commit in the product line's repository:
```java
        try {
            splRepository.checkoutCommit(splCommit);
        } catch (final GitAPIException | IOException e) {
            Logger.error("Failed to checkout commit " + splCommit.id() + " of " + splRepository.getPath() + "!", e);
            return;
        }
```

Finally, we may indeed generate our variants:
```java
        for (final Variant variant : variants) {
            /// Let's put the variant into our target directory but indexed by commit hash and its name.
            final CaseSensitivePath variantDir = variantsGenerationDir.resolve(splCommit.id(), variant.getName());
            final Result<GroundTruth, Exception> result =
                pcs.generateVariant(variant, splRepositoryPath, variantDir, generationOptions);
```
The generation returns a `Result` that either represents the ground truth for the generated variant, or contains an exception if something went wrong.
In case the generation was successful, we can inspect the `groundTruth` of the variant.
The `groundTruth` consists of
- the presence conditions and feature mappings of the variant (which are different from the presence conditions of the software product line, for example because line numbers shifted),
- and a block matching that for each source code file (key of the map) tells us which blocks of source code in the variant stem from which blocks of source code in the software product line.
We may also export ground truth data to disk for later usage.

(Here it is important to export the ground truth as `.variant.csv` as this suffix is used by our `Resources` to correctly load the ground truth.
In contrast, the suffix is `.spl.csv` for ground truth presence conditions of the input software product line. The major difference here is that some line numbers have to be interpreted differently upon read and write because variants are stripped off their annotations while product lines still have them.)
```java
            if (result.isSuccess()) {
                final GroundTruth groundTruth = result.getSuccess();/// 1. the presence conditions.
                final Artefact presenceConditionsOfVariant = groundTruth.variant();
                /// 2. a map that stores matchings of blocks for each source code file
                final Map<CaseSensitivePath, AnnotationGroundTruth> fileMatches = groundTruth.fileMatches();

                /// We can also export the ground truth PCs of the variant.
                Resources.Instance().write(Artefact.class, presenceConditionsOfVariant, variantDir.resolve("pcs.variant.csv").path());
            }
        }
```
In case we use Busybox as our input product line, we have to clean its repository as a last step before we can proceed to the next `SPLCommit`:
```java
        if (splRepository instanceof BusyboxRepository b) {
            try {
                b.postprocess();
            } catch (final GitAPIException | IOException e) {
                Logger.error("Busybox postprocessing failed, please clean up manually (e.g., git stash, git stash drop) at " + splRepository.getPath(), e);
            }
        }
```
This was round-trip about the major features of VEVOS/Simulation.

## Project Structure

The project is structured into the following packages:
- [`vevos.examples`](src/main/java/vevos/examples) contains the code of our example described above
- [`vevos.feature`](src/main/java/vevos/feature) contains our representation for `Variant`s and their `Configuration`s as well as sampling of configurations and variants
- [`vevos.io`](src/main/java/vevos/io) contains our `Resources` service and default implementations for loading `CSV` files, ground truth, feature models, and configurations
- [`vevos.repository`](src/main/java/vevos/repository) contains classes for representing git repositories and commits
- [`vevos.sat`](src/main/java/vevos/sat) contains an interface for SAT solving (currently only used for annotation simplification, which is deactivated by default)
- [`vevos.util`](src/main/java/vevos/util) is the conventional utils package with helper methods for interfacing with FeatureIDE, name generation, logging, and others.
- [`vevos.variability`](src/main/java/vevos/variability) contains the classes for representing evolution histories and the ground truth dataset.
  The package is divided into:
    - [`vevos.variability.pc`](src/main/java/vevos/variability/pc) contains classes for representing annotations (i.e., presence conditions and feature mappings). We store annotations in `Artefact`s that follow a tree structure similar to the annotations in preprocessor based software product lines.
    - [`vevos.variability.pc.groundtruth`](src/main/java/vevos/variability/pc/groundtruth) contains datatypes for the ground truth of generated variants.
    - [`vevos.variability.pc.options`](src/main/java/vevos/variability/pc/options) contains the options for the variant generation process.
    - [`vevos.variability.pc.visitor`](src/main/java/vevos/variability/pc/visitor) contains an implementation of the visitor pattern for traversing and inspecting `ArtefactTree`s. Some visitors for querying a files or a line's presence condition, as well as a pretty printer can be found in [`vevos.variability.pc.visitor.common`](src/main/java/vevos/variability/pc/visitor/common).
    - [`vevos.variability.sequenceextraction`](src/main/java/vevos/variability/sequenceextraction) contains default implementations for `SequenceExtractor`. These are algorithms for sorting pairs of commits into continuous histories (see example above).

## Setup

VEVOS/Simulation is a Java 16 library and Maven project.
You may include VEVOS/Simulation as a pre-build `jar` file or build it on your own.
The `jar` file can be found in the releases of this repository.
Building VEVOS/Simulation comes with no other requirements other than Maven.
