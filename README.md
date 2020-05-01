# mvnx

An experiment with Maven dependencies and classloading. I wanted to see
whether I could "run" a maven POM. Basically what this does is read the
dependency tree of a POM (including inherited dependencies, transient
dependencies and dependency management) and then creates a classpath
of necessary dependencies (e.g. excluding test dependencies) with URLs
either from the local repository - if available - or from remote
repositories. Then you can call some main method with that classpath.

Basically you can run any pom without ever downloading anything to disk.
