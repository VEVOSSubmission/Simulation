package vevos;

import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import org.junit.BeforeClass;
import org.junit.Test;
import org.prop4j.And;
import org.prop4j.Literal;
import org.prop4j.Node;
import org.prop4j.Or;
import vevos.feature.Variant;
import vevos.feature.config.FeatureIDEConfiguration;
import vevos.feature.config.IConfiguration;
import vevos.feature.config.SayYesToAllConfiguration;
import vevos.feature.sampling.FeatureIDESampler;
import vevos.feature.sampling.Sampler;
import vevos.functjonal.Result;
import vevos.io.ResourceLoader;
import vevos.io.Resources;
import vevos.io.TextIO;
import vevos.io.kernelhaven.KernelHavenSPLPCIO;
import vevos.sat.SAT;
import vevos.util.Logger;
import vevos.util.fide.FeatureModelUtils;
import vevos.util.fide.bugfix.FixTrueFalse;
import vevos.util.io.CaseSensitivePath;
import vevos.util.io.PathUtils;
import vevos.variability.pc.*;
import vevos.variability.pc.options.ArtefactFilter;
import vevos.variability.pc.options.VariantGenerationOptions;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests for presence condition loading and variant generation.
 */
public class VariantGenerationTest {
    private static class TestCaseData {
        // init in constructor
        CaseSensitivePath pcs, splDir, variantsDir;
        IFeatureModel features;
        Result<Artefact, ?> traces;

        public TestCaseData(final CaseSensitivePath pcs) {
            this.pcs = pcs;
            assert splPCLoader.canLoad(pcs.path());
            traces = splPCLoader.load(pcs.path());
        }

        public TestCaseData(final CaseSensitivePath pcs, final CaseSensitivePath splDir, final CaseSensitivePath variantsDir) {
            this(pcs);
            this.splDir = splDir;
            this.variantsDir = variantsDir;
        }

        public TestCaseData(final CaseSensitivePath pcs, final CaseSensitivePath splDir, final CaseSensitivePath variantsDir, final IFeatureModel fm) {
            this(pcs, splDir, variantsDir);
            this.features = fm;
        }

        public boolean generate(final List<Variant> variantsToTest, final boolean writeConfigs) {
            traces.assertSuccess();
            PathUtils.deleteDirectory(variantsDir.path()).assertSuccess();
            final Artefact traceToTest = traces.getSuccess();

//            System.out.println("=== [SPL] ===");
//            System.out.println(traceToTest.prettyPrint());

            for (final Variant v : variantsToTest) {
                traceToTest
                        .generateVariant(v, splDir,
                                variantsDir.resolve(v.getName()),
                                VariantGenerationOptions.ExitOnErrorButAllowNonExistentFiles(ArtefactFilter.KeepAll()))
                        // Write ground truth
                        .bind(groundTruth -> Result.Try(() -> Resources.Instance().write(
                                Artefact.class,
                                groundTruth.variant(),
                                variantsDir.resolve(v.getName()).resolve("ground_truth.variant.csv").path())))
                        // Write configuration
                        .bind(unit -> {
                            if (writeConfigs) {
                                return Result.Try(() -> Resources.Instance().write(
                                        IConfiguration.class,
                                        v.getConfiguration(),
                                        variantsDir.resolve(v.getName()).resolve("configuration.xml").path()));
                            }

                            return Result.Success(unit);
                        })
                        .assertSuccess();
            }

            return true;
        }
    }

    private static final ResourceLoader<Artefact> splPCLoader = new KernelHavenSPLPCIO();

    private static final CaseSensitivePath resDir = CaseSensitivePath.of("src", "test", "resources", "variantgeneration");
    private static final CaseSensitivePath genDir = resDir.resolve("gen");
    private static final CaseSensitivePath datasetsDir = CaseSensitivePath.of("..", "variantevolution_datasets");

    private static TestCaseData pcTest1;
    private static TestCaseData illPcTest;
    private static TestCaseData linuxSample;
    private static TestCaseData linux;

    @BeforeClass
    public static void setupStatic() {
        VEVOS.Initialize();

        pcTest1 = new TestCaseData(
                resDir.resolve("KernelHavenPCs.spl.csv"),
                resDir.resolve("tinySPLRepo"),
                genDir.resolve("tinySPLRepo"),
                FeatureModelUtils.FromOptionalFeatures("A", "B", "C", "D", "E")
        );
        illPcTest = new TestCaseData(
                resDir.resolve("KernelHavenPCs_illformed.spl.csv")
        );
        linuxSample = new TestCaseData(
                resDir.resolve("LinuxPCS_Simple.spl.csv"),
                datasetsDir.resolve("linux"),
                genDir.resolve("linux-sample")
        );
        linux = new TestCaseData(
                datasetsDir.resolve("LinuxVariabilityData", "code-variability.spl.csv"),
                datasetsDir.resolve("linux"),
                genDir.resolve("linux")
        );
    }

