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
        dep-key (if (= config-path "shadow-cljs.edn") :dependencies :deps)
        deps (if-let [d (z/get zloc dep-key)]
               d
               (z/get (z/assoc zloc dep-key {}) dep-key))]
    (println (str "Adding " groupID "/" artifactID " v" version))
    (-> deps
        (z/assoc (symbol (str groupID "/" artifactID)) {:mvn/version version})
        (z/root-string)
        (zp/zprint-str {:parse-string? true
                        :map {:sort? false}})
        (as-> new-conf (spit config-path new-conf)))))

(defmethod write-dependency "project.clj"
  [config-path arg]
  (let [zloc (z/of-string (slurp config-path))
        {:keys [groupID artifactID version]} (r/conform-version arg)
        dep-sym (symbol (if (= groupID artifactID)
                          artifactID
                          (str groupID "/" artifactID)))
        zipper (if (z/find-value zloc z/next :dependencies)
                 zloc
                 (-> zloc
                     (z/append-child :dependencies)
                     (z/append-child  [])))]
    (println (str "Adding " groupID "/" artifactID " v" version))
    (-> zipper
        (z/find-value z/next :dependencies)
        (z/right)
        (z/string)
        (read-string)
        (as-> dep-vec
              (filter #(not= dep-sym (first %)) dep-vec))
        (vec)
        (conj [dep-sym version])
        (as-> new-deps
              (-> zipper
                  (z/find-value z/next :dependencies)
                  (z/right)
                  (z/replace new-deps)))
        (as-> veczip
              (z/map (fn [z] (if (z/rightmost? z)
                               z
                               (z/insert-newline-right z))) veczip))
        (z/root-string)
        (zp/zprint-str {:parse-string? true
                        :vector {:respect-nl? true
                                 :wrap-coll? nil}})
        (as-> new-conf (spit config-path new-conf)))))
