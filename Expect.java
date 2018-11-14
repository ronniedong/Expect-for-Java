import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;


/**
 * Provides similar functions as the Unix Expect tool.<br>
 * There are two ways to create an Expect object: a constructor that takes an
 * {@link InputStream} handle and {@link OutputStream} handle; or spawning a
 * process by providing a comamnd String. <br>
 * <br>
 * The API is loosely based on Perl Expect library:<br>
 * <a href="http://search.cpan.org/~rgiersig/Expect-1.15/Expect.pod">
 * http://search.cpan.org/~rgiersig/Expect-1.15/Expect.pod</a>
 * 
 * <br>
 * If you are not familiar with the Tcl version of Expect, take a look at:<br>
 * <a href="http://oreilly.com/catalog/expect/chapter/ch03.html">
 * http://oreilly.com/catalog/expect/chapter/ch03.html</a> <br>
 * <br>
 * Expect uses a thread to convert InputStream to a SelectableChannel; other
 * than this, no multi-threading is used.<br>
 * A call to expect() will block for at most timeout seconds. Expect is not
 * designed to be thread-safe, in other words, do not call methods of the same
 * Expect object in different threads.
 * 
 * @author Ronnie Dong
 * @version 1.1
 */
public class Expect {
	static final Logger log = Logger.getLogger(Expect.class);
	/**Logging is turned off by default.*/
	static {
		log.setLevel(Level.OFF);
	}
	
	private OutputStream output;
	private Pipe.SourceChannel inputChannel;
	
	private Selector selector;
	
	public Expect(InputStream input, OutputStream output) {
		try {
			this.inputChannel = inputStreamToSelectableChannel(input);
			selector = Selector.open();
			inputChannel.register(selector, SelectionKey.OP_READ);
		} catch (IOException e) {
			log.fatal("Fatal error when initializing pipe or selector", e);
			//e.printStackTrace();
		}
		this.output = output;
	}
	
	/**
	 * Essentially, this method converts an {@link InputStream} to a
	 * {@link SelectableChannel}. A thread is created to read from the
	 * InputStream, and write to a pipe. The source of the pipe is returned as
	 * an input handle from which you can perform unblocking read. The thread
	 * will terminate when reading EOF from InputStream, or when InputStream is
	 * closed, or when the returned Channel is closed(pipe broken).
	 * 
	 * @param input
	 * @return a non-blocking Channel you can read from
	 * @throws IOException
	 *             most unlikely
	 * 
	 */
	private static Pipe.SourceChannel inputStreamToSelectableChannel(
			final InputStream input) throws IOException {
		Pipe pipe = Pipe.open();
		pipe.source().configureBlocking(false);
		final OutputStream out = Channels.newOutputStream(pipe.sink());
		Thread piping = new Thread(new Runnable() {
			@Override
			public void run() {
				//LOG
				byte[] buffer = new byte[1024];
				try {
					for (int n = 0; n != -1; n = input.read(buffer)) {
						out.write(buffer, 0, n);
						if (duplicatedTo != null) {
							String toWrite = new String(buffer, 0, n);
							duplicatedTo.append(toWrite);	// no Exception will be thrown
						}
					}
					log.debug("EOF from InputStream");
					input.close();		// now that input has EOF, close it.
										// other than this, do not close input
				} catch (IOException e) {
					log.warn("IOException when piping from InputStream, "
							+ "now the piping thread will end", e);
					//e.printStackTrace();
				} finally {
					try {
						log.debug("closing sink of the pipe");
						out.close();
					} catch (IOException e) {
					}
				}
			}
		});
		piping.setName("Piping InputStream to SelectableChannel Thread");
		piping.setDaemon(true);
		piping.start();
		return pipe.source();
	}
	
	private Process process = null;
	/**
	 * @return the spawned process, if this {@link Expect} object is created by
	 *         spawning a process
	 */
	public Process getProcess() {
		return process;
	}
	
	/**
	 * Creates an Expect object by spawning a command.<br>
	 * To Linux users, perhaps you need to use "bash -i" if you want to spawn
	 * Bash.<br>
	 * Note: error stream of the process is redirected to output stream.
	 * 
	 * @param command
	 * @return Expect object created using the input and output handles from the
	 *         spawned process
	 */
	public static Expect spawn(String command) {
		ProcessBuilder pb = new ProcessBuilder(command.split(" "));
		pb.redirectErrorStream(true);
		Process p;
		try {
			p = pb.start();
		} catch (IOException e) {
			//e.printStackTrace();
			log.error("Error when spawning command: " + command, e);
			return null;
		}
		Expect retv = new Expect(p.getInputStream(), p.getOutputStream());
		retv.process = p;
		return retv;
	}
	
