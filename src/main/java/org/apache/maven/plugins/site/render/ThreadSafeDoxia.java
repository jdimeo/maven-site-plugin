/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.site.render;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import com.google.inject.Provider;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.maven.doxia.DefaultDoxia;
import org.apache.maven.doxia.Doxia;
import org.apache.maven.doxia.module.markdown.MarkdownParser;
import org.apache.maven.doxia.module.xhtml5.Xhtml5Parser;
import org.apache.maven.doxia.parser.ParseException;
import org.apache.maven.doxia.parser.Parser;
import org.apache.maven.doxia.parser.manager.ParserNotFoundException;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.project.MavenProject;
import org.asciidoctor.maven.site.parser.AsciidoctorAstDoxiaParser;

public class ThreadSafeDoxia extends DefaultDoxia implements Provider<MavenProject> {
    private ThreadLocal<Map<String, Parser>> parsers = ThreadLocal.withInitial(HashMap::new);

    private MavenProject project;
    private Doxia delegate;

    public ThreadSafeDoxia(MavenProject project, Doxia delegate) {
        this.project = project;
        this.delegate = delegate;
    }

    @Override
    public MavenProject get() {
        return project;
    }

    @Override
    public Parser getParser(String parserId) throws ParserNotFoundException {
        Map<String, Parser> map = parsers.get();
        try {
            return map.computeIfAbsent(parserId, $ -> {
                if ("xhtml".equals($)) {
                    return new Xhtml5Parser();
                }
                if ("markdown".equals($)) {
                    MarkdownParser ret = new MarkdownParser();
                    try {
                        FieldUtils.writeField(ret, "parser", new MarkdownParser.MarkdownHtmlParser(), true);
                    } catch (IllegalAccessException e) {
                        // will throw NPE
                    }
                    ret.setEmitAnchorsForIndexableEntries(false);
                    return ret;
                }
                if ("asciidoc".equals($)) {
                    return new AsciidoctorAstDoxiaParser() {
                        {
                            mavenProjectProvider = ThreadSafeDoxia.this;
                        }
                    };
                }
                // Else, fall back to the default which are presumably thread
                // safe instances
                try {
                    return delegate.getParser(parserId);
                } catch (ParserNotFoundException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            throw new ParserNotFoundException(e);
        }
    }

    @Override
    public void parse(Reader source, String parserId, Sink sink) throws ParserNotFoundException, ParseException {
        getParser(parserId).parse(source, sink);
    }

    @Override
    public void parse(Reader source, String parserId, Sink sink, String reference)
            throws ParserNotFoundException, ParseException {
        getParser(parserId).parse(source, sink, reference);
    }
}
