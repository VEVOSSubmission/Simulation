package vevos.variability.pc.groundtruth;

import vevos.util.io.CaseSensitivePath;
import vevos.variability.pc.Artefact;
import vevos.variability.pc.SourceCodeFile;

import java.util.HashMap;
import java.util.Map;

/**
 * GroundTruth of a variant generation process.
 * A GroundTruth consists of
 * (1) an artefact (tree) representing the variant and its presence conditions,
 * (2) a matching of source code lines for each generated file.
 *     The map is indexed by paths to source code files and valued by the ground truth data
 *     for the annotation blocks in the corresponding file.
 */
public record GroundTruth(Artefact variant, Map<CaseSensitivePath, AnnotationGroundTruth> fileMatches) {
    public void add(final GroundTruth other) {
        fileMatches.putAll(other.fileMatches);
    }

    public static GroundTruth withoutAnnotations(final Artefact artefact) {
        return new GroundTruth(artefact, new HashMap<>());
    }

    public static GroundTruth forSourceCodeFile(final SourceCodeFile codeFile, final AnnotationGroundTruth annotationGroundTruth) {
        final Map<CaseSensitivePath, AnnotationGroundTruth> map = new HashMap<>();
        map.put(codeFile.getFile(), annotationGroundTruth);
        return new GroundTruth(codeFile, map);
    }
}
