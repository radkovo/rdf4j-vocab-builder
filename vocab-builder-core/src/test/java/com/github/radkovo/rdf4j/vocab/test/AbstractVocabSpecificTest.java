package com.github.radkovo.rdf4j.vocab.test;


import org.apache.commons.io.FileUtils;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.github.radkovo.rdf4j.vocab.GenerationException;
import io.github.radkovo.rdf4j.vocab.VocabBuilder;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public abstract class AbstractVocabSpecificTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private Path output;

    @Before
    public void setUp() throws IOException {
        File input = temp.newFile(String.format("%s.%s", getBasename(), getFormat().getDefaultFileExtension()));
        FileUtils.copyInputStreamToFile(getInputStream(), input);

        output = temp.newFile(String.format("%S.java", getBasename())).toPath();

        try {
            VocabBuilder vb = new VocabBuilder(input.getAbsolutePath(), getFormat());
            vb.generate(output);
            System.out.println(output);
        } catch (GenerationException e) {
            Assert.fail("Could not generate vocab " + e.getMessage());
        } catch (RDFParseException e) {
            Assert.fail("Could not parse test-file: " + e.getMessage());
        }
    }

    protected abstract InputStream getInputStream();

    protected abstract String getBasename();

    protected abstract RDFFormat getFormat();

    @Test
    public void testCompilation() {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        int result = compiler.run(null, null, null, output.toString());
        Assert.assertEquals("Compiling the Vocab failed", 0, result);

    }


}
