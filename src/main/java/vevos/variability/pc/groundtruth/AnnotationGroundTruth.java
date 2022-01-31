package vevos.variability.pc.groundtruth;

import vevos.variability.pc.LineBasedAnnotation;

public record AnnotationGroundTruth(
        LineBasedAnnotation splArtefact,
        LineBasedAnnotation variantArtefact,
        BlockMatching matching
)
{
}
