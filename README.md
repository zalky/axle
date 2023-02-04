<img src="https://i.imgur.com/GH71uSi.png" title="zalky" align="right" width="250"/>

# Axle

[![Clojars Project](https://img.shields.io/clojars/v/io.zalky/axle?labelColor=blue&color=green&style=flat-square&logo=clojure&logoColor=fff)](https://clojars.org/io.zalky/axle)

Axle is an efficient cross-platform DirectoryWatcher based service
for Clojure.

BarbaryWatchService for Mac is considered deprecated and no longers
works with newer versions of Java. This means existing watcher
implementations that depend on it, like
[Hawk](https://github.com/wkf/hawk), no longer perform well. Axle
introduces a `axle.core/watch!` fn that is a thin wrapper of the
cross-platform, high performance
[DirectoryWatcher](https://github.com/gmethvin/directory-watcher).

## Getting Started

Just add the following dependency in your `deps.edn`:

```clj
io.zalky/axle {:mvn/version "0.2.1"}
```

You can start a watch task like so:

```clj
(require '[axle.core :as axle])

(def watcher
  (axle/watch!
   {:paths   ["src/clojure" "src/assets"]
    :context {:init "context"}
    :handler (fn [context event]
               (update-context ...))}))
```

Where `:handler` is a reducing function that accepts the current
context and a file event to return an updated context, `:paths` is a
list of paths (to individual files or directories) to watch, and
optionally, `:context` is the initial value of the accumulated
context.

File events have the following form:

```clj
{:type       :modify,
 :path       "/path/to/file.clj",
 :count      1,
 :root-path  "src",
 :directory? false,
```

There are four event types, `:create`, `:delete`, `:modify` and
`:overflow` (which is emitted if the file system is generating events
faster than the underlying implementation can process them).

Each event can optionally include a file hash. Since this affects
performance it is disabled by default, but can be turned on via the
`:file-hashing true` option:

```clj
(def watcher
  (axle/watch!
   {:paths        ["src/clojure" "src/assets"]
    :handler      handler
    :file-hashing true}))
```

Now events will include an additional `:hash` attribute.

On uncaught exceptions, `watch!` will print a stacktrace that includes
the context at the time of the error. However, you should consider
catching errors in your handler, where you have access to both the
context and the event, and also have the option to update the context
in response to an error.

Finally, a watch task can be stopped via:

```clj
(axle/stop! watcher)
```

### Windowing

You can return a windowing handler, which handles a collection of
events that have occurred within a certain number of milliseconds,
via:

```clj
{:handler (axle/window ms (fn handler [context events] ...))}
```
Where `events` is just a sequence of event maps, and `ms` is just an
integer number of milliseconds.

## Alternatives

[Beholder](https://github.com/nextjournal/beholder) is a similar
`DirectoryWatcher` based wrapper. However, its callback is not a
reducing function, so you cannot accumulate a context, and it does not
provide windowing functionality.

## License

Axle is distributed under the terms of the Apache License 2.0.
