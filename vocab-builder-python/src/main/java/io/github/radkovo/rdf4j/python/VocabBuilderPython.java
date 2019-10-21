/**
 * VocabBuilderPython.java
 *
 * Created on 5. 8. 2019, 11:07:32 by burgetr
 */
package io.github.radkovo.rdf4j.python;

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
public class VocabBuilderPython extends VocabBuilder
{
    private static final Logger log = LoggerFactory.getLogger(VocabBuilderPython.class);

    public VocabBuilderPython(String filename, RDFFormat format) throws IOException, RDFParseException
    {
        super(filename, format);
    }

    public VocabBuilderPython(String filename, String format) throws IOException, RDFParseException
    {
        super(filename, format);
    }
    
    @Override
    public void generate(Path output) throws IOException, GenerationException {
        final String className = output.getFileName().toString().replaceFirst("\\.py$", "");
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(output, StandardCharsets.UTF_8))) {
            generate(className, out);
        }
    }
    
    @Override
    public void generate(String className, PrintWriter out) throws IOException, GenerationException
    {
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

        //imports
        /*out.println("import org.eclipse.rdf4j.model.IRI;");
        out.println("import org.eclipse.rdf4j.model.ValueFactory;");
        out.println("import org.eclipse.rdf4j.model.impl.SimpleValueFactory;");
        out.println();*/

        final IRI pfx = SimpleValueFactory.getInstance().createIRI(getPrefix());
        Literal oTitle = getFirstExistingObjectLiteral(getModel(), pfx, getPreferredLanguage(), LABEL_PROPERTIES);
        Literal oDescr = getFirstExistingObjectLiteral(getModel(), pfx, getPreferredLanguage(), COMMENT_PROPERTIES);
        Set<Value> oSeeAlso = getModel().filter(pfx, RDFS.SEEALSO, null).objects();

        //class Definition
        out.printf("class %s:%n", className);
        //class doc
        out.println(getIndent(1) + "\"\"\"");
        if (oTitle != null) {
            out.printf(getIndent(1) + "%s.%n", WordUtils.wrap(oTitle.getLabel().replaceAll("\\s+", " "), 70, "\n" + getIndent(1), false));
            out.println();
        }
        if (oDescr != null) {
            out.printf(getIndent(1) + "%s.%n", WordUtils.wrap(oDescr.getLabel().replaceAll("\\s+", " "), 70, "\n" + getIndent(1), false));
            out.println();
        }
        out.printf(getIndent(1) + "Namespace %s.%n", getName());
        out.printf(getIndent(1) + "Prefix: <%s>%n", getPrefix());
        if (!oSeeAlso.isEmpty()) {
            out.println(" *");
            for (Value s : oSeeAlso) {
                if (s instanceof IRI) {
                    out.printf(getIndent(1) + "@see <a href=\"%s\">%s</a>%n", s.stringValue(), s.stringValue());
                }
            }
        }
        out.println(getIndent(1) + "\"\"\"");
        out.println();

        //constants
        out.printf(getIndent(1) + "# %s%n", getPrefix());
        out.printf(getIndent(1) + "NAMESPACE = \"%s\"%n", getPrefix());
        out.println();
        out.printf(getIndent(1) + "# %s%n", getName().toLowerCase());
        out.printf(getIndent(1) + "PREFIX = \"%s\"%n", getName().toLowerCase());
        out.println();

        List<String> keys = new ArrayList<>();
        keys.addAll(splitUris.keySet());
        Collections.sort(keys, String.CASE_INSENSITIVE_ORDER);

        //string constant values
        if (getStringCaseFormat() != null || StringUtils.isNotBlank(getStringPropertyPrefix()) || (StringUtils.isNotBlank(getStringPropertySuffix()))) {
            // add the possibility to add a string property with the namespace for usage in
            for (String key : keys) {
                final String nextKey = cleanKey(String.format("%s%s%s", StringUtils.defaultString(getStringPropertyPrefix()),
                        doCaseFormatting(key, getStringConstantCase()),
                        StringUtils.defaultString(getStringPropertySuffix())));
                checkField(className, nextKey);
                out.printf(getIndent(1) + "%s = %s.NAMESPACE + \"%s\"%n",
                         nextKey, className, key);
                out.println();
            }
        }

        //and now the resources
        for (String key : keys) {
            Literal comment = getFirstExistingObjectLiteral(getModel(), splitUris.get(key), getPreferredLanguage(), COMMENT_PROPERTIES);
            Literal label = getFirstExistingObjectLiteral(getModel(), splitUris.get(key), getPreferredLanguage(), LABEL_PROPERTIES);

            if (label != null) {
                out.printf(getIndent(1) + "# %s%n", label.getLabel());
            }
            out.printf(getIndent(1) + "# %s.%n", splitUris.get(key).stringValue());
            if (comment != null) {
                out.printf(getIndent(1) + "# %s%n", WordUtils.wrap(comment.getLabel().replaceAll("\\s+", " "), 70, "\n" + getIndent(1) + "# ", false));
            }
            out.printf(getIndent(1) + "# <a href=\"%s\">%s</a>%n", splitUris.get(key), key);

            String nextKey = cleanKey(doCaseFormatting(key, getConstantCase()));
            checkField(className, nextKey);
            out.printf(getIndent(1) + "%s = NAMESPACE + \"%s\"%n", nextKey, key);
            out.println();
        }

        //class end
        out.flush();
    }


}