	/**
	 * @param str
	 * Convenience method to send a string to output handle
	 */
	public void send(String str) {
		this.send(str.getBytes());
	}

	/**
	 * @param toWrite
	 * Write a byte array to the output handle, notice flush()
	 */
	public void send(byte[] toWrite) {
		//System.out.println("sending: " + bytesToPrintableString(toWrite));
		log.info("sending: " + bytesToPrintableString(toWrite));
		try {
			output.write(toWrite);
			output.flush();
		} catch (IOException e) {
			log.error("Error when sending bytes to output", e);
			//e.printStackTrace();
		}
	}

	private int default_timeout = 60;
	private boolean restart_timeout_upon_receive = false;
	private StringBuffer buffer = new StringBuffer();
	private boolean notransfer = false;
	
	/**String before the last match(if there was a match),
	 *  updated after each expect() call*/
	public String before;
	/**String representing the last match(if there was a match),
	 *  updated after each expect() call*/
	public String match;
	/**Whether the last match was successful,
	 *  updated after each expect() call*/
	public boolean isSuccess = false;
	
	public static final int RETV_TIMEOUT = -1, RETV_EOF = -2,
			RETV_IOEXCEPTION = -9;
	
	/**
	 * Convenience method, same as calling {@link #expect(int, Object...)
	 * expect(default_timeout, patterns)}
	 * 
	 * @param patterns
	 * @return
	 */
	public int expect(Object... patterns) {
		return expect(default_timeout, patterns);
	}

	/**
	 * Convenience method, internally it constructs a List{@literal <Pattern>}
	 * using the object array, and call {@link #expect(int, List) } using the
	 * List. The {@link String}s in the object array will be treated as
	 * literals; meanwhile {@link Pattern}s will be directly added to the List.
	 * If the array contains other objects, they will be converted by
	 * {@link #toString()} and then used as literal strings.
	 * 
	 * @param patterns
	 * @return
	 */
	public int expect(int timeout, Object... patterns) {
		ArrayList<Pattern> list = new ArrayList<Pattern>();
		for (Object o : patterns) {
			if (o instanceof String)
				list.add(Pattern.compile(Pattern.quote((String) o))); // requires 1.5 and up
			else if (o instanceof Pattern)
				list.add((Pattern) o);
			else{
				log.warn("Object " + o.toString() + " (class: "
						+ o.getClass().getName() + ") is neither a String nor "
						+ "a java.util.regex.Pattern, using as a literal String");
				list.add(Pattern.compile(Pattern.quote(o.toString())));
			}
		}
		return expect(timeout, list);
	}
	
	/**
	 * Expect will wait for the input handle to produce one of the patterns in
	 * the list. If a match is found, this method returns immediately;
	 * otherwise, the methods waits for up to timeout seconds, then returns. If
	 * timeout is less than or equal to 0 Expect will check one time to see if
	 * the internal buffer contains the pattern.
	 * 
	 * @param timeout
	 *            timeout in seconds
	 * @param list
	 *            List of Java {@link Pattern}s used for match the internal
	 *            buffer obtained by reading the InputStream
	 * @return position of the matched pattern within the list (starting from
	 *         0); or a negative number if there is an IOException, EOF or
	 *         timeout
	 */
	public int expect(int timeout, List<Pattern> list) {
		log.debug("Expecting " + list);
		
		clearGlobalVariables();
		long endTime = System.currentTimeMillis() + (long)timeout * 1000;
		
		try {
			ByteBuffer bytes = ByteBuffer.allocate(1024);
			int n;
			while (true) {
				for (int i = 0; i < list.size(); i++) {
					log.trace("trying to match " + list.get(i)
							+ " against buffer \"" + buffer + "\"");
					Matcher m = list.get(i).matcher(buffer);
					if (m.find()) {
						log.trace("success!");
						int matchStart = m.start(), matchEnd = m.end();
						this.before = buffer.substring(0, matchStart);
						this.match = m.group();
						this.isSuccess = true;
						if(!notransfer)buffer.delete(0, matchEnd);
						return i;
					}
				}

				long waitTime = endTime - System.currentTimeMillis();
				if (restart_timeout_upon_receive)
					waitTime = (long) (timeout * 1000);
				if (waitTime <= 0) {
					log.debug("Timeout when expecting " + list);
					return RETV_TIMEOUT;
				}
				//System.out.println("waiting for "+waitTime);

				selector.select(waitTime);
				//System.out.println(selector.selectedKeys().size());
				if (selector.selectedKeys().size() == 0) {
					//System.err.println("timeout!");
					//break;	//we can directly "break" here
					log.debug("Timeout when expecting " + list);
					return RETV_TIMEOUT;
				}
				selector.selectedKeys().clear();
				if ((n = inputChannel.read(bytes)) == -1) {
					//System.err.println("EOF!");
					//break;
					log.debug("EOF when expecting " + list);
					return RETV_EOF;
				}
				StringBuilder tmp = new StringBuilder();
				for (int i = 0; i < n; i++) {
					buffer.append((char) bytes.get(i));
					tmp.append(byteToPrintableString(bytes.get(i)));
				}
				log.debug("Obtained following from InputStream: " + tmp);
				bytes.clear();
				
				//System.out.println(buffer);
			}
		} catch (IOException e) {
			//e.printStackTrace();
			log.error("IOException when selecting or reading", e);
			thrownIOE = e;
			return RETV_IOEXCEPTION;
		}
		
	}

