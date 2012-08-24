import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.util.regex.Pattern;

import org.apache.log4j.Level;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * JUnit Tests for Expect. Using a pipe and another thread to simulate bytes
 * coming out of the InputStream handle of an Expect object.
 * 
 * @author Ronnie Dong
 * 
 */
public class TestExpect {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}
	
	/**
	 * test successful expect and timeout
	 */
	@Test
	public void testExpect(){
		final Pipe pipe;
		try {
			pipe = Pipe.open();
		} catch (IOException e) {
			e.printStackTrace();
			fail("failed to open pipe!");
			return;
		}
		final InputStream in = Channels.newInputStream(pipe.source());
		final OutputStream out = Channels.newOutputStream(pipe.sink());
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					sleep(5);
					out.write("hello".getBytes());
					sleep(10);
					out.write(" world".getBytes());
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try { out.close(); } catch (IOException e) {}
				}
			}
		}).start();
		
		Expect.addLogToConsole(Level.ALL);
		
		Expect expect = new Expect(in, new NullOutputStream());
		expect.expect(10, Pattern.compile(".*llo"));
		assertEquals("hello", expect.match);
		int retv = expect.expect(5, "world");
		assertNull(expect.match);
		assertEquals(retv, Expect.RETV_TIMEOUT);
		expect.expect(20, "world");
		assertEquals("world", expect.match);
		expect.expectEOF(60);
		assertTrue(expect.isSuccess);
		expect.close();
		
		Expect.turnOffLogging();
	}
	
	/**
	 * see if expect will timeout even if InputStream keeps receiving bytes. The
	 * thread keeps sending "hello" for 2 seconds, expect will timeout in 1
	 * seconds.
	 */
	@Test
	public void testTimeout(){
		final Pipe pipe;
		try {
			pipe = Pipe.open();
		} catch (IOException e) {
			e.printStackTrace();
			fail("failed to open pipe!");
			return;
		}
		final InputStream in = Channels.newInputStream(pipe.source());
		final OutputStream out = Channels.newOutputStream(pipe.sink());
		
		/** send "hello" for 2 seconds*/
		final Thread writeThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (true) {
						if (Thread.interrupted())
							break;
						out.write("hello".getBytes());
					}
					// Once the current thread is interrupted, the out channel is closed.
					// see ClosedByInterruptException
					// so this will not work.
					//out.write("world".getBytes());
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try { out.close(); } catch (IOException e) {}
				}
			}
		});
		writeThread.start();
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {}
				writeThread.interrupt();
			}
		}).start();
		
		Expect expect = new Expect(in, new NullOutputStream());
		int retv = expect.expect(1, "world");
		assertNull(expect.match);
		assertEquals(retv, Expect.RETV_TIMEOUT);
		//expect.expect(100, "world");
		//Assert.assertNotNull(expect.match);
		expect.expectEOF(60);
		assertTrue(expect.isSuccess);
		expect.close();
	}

	/**
	 * test resetting timeout when receives anything from InputStream. "!" is
	 * sent after 15 seconds, expect times out in 10 seconds. Because there are
	 * new bytes every 5 seconds, it will not timeout.
	 */
	@Test
	public void testRestart_timeout_upon_receive(){
		final Pipe pipe;
		try {
			pipe = Pipe.open();
		} catch (IOException e) {
			e.printStackTrace();
			fail("failed to open pipe!");
			return;
		}
		final InputStream in = Channels.newInputStream(pipe.source());
		final OutputStream out = Channels.newOutputStream(pipe.sink());
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					sleep(5);
					out.write("hello".getBytes());
					sleep(5);
					out.write(" world".getBytes());
					sleep(5);
					out.write("!".getBytes());
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try { out.close(); } catch (IOException e) {}
				}
			}
		}).start();
		
		Expect expect = new Expect(in, new NullOutputStream());
		expect.setRestart_timeout_upon_receive(true);
		expect.expect(10, "!");
		assertEquals("!", expect.match);
		expect.close();
	}
	
	@Test
	public void testNoTransfer(){
		final Pipe pipe;
		try {
			pipe = Pipe.open();
		} catch (IOException e) {
			e.printStackTrace();
			fail("failed to open pipe!");
			return;
		}
		final InputStream in = Channels.newInputStream(pipe.source());
		final OutputStream out = Channels.newOutputStream(pipe.sink());
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					out.write("hello".getBytes());
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try { out.close(); } catch (IOException e) {}
				}
			}
		}).start();
		
		Expect expect = new Expect(in, new NullOutputStream());
		expect.setNotransfer(true);
		expect.expect("hello");
		assertEquals("hello", expect.match);
		expect.expect("hello");
		assertEquals("hello", expect.match);
		expect.setNotransfer(false);
		expect.expect(5, "hello");
		assertEquals("hello", expect.match);
		expect.expect("hello");
		assertNull(expect.match);
		expect.close();
	}
	
	/**
	 * test expecting multiple patterns, (String will be treated as literal)
	 */
	@Test
	public void testMultipleExpect(){
		final Pipe pipe;
		try {
			pipe = Pipe.open();
		} catch (IOException e) {
			e.printStackTrace();
			fail("failed to open pipe!");
			return;
		}
		final InputStream in = Channels.newInputStream(pipe.source());
		final OutputStream out = Channels.newOutputStream(pipe.sink());
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					out.write("hello".getBytes());
					sleep(5);
					out.write(" world".getBytes());
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try { out.close(); } catch (IOException e) {}
				}
			}
		}).start();
		
		Expect.addLogToConsole(Level.ALL);
		
		Expect expect = new Expect(in, new NullOutputStream());
		int retv = expect.expect(Pattern.compile(".*llo"), " wor");
		assertEquals("hello", expect.match);
		assertEquals(retv, 0);
		
		retv = expect.expect(Pattern.compile(".*llo"), " wor");
		assertEquals(" wor", expect.match);
		assertEquals(retv, 1);
		
		expect.expectEOF();
		expect.close();
		
		Expect.turnOffLogging();
	}
	
	/**
	 * test expectEOF
	 */
	@Test
	public void testExpectEOF(){
		final Pipe pipe;
		try {
			pipe = Pipe.open();
		} catch (IOException e) {
			e.printStackTrace();
			fail("failed to open pipe!");
			return;
		}
		final InputStream in = Channels.newInputStream(pipe.source());
		final OutputStream out = Channels.newOutputStream(pipe.sink());
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					out.write("hello".getBytes());
					out.write(" world!".getBytes());
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try { out.close(); } catch (IOException e) {}
				}
			}
		}).start();
		
		
		Expect expect = new Expect(in, new NullOutputStream());
		
		int retv = expect.expectEOF();
		assertEquals(retv, Expect.RETV_EOF);
		assertEquals("hello world!", expect.before);
		assertTrue(expect.isSuccess);
		
		retv = expect.expectEOF();
		assertEquals(retv, Expect.RETV_EOF);
		assertEquals("", expect.before);
		assertTrue(expect.isSuccess);
		
		expect.close();
	}
	
	/**
	 * test throwing EOFException
	 */
	@Test(expected = Expect.EOFException.class)
	public void testEOFException() throws Expect.TimeoutException,
			Expect.EOFException, IOException {
		final Pipe pipe;
		try {
			pipe = Pipe.open();
		} catch (IOException e) {
			e.printStackTrace();
			fail("failed to open pipe!");
			return;
		}
		final InputStream in = Channels.newInputStream(pipe.source());
		final OutputStream out = Channels.newOutputStream(pipe.sink());
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					out.write("hello".getBytes());
					out.write(" world!".getBytes());
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try { out.close(); } catch (IOException e) {}
				}
			}
		}).start();
		
		
		Expect expect = new Expect(in, new NullOutputStream());
		
		expect.expectOrThrow("nomatch");
		expect.expectEOF();
		
		expect.close();
	}
	
	/**
	 * test throwing TimeoutException
	 */
	@Test(expected = Expect.TimeoutException.class)
	public void testTimeoutException() throws Expect.TimeoutException,
			Expect.EOFException, IOException {
		final Pipe pipe;
		try {
			pipe = Pipe.open();
		} catch (IOException e) {
			e.printStackTrace();
			fail("failed to open pipe!");
			return;
		}
		final InputStream in = Channels.newInputStream(pipe.source());
		final OutputStream out = Channels.newOutputStream(pipe.sink());
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					sleep(10);
					out.write("hello".getBytes());
					out.write(" world!".getBytes());
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try { out.close(); } catch (IOException e) {}
				}
			}
		}).start();
		
		
		Expect expect = new Expect(in, new NullOutputStream());
		
		expect.expectOrThrow(5, "hello");
		expect.expectEOF();
		
		expect.close();
	}
	
	public static void sleep(int sec) {
		try {
			Thread.sleep(sec * 1000);
		} catch (InterruptedException e) {
			fail("No one should interrupt me while I am sleeping!");
			e.printStackTrace();
		}
	}
	
	class NullOutputStream extends OutputStream {
		@Override
		public void write(int b) throws IOException {
		}
	}

}
