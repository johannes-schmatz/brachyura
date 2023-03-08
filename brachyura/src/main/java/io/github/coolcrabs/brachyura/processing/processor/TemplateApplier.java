package io.github.coolcrabs.brachyura.processing.processor;

import io.github.coolcrabs.brachyura.processing.ProcessingCollector;
import io.github.coolcrabs.brachyura.processing.ProcessingEntry;
import io.github.coolcrabs.brachyura.processing.ProcessingSink;
import io.github.coolcrabs.brachyura.processing.Processor;
import io.github.coolcrabs.brachyura.util.Lazy;
import io.github.coolcrabs.brachyura.util.StreamUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateApplier implements Processor {
	public final Map<Pattern, Lazy<String>> templateMappings = new HashMap<>();

	public static Pattern getPatternForSources(String key) {
		//@formatter:off
		return Pattern.compile(
				"(" +
					"(" + // variants of simple data types
						"Boolean\\s*\\.\\s*parseBoolean" + "|" +
						"Byte\\s*\\.\\s*parseByte" + "|" +
						"Short\\s*\\.\\s*parseShort" + "|" +
						"Integer\\s*\\.\\s*parseInt" + "|" +
						"Long\\s*\\.\\s*parseLong" +
					")\\s*" +
					"(" +
						"\\(\"\\$\\{" + key + "}\"\\)" + // ("${key}")
					")" +
				"|" +
					"(?<=\")" + // has " in front
					"\\$\\{" + key + "}" + // ${key}
					"(?=\")" + // has " in back
				")"
		);
		//@formatter:on
	}

	public static Pattern getPatternForResources(String key) {
		//@formatter:off
		return Pattern.compile(
				"\\$\\{" + key + "}" // ${key}
		);
		//@formatter:on
	}

	public static Pattern getPattern(String key, boolean isForResources) {
		if (isForResources) {
			return getPatternForResources(key);
		} else {
			return getPatternForSources(key);
		}
	}

	public TemplateApplier(Map<String, Lazy<String>> templateMappings, boolean isForResources) {
		for (Map.Entry<String, Lazy<String>> template : templateMappings.entrySet()) {
			this.templateMappings.put(getPattern(template.getKey(), isForResources), template.getValue());
		}
	}

	@Override
	public void process(ProcessingCollector inputs, ProcessingSink sink) throws IOException {
		for (ProcessingEntry entry : inputs.map.values()) {
			sink.sink(() -> applyTemplates(entry), entry.id);
		}
	}

	public InputStream applyTemplates(ProcessingEntry entry) {
		String data = StreamUtil.readFullyAsString(entry.in.get());

		for (Map.Entry<Pattern, Lazy<String>> template : templateMappings.entrySet()) {
			Matcher matcher = template.getKey().matcher(data);

			if (matcher.find()) {
				data = matcher.replaceAll(template.getValue().get());
			}
		}

		return StreamUtil.writeFullyFromString(data);
	}
}