	/**
	 * Convenience method, internally it calls {@link #expect(int, List)
	 * expect(timeout, new ArrayList&lt;Pattern&gt;())}. Given an empty list,
	 * {@link #expect(int, List)} will not perform any regex matching, therefore
	 * the only conditions for it to return is EOF or timeout (or IOException).
	 * If EOF is detected, {@link #isSuccess} and {@link #before} are properly
	 * set.
	 * 
	 * @param timeout
	 * @return same as return value of {@link #expect(int, List)}
	 */
	public int expectEOF(int timeout) {
		int retv = expect(timeout, new ArrayList<Pattern>());
		if (retv == RETV_EOF) {
			this.isSuccess = true;
			this.before = this.buffer.toString();
			this.buffer.delete(0, buffer.length());
		}
		return retv;
	}
	/**Convenience method, same as calling {@link #expectEOF(int)
	 * expectEOF(default_timeout)}*/
	public int expectEOF() {
		return expectEOF(default_timeout);
	}
	
	/**
	 * Throws checked exceptions when expectEOF was not successful.
	 */
	public int expectEOFOrThrow(int timeout) throws TimeoutException,
			IOException {
		int retv = expectEOF(timeout);
		if (retv == RETV_TIMEOUT)
			throw new TimeoutException();
		if (retv == RETV_IOEXCEPTION)
			throw thrownIOE;
		return retv;
	}
	/**Convenience method, same as calling {@link #expectEOF(int)
	 * expectEOF(default_timeout)}*/
	public int expectEOFOrThrow() throws TimeoutException, IOException {
		return expectEOFOrThrow(default_timeout);
	}

	/**useful when calling {@link #expectOrThrow(int, Object...)}*/
	private IOException thrownIOE;
	
	/**
	 * This method calls {@link #expect(int, Object...) expect(timeout,
	 * patterns)}, and throws checked exceptions when expect was not successful.
	 * Useful when you want to simplify error handling: for example, when you
	 * send a series of commands to an SSH server, you expect a prompt after
	 * each send, however the server may die or the prompt may take forever to
	 * appear, you would want to skip the following commands if those occurred.
	 * In such a case this method will be handy.
	 * 
	 * @param timeout
	 * @param patterns
	 * @throws TimeoutException
	 *             when expect times out
	 * @throws EOFException
	 *             when EOF is encountered
	 * @throws IOException
	 *             when there is a problem reading from the InputStream
	 * @return same as {@link #expect(int, Object...) expect(timeout, patterns)}
	 */
	public int expectOrThrow(int timeout, Object... patterns)
			throws TimeoutException, EOFException, IOException {
		int retv = expect(timeout, patterns);
		switch (retv) {
		case RETV_TIMEOUT:
			throw new TimeoutException();
		case RETV_EOF:
			throw new EOFException();
		case RETV_IOEXCEPTION:
			throw thrownIOE;
		default:
			return retv;
		}
	}
	/**Convenience method, same as calling {@link #expectOrThrow(int, Object...)
	 * expectOrThrow(default_timeout, patterns)}*/
	public int expectOrThrow(Object... patterns) throws TimeoutException,
			EOFException, IOException {
		return expectOrThrow(default_timeout, patterns);
	}
	
