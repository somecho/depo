# Depo
[![cljdoc badge](https://cljdoc.org/badge/org.clojars.some/depo)](https://cljdoc.org/d/org.clojars.some/depo)
[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.some/depo.svg)](https://clojars.org/org.clojars.some/depo)
![](https://github.com/somecho/depo/actions/workflows/deploy.yml/badge.svg)
![](https://github.com/somecho/depo/actions/workflows/tests.yml/badge.svg)

A simple CLI tool to easily managage dependencies for Clojure projects. Supports `deps.edn`, [Leiningen](https://codeberg.org/leiningen/leiningen), [Shadow-cljs](https://github.com/thheller/shadow-cljs) and [Babashka](https://github.com/babashka/babashka) project configurations. It uses [rewrite-clj](https://github.com/clj-commons/rewrite-clj) and [zprint](https://github.com/kkinnear/zprint) under the hood to rewrite your  configuration files.

## Why
The simplest way to add a dependency to a Clojure project is to first find the artifact coordinates. Then, depending on whether you use Leiningen or you need the Maven artifact coordinates, you look for that and add it to your configuration. There are a few tools to help ease this flow. 

For one, there is `clj -X:deps find-versions`, which downloads a bunch of jars on first start up and returns a bunch of versions. Some other tools include [depot](https://github.com/Olical/depot) (the similar naming is coincidental), [ancient-clj](https://github.com/xsc/ancient-clj) and [neil](https://github.com/babashka/neil), which also has other features, like adding common aliases. 

While all of these tools have their own merits, they have their own drawbacks too, such as being slow to start up (all JVM based CLIs, including Depo for now), or only supporting `edn` configuration files, or not having a CLI at all. 

Depo aims to cover these bases.

#### NOTE
Depo is still a work in progress. See the [roadmap](https://github.com/somecho/depo/issues/1)

## Usage
### Depo CLI 
To try Depo out, you can use this one liner:
```bash
clojure -Sdeps '{:deps {org.clojars.some/depo {:mvn/version "RELEASE"}}}' -M -m depo.core --help
```
Alternatively, you can add the following to your aliases:
```edn
{:depo {:extra-deps {org.clojars.some/depo {:mvn/version "RELEASE"}}
        :main-opts [ "-m" "depo.core"]}
```
Now you can run `clojure -M:depo --help`.
### Depo Library
You can also use Depo as a library.

##### Example 
```clj
(ns example
  (:require [depo.resolver :as r]))

(r/get-release-version "reagent") ;=> "1.2.0"
(r/conform-version "org.clojure/clojure") ;=> {:groupID "org.clojure", :artifactID "clojure", :version "1.5.0-alpha3"}
(r/version-exists? "org.clojure/clojure" "0.20.7") ;=> false
```
For more information, see the [API documentation](https://cljdoc.org/d/org.clojars.some/depo/0.0.12).

## Installation
Depo can be built and installed as a native image:
1. Install [GraalVM](https://github.com/graalvm/graalvm-ce-builds/releases/)
2. Clone this repo
3. Run `clj -T:build native-image` or, if you have babashka installed, `bb native-image`
4. The binary is now in `target/`. Copy it to somewhere on your path.
   
## Features / Commands
To see a list of commands, run `depo --help`. 

Depo first looks for a config file in your current working directory in the following order:
1. `deps.edn`
2. `project.clj`
3. `shadow-cljs.edn`
4. `bb.edn`

It then uses first config file that it finds for its operations.

### Global Flags
There are flags that you can set if you'd like to override the default behavior of the commands.

- `--file/-f` - Use a different config file instead of the default file Depo finds.

### Add
#### Usage
```bash
# simple usage
depo add org.clojure/clojure

# If the groupID and artifactID are the same, you can ommit the groupID
depo add reagent

# Use specific version
depo add reagent@1.11.0

# Use a different config file instead of default
depo -f bb.edn add metosin/malli
```
### Remove
#### Usage
```bash
depo remove clj-http
```

### Update
#### Usage
```bash
# Updates a specific dependency
depo update metosin/malli

# Updates all dependencies
depo update
```

---

© 2023 Somē Cho
