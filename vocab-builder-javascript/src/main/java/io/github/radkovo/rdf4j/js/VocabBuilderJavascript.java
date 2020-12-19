/**
 * VocabBuilderPython.java
 *
 * Created on 5. 8. 2019, 11:07:32 by burgetr
 */
package io.github.radkovo.rdf4j.js;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.radkovo.rdf4j.vocab.GenerationException;
import io.github.radkovo.rdf4j.vocab.VocabBuilder;

/**
 * A vocab builder that generates python code.
 * 
 * @author burgetr
 */
public class VocabBuilderJavascript extends VocabBuilder
{
    private static final Logger log = LoggerFactory.getLogger(VocabBuilderJavascript.class);

    public VocabBuilderJavascript(String filename, RDFFormat format) throws IOException, RDFParseException
    {
        super(filename, format);
    }

    public VocabBuilderJavascript(String filename, String format) throws IOException, RDFParseException
    {
        super(filename, format);
    }
    
    @Override
    public void generate(Path output) throws IOException, GenerationException {
        final String className = output.getFileName().toString().replaceFirst("\\.js$", "");
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(output, StandardCharsets.UTF_8))) {
            generate(className, out);
        }
    }
    
    @Override
    public void generate(String className, PrintWriter out) throws IOException, GenerationException {
        log.trace("classname: {}", className);
        if (StringUtils.isBlank(getName())) {
            setName(className);
        }
        if (StringUtils.isBlank(getPrefix())) {
            throw new GenerationException("could not detect prefix, please set explicitly");
        } else {
            log.debug("prefix: {}", getPrefix());
        }

        Pattern pattern = Pattern.compile(Pattern.quote(getPrefix()) + "(.+)");
        ConcurrentMap<String, IRI> splitUris = new ConcurrentHashMap<>();
        for (Resource nextSubject : getModel().subjects()) {
            if (nextSubject instanceof IRI) {
                Matcher matcher = pattern.matcher(nextSubject.stringValue());
                if (matcher.find()) {
                    String k = matcher.group(1);
                    IRI putIfAbsent = splitUris.putIfAbsent(k, (IRI) nextSubject);
                    if (putIfAbsent != null) {
                        log.warn("Conflicting keys found: uri={} key={} existing={}",
                                nextSubject.stringValue(), k, putIfAbsent);
                    }
                }
            }
        }

        //print

        final IRI pfx = SimpleValueFactory.getInstance().createIRI(getPrefix());
        Literal oTitle = getFirstExistingObjectLiteral(getModel(), pfx, getPreferredLanguage(), LABEL_PROPERTIES);
        Literal oDescr = getFirstExistingObjectLiteral(getModel(), pfx, getPreferredLanguage(), COMMENT_PROPERTIES);
        Set<Value> oSeeAlso = getModel().filter(pfx, RDFS.SEEALSO, null).objects();

        //namespace
        out.printf("const NAMESPACE = '%s';%n", getPrefix());
        out.println();
        
        //class JavaDoc
        out.println("/**");
        if (oTitle != null) {
            out.printf(" * %s.%n", WordUtils.wrap(oTitle.getLabel().replaceAll("\\s+", " "), 70, "\n * ", false));
            out.println(" * <p>");
        }
        if (oDescr != null) {
            out.printf(" * %s.%n", WordUtils.wrap(oDescr.getLabel().replaceAll("\\s+", " "), 70, "\n * ", false));
            out.println(" * <p>");
        }
        out.printf(" * Namespace %s.%n", getName());
        out.printf(" * Prefix: {@code <%s>}%n", getPrefix());
        if (!oSeeAlso.isEmpty()) {
            out.println(" *");
            for (Value s : oSeeAlso) {
                if (s instanceof IRI) {
                    out.printf(" * @see <a href=\"%s\">%s</a>%n", s.stringValue(), s.stringValue());
                }
            }
        }
        out.println(" */");
        //class Definition
        out.printf("const %s = {%n", className);
        out.println();

        //constants
        out.printf(getIndent(1) + "NAMESPACE: '%s',%n", getPrefix());
        out.println();
        out.printf(getIndent(1) + "PREFIX: '%s',%n", getName().toLowerCase());
        out.println();

        List<String> keys = new ArrayList<>();
        keys.addAll(splitUris.keySet());
        Collections.sort(keys, String.CASE_INSENSITIVE_ORDER);

        //string constant values
        if (getStringCaseFormat() != null || StringUtils.isNotBlank(getStringPropertyPrefix()) || (StringUtils.isNotBlank(getStringPropertySuffix()))) {
            // add the possibility to add a string property with the namespace for usage in
            for (String key : keys) {
                final Literal comment = getFirstExistingObjectLiteral(getModel(), splitUris.get(key), getPreferredLanguage(), COMMENT_PROPERTIES);
                final Literal label = getFirstExistingObjectLiteral(getModel(), splitUris.get(key), getPreferredLanguage(), LABEL_PROPERTIES);

                out.println(getIndent(1) + "/**");
                if (label != null) {
                    out.printf(getIndent(1) + " * %s%n", label.getLabel());
                    out.println(getIndent(1) + " * <p>");
                }
                out.printf(getIndent(1) + " * %s.%n", splitUris.get(key).stringValue());
                if (comment != null) {
                    out.printf(getIndent(1) + " * %s%n", WordUtils.wrap(comment.getLabel().replaceAll("\\s+", " "), 70, "\n" + getIndent(1) + " * ", false));
                }
                out.println(getIndent(1) + " */");

                final String nextKey = cleanKey(String.format("%s%s%s", StringUtils.defaultString(getStringPropertyPrefix()),
                        doCaseFormatting(key, getStringConstantCase()),
                        StringUtils.defaultString(getStringPropertySuffix())));
                checkField(className, nextKey);
                out.printf(getIndent(1) + "%s: NAMESPACE + \'%s',%n",
                         nextKey, className, key);
                out.println();
            }
        }

        //and now the resources
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
        
            Literal comment = getFirstExistingObjectLiteral(getModel(), splitUris.get(key), getPreferredLanguage(), COMMENT_PROPERTIES);
            Literal label = getFirstExistingObjectLiteral(getModel(), splitUris.get(key), getPreferredLanguage(), LABEL_PROPERTIES);

            out.println(getIndent(1) + "/**");
            if (label != null) {
                out.printf(getIndent(1) + " * %s%n", label.getLabel());
            }
            out.printf(getIndent(1) + " * %s%n", splitUris.get(key).stringValue());
            if (comment != null) {
                out.printf(getIndent(1) + " * %s%n", WordUtils.wrap(comment.getLabel().replaceAll("\\s+", " "), 70, "\n" + getIndent(1) + " * ", false));
            }
            out.println(getIndent(1) + " */");

            String nextKey = cleanKey(doCaseFormatting(key, getConstantCase()));
            checkField(className, nextKey);
            if (i < keys.size() - 1)
                out.printf(getIndent(1) + "%s: NAMESPACE + '%s',%n", nextKey, key);
            else
                out.printf(getIndent(1) + "%s: NAMESPACE + '%s'%n", nextKey, key);
            out.println();
        }

        //class end
        out.println("}");
        
        //export
        out.println();
        out.printf("export default %s;%n", className);
        out.flush();
    }


}
