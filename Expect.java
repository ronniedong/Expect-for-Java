import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Provide an object that provide similar functions as Expect tool.<br>
 * There are two ways to create an Expect object: providing an
 * {@link InputStream} handle and {@link OutputStream} handle; or spawning a
 * process by providing a comamnd String.
 * <br>
 * <br>
 * When designing the API, I used the Perl Expect library for reference: 
 * <a href="http://search.cpan.org/~rgiersig/Expect-1.15/Expect.pod">
 * http://search.cpan.org/~rgiersig/Expect-1.15/Expect.pod</a>
 * 
 * <br>
 * If you are not familiar with the Tcl version of Expect, take a look at: 
 * <a href="http://oreilly.com/catalog/expect/chapter/ch03.html">
 * http://oreilly.com/catalog/expect/chapter/ch03.html</a>
 * 
 * @author Ronnie Dong
 */
public class Expect {
	
	OutputStream output;
	Pipe.SourceChannel inputChannel;
	
	private Selector selector;
	
	public Expect(InputStream input, OutputStream output) {
		try {
			this.inputChannel = inputStreamToSelectableChannel(input);
			selector = Selector.open();
			inputChannel.register(selector, SelectionKey.OP_READ);
		} catch (IOException e) {
			e.printStackTrace();
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
					for (int n = 0; n != -1; n = input.read(buffer))
						out.write(buffer, 0, n);
					input.close();		// now that input has EOF, close it.
										// other than this, do not close input
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try {
						//LOG
						out.close();
					} catch (IOException e) {
					}
				}
			}
		});
		piping.setName("Piping InputStream to a SelectableChannel");
		piping.setDaemon(true);
		piping.start();
		return pipe.source();
	}
	
	/**
	 * Creates an Expect object by spawning a command.<br>
	 * To Linux users, perhaps you need to use "bash -i" if you want to spawn
	 * Bash.<br>
	 * 
	 * @param command
	 * @return
	 */
	public static Expect spawn(String command) {
		ProcessBuilder pb = new ProcessBuilder(command.split(" "));
		pb.redirectErrorStream(true);
		Process p;
		try {
			p = pb.start();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		return new Expect(p.getInputStream(), p.getOutputStream());
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
		System.out.println("sending: " + bytesToPrintableString(toWrite));
		try {
			output.write(toWrite);
			output.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	int default_timeout = 60;
	boolean restart_timeout_upon_receive = false;
	StringBuffer buffer = new StringBuffer();
	String before;
	String match;

	boolean isSuccess = false;
	
	public void expectLiteral(String literal) {
		expect(Pattern.quote(literal)); // requires 1.5 and up
	}

	public void expectLiteral(int timeout, String literal) {
		expect(timeout, Pattern.quote(literal));
	}

	public void expect(String expect) {
		expect(default_timeout, expect);
	}

	/**
	 * Expect will wait for the input handle to produce the pattern. If a match
	 * is found, this method returns immediately; otherwise, the methods waits
	 * for up to timeout seconds, then returns. If timeout is less than or equal
	 * to 0 Expect will check one time to see if the internal buffer contains
	 * the pattern.
	 * 
	 * @param timeout
	 *            timeout in seconds
	 * @param pattern
	 *            string representation of a regular expression used for
	 *            matching
	 */
	public void expect(int timeout, String pattern) {
		clearMatch();
		long endTime = System.currentTimeMillis() + timeout * 1000;
		
		try {
			ByteBuffer bytes = ByteBuffer.allocate(1024);
			int n;
			while (true) {
				Pattern p = Pattern.compile(pattern);
				Matcher m = p.matcher(buffer);
				if (m.find()) {
					int matchStart = m.start(), matchEnd = m.end();
					this.before = buffer.substring(0, matchStart);
					this.match = m.group();
					this.isSuccess = true;
					buffer.delete(0, matchEnd);
					return;
				}

				long waitTime = endTime - System.currentTimeMillis();
				if (restart_timeout_upon_receive)
					waitTime = timeout * 1000;
				if (waitTime <= 0)
					return;
				//System.out.println("waiting for "+waitTime);

				selector.select(waitTime);
				//System.out.println(selector.selectedKeys().size());
				if (selector.selectedKeys().size() == 0) {
					System.err.println("timeout!");
					break;	//we can directly "break" here
				}
				selector.selectedKeys().clear();
				if ((n = inputChannel.read(bytes)) == -1) {
					System.err.println("EOF!");
					break;
				}
				for (int i = 0; i < n; i++)
					buffer.append((char) bytes.get(i));
				bytes.clear();
				
				//System.out.println(buffer);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	private void clearMatch() {
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
			e.printStackTrace();
		}
		try {
			this.inputChannel.close();
		} catch (IOException e) {
			e.printStackTrace();
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
	public boolean isSuccess() {
		return isSuccess;
	}

	public static String bytesToPrintableString(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			String s = new String(new byte[] { b });
			// control characters
			if (b >= 0 && b < 32) s = "^" + (char) (b + 64);
			else if (b == 127) s = "^?";
			// some escape characters
			if (b == 9) s = "\\t";
			if (b == 10) s = "\\n";
			if (b == 13) s = "\\r";
			sb.append(s);
		}
		return sb.toString();
	}

}
