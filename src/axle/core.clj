(ns axle.core
  (:require [clojure.stacktrace :as err])
  (:import (java.nio.file Path Paths)
           (io.methvin.watcher DirectoryWatcher
                               DirectoryChangeEvent
                               DirectoryChangeListener
                               DirectoryChangeEvent$EventType)))

(defn- nio-path
  [p]
  (->> (into-array String [])
       (Paths/get p)))

(def ^:private event-type
  {DirectoryChangeEvent$EventType/CREATE   :create
   DirectoryChangeEvent$EventType/DELETE   :delete
   DirectoryChangeEvent$EventType/MODIFY   :modify
   DirectoryChangeEvent$EventType/OVERFLOW :overflow})

(defn- add-hash
  [e-clj ^DirectoryChangeEvent e]
  (let [h (.hash e)]
    (cond-> e-clj
      h (assoc :hash h))))

(defn- event->clj
  [^DirectoryChangeEvent e]
  (-> {:type       (event-type (.eventType e))
       :path       (str (.path e))
       :count      (.count e)
       :root-path  (str (.rootPath e))
       :directory? (.isDirectory e)}
      (add-hash e)))

(defn- print-error
  [context ex]
  (-> "Uncaught watcher exception"
      (ex-info {:context @context} ex)
      (err/print-cause-trace)))

(defn- listener
  [{:keys [handler]} context]
  (proxy [DirectoryChangeListener] []
    (onEvent [^DirectoryChangeEvent e]
      (->> e
           (event->clj)
           (send-off context handler))
      nil)

    (onException [^Exception ex]
      (print-error context ex))

    (isWatching [] true)

    (onIdle [count])))

(defn- context-agent
  [{init-context :context}]
  (let [c (agent init-context)]
    (set-error-mode! c :continue)
    (set-error-handler! c print-error)
    c))

(defn ^DirectoryWatcher watcher
  [{:keys [paths handler file-hashing]
    :as   config}]
  {:pre [paths handler]}
  (let [context (context-agent config)]
    (-> (DirectoryWatcher/builder)
        (.paths (mapv nio-path paths))
        (.listener (listener config context))
        (.fileHashing (boolean file-hashing))
        (.build))))

(defn watch!
  "Replaces Hawk watch! task, which depends on deprecated Barbary
  WatchService. Barbary WatchService is no longer supported and very
  laggy on M1 macs.

  :paths        - A seq of string paths to watch
  :context      - Initial context map to start the watch reduce
                  process
  :handler      - Handler that reduces over file watch events to
                  accumulate a context. This is a function of two
                  args, the first is the context that is being
                  accumulated, and the second is the current watch
                  event being processed.
  :file-hashing - Boolean whether file hashing is enabled.
                  Default, false.

  Watch events are maps with the following attributes:

  :type         - Either :create, :delete, :modify, or :overflow
  :path         - The path of the file
  :count        - Number of times the event applied
  :root-path    - Root path of the file
  :directory?   - Boolean, true if it is a directory, false
                  otherwise
  :hash         - [Optional] File hash"
  [config]
  (let [w (watcher config)]
    {:future  (.watchAsync w)
     :watcher w}))

(defn stop!
  [{^DirectoryWatcher w :watcher}]
  (.close w)
  w)

(defn- wrap-init
  [handler context-init]
  (fn [context events]
    (if (= context-init ::started)
      (handler context events)
      (handler context-init events))))

(defn window
  "Window events for efficiency."
  [ms handler]
  (let [events     (ref [])
        windowing? (ref false)
        context    (ref ::started)]
    (fn [context-init e]
      (let [start (ref false)
            f     (wrap-init handler context-init)]
        (dosync (alter events conj e)
                (when-not @windowing?
                  (ref-set windowing? true)
                  (ref-set start true)))
        (when @start
          (future
            (Thread/sleep ms)
            (dosync (alter context f @events)
                    (ref-set events [])
                    (ref-set windowing? false)))))
      ::started)))
