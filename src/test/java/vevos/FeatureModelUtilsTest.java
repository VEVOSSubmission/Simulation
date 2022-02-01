package vevos;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureModelElement;
import org.junit.Test;
import vevos.util.fide.FeatureModelUtils;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class FeatureModelUtilsTest {
    
    static {
        VEVOS.Initialize();
    }
    
    @Test
    public void simpleModelIntersection() {
        IFeatureModel modelA = FeatureModelUtils.FromOptionalFeatures("A", "B", "C", "D", "E");
        IFeatureModel modelB = FeatureModelUtils.FromOptionalFeatures("B", "C", "E", "F", "G");
        
        IFeatureModel intersection = FeatureModelUtils.IntersectionModel(modelA, modelB);
        
        Set<String> featureIntersection = intersection.getFeatures().stream().map(IFeatureModelElement::getName).collect(Collectors.toSet());
        assert featureIntersection.contains("B");
        assert featureIntersection.contains("C");
        assert featureIntersection.contains("E");
        
        assert !featureIntersection.contains("A");
        assert !featureIntersection.contains("D");
        assert !featureIntersection.contains("F");
        assert !featureIntersection.contains("G");
    }

    @Test
    public void simpleModelUnion() {
        IFeatureModel modelA = FeatureModelUtils.FromOptionalFeatures("A", "B", "C", "D", "E");
        IFeatureModel modelB = FeatureModelUtils.FromOptionalFeatures("B", "C", "E", "F", "G");

        Collection<String> union = FeatureModelUtils.UnionModel(modelA, modelB).getFeatures().stream().map(IFeatureModelElement::getName).collect(Collectors.toSet());
        assert union.contains("A");
        assert union.contains("B");
        assert union.contains("C");
        assert union.contains("D");
        assert union.contains("E");
        assert union.contains("F");
        assert union.contains("G");
    }

    @Test
    public void simpleFeatureDifference() {
        IFeatureModel modelA = FeatureModelUtils.FromOptionalFeatures("A", "B", "C", "D", "E");
        IFeatureModel modelB = FeatureModelUtils.FromOptionalFeatures("B", "C", "E", "F", "G");

        Collection<String> difference = FeatureModelUtils.getSymmetricFeatureDifference(modelA, modelB);
        assert difference.contains("A");
        assert !difference.contains("B");
        assert !difference.contains("C");
        assert difference.contains("D");
        assert !difference.contains("E");
        assert difference.contains("F");
        assert difference.contains("G");
    }
}
