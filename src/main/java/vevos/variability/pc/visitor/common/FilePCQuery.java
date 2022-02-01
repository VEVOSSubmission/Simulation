package vevos.variability.pc.visitor.common;

import org.prop4j.Node;
import vevos.functjonal.Result;
import vevos.util.io.CaseSensitivePath;
import vevos.variability.pc.ArtefactTree;
import vevos.variability.pc.visitor.ArtefactVisitor;
import vevos.variability.pc.visitor.LineBasedAnnotationVisitorFocus;
import vevos.variability.pc.visitor.SourceCodeFileVisitorFocus;
import vevos.variability.pc.visitor.SyntheticArtefactTreeNodeVisitorFocus;

import java.io.FileNotFoundException;

public class FilePCQuery implements ArtefactVisitor {
    private final CaseSensitivePath relativePath;

    private boolean fileFound = false;
    private Node result = null;

    public FilePCQuery(final CaseSensitivePath relativePath) {
        this.relativePath = relativePath;
    }

    public Result<Node, Exception> getResult() {
        if (fileFound) {
            return Result.Success(result);
        }

        return Result.Failure(new FileNotFoundException("Could not find file " + relativePath.toString() + "!"));
    }

    @Override
    public <C extends ArtefactTree<?>> void visitGenericArtefactTreeNode(final SyntheticArtefactTreeNodeVisitorFocus<C> focus) {
        for (int i = 0; !fileFound && i < focus.getValue().getNumberOfSubtrees(); ++i) {
            focus.visitSubtree(i, this);
        }
    }

    @Override
    public void visitSourceCodeFile(final SourceCodeFileVisitorFocus focus) {
        if (!fileFound && focus.getValue().getFile().equals(relativePath)) {
            result = focus.getValue().getPresenceCondition();
            fileFound = true;
        }
    }

    @Override
    public void visitLineBasedAnnotation(final LineBasedAnnotationVisitorFocus focus) {
        // should never be invoked
        assert false;
    }
}
