(ns depo.readwrite
  (:require [clojure.java.io :as io]
            [depo.resolver :as r]
            [zprint.core :as zp]
            [rewrite-clj.zip :as z]))

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

(defmulti write-dependency
  "Writes the dependency to the given configuration file"
  (fn [config-path dependency] config-path))

(defmethod write-dependency :default
  [config-path arg]
  (let [zloc (z/of-string (slurp config-path))
        {:keys [groupID artifactID version]} (r/conform-version arg)
        deps (if-let [d (z/get zloc :deps)]
               d
               (z/get (z/assoc zloc :deps {}) :deps))]
    (println (str "Adding " groupID "/" artifactID " v" version))
    (-> deps
        (z/assoc (symbol (str groupID "/" artifactID)) {:mvn/version version})
        (z/root-string)
        (zp/zprint-str {:parse-string? true
                        :map {:sort? false}})
        (as-> new-conf (spit config-path new-conf)))))
