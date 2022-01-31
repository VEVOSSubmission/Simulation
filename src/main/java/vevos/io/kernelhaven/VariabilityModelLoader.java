package vevos.io.kernelhaven;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import net.ssehub.kernel_haven.variability_model.JsonVariabilityModelCache;
import vevos.functjonal.Result;
import vevos.io.ResourceLoader;
import vevos.util.fide.FeatureModelUtils;
import vevos.util.io.PathUtils;

import java.nio.file.Path;

public class VariabilityModelLoader implements ResourceLoader<IFeatureModel> {
    
    @Override
    public boolean canLoad(Path p) {
        return PathUtils.hasExtension(p,".json");
    }

    @Override
    public Result<IFeatureModel, ? extends Exception> load(Path p) {
        return Result.Try(() -> {
            JsonVariabilityModelCache cache = new JsonVariabilityModelCache(p.getParent().toFile());
            return FeatureModelUtils.FromVariabilityModel(cache.readFixed(p.toFile()));
        });
    }


}
