Expect-for-Java
===============

Pure Java implementation of the Expect tool

Introduction
------------
Expect for Java is a pure Java implementation of the Unix Expect tool.  
The target is robust, easy-to-maintain code, with only one class file.  
In order to use this tool, you need to know Java regular expressions and have some basic ideas about the original Expect tool.


Dependencies
------------
Expect.java:  
[log4j-1.2.X.jar](http://archive.apache.org/dist/logging/log4j/1.2.17/log4j-1.2.17.jar)

TestExpect.java:  
[junit-4.X.jar](http://cloud.github.com/downloads/KentBeck/junit/junit-4.10.jar)


How to Use
----------
The JavaDoc explains how to use the class. You may find the following useful:  
<http://search.cpan.org/~rgiersig/Expect-1.15/Expect.pod>  
<http://oreilly.com/catalog/expect/chapter/ch03.html>

Note Expect does not provide a "forever" timeout, you may use a very large integer for that purpose, eg. 99999 or Integer.MAX_VALUE.

Expect can expect for a list of patterns/strings at one time. However there is no callback, because in Java the code does not look neat (refer to expect4j examples)

The expect() method can handle a mixture of regular expression Pattern and literal String: in fact it accepts arbitrary number of Object, then uses Pattern as regex and uses String as literal string.

Expect does NOT expect on more than one connection at a time (which IS a feature for Unix Expect/Perl Expect)

Those methods that end with "OrThrow" will throw checked exceptions when something goes wrong, for example timeout or lost connection(EOF), or IOException caused by alien invasion. This helps handling unexpected results.

Examples
--------
Example that connects to an SSH server and send "ls" command. (assuming your prompt is `$`)
```java
try {
	JSch jsch = new JSch();
	Session session = jsch.getSession(USER, HOST);
	session.setPassword(PASSWD);
	session.setConfig("StrictHostKeyChecking", "no");
	session.connect(60 * 1000);
	Channel channel = session.openChannel("shell");
	Expect expect = new Expect(channel.getInputStream(),
			channel.getOutputStream());
	channel.connect();
	expect.expect("$");
	System.out.println(expect.before + expect.match);
	expect.send("ls\n");
	expect.expect("$");
	System.out.println(expect.before + expect.match);
	expect.send("exit\n");
	expect.expectEOF();
	System.out.println(expect.before);
	expect.close();
	session.disconnect();
} catch (JSchException e) {
	e.printStackTrace();
} catch (IOException e) {
	e.printStackTrace();
}
```
You can substitute `expect.expect("$");` with `expect.expectOrThrow("$");`, and add `catch` clauses in the end.

It is possible the prompt is either `$`, or `#` with/without a space:
```java
	expect.expect("$", Pattern.compile("#\\s?"));
```


Related Work
------------
Through Google, I found two Java-based Expect library: ExpectJ and Expect4j. Both of them are well-maintained and have quite a few users, and I learned a lot by looking at the source code. However, both libraries are very complex with a lot of multi-threading, making them hard to customize. Some functions from other expect packages are also missing.

[ExpectJ](http://expectj.sourceforge.net/)  
There is no regular expression matching, only plain text.

[Expect4j](http://code.google.com/p/expect4j/)  
Expect4j is basically a "ported" version for the original Expect.  
Cannot specify timeout at each expect call.  
Uses glob regex or perl regex, not native Java regex.  
In the source code there are too many unused classes.


Notes
-----
Some advice if you want to implement Expect: avoid multi-threading as much as possible, you will find it not worth the efforts for such a program, where performance is not as important.


Disclaimer
----------
THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
