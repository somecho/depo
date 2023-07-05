(ns depo.dispatch
  (:require [clojure.java.io :as io]
            [depo.schema :as schema]
            [depo.utils :as dutils]
            [depo.zoperations :as zo]
            [malli.core :as m]
            [rewrite-clj.zip :as z]
            [zprint.core :as zp]))

(defn skip-procedure
  [{:keys [zloc]} & reason]
  (apply println (concat reason ["Skipping."]))
  zloc)

(defn ignore-pass
  [{:keys [dep-data deps-type identifier]
    :as procedure} f]
  (case deps-type
    :map (cond
           (= "RELEASE" (:mvn/version dep-data))
           (skip-procedure procedure "Version has been set as release.")
           (contains? dep-data :local/root)
           (skip-procedure procedure identifier "is a local dependency.")
           :else (f))
    :vector (f)))

(defn dispatch
  [{:keys [operation dep-exists identifier]
    :as procedure}]
  (case operation
    :add (if dep-exists
           (ignore-pass procedure
                        #(zo/update-dependency procedure))
           (zo/append-dependency procedure))
    :remove (if dep-exists
              (zo/remove-dependency procedure)
              (skip-procedure identifier "is not a dependency."))
    :update (if dep-exists
              (ignore-pass procedure
                           #(zo/update-dependency procedure))
              (skip-procedure procedure identifier "is not a dependency."))))

(defn apply-operation
  [{:keys [config-path id operation]}]
  (let [config-zip (z/of-string (slurp config-path))
        project-type (dutils/get-project-type config-path)
        access-keys [(dutils/create-keys project-type)]
        deps (zo/get-deps config-zip access-keys project-type)
        {:keys [groupID artifactID]}  (dutils/parse id)
        deps-type (cond
                    (z/map? deps) :map
                    (z/vector? deps) :vector)
        identifier (symbol (dutils/create-identifier groupID artifactID deps-type))
        dependency-data (zo/get-dependency-data deps identifier)
        procedure {:operation operation
                   :dep-exists (if dependency-data true false)
                   :identifier identifier
                   :project-type project-type
                   :argument id
                   :dep-data dependency-data
                   :deps-type deps-type
                   :zloc deps}]
    (if (m/validate schema/PROCEDURE procedure)
      (-> (dispatch procedure)
          (z/root-string)
          (zp/zprint-str {:parse-string? true
                          :style :indent-only})
          (as-> new-conf (spit config-path new-conf)))
      (println "Failed to validate procedure."))))

(defn get-config
  "Looks in the current directory for the following files
  - deps.edn
  - project.clj
  - shadow-cljs.edn
  - bb.edn

  Returns the a string matching the first file that it finds.

  Returns nil otherwise"
  []
  (cond
    (.exists (io/file "deps.edn")) "deps.edn"
    (.exists (io/file "project.clj")) "project.clj"
    (.exists (io/file "shadow-cljs.edn")) "shadow-cljs.edn"
    (.exists (io/file "bb.edn")) "bb.edn"))
