package io.github.coolcrabs.brachyura.mappings.tinyremapper;

import io.github.coolcrabs.brachyura.processing.ProcessingEntry;
import io.github.coolcrabs.brachyura.util.StreamUtil;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class TrUtil {
	public static Builder newRemapper() {
		return new Builder();
	}

	public static class Builder {
		private final TinyRemapper.Builder builder = TinyRemapper.newRemapper();
		private Builder() { }

		public Builder withMappings(IMappingProvider provider) {
			builder.withMappings(provider);
			return this;
		}

		public Builder ignoreFieldDesc(boolean value) {
			builder.ignoreFieldDesc(value);
			return this;
		}

		public Builder threads(int threadCount) {
			builder.threads(threadCount);
			return this;
		}

		/**
		 * Keep the input data after consuming it for apply(), allows multiple apply invocations() even without input tag use.
		 */
		public Builder keepInputData(boolean value) {
			builder.keepInputData(value);
			return this;
		}

		public Builder withForcedPropagation(Set<String> entries) {
			builder.withForcedPropagation(entries);
			return this;
		}

		public Builder propagatePrivate(boolean value) {
			builder.propagatePrivate(value);
			return this;
		}

		public Builder propagateBridges(TinyRemapper.LinkedMethodPropagation value) {
			builder.propagateBridges(value);
			return this;
		}

		public Builder propagateRecordComponents(TinyRemapper.LinkedMethodPropagation value) {
			builder.propagateRecordComponents(value);
			return this;
		}

		public Builder removeFrames(boolean value) {
			builder.removeFrames(value);
			return this;
		}

		public Builder ignoreConflicts(boolean value) {
			builder.ignoreConflicts(value);
			return this;
		}

		public Builder resolveMissing(boolean value) {
			builder.resolveMissing(value);
			return this;
		}

		public Builder checkPackageAccess(boolean value) {
			builder.checkPackageAccess(value);
			return this;
		}

		public Builder fixPackageAccess(boolean value) {
			builder.fixPackageAccess(value);
			return this;
		}

		public Builder rebuildSourceFilenames(boolean value) {
			builder.rebuildSourceFilenames(value);
			return this;
		}

		public Builder skipLocalVariableMapping(boolean value) {
			builder.skipLocalVariableMapping(value);
			return this;
		}

		public Builder renameInvalidLocals(boolean value) {
			builder.renameInvalidLocals(value);
			return this;
		}

		/**
		 * Pattern that flags matching local variable (and arg) names as invalid for the usual renameInvalidLocals processing.
		 */
		public Builder invalidLvNamePattern(Pattern value) {
			builder.invalidLvNamePattern(value);
			return this;
		}

		/**
		 * Whether to copy lv names from other local variables if the original name was missing or invalid.
		 */
		public Builder inferNameFromSameLvIndex(boolean value) {
			builder.inferNameFromSameLvIndex(value);
			return this;
		}

		public Builder extraAnalyzeVisitor(TinyRemapper.AnalyzeVisitorProvider provider) {
			builder.extraAnalyzeVisitor(provider);
			return this;
		}

		public Builder extraStateProcessor(TinyRemapper.StateProcessor processor) {
			builder.extraStateProcessor(processor);
			return this;
		}

		public Builder extraRemapper(Remapper remapper) {
			builder.extraRemapper(remapper);
			return this;
		}

		public Builder extraPreApplyVisitor(TinyRemapper.ApplyVisitorProvider provider) {
			builder.extraPreApplyVisitor(provider);
			return this;
		}

		public Builder extraPostApplyVisitor(TinyRemapper.ApplyVisitorProvider provider) {
			builder.extraPostApplyVisitor(provider);
			return this;
		}

		public Builder extension(TinyRemapper.Extension extension) {
			builder.extension(extension);
			return this;
		}

		public TrWrapper build() {
			return new TrWrapper(builder);
		}
	}


	public static FileSystem createBruhFileSystem() {
		return new BruhFileSystem(new BruhFileSystemProvider());
	}

	public static Path createBruhPath(FileSystem fs, ProcessingEntry entry) {
		return new BruhPath(entry, fs);
	}

	private static class BruhFileSystemProvider extends Stubs.StubFileSystemProvider {
		/**
		 * required because of {@link Files#newByteChannel(Path, Set, FileAttribute[])}, which
		 * {@link TinyRemapper#analyze(boolean, InputTag[], Path, Path)} calls
		 */
		@Override
		public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
				FileAttribute<?>... attrs) throws IOException {
			try (InputStream in = ((BruhPath)path).entry.in.get()) {
				return new SeekableInMemoryByteChannel(StreamUtil.readFullyAsBytes(in));
			}
		}

		@Override
		public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
			return ((BruhPath)path).entry.in.get();
		}

		@Override
		@SuppressWarnings("unchecked")
		public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
			if (type != BasicFileAttributeView.class) return null;
			return (V) new BruhFileAttributeView(this, path, options);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
		throws IOException {
			return (A) new BruhFileAttributes();
		}
	}

	private static class BruhFileSystem extends Stubs.StubFileSystem {
		private final FileSystemProvider provider;
		private BruhFileSystem(FileSystemProvider provider) {
			this.provider = provider;
		}

		@Override
		public FileSystemProvider provider() {
			return provider;
		}
	}

	private static class BruhPath extends Stubs.StubPath {
		public final ProcessingEntry entry;
		public final FileSystem fs;
		public BruhPath(ProcessingEntry entry, FileSystem fs) {
			this.entry = entry;
			this.fs = fs;
		}

		/**
		 * required because {@link TinyRemapper#analyze(boolean, InputTag[], Path, Path)} calls
		 * {@link Files#readAllBytes(Path)} which calls us and then {@link FileSystem#provider()}
		 */
		@Override
		public FileSystem getFileSystem() {
			return fs;
		}

		/**
		 * required for {@link TinyRemapper#read(Path, boolean, InputTag[], Path, boolean, List)}
		 * it later calls {@link #toString} on it
		 */
		@Override
		public Path getFileName() {
			return this;
		}

		/**
		 * it's called on a path that a SimpleFileVisitor gives to visitFile
		 * required for {@link TinyRemapper#readFile(Path, boolean, InputTag[], Path, List)}
		 */
		@Override
		public String toString() {
			return entry.id.path;
		}

		/**
		 * used by {@link net.fabricmc.tinyremapper.TinyRemapper#analyzeMrjVersion}
		 */
		@Override
		public int getNameCount() {
			//checkNotUsed();
			return 0;
		}
	}

	private static class BruhFileAttributeView extends Stubs.StubFileAttributeView {
		private final FileSystemProvider provider;
		private final Path path;
		private final LinkOption[] options;
		private BruhFileAttributeView(FileSystemProvider provider, Path path, LinkOption[] options) {
			this.provider = provider;
			this.path = path;
			this.options = options;
		}

		@Override
		public String name() {
			return "bruh";
		}

		@Override
		public BasicFileAttributes readAttributes() throws IOException {
			return provider.readAttributes(path, BasicFileAttributes.class, options);
		}

	}

	private static class BruhFileAttributes extends Stubs.StubFileAttributes {
		/**
		 * used by
		 * {@link net.fabricmc.tinyremapper.TinyRemapper#read(Path, boolean, InputTag[], Path, boolean, List)}
		 */
		@Override
		public boolean isDirectory() {
			//checkNotUsed();
			return false;
		}
	}

	// Stolen from apache
	// WHY TR WHYYYY
	private static class SeekableInMemoryByteChannel implements SeekableByteChannel {

		private static final int NAIVE_RESIZE_LIMIT = Integer.MAX_VALUE >> 1;

		private byte[] data;
		private final AtomicBoolean closed = new AtomicBoolean();
		private int position, size;

		/**
		 * Constructor taking a byte array.
		 *
		 * <p>This constructor is intended to be used with pre-allocated buffer or when
		 * reading from a given byte array.</p>
		 *
		 * @param data input data or pre-allocated array.
		 */
		public SeekableInMemoryByteChannel(final byte[] data) {
			this.data = data;
			size = data.length;
		}

		/**
		 * Constructor taking a size of storage to be allocated.
		 *
		 * <p>Creates a channel and allocates internal storage of a given size.</p>
		 *
		 * @param size size of internal buffer to allocate, in bytes.
		 */
		public SeekableInMemoryByteChannel(final int size) {
			this(new byte[size]);
		}

		/**
		 * Returns this channel's position.
		 *
		 * <p>This method violates the contract of {@link SeekableByteChannel#position()} as it will not throw any exception
		 * when invoked on a closed channel. Instead it will return the position the channel had when close has been
		 * called.</p>
		 */
		@Override
		public long position() {
			return position;
		}

		@Override
		public SeekableByteChannel position(final long newPosition) throws IOException {
			ensureOpen();
			if (newPosition < 0L || newPosition > Integer.MAX_VALUE) {
				throw new IOException("Position has to be in range 0.. " + Integer.MAX_VALUE);
			}
			position = (int) newPosition;
			return this;
		}

		/**
		 * Returns the current size of entity to which this channel is connected.
		 *
		 * <p>This method violates the contract of {@link SeekableByteChannel#size} as it will not throw any exception when
		 * invoked on a closed channel. Instead it will return the size the channel had when close has been called.</p>
		 */
		@Override
		public long size() {
			return size;
		}

		/**
		 * Truncates the entity, to which this channel is connected, to the given size.
		 *
		 * <p>This method violates the contract of {@link SeekableByteChannel#truncate} as it will not throw any exception when
		 * invoked on a closed channel.</p>
		 *
		 * @throws IllegalArgumentException if size is negative or bigger than the maximum of a Java integer
		 */
		@Override
		public SeekableByteChannel truncate(final long newSize) {
			if (newSize < 0L || newSize > Integer.MAX_VALUE) {
				throw new IllegalArgumentException("Size has to be in range 0.. " + Integer.MAX_VALUE);
			}
			if (size > newSize) {
				size = (int) newSize;
			}
			if (position > newSize) {
				position = (int) newSize;
			}
			return this;
		}

		@Override
		public int read(final ByteBuffer buf) throws IOException {
			ensureOpen();
			int wanted = buf.remaining();
			final int possible = size - position;
			if (possible <= 0) {
				return -1;
			}
			if (wanted > possible) {
				wanted = possible;
			}
			buf.put(data, position, wanted);
			position += wanted;
			return wanted;
		}

		@Override
		public void close() {
			closed.set(true);
		}

		@Override
		public boolean isOpen() {
			return !closed.get();
		}

		@Override
		public int write(final ByteBuffer b) throws IOException {
			ensureOpen();
			int wanted = b.remaining();
			final int possibleWithoutResize = size - position;
			if (wanted > possibleWithoutResize) {
				final int newSize = position + wanted;
				if (newSize < 0) { // overflow
					resize(Integer.MAX_VALUE);
					wanted = Integer.MAX_VALUE - position;
				} else {
					resize(newSize);
				}
			}
			b.get(data, position, wanted);
			position += wanted;
			if (size < position) {
				size = position;
			}
			return wanted;
		}

		/**
		 * Obtains the array backing this channel.
		 *
		 * <p>NOTE:
		 * The returned buffer is not aligned with containing data, use
		 * {@link #size()} to obtain the size of data stored in the buffer.</p>
		 *
		 * @return internal byte array.
		 */
		public byte[] array() {
			return data;
		}

		private void resize(final int newLength) {
			int len = data.length;
			if (len <= 0) {
				len = 1;
			}
			if (newLength < NAIVE_RESIZE_LIMIT) {
				while (len < newLength) {
					len <<= 1;
				}
			} else { // avoid overflow
				len = newLength;
			}
			data = Arrays.copyOf(data, len);
		}

		private void ensureOpen() throws ClosedChannelException {
			if (!isOpen()) {
				throw new ClosedChannelException();
			}
		}

	}

	private static class Stubs {
		private static void checkNotUsed() {
			if (true) throw new UnsupportedOperationException("Operation unsupported!");
		}
		private abstract static class StubFileSystemProvider extends FileSystemProvider {
			@Override
			public String getScheme() {
				checkNotUsed();
				return null;
			}

			@Override
			public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
				checkNotUsed();
				return null;
			}

			@Override
			public FileSystem getFileSystem(URI uri) {
				checkNotUsed();
				return null;
			}

			@Override
			public Path getPath(URI uri) {
				checkNotUsed();
				return null;
			}

			@Override
			public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
				checkNotUsed();
				return null;
			}

			@Override
			public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
				checkNotUsed();
			}

			@Override
			public void delete(Path path) throws IOException {
				checkNotUsed();
			}

			@Override
			public void copy(Path source, Path target, CopyOption... options) throws IOException {
				checkNotUsed();
			}

			@Override
			public void move(Path source, Path target, CopyOption... options) throws IOException {
				checkNotUsed();
			}

			@Override
			public boolean isSameFile(Path path, Path path2) throws IOException {
				checkNotUsed();
				return false;
			}

			@Override
			public boolean isHidden(Path path) throws IOException {
				checkNotUsed();
				return false;
			}

			@Override
			public FileStore getFileStore(Path path) throws IOException {
				checkNotUsed();
				return null;
			}

			@Override
			public void checkAccess(Path path, AccessMode... modes) throws IOException {
				checkNotUsed();
			}

			@Override
			public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
				checkNotUsed();
				return null;
			}

			@Override
			public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
				checkNotUsed();
			}
		}

		private abstract static class StubFileSystem extends FileSystem {
			@Override
			public void close() throws IOException {
				checkNotUsed();
			}

			@Override
			public boolean isOpen() {
				checkNotUsed();
				return false;
			}

			@Override
			public boolean isReadOnly() {
				checkNotUsed();
				return false;
			}

			@Override
			public String getSeparator() {
				checkNotUsed();
				return null;
			}

			@Override
			public Iterable<Path> getRootDirectories() {
				checkNotUsed();
				return null;
			}

			@Override
			public Iterable<FileStore> getFileStores() {
				checkNotUsed();
				return null;
			}

			@Override
			public Set<String> supportedFileAttributeViews() {
				checkNotUsed();
				return null;
			}

			@Override
			public Path getPath(String first, String... more) {
				checkNotUsed();
				return null;
			}

			@Override
			public PathMatcher getPathMatcher(String syntaxAndPattern) {
				checkNotUsed();
				return null;
			}

			@Override
			public UserPrincipalLookupService getUserPrincipalLookupService() {
				checkNotUsed();
				return null;
			}

			@Override
			public WatchService newWatchService() throws IOException {
				checkNotUsed();
				return null;
			}
		}

		private abstract static class StubPath implements Path {
			@Override
			public boolean isAbsolute() {
				checkNotUsed();
				return false;
			}

			@Override
			public Path getRoot() {
				checkNotUsed();
				return null;
			}

			@Override
			public Path getParent() {
				checkNotUsed();
				return null;
			}

			@Override
			public Path getName(int index) {
				checkNotUsed();
				return null;
			}

			@Override
			public Path subpath(int beginIndex, int endIndex) {
				checkNotUsed();
				return null;
			}

			@Override
			public boolean startsWith(Path other) {
				checkNotUsed();
				return false;
			}

			@Override
			public boolean startsWith(String other) {
				checkNotUsed();
				return false;
			}

			@Override
			public boolean endsWith(Path other) {
				checkNotUsed();
				return false;
			}

			@Override
			public boolean endsWith(String other) {
				checkNotUsed();
				return false;
			}

			@Override
			public Path normalize() {
				checkNotUsed();
				return null;
			}

			@Override
			public Path resolve(Path other) {
				checkNotUsed();
				return null;
			}

			@Override
			public Path resolve(String other) {
				checkNotUsed();
				return null;
			}

			@Override
			public Path resolveSibling(Path other) {
				checkNotUsed();
				return null;
			}

			@Override
			public Path resolveSibling(String other) {
				checkNotUsed();
				return null;
			}

			@Override
			public Path relativize(Path other) {
				checkNotUsed();
				return null;
			}

			@Override
			public URI toUri() {
				checkNotUsed();
				return null;
			}

			@Override
			public Path toAbsolutePath() {
				checkNotUsed();
				return null;
			}

			@Override
			public Path toRealPath(LinkOption... options) throws IOException {
				checkNotUsed();
				return null;
			}

			@Override
			public File toFile() {
				checkNotUsed();
				return null;
			}

			@Override
			public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
				checkNotUsed();
				return null;
			}

			@Override
			public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
				checkNotUsed();
				return null;
			}

			@Override
			public Iterator<Path> iterator() {
				checkNotUsed();
				return null;
			}

			@Override
			public int compareTo(Path other) {
				checkNotUsed();
				return 0;
			}

		}

		private abstract static class StubFileAttributeView implements BasicFileAttributeView {
			@Override
			public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
				checkNotUsed();
			}
		}

		private abstract static class StubFileAttributes implements BasicFileAttributes {
			@Override
			public FileTime lastModifiedTime() {
				checkNotUsed();
				return null;
			}

			@Override
			public FileTime lastAccessTime() {
				checkNotUsed();
				return null;
			}

			@Override
			public FileTime creationTime() {
				checkNotUsed();
				return null;
			}

			@Override
			public boolean isRegularFile() {
				checkNotUsed();
				return true;
			}

			@Override
			public boolean isSymbolicLink() {
				checkNotUsed();
				return false;
			}

			@Override
			public boolean isOther() {
				checkNotUsed();
				return false;
			}

			@Override
			public long size() {
				checkNotUsed();
				return 0;
			}

			@Override
			public Object fileKey() {
				checkNotUsed();
				return null;
			}
		}
	}
}
