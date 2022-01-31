package vevos.feature.sampling;

import de.ovgu.featureide.fm.core.base.IFeatureModel;


public interface Sampler {
    int size();
    Sample sample(IFeatureModel model);
}
