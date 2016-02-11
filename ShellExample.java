package ronnie_dong_expect_for_java;

import java.util.regex.Pattern;

/**
 * Demonstrates calling bash from Java using Expect-for-java from ronniedong
 */
public class ShellExample {
	public static void main(String[] args) throws Exception {
		ProcessBuilder builder = new ProcessBuilder("bash");
		builder.redirectErrorStream(true);
		Process shell = builder.start();
		Expect expect = new Expect(shell.getInputStream(), shell.getOutputStream());
		String [] cmds = { "pwd", "ls", "exit" };
		// Consume all output by allowing . to match newline
		Object [] anyPat = new Object[] { Pattern.compile(".+", Pattern.DOTALL)};
		for (String cmd : cmds) {
			System.out.println("Sending '" + cmd + "'");
			expect.send(cmd + "\n");
			int rc = expect.expect(1, anyPat);
			if (Expect.RETV_TIMEOUT == rc)
				System.out.println("Timeout");
			else
				System.out.println("rc=" + rc + ", match=>" + expect.match + "<");
		}
		expect.close();
		shell.destroy();
	}
}
