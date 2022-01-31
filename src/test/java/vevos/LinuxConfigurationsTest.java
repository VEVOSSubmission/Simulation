package vevos;

import org.junit.Test;
import vevos.feature.sampling.LinuxKernel;
import vevos.feature.sampling.Sample;

import java.io.IOException;

public class LinuxConfigurationsTest {
    @Test
    public void configsAreLoaded() throws IOException {
        final Sample sample = LinuxKernel.GetSample();
        assert sample.size() == 5;
    }
}
