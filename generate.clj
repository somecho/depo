(ns generate
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]))

(def INPUT-FOLDER "test/resources/input/")
(def SNAPSHOTS-FOLDER "test/resources/snapshots/")
(def FILES ["project.clj" "deps.edn" "bb.edn" "shadow-cljs.edn"])
; (def TARGETS ["add-reagent-1.2.0" "add-multiple"])
(def TARGETS [{:name "add-reagent-1.2.0"
               :commands ["add" "reagent@1.2.0"]}
              {:name "add-multiple"
               :commands ["add" "reagent@1.2.0" "org.clojure/java.jdbc@0.7.12"]}])

(defn setup-directories []
  (mapv #(io/make-parents (str SNAPSHOTS-FOLDER (:name %) "/FILE")) TARGETS))

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
  (doall
   (for [{:keys [name commands]} TARGETS]
     (do (println name "snapshots")
         (mapv #(apply create-snapshot (concat [% name] commands)) FILES))))
  ; (println "Creating snapshots")
  ; (println "add-reagent-1.2.0 snapshots")
  ; (mapv #(create-snapshot % "add-reagent-1.2.0" "add" "reagent@1.2.0") FILES)
  ; (mapv #(create-snapshot % "add-multiple" "add" "reagent@1.2.0" "org.clojure/java.jdbc@0.7.12") FILES)
  (println "All snapshots have been created"))
