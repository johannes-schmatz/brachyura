package io.github.coolcrabs.brachyura.processing;

import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ProcessingCollector implements ProcessingSink, Iterable<ProcessingEntry> {
	@Deprecated
	public final Map<ProcessingId, ProcessingEntry> map = new HashMap<>();
	private final Map<String, Set<ProcessingId>> pathToIdMap = new HashMap<>();
	private final Map<ProcessingSource, Set<ProcessingId>> sourcesToIds = new HashMap<>();

	public Set<ProcessingSource> getSources() {
		return sourcesToIds.keySet();
	}

	public void duplicate(ProcessingEntry oldEntry, ProcessingEntry newEntry) {
		throw new IllegalArgumentException("Duplicate entry " + newEntry.id.path + " at " + this + ", with " + oldEntry + " and " + newEntry);
	}

	@Override
	public void sink(Supplier<InputStream> in, ProcessingId id) {
		ProcessingEntry entry = new ProcessingEntry(in, id);

		ProcessingEntry old = map.put(entry.id, entry);
		pathToIdMap.computeIfAbsent(entry.id.path, k -> new HashSet<>())
				.add(entry.id);
		sourcesToIds.computeIfAbsent(entry.id.source, k -> new HashSet<>())
				.add(entry.id);
		if (old != null) {
			duplicate(old, entry);
		}
	}

	public void sinkRemaining(ProcessingSink sink) {
		if (sink instanceof ProcessingCollector) {
			ProcessingCollector other = (ProcessingCollector) sink;

			// TODO: optimise this
			for (ProcessingEntry entry : map.values()) {
				ProcessingEntry old = other.map.put(entry.id, entry);
				other.pathToIdMap.computeIfAbsent(entry.id.path, k -> new HashSet<>())
						.add(entry.id);
				other.sourcesToIds.computeIfAbsent(entry.id.source, k -> new HashSet<>())
						.add(entry.id);
				if (old != null) {
					duplicate(old, entry);
				}
			}

		} else {
			for (ProcessingEntry entry : map.values()) {
				sink.sink(entry.in, entry.id);
			}
		}
	}

	public Set<ProcessingEntry> removeByPath(String path) {
		Set<ProcessingId> ids = pathToIdMap.remove(path);
		if (ids == null) return Collections.emptySet();
		return ids.stream()
				.map(map::remove)
				.collect(Collectors.toSet());
	}

	public Set<ProcessingEntry> getByPath(String path) {
		Set<ProcessingId> ids = pathToIdMap.get(path);
		if (ids == null) return Collections.emptySet();
		return ids.stream()
				.map(map::get)
				.collect(Collectors.toSet());
	}

	public ProcessingEntry removeById(ProcessingId id) {
		Set<ProcessingId> ids = pathToIdMap.get(id.path);
		if (ids != null) {
			ids.remove(id);
		}
		return map.remove(id);
	}

	@NotNull
	@Override
	public Iterator<ProcessingEntry> iterator() {
		return map.values().iterator();
	}

	@Override
	public void forEach(Consumer<? super ProcessingEntry> action) {
		map.values().forEach(action);
	}
}
