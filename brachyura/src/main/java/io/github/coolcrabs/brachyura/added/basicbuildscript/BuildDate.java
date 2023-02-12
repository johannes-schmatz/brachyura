package io.github.coolcrabs.brachyura.added.basicbuildscript;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class BuildDate {
	private static final ZoneId zone = ZoneId.of("UTC");
	private final ZonedDateTime date;
	private final DateTimeFormatter format;

	public BuildDate(DateTimeFormatter format) {
		date = ZonedDateTime.now(zone);
		this.format = format;
	}

	@Override
	public String toString() {
		return date.format(format);
	}

	public String format(String format) {
		return date.format(DateTimeFormatter.ofPattern(format));
	}
}
