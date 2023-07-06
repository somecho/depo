(ns depo.dispatch
  "Functions related to the control flow of the CLI,
  as well as IO operations, such as reading and writing the config."
  (:require [clojure.java.io :as io]
            [depo.errors :as e]
            [depo.schema :as schema]
            [depo.utils :as dutils]
            [depo.zoperations :as zo]
            [malli.core :as m]
            [rewrite-clj.zip :as z]
            [zprint.core :as zp]))

(defn skip-procedure
  "Takes in a `depo.schema/PROCEDURE` map and returns it,
  printing `reason` on the way."
  [{:keys [zloc]} & reason]
  (apply println (concat reason ["Skipping."]))
  zloc)

(defn ignore-pass
  "Takes in a `depo.schema/PROCEDURE` map and 
  checks whether the operation `f` should be skipped."
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
  "Takes in a `depo.schema/PROCEDURE` map and
  dispatches the appropriate operations on it.
  Returns a zipper object after the operation.
  
  Operations
  - `:add`
  - `:remove`
  - `:update`"
  [{:keys [argument operation dep-exists identifier zloc]
    :as procedure}]
  (try (case operation
         :add (if dep-exists
                (ignore-pass procedure
                             #(zo/update-dependency procedure))
                (zo/append-dependency procedure))
         :remove (if dep-exists
                   (zo/remove-dependency procedure)
                   (skip-procedure procedure identifier "is not a dependency."))
         :update (if dep-exists
                   (ignore-pass procedure
                                #(zo/update-dependency procedure))
                   (skip-procedure procedure identifier "is not a dependency.")))
       (catch Exception e (do  (-> e
                                   ex-data
                                   :status
                                   str
                                   keyword
                                   (e/err {:argument argument})
                                   println)
                               zloc))))

(defn apply-operation
  "Takes in a map containing
  - `:config-path` - the path to the config file
  - `:id` - the CLI argument 
  - `:operation` - `:add`, `:update` or `:remove`
 
  Creates a `depo.schema/PROCEDURE` map to dispatch operations.
  It writes the resulting configuration into `config-path`. "
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
          ; (println))
          (as-> new-conf (spit config-path new-conf)))
      (println "Failed to validate procedure."))))

(apply-operation {:config-path "deps.edn"
                  :id "reagentttt"
                  :operation :remove})

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
