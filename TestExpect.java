import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;

import junit.framework.Assert;

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
		
		Expect expect = new Expect(in, new NullOutputStream());
		expect.expectLiteral(10, ".*llo");
		Assert.assertEquals("hello", expect.match);
		expect.expectLiteral(5, "world");
		Assert.assertEquals(null, expect.match);
		expect.expectLiteral(20, "world");
		Assert.assertEquals("world", expect.match);
		expect.close();
	}
	
	/**
	 * test resetting timeout when receives anything from InputStream
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
		expect.expectLiteral(10, "!");
		Assert.assertEquals("!", expect.match);
		expect.close();
	}

	// @Test
	public void originalMainTest() {
		final Pipe pipe;
		try {
			pipe = Pipe.open();

		} catch (IOException e2) {
			e2.printStackTrace();
			return;
		}
		final InputStream in = Channels.newInputStream(pipe.source());
		OutputStream out = Channels.newOutputStream(pipe.sink());
		
		
		//final PipedInputStream in = new PipedInputStream();
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Expect expect = new Expect(in, System.out);
					expect.setRestart_timeout_upon_receive(true);
					expect.expectLiteral(10, "!");
					System.out.println("Before: " + expect.before);
					System.out.println("Match: " + expect.match);
					expect.expect( "x");
					System.out.println("Before: " + expect.before);
					System.out.println("Match: " + expect.match);
					expect.expect( "x");
					System.out.println("Before: " + expect.before);
					System.out.println("Match: " + expect.match);
					
					/*expect.expect(10, "llo");
					System.out.println("Before: " + expect.before);
					System.out.println("Match: " + expect.match);
					expect.expect("ld");
					System.out.println("Before: " + expect.before);
					System.out.println("Match: " + expect.match);
					expect.expect("!");
					System.out.println("Before: " + expect.before);
					System.out.println("Match: " + expect.match);*/
					
					expect.close();
					/*
					expect.expect("ld");
					System.out.println("Before: " + expect.before);
					System.out.println("Match: " + expect.match);*/
				} finally{
					/**
					 * should not close here, the thread which reads from "in"
					 * is the daemon piping thread in Expect
					 * try {
						in.close();
					} catch (IOException e) {
						e.printStackTrace();
					}*/
				}
			}
		}).start();
		//PipedOutputStream out = null;
		try {
			
			//out= new PipedOutputStream(in);
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			out.write("hello".getBytes());
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			out.write(" world".getBytes());
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			out.write("!".getBytes());

		} catch (IOException e) {
			e.printStackTrace();
		} finally{
			try {
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
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
