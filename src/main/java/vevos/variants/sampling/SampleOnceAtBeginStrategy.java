package vevos.variants.sampling;

import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import org.prop4j.Node;
import vevos.feature.Variant;
import vevos.feature.sampling.ConstSampler;
import vevos.feature.sampling.Sample;
import vevos.feature.sampling.Sampler;
import vevos.functjonal.Functjonal;
import vevos.variants.blueprints.VariantsRevisionBlueprint;

import java.util.Optional;

public class SampleOnceAtBeginStrategy implements Sampler, SamplingStrategy {
    private final Sampler sampler;

    public SampleOnceAtBeginStrategy(final Sampler sampler) {
        this.sampler = sampler;
    }

    @Override
    public int size() {
        return sampler.size();
    }

    @Override
    public Sample sample(final IFeatureModel model) {
        return sampler.sample(model);
    }

    @Override
    public Sample sampleForRevision(final Optional<IFeatureModel> model, final VariantsRevisionBlueprint blueprint) {
        return Functjonal.match(blueprint.getPredecessor(),
                // Use the sample we already computed.
                p -> {
                    final Sample previousSample = p.getSample().run();

                    // If we have a feature model, validate that the previous sample is still valid.
                    model.ifPresent(fm -> {
                        final Node featureModelFormula = new FeatureModelFormula(fm).getPropositionalNode();
                        for (final Variant variant : previousSample.variants()) {
                            if (!variant.getConfiguration().satisfies(featureModelFormula)) {
                                throw new IllegalSampleException("Sampled " + variant + " is not valid anymore for feature model " + model + "!");
                            }
                        }
                    });

                    return previousSample;
                },
                // If there is no predecessor the given blueprint is the first one.
                () -> Functjonal.match(model,
                        this::sample,
                        () -> {
                            if (sampler instanceof ConstSampler c) {
                                return c.sample();
                            }
                            throw new IllegalStateException("The given blueprint has neither a predecessor (thus it is assumed it is the first blueprint) nor a feature model (that could be sampled). The given sampler also is not a ConstSampler, thus no sample could be determined! Either provide a previous valid blueprint, a feature model, or a ConstSampler!");
                        }
                )
        );
    }
}
