package launcher.helper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import launcher.LauncherAPI;

public final class IOHelper {
	// Charset
	@LauncherAPI public static final Charset UNICODE_CHARSET = StandardCharsets.UTF_8;
	@LauncherAPI public static final Charset ASCII_CHARSET = StandardCharsets.US_ASCII;

	// Constants
	@LauncherAPI public static final int TIMEOUT = 30000;
	@LauncherAPI public static final int BUFFER_SIZE = 0x10000;
	@LauncherAPI public static final String CROSS_SEPARATOR = "/";
	@LauncherAPI public static final FileSystem FS = FileSystems.getDefault();
	@LauncherAPI public static final String PLATFORM_SEPARATOR = FS.getSeparator();
	@LauncherAPI public static final boolean POSIX = FS.supportedFileAttributeViews().contains("posix");

	// Paths
	@LauncherAPI public static final Path JVM_DIR = Paths.get(System.getProperty("java.home"));
	@LauncherAPI public static final Path HOME_DIR = Paths.get(System.getProperty("user.home"));
	@LauncherAPI public static final Path WORKING_DIR = Paths.get(System.getProperty("user.dir"));

	// File options
	private static final LinkOption[] LINK_OPTIONS = {};
	private static final OpenOption[] READ_OPTIONS = { StandardOpenOption.READ };
	private static final CopyOption[] COPY_OPTIONS = { StandardCopyOption.REPLACE_EXISTING };
	private static final OpenOption[] APPEND_OPTIONS = { StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND };
	private static final OpenOption[] WRITE_OPTIONS = { StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING };
	private static final Set<FileVisitOption> WALK_OPTIONS = Collections.singleton(FileVisitOption.FOLLOW_LINKS);

	private IOHelper() {
	}

	@LauncherAPI
	public static void close(AutoCloseable closeable) {
		try {
			closeable.close();
		} catch (Exception exc) {
			LogHelper.error(exc);
		}
	}

	@LauncherAPI
	public static void copy(Path source, Path target) throws IOException {
		createParentDirs(target);
		Files.copy(source, target, COPY_OPTIONS);
	}

	@LauncherAPI
	public static void createParentDirs(Path path) throws IOException {
		Path parent = path.getParent();
		if (parent != null && !isDir(parent)) {
			Files.createDirectories(parent);
		}
	}

	@LauncherAPI
	public static String decode(byte[] bytes) {
		return new String(bytes, UNICODE_CHARSET);
	}

	@LauncherAPI
	public static String decodeASCII(byte[] bytes) {
		return new String(bytes, ASCII_CHARSET);
	}

	@LauncherAPI
	public static void deleteDir(Path dir, boolean self) throws IOException {
		walk(dir, new DeleteDirVisitor(dir, self), true);
	}

	@LauncherAPI
	public static byte[] encode(String s) {
		return s.getBytes(UNICODE_CHARSET);
	}

	@LauncherAPI
	public static byte[] encodeASCII(String s) {
		return s.getBytes(ASCII_CHARSET);
	}

	@LauncherAPI
	public static boolean exists(Path path) {
		return Files.exists(path, LINK_OPTIONS);
	}

	@LauncherAPI
	public static Path getCodeSource(Class<?> clazz) {
		return Paths.get(toURI(clazz.getProtectionDomain().getCodeSource().getLocation()));
	}

	@LauncherAPI
	public static String getFileName(Path path) {
		return path.getFileName().toString();
	}

	@LauncherAPI
	public static String getIP(SocketAddress address) {
		return ((InetSocketAddress) address).getAddress().getHostAddress();
	}

	@LauncherAPI
	public static byte[] getResourceBytes(String name) throws IOException {
		return read(getResourceURL(name));
	}

	@LauncherAPI
	public static URL getResourceURL(String name) throws NoSuchFileException {
		URL url = ClassLoader.getSystemResource(name);
		if (url == null) {
			throw new NoSuchFileException(name);
		}
		return url;
	}

	@LauncherAPI
	public static boolean hasExtension(Path file, String extension) {
		return getFileName(file).endsWith('.' + extension);
	}

