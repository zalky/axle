(ns axle.core-test
  (:require [axle.core :as watch]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest testing is use-fixtures]])
  (:import java.util.UUID
           java.io.File))

(def ^:dynamic *tmpdir*
  nil)

(defn tmpdir
  "Returns a new non-existent tmp path."
  []
  (-> "java.io.tmpdir"
      (System/getProperty)
      (io/file (str "watcher-" (UUID/randomUUID)))))

(defn tmp?
  "Returns true if `f` is a temporary watcher file. Fully resolves
  symbolic links for safety."
  [^File f]
  (let [p (.getCanonicalPath f)]
    (-> "java.io.tmpdir"
        (System/getProperty)
        (io/file)
        (.getCanonicalPath)
        (str "/watcher-")
        (re-pattern)
        (re-find p))))

(defn delete-tmp
  "Recursively deletes file if it is temporary. Note that
  File.deleteOnExit() does not recursively delete non-empty
  directories, better to do it here. Obviously, take great care when
  recursively deleting files."
  [^File f]
  {:pre [(tmp? f)]}
  (when (.isDirectory f)
    (doseq [file (.listFiles f)]
      (delete-tmp file)))
  (io/delete-file f))

(use-fixtures :each
  (fn [f]
    (binding [*tmpdir* (tmpdir)]
      (.mkdir *tmpdir*)
      (f)
      (delete-tmp *tmpdir*))))

(defn event
  [t p s]
  (case t
    :write  (spit (io/file p s) s)
    :append (spit (io/file p s) s :append true)
    :delete (io/delete-file (io/file p s)))
  (Thread/sleep 500))

(defn clean-event
  [{t    :type
    path :path
    root :root-path
    n    :count
    dir  :directory?
    hash :hash}]
  {:type      t
   :count     n
   :directory dir
   :path      (str/replace path (re-pattern root) "tmpdir")
   :hash      (str hash)})

(deftest watch!-test
  (let [done (promise)
        w    (watch/watch!
              {:file-hashing true
               :paths        [(str *tmpdir*)]
               :context      []
               :handler      (fn [context e]
                               (let [c (->> e
                                            (clean-event)
                                            (conj context))]
                                 (if (< (count c) 4)
                                   c
                                   (deliver done c))))})]
    (event :write *tmpdir* "one")
    (event :append *tmpdir* "one")
    (event :write *tmpdir* "two")
    (event :delete *tmpdir* "two")
    (is (= (deref done 1000 nil)
           [{:type      :create
             :count     1
             :directory false
             :path      "tmpdir/one"
             :hash      "5dX5ODVSJuThrGv41diesg=="}
            {:type      :modify
             :count     1
             :directory false
             :path      "tmpdir/one"
             :hash      "VmplxCEzYBelzfz/Bi37nQ=="}
            {:type      :create
             :count     1
             :directory false
             :path      "tmpdir/two"
             :hash      "hklSmTRfaoxSsX5EC7DVYQ=="}
            {:type      :delete
             :count     1
             :directory false
             :path      "tmpdir/two"
             :hash      "hklSmTRfaoxSsX5EC7DVYQ=="}]))
    (watch/stop! w)))

