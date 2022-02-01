package vevos;

import de.ovgu.featureide.fm.core.base.impl.*;
import de.ovgu.featureide.fm.core.configuration.*;
import de.ovgu.featureide.fm.core.io.sxfm.SXFMFormat;
import de.ovgu.featureide.fm.core.io.xml.XmlFeatureModelFormat;
import vevos.util.Logger;

public final class VEVOS {
    private static boolean initialized = false;

    private VEVOS() {}

    private static void InitFeatureIDE() {
        FMFactoryManager.getInstance().addExtension(DefaultFeatureModelFactory.getInstance());
        FMFactoryManager.getInstance().addExtension(MultiFeatureModelFactory.getInstance());
        FMFactoryManager.getInstance().setWorkspaceLoader(new CoreFactoryWorkspaceLoader());

        FMFormatManager.getInstance().addExtension(new XmlFeatureModelFormat());
        FMFormatManager.getInstance().addExtension(new SXFMFormat());

        ConfigurationFactoryManager.getInstance().addExtension(DefaultConfigurationFactory.getInstance());
        ConfigurationFactoryManager.getInstance().setWorkspaceLoader(new CoreFactoryWorkspaceLoader());

        ConfigFormatManager.getInstance().addExtension(new XMLConfFormat());
        ConfigFormatManager.getInstance().addExtension(new DefaultFormat());
        ConfigFormatManager.getInstance().addExtension(new FeatureIDEFormat());
        ConfigFormatManager.getInstance().addExtension(new EquationFormat());
        ConfigFormatManager.getInstance().addExtension(new ExpressionFormat());
    }

    public static void Initialize() {
        if (!initialized) {
            Logger.initConsoleLogger();
            InitFeatureIDE();
            initialized = true;
            Logger.debug("Finished initialization");
        }
    }
}