	@LauncherAPI
	public static boolean isDir(Path path) {
		return Files.isDirectory(path, LINK_OPTIONS);
	}

	@LauncherAPI
	public static boolean isEmpty(Path dir) throws IOException {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			return !stream.iterator().hasNext();
		}
	}

	@LauncherAPI
	public static boolean isFile(Path path) {
		return Files.isRegularFile(path, LINK_OPTIONS);
	}

	@LauncherAPI
	public static boolean isValidFileName(String fileName) {
		return !fileName.equals(".") && !fileName.equals("..") && fileName.chars().noneMatch(ch -> ch == '/' || ch == '\\') && isValidPath(fileName);
	}

	@LauncherAPI
	public static boolean isValidPath(String path) {
		try {
			toPath(path);
			return true;
		} catch (InvalidPathException ignored) {
			return false;
		}
	}

	@LauncherAPI
	public static void move(Path source, Path target) throws IOException {
		createParentDirs(target);
		Files.move(source, target, COPY_OPTIONS);
	}

	@LauncherAPI
	public static byte[] newBuffer() {
		return new byte[BUFFER_SIZE];
	}

	@LauncherAPI
	public static ByteArrayOutputStream newByteArrayOutput() {
		return new ByteArrayOutputStream(BUFFER_SIZE);
	}

	@LauncherAPI
	public static char[] newCharBuffer() {
		return new char[BUFFER_SIZE];
	}

	@LauncherAPI
	public static InputStream newInput(URL url) throws IOException {
		URLConnection connection = url.openConnection();
		if (connection instanceof HttpURLConnection) {
			connection.setReadTimeout(TIMEOUT);
			connection.setConnectTimeout(TIMEOUT);
		}
		return connection.getInputStream();
	}

	@LauncherAPI
	public static InputStream newInput(Path file) throws IOException {
		return Files.newInputStream(file, READ_OPTIONS);
	}

	@LauncherAPI
	public static OutputStream newOutput(Path file) throws IOException {
		return newOutput(file, false);
	}

	@LauncherAPI
	public static OutputStream newOutput(Path file, boolean append) throws IOException {
		createParentDirs(file);
		return Files.newOutputStream(file, append ? APPEND_OPTIONS : WRITE_OPTIONS);
	}

	@LauncherAPI
	public static BufferedReader newReader(InputStream input) {
		return newReader(input, UNICODE_CHARSET);
	}

	@LauncherAPI
	public static BufferedReader newReader(InputStream input, Charset charset) {
		return new BufferedReader(new InputStreamReader(input, charset), BUFFER_SIZE);
	}

	@LauncherAPI
	public static BufferedReader newReader(URL url) throws IOException {
		return newReader(newInput(url));
	}

	@LauncherAPI
	public static BufferedReader newReader(Path file) throws IOException {
		return Files.newBufferedReader(file, UNICODE_CHARSET);
	}

	@LauncherAPI
	public static Socket newSocket() throws SocketException {
		Socket socket = new Socket();
		setSocketFlags(socket);
		return socket;
	}

	@LauncherAPI
	public static BufferedWriter newWriter(OutputStream output) {
		return new BufferedWriter(new OutputStreamWriter(output, UNICODE_CHARSET), BUFFER_SIZE);
	}

	@LauncherAPI
	public static BufferedWriter newWriter(Path file) throws IOException {
		return newWriter(file, false);
	}

	@LauncherAPI
	public static BufferedWriter newWriter(Path file, boolean append) throws IOException {
		createParentDirs(file);
		return Files.newBufferedWriter(file, UNICODE_CHARSET, append ? APPEND_OPTIONS : WRITE_OPTIONS);
	}

	@LauncherAPI
	public static BufferedWriter newWriter(FileDescriptor fd) {
		return newWriter(new FileOutputStream(fd));
	}

	@LauncherAPI
	public static ZipEntry newZipEntry(String name) {
		ZipEntry entry = new ZipEntry(name);
		entry.setTime(0);
		return entry;
	}

	@LauncherAPI
	public static ZipEntry newZipEntry(ZipEntry entry) {
		return newZipEntry(entry.getName());
	}

	@LauncherAPI
	public static ZipInputStream newZipInput(InputStream input) {
		return new ZipInputStream(input, UNICODE_CHARSET);
	}

	@LauncherAPI
	public static ZipInputStream newZipInput(URL url) throws IOException {
		return newZipInput(newInput(url));
	}

	@LauncherAPI
	public static ZipInputStream newZipInput(Path file) throws IOException {
		return newZipInput(newInput(file));
	}

	@LauncherAPI
	public static byte[] read(Path file) throws IOException {
		long size = readAttributes(file).size();
		if (size > Integer.MAX_VALUE) {
			throw new IOException("File too big");
		}

		// Read bytes from file
		byte[] bytes = new byte[(int) size];
		try (InputStream input = newInput(file)) {
			read(input, bytes);
		}

		// Return result
		return bytes;
	}

	@LauncherAPI
	public static byte[] read(URL url) throws IOException {
		try (InputStream input = newInput(url)) {
			return read(input);
		}
	}

	@LauncherAPI
	public static void read(InputStream input, byte[] bytes) throws IOException {
		int offset = 0;
		while (offset < bytes.length) {
			int length = input.read(bytes, offset, bytes.length - offset);
			if (length < 0) {
				throw new EOFException(String.format("%d bytes remaining", bytes.length - offset));
			}
			offset += length;
		}
	}

	@LauncherAPI
	public static byte[] read(InputStream input) throws IOException {
		try (ByteArrayOutputStream output = newByteArrayOutput()) {
			transfer(input, output);
			return output.toByteArray();
		}
	}

	@LauncherAPI
	public static BasicFileAttributes readAttributes(Path path) throws IOException {
		return Files.readAttributes(path, BasicFileAttributes.class, LINK_OPTIONS);
	}

	@LauncherAPI
	public static String request(URL url) throws IOException {
		return decode(read(url)).trim();
	}

	@LauncherAPI
	public static InetSocketAddress resolve(InetSocketAddress address) {
		if (address.isUnresolved()) { // Create resolved address
			return new InetSocketAddress(address.getHostString(), address.getPort());
		}
		return address;
	}

	@LauncherAPI
	public static Path resolveIncremental(Path dir, String name, String extension) {
		Path original = dir.resolve(name + '.' + extension);
		if (!exists(original)) { // Not need to increment
			return original;
		}

		// Incremental resolve
		int counter = 1;
		while (true) {
			Path path = dir.resolve(String.format("%s (%d).%s", name, counter, extension));
			if (exists(path)) {
				counter++;
				continue;
			}
			return path;
		}
	}

	@LauncherAPI
	public static Path resolveJavaBin(Path javaDir) {
		// Get Java binaries path
		Path javaBinDir = (javaDir == null ? JVM_DIR : javaDir).resolve("bin");

		// Verify has "javaw.exe" file
		if (!LogHelper.isDebugEnabled()) {
			Path javawExe = javaBinDir.resolve("javaw.exe");
			if (isFile(javawExe)) {
				return javawExe;
			}
		}

		// Verify has "java.exe" file
		Path javaExe = javaBinDir.resolve("java.exe");
		if (isFile(javaExe)) {
			return javaExe;
		}

		// Verify has "java" file
		Path java = javaBinDir.resolve("java");
		if (isFile(java)) {
			return java;
		}

		// Throw exception as no runnable found
		throw new RuntimeException("Java binary wasn't found");
	}

	@LauncherAPI
	public static void setSocketFlags(Socket socket) throws SocketException {
		// Set socket flags
		socket.setKeepAlive(false);
		socket.setTcpNoDelay(false);
		socket.setReuseAddress(true);

		// Set socket options
		socket.setSoTimeout(TIMEOUT);
		socket.setTrafficClass(0b11100);
		socket.setSendBufferSize(BUFFER_SIZE);
		socket.setReceiveBufferSize(BUFFER_SIZE);
		socket.setPerformancePreferences(0, 1, 2);
	}

	@LauncherAPI
	public static Path toPath(String path) {
		return Paths.get(path.replace(PLATFORM_SEPARATOR, CROSS_SEPARATOR));
	}

	@LauncherAPI
	public static String toString(Path path) {
		return path.toString().replace(PLATFORM_SEPARATOR, CROSS_SEPARATOR);
	}

	@LauncherAPI
	public static URI toURI(URL url) {
		try {
			return url.toURI();
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@LauncherAPI
	public static URL toURL(Path path) {
		try {
			return path.toUri().toURL();
		} catch (MalformedURLException e) {
			throw new AssertionError(e);
		}
	}

	@LauncherAPI
	public static int transfer(InputStream input, OutputStream output) throws IOException {
		int transferred = 0;
		byte[] buffer = newBuffer();
		for (int length = input.read(buffer); length >= 0; length = input.read(buffer)) {
			output.write(buffer, 0, length);
			transferred += length;
		}
		return transferred;
	}

	@LauncherAPI
	public static void transfer(Path file, OutputStream output) throws IOException {
		try (InputStream input = newInput(file)) {
			transfer(input, output);
		}
	}

	@LauncherAPI
	public static int transfer(InputStream input, Path file) throws IOException {
		return transfer(input, file, false);
	}

	@LauncherAPI
	public static int transfer(InputStream input, Path file, boolean append) throws IOException {
		try (OutputStream output = newOutput(file, append)) {
			return transfer(input, output);
		}
	}

	@LauncherAPI
	public static String urlDecode(String s) {
		try {
			return URLDecoder.decode(s, UNICODE_CHARSET.name());
		} catch (UnsupportedEncodingException e) {
			throw new InternalError(e);
		}
	}

	@LauncherAPI
	public static String urlEncode(String s) {
		try {
			return URLEncoder.encode(s, UNICODE_CHARSET.name());
		} catch (UnsupportedEncodingException e) {
			throw new InternalError(e);
		}
	}

	@LauncherAPI
	public static String verifyFileName(String fileName) {
		return VerifyHelper.verify(fileName, IOHelper::isValidFileName, String.format("Invalid file name: '%s'", fileName));
	}

	@LauncherAPI
	public static int verifyLength(int length, int max) throws IOException {
		if (length < 0 || max < 0 && length != -max || max > 0 && length > max) {
			throw new IOException("Illegal length: " + length);
		}
		return length;
	}

	@LauncherAPI
	public static String verifyURL(String url) {
		try {
			new URL(url).toURI();
			return url;
		} catch (MalformedURLException | URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@LauncherAPI
	public static void walk(Path dir, FileVisitor<Path> visitor, boolean hidden) throws IOException {
		Files.walkFileTree(dir, WALK_OPTIONS, Integer.MAX_VALUE, hidden ? visitor : new SkipHiddenVisitor(visitor));
	}

	@LauncherAPI
	public static void write(Path file, byte[] bytes) throws IOException {
		createParentDirs(file);
		Files.write(file, bytes, WRITE_OPTIONS);
	}

	private static final class DeleteDirVisitor extends SimpleFileVisitor<Path> {
		private final Path dir;
		private final boolean self;

		private DeleteDirVisitor(Path dir, boolean self) {
			this.dir = dir;
			this.self = self;
		}

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
			FileVisitResult result = super.postVisitDirectory(dir, exc);
			if (self || !this.dir.equals(dir)) {
				Files.delete(dir);
			}
			return result;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			Files.delete(file);
			return super.visitFile(file, attrs);
		}
	}

	private static final class SkipHiddenVisitor implements FileVisitor<Path> {
		private final FileVisitor<Path> visitor;

		private SkipHiddenVisitor(FileVisitor<Path> visitor) {
			this.visitor = visitor;
		}

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
			return Files.isHidden(dir) ? FileVisitResult.CONTINUE : visitor.postVisitDirectory(dir, exc);
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
			return Files.isHidden(dir) ? FileVisitResult.SKIP_SUBTREE : visitor.preVisitDirectory(dir, attrs);
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			return Files.isHidden(file) ? FileVisitResult.CONTINUE : visitor.visitFile(file, attrs);
		}

		@Override
		public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
			return visitor.visitFileFailed(file, exc);
		}
	}
}