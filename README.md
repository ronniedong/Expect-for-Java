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

Expect does NOT expect on more than one connection at a time.

Examples
--------
Example that connects to an SSH server and send "ls" command.
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
	expect.expect(Pattern.compile("\\Q$\\E\\s?"));
	System.out.println(expect.before + expect.match);
	expect.send("ls\n");
	expect.expect(Pattern.compile("\\Q$\\E\\s?"));
	System.out.println(expect.before + expect.match);
	expect.send("exit\n");
	expect.expectEOF();
	System.out.println(expect.before + expect.match);
	expect.close();
	session.disconnect();
} catch (JSchException e) {
	e.printStackTrace();
} catch (IOException e) {
	e.printStackTrace();
}
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
