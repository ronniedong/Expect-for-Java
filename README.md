Expect-for-Java
===============

Pure Java implementation of the Expect tool

Introduction
------------
Expect for Java is a Java implementation of the Unix Expect tool.
The target is robust, easy-to-maintain code, with only one class file.
In order to use this tool, you need to know Java regular expressions and have some basic ideas about the original Expect tool.


Dependencies
------------
Expect.java:
none
TestExpect.java:
junit-4.XX.jar	http://cloud.github.com/downloads/KentBeck/junit/junit-4.10.jar


How to Use
----------
The JavaDoc explains how to use the class. You may find the following useful:
http://search.cpan.org/~rgiersig/Expect-1.15/Expect.pod
http://oreilly.com/catalog/expect/chapter/ch03.html


Related Work
------------
Through Google, I found two Java-based Expect library: ExpectJ and Expect4j. Both of them are well-maintained and have quite a few users, and I learned a lot by looking at the source code. However, both libraries are very complex with a lot of multi-threading, making them hard to customize. Some functions from other expect packages are also missing.

ExpectJ:
There is no regular expression matching, only plain text.

Expect4j:
Expect4j is basically a "ported" version for the original Expect.
Cannot specify timeout at each expect call.
Uses glob regex or perl regex, not native Java regex.
In the source code there are too many unused classes.


Notes
-----
Some advice if you want to implement Expect: avoid multi-threading as much as possible, you will find it not worth the efforts for such a program, which does not require performance as good as a web server.
