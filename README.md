# utils4pmap

An implementation of a pmap-like function for which calls to the argument function
are aborted after a specified timeout.

Caveat: Java doesn't have a reliable way to stop a process, thus neither does Clojure.
Use of .stop is much discouraged and indeed my experience suggests that .stop-ing causes weird behavior.
A compromise in design is for the calling code to use (.isInterrupted (Thread/currentThread)) or
similar calls to check whether future-cancel (the mechanism used here) has been called on the thread. 
Experience suggest that 'busy' code needs such checks. (See the tests for an example.)
Threads that sleep do not need to perform this test.

In light of the above considerations, consider the status of this code as 'still under development'.
If you have thoughts on how it might be improved, please let me know through github issues. 

## Usage

[![Clojars Project](http://clojars.org/pdenno/utils4pmap/latest-version.svg)](http://clojars.org/pdenno) <br>
[![Build Status](https://travis-ci.org/pdenno/utils4pmap.svg?branch=master)](https://travis-ci.org/pdenno/utils4pmap)


````clojure
(pmap-timeout fn coll timeout)
````

## License

Copyright © 2018 Peter Denno

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
