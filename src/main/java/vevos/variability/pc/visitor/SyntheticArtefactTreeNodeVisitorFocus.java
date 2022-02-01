package vevos.variability.pc.visitor;

import vevos.variability.pc.ArtefactTree;
import vevos.variability.pc.SyntheticArtefactTreeNode;

public class SyntheticArtefactTreeNodeVisitorFocus<C extends ArtefactTree<?>> extends ArtefactTreeVisitorFocus<SyntheticArtefactTreeNode<C>> {
    public SyntheticArtefactTreeNodeVisitorFocus(final SyntheticArtefactTreeNode<C> artefact) {
        super(artefact);
    }

    @Override
    public void accept(final ArtefactVisitor visitor) {
        visitor.visitGenericArtefactTreeNode(this);
    }
}
