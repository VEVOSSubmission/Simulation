package vevos.variability.pc.visitor.common;

import org.prop4j.Node;
import vevos.functjonal.Result;
import vevos.util.io.CaseSensitivePath;
import vevos.variability.pc.ArtefactTree;
import vevos.variability.pc.LineBasedAnnotation;
import vevos.variability.pc.SourceCodeFile;
import vevos.variability.pc.visitor.ArtefactVisitor;
import vevos.variability.pc.visitor.LineBasedAnnotationVisitorFocus;
import vevos.variability.pc.visitor.SourceCodeFileVisitorFocus;
import vevos.variability.pc.visitor.SyntheticArtefactTreeNodeVisitorFocus;

import java.io.FileNotFoundException;

public class LinePCQuery implements ArtefactVisitor {
    private final CaseSensitivePath relativePath;
    private final int lineNumber;

    private SourceCodeFile foundFile = null;
    private boolean lineFound = false;
    private Node result = null;

    public LinePCQuery(final CaseSensitivePath relativePath, final int lineNumber) {
        this.relativePath = relativePath;
        this.lineNumber = lineNumber;
    }

    public Result<Node, Exception> getResult() {
        if (foundFile != null) {
            if (lineFound) {
                return Result.Success(result);
            }

            return Result.Failure(new IndexOutOfBoundsException(
                    "Given line number "
                            + lineNumber
                            + " is not within bounds of file "
                            + relativePath
                            + " that ranges from "
                            + foundFile.getRootAnnotation().getLineFrom()
                            + " to "
                            + foundFile.getRootAnnotation().getLineTo()
                            + "."
            ));
        }

        return Result.Failure(new FileNotFoundException("Could not find file " + relativePath.toString() + "!"));
    }

    @Override
    public <C extends ArtefactTree<?>> void visitGenericArtefactTreeNode(final SyntheticArtefactTreeNodeVisitorFocus<C> focus) {
//        Logger.info("visitGenericArtefactTreeNode(" + focus.getValue() + ")");
        for (int i = 0; foundFile == null && i < focus.getValue().getNumberOfSubtrees(); ++i) {
            focus.visitSubtree(i, this);
        }
    }

    @Override
    public void visitSourceCodeFile(final SourceCodeFileVisitorFocus focus) {
//        Logger.info("visitSourceCodeFile(" + focus.getValue() + ")");
        if (foundFile == null && focus.getValue().getFile().equals(relativePath)) {
            foundFile = focus.getValue();
            focus.skipRootAnnotationButVisitItsSubtrees(this);
        }
    }

    @Override
    public void visitLineBasedAnnotation(final LineBasedAnnotationVisitorFocus focus) {
//        Logger.info("visitLineBasedAnnotation(" + focus.getValue() + ")");
        final LineBasedAnnotation val = focus.getValue();
        if (!lineFound && val.annotates(lineNumber)) {
            focus.visitAllSubtrees(this);
            if (!lineFound) {
                result = val.getPresenceCondition();
                lineFound = true;
            }
        }
    }
}
