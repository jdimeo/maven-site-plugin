package org.apache.maven.plugins.site.render;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.doxia.DefaultDoxia;
import org.apache.maven.doxia.Doxia;
import org.apache.maven.doxia.module.xhtml5.Xhtml5Parser;
import org.apache.maven.doxia.parser.ParseException;
import org.apache.maven.doxia.parser.Parser;
import org.apache.maven.doxia.parser.manager.ParserNotFoundException;
import org.apache.maven.doxia.sink.Sink;

public class ThreadSafeDoxia extends DefaultDoxia {
	private ThreadLocal<Map<String, Parser>> parsers = ThreadLocal.withInitial(HashMap::new);
	
	private Doxia delegate;
	
	public ThreadSafeDoxia(Doxia delegate) {
		this.delegate = delegate;
	}
	
	@Override
	public Parser getParser(String parserId) throws ParserNotFoundException {
		Map<String, Parser> map = parsers.get();
		try {
			return map.computeIfAbsent(parserId, $ -> {
				if ("xhtml".equals($)) {
					return new Xhtml5Parser();
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