	private void clearGlobalVariables() {
		isSuccess = false;
		match = null;
		before = null;
	}
	
	/**
	 * The OutputStream passed to Expect constructor is closed; the InputStream
	 * is not closed (there is no need to close the InputStream).<br>
	 * It is suggested that this method be called after the InputStream has come
	 * to EOF. For example, when you connect through SSH, send an "exit" command
	 * first, and then call this method.<br>
	 * <br>
	 * 
	 * When this method is called, the thread which write to the sink of the
	 * pipe will end.
	 */
	public void close() {
		try {
			this.output.close();
		} catch (IOException e) {
			log.warn("Exception when closing OutputStream", e);
			//e.printStackTrace();
		}
		try {
			this.inputChannel.close();
		} catch (IOException e) {
			log.warn("Exception when closing input Channel", e);
			//e.printStackTrace();
		}
	}
	
	public int getDefault_timeout() {
		return default_timeout;
	}
	public void setDefault_timeout(int default_timeout) {
		this.default_timeout = default_timeout;
	}
	public boolean isRestart_timeout_upon_receive() {
		return restart_timeout_upon_receive;
	}
	public void setRestart_timeout_upon_receive(boolean restart_timeout_upon_receive) {
		this.restart_timeout_upon_receive = restart_timeout_upon_receive;
	}
	public void setNotransfer(boolean notransfer) {
		this.notransfer = notransfer;
	}
	public boolean isNotransfer() {
		return notransfer;
	}

	/**
	 * Static method used for convert byte array to string, each byte is
	 * converted to an ASCII character, if the byte represents a control
	 * character, it is replaced by a printable caret notation <a
	 * href="http://en.wikipedia.org/wiki/ASCII">
	 * http://en.wikipedia.org/wiki/ASCII </a>, or an escape code if possible.
	 * 
	 * @param bytes
	 *            bytes to be printed
	 * @return String representation of the byte array
	 */
	public static String bytesToPrintableString(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes)
			sb.append(byteToPrintableString(b));
		return sb.toString();
	}
	public static String byteToPrintableString(byte b) {
		String s = new String(new byte[] { b });
		// control characters
		if (b >= 0 && b < 32) s = "^" + (char) (b + 64);
		else if (b == 127) s = "^?";
		// some escape characters
		if (b == 9) s = "\\t";
		if (b == 10) s = "\\n";
		if (b == 13) s = "\\r";
		return s;
	}
	
	@SuppressWarnings("serial")
	public static class TimeoutException extends Exception{
	}
	@SuppressWarnings("serial")
	public static class EOFException extends Exception{
	}
	
	private static Layout layout = new PatternLayout(
			PatternLayout.TTCC_CONVERSION_PATTERN);

	public static void addLogToConsole(Level level) {
		log.setLevel(Level.ALL);
		ConsoleAppender console = new ConsoleAppender(layout);
		console.setThreshold(level);
		log.addAppender(console);
	}
	public static void addLogToFile(String filename, Level level) throws IOException {
		log.setLevel(Level.ALL);
		FileAppender file = new FileAppender(layout, filename);
		file.setThreshold(level);
		log.addAppender(file);
	}
	public static void turnOffLogging(){
		log.setLevel(Level.OFF);
		log.removeAllAppenders();
	}
	
	private static PrintStream duplicatedTo = null;
	/**
	 * While performing expect operations on the InputStream provided, duplicate
	 * the contents obtained from InputStream to a PrintStream (you can use
	 * System.err or System.out). <b>DO NOT</b> call this function while there
	 * are live Expect objects as this may cause the piping thread to end due to
	 * unsynchronized code; if you need this feature, add the following to both
	 * {@link #inputStreamToSelectableChannel(InputStream)} and
	 * {@link #forwardInputStreamTo(PrintStream)}:
	 * <pre>
	 * {@code
	 * 	synchronized(Expect.duplicatedTo) {...}
	 * </pre>
	 * @param duplicatedTo
	 *            call with null if you want to turn off
	 */
	public static void forwardInputStreamTo(PrintStream duplicatedTo) {
		Expect.duplicatedTo = duplicatedTo;
	}

}
