package vevos.feature.sampling;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.AConfigurationGenerator;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.IConfigurationGenerator;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.RandomConfigurationGenerator;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.job.LongRunningWrapper;
import de.ovgu.featureide.fm.core.job.monitor.NullMonitor;
import vevos.feature.Variant;
import vevos.feature.config.FeatureIDEConfiguration;
import vevos.util.fide.FeatureModelUtils;
import vevos.util.names.NameGenerator;
import vevos.util.names.NumericNameGenerator;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FeatureIDESampler implements Sampler {
    private final int size;
    private final Function<CNF, IConfigurationGenerator> generatorFactory;
    private NameGenerator variantNameGenerator;

    public static FeatureIDESampler CreateRandomSampler(final int size) {
        return CreateRandomSampler(size, FeatureModelUtils.HOUR);
    }

    public static FeatureIDESampler CreateRandomSampler(final int size, final int timeoutInMilliseconds) {
        return new FeatureIDESampler(
                size,
                cnf -> {
                    final AConfigurationGenerator randomSampler =  new RandomConfigurationGenerator(cnf, size);
                    randomSampler.setTimeout(timeoutInMilliseconds);
                    return randomSampler;
                }
        );
    }

    public FeatureIDESampler(final int size, final Function<CNF, IConfigurationGenerator> generatorFactory) {
        this.size = size;
        this.generatorFactory = generatorFactory;
        variantNameGenerator = new NumericNameGenerator("Variant");
    }

    public void setNameGenerator(final NameGenerator variantNameGenerator) {
        this.variantNameGenerator = variantNameGenerator;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public Sample sample(final IFeatureModel model) {
        final FeatureModelFormula featureModelFormula = new FeatureModelFormula(model);
        final CNF cnf = featureModelFormula.getCNF();
        final IConfigurationGenerator generator = generatorFactory.apply(cnf);
        // We could add a monitor here that writes to the log for example.
        // The monitor gets notified about the progress of the generator and can for example be used to update a progress bar.
        // I guess we do not need it.
        final List<LiteralSet> result = LongRunningWrapper.runMethod(generator, new NullMonitor<>());
        final AtomicInteger variantNo = new AtomicInteger();
        return new Sample(result.stream().map(literalSet -> new Variant(
                variantNameGenerator.getNameAtIndex(variantNo.getAndIncrement()),
                new FeatureIDEConfiguration(literalSet, featureModelFormula)
        )).collect(Collectors.toList()));
    }
}
