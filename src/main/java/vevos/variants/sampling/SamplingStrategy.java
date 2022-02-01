package vevos.variants.sampling;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import vevos.feature.sampling.Sample;
import vevos.variants.blueprints.VariantsRevisionBlueprint;

import java.util.Optional;

public interface SamplingStrategy {
    Sample sampleForRevision(Optional<IFeatureModel> model, VariantsRevisionBlueprint blueprint);
}