    @Test
    public void loadPCTest1FileCorrectly() {
        final TestCaseData dataToCheck = pcTest1;
        dataToCheck.traces.assertSuccess();

        final Artefact expectedTrace;
        { // Build the expected result by hand.
            final SourceCodeFile foofoo = new SourceCodeFile(FixTrueFalse.True, CaseSensitivePath.of("src", "FooFoo.cpp"));
            {
                final LineBasedAnnotation a = new LineBasedAnnotation(new Literal("A"), 4, 11, AnnotationStyle.Internal);
                a.addTrace(new LineBasedAnnotation(new Literal("B"), 6, 8, AnnotationStyle.Internal));
                final LineBasedAnnotation tru = new LineBasedAnnotation(FixTrueFalse.True, 1, 21, AnnotationStyle.External);
                tru.addTrace(a);
                tru.addTrace(new LineBasedAnnotation(new Or(new And(new Literal("C"), new Literal("D")), new Literal("E")), 16, 18, AnnotationStyle.Internal));
                foofoo.addTrace(tru);
            }

            final SourceCodeFile bar = new SourceCodeFile(new Literal("A"), CaseSensitivePath.of("src", "foo", "bar.cpp"));
            {
                // This is a challenging case for the importer.
                // We can not differentiate if a block starting at line 1 is an external annotation by Kernelhaven or an actual macro.
                bar.addTrace(new LineBasedAnnotation(FixTrueFalse.False, 1, 4, AnnotationStyle.Internal));
            }

            expectedTrace = new SyntheticArtefactTreeNode<>(Arrays.asList(foofoo, bar));
        }

        if (!expectedTrace.equals(dataToCheck.traces.getSuccess())) {
            Logger.error("Loaded PCs:\n"
                    + dataToCheck.traces.getSuccess().prettyPrint()
                    + "\nis different from expected result:\n"
                    + expectedTrace.prettyPrint());
            assert false;
        }
    }

    @Test
    public void crashOnIllFormedFile() {
        assert illPcTest.traces.isFailure();
        assert illPcTest.traces.getFailure() instanceof IllegalFeatureTraceSpecification;
    }

    private static void readFromAndDirectlyWriteTo(final CaseSensitivePath inputPath, final CaseSensitivePath outputPath) throws Resources.ResourceIOException {
        // load pcs
        Logger.info("Reading " + inputPath);
        final Artefact pcs = Resources.Instance().load(Artefact.class, inputPath.path());

        // write pcs unmodified
        Logger.info("Writing " + outputPath);
        Resources.Instance().write(Artefact.class, pcs, outputPath.path());
    }

    @Test
    public void idempotentReadWriteOfPCFiles() throws Resources.ResourceIOException, IOException {
        final List<TestCaseData> testCases = Arrays.asList(pcTest1, linuxSample);
        for (final TestCaseData testCase : testCases) {
            final CaseSensitivePath sourcePath = testCase.pcs;
            final CaseSensitivePath intermediatePath = genDir.resolve(sourcePath.path().getFileName());
            final CaseSensitivePath outputPath = genDir.resolve(sourcePath.path().getFileName() + ".idempotent.spl.csv");

            PathUtils.deleteDirectory(intermediatePath.path());
            PathUtils.deleteDirectory(outputPath.path());

            readFromAndDirectlyWriteTo(sourcePath, intermediatePath);
            readFromAndDirectlyWriteTo(intermediatePath, outputPath);

            // assert that text at intermediatePath is the same as at outputPath
            assert TextIO.readAsString(intermediatePath.path()).equals(TextIO.readAsString(outputPath.path()));
        }
    }

    @Test
    public void testPCQuery() {
        assert pcTest1.traces.isSuccess();
        final Result<Node, Exception> result =
                pcTest1.traces.getSuccess().getPresenceConditionOf(CaseSensitivePath.of("src", "FooFoo.cpp"), 7);
        Logger.log(result);
        assert result.isSuccess();
        assert SAT.equivalent(result.getSuccess(), new And(new Literal("A"), new Literal("B")));
    }

    @Test
    public void testGeneration() {
        final FeatureModelFormula fmf = new FeatureModelFormula(pcTest1.features);
        assert pcTest1.generate(Arrays.asList(
                new Variant("justA", new FeatureIDEConfiguration(fmf, Collections.singletonList("A"))),
                new Variant("justB", new FeatureIDEConfiguration(fmf, Collections.singletonList("B"))),
                new Variant("all", new FeatureIDEConfiguration(fmf, Arrays.asList("A", "B", "C", "D", "E")))
                ),
                true);
    }

    @Test
    public void testGenerationWithCustomSample() {
        final Sampler sampler = FeatureIDESampler.CreateRandomSampler(5);
        assert pcTest1.generate(sampler.sample(pcTest1.features).variants(), true);
    }

    @Test
    public void testLinuxSampleGeneration() {
        assert linuxSample.generate(
                List.of(new Variant("all", new SayYesToAllConfiguration())),
                false);
    }

//    @Test
    public void testLinuxGeneration() {
        assert linux.generate(
                List.of(new Variant("all", new SayYesToAllConfiguration())),
                false);
    }

    @Test
    public void caseSensitivePathTest() {
        final CaseSensitivePath sharedRoot = CaseSensitivePath.of("net", "netfilter");
        final CaseSensitivePath a = sharedRoot.resolve("xt_RATEEST.c");
        final CaseSensitivePath b = sharedRoot.resolve("xt_rateest.c");
        assert !a.equals(b);
    }
}
