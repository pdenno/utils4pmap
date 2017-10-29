# utils4pmap

An implementation of a pmap-like function for which calls to the argument function
are aborted after a specified timeout.

NOTE: This is experimental code that passes tests but does not seem to be working correctly in
a project where I've tried it!

## Usage

````clojure
(pmap-timeout1 fn coll timeout)
````


````clojure
(pmap-timeout2 fn coll timeout) ; Commented out. See utils4fpmap.clj and utils4pmap_test.clj
````

## License

Copyright © 2017 Peter Denno

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
