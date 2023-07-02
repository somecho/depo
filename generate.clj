(ns generate
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]))

(def INPUT-FOLDER "test/resources/input/")
(def SNAPSHOTS-FOLDER "test/resources/snapshots/")
(def FILES ["project.clj" "deps.edn" "bb.edn" "shadow-cljs.edn"])
(def TARGETS ["update-all"])

(defn setup-directories []
  (mapv #(io/make-parents (str SNAPSHOTS-FOLDER % "/FILE")) TARGETS))

(defn create-snapshot
  [input-file target-folder & commands]
  (println "Creating" target-folder "snapshot for" input-file)
  (let [target (str SNAPSHOTS-FOLDER target-folder "/" input-file)]
    (io/copy (io/file (str INPUT-FOLDER input-file))
             (io/file target))
    (apply sh/sh (concat ["clj" "-M" "-m" "depo.core" "-f" target] commands)))
  (println "Snapshot created"))

(defn all-snapshots [_]
  (println "Setting up snapshot directories")
  (setup-directories)
  (println "Creating snapshots")
  (println "Update-all snapshots")
  (mapv #(create-snapshot % "update-all" "update") FILES)
  (println "All snapshots have been created"))
