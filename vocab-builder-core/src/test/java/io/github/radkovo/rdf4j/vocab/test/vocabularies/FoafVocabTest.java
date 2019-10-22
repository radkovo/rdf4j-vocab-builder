package io.github.radkovo.rdf4j.vocab.test.vocabularies;

import org.eclipse.rdf4j.rio.RDFFormat;
import org.junit.Assume;

import io.github.radkovo.rdf4j.vocab.test.AbstractVocabSpecificTest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class FoafVocabTest extends AbstractVocabSpecificTest {

    @Override
    protected InputStream getInputStream() {
        try {
            return new URL("http://xmlns.com/foaf/spec/index.rdf").openStream();
        } catch (IOException e) {
            Assume.assumeNoException(e);
            return null;
        }
    }

    @Override
    protected String getBasename() {
        return "foaf";
    }

    @Override
    protected RDFFormat getFormat() {
        return RDFFormat.RDFXML;
    }
}
