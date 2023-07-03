(ns depo.readwrite
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [depo.parser :as dp]
            [depo.resolver :as r]
            [zprint.core :as zp]
            [rewrite-clj.zip :as z]))

(defmulti get-all-dependency-names
  "Returns all the dependencies from the config"
  (fn [config-path] (last (str/split config-path #"/"))))

(defmethod get-all-dependency-names :default
  [config-path]
  (let [zloc (z/of-string (slurp config-path))
        dep-key (if (= (last (str/split config-path #"/")) "shadow-cljs.edn") :dependencies :deps)]
    (-> zloc
        (z/get dep-key)
        (z/string)
        (read-string)
        (keys)
        (as-> keys (map str keys)))))

(defmethod get-all-dependency-names "project.clj"
  [config-path]
  (let [zloc (z/of-string (slurp config-path))]
    (-> zloc
        (z/find-value z/next :dependencies)
        (z/next)
        (z/string)
        (read-string)
        (as-> dep-vec (map #(str (first %)) dep-vec)))))

(defn create-identifier
  [groupID artifactID dep-type]
  (case dep-type
    :map (str groupID "/" artifactID)
    :vector (if (= groupID artifactID)
              artifactID
              (str groupID "/" artifactID))))

(defn create-keys
  "- `config-path` - the full path to the config file as a string

  Returns
  - `:dependencies` for `:lein` and `:shadow`
  - `:deps` for `:default`
  "
  [project-type]
  (case project-type
    (or :lein :shadow) :dependencies
    :default :deps))

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

(defn get-dependency-data
  [deps identifier]
  (cond
    (map? deps) (get deps identifier)
    (vector? deps) (-> #(= identifier (first %))
                       (filter deps)
                       (first)
                       (rest))))

(defn get-dependency-type
  [deps]
  (cond
    (map? deps) :map
    (vector? deps) :vector))

(defn get-project-type
  "- `config-path` - the full path to the config file as a string

  Returns
  - `:shadow` for shadow-cljs.edn
  - `:lein` for project.clj
  - `:default` for everything else
  "
  [config-path]
  (let [config-name (-> config-path
                        (str/split #"/")
                        (last))]
    (case config-name
      "shadow-cljs.edn" :shadow
      "project.clj" :lein
      :default)))

(defn traverse-zip-map
  "- `zloc` - a zipper object created by rewrite-clj
  - `keys` - a vector of keys
 
  Traverses the zipper object using `z/get`, `z` being
 the `rewrite-clj.zip` namespace, using `keys` from left
 to right"
  [zloc keys]
  (loop [zloc zloc
         keys keys]
    (if (not-empty keys)
      (recur (z/get zloc (first keys))
             (rest keys))
      zloc)))

(defmulti get-dep-vec-map
  (fn [m] (:project-type m)))

(defmethod get-dep-vec-map :default
  [{:keys [zloc keys]}]
  (-> zloc
      (traverse-zip-map keys)
      (z/string)
      (read-string)))

(defmethod get-dep-vec-map :lein
  [{:keys [zloc keys]}]
  (-> zloc
      (z/find-value z/next (first keys))
      (z/next)
      (as-> zpos
            (if (not-empty (rest keys))
              (traverse-zip-map zpos (rest keys))
              zpos))
      (z/string)
      (read-string)))

(defn add-dependency
  [{:keys [deps id]}]
  (let [{:keys [groupID artifactID version]} (r/conform-version id)
        dep-type (get-dependency-type deps)
        identifier (create-identifier groupID
                                      artifactID
                                      dep-type)]
    (println "Adding" identifier version)
    (case dep-type
      :map (assoc deps (symbol identifier) {:mvn/version version})
      :vector (-> (conj deps [(symbol identifier) version])
                  (distinct)
                  (vec)))))

(defn remove-dependency
  [{:keys [deps id]}]
  (let [{:keys [groupID artifactID version]} (r/conform-version id)
        dep-type (get-dependency-type deps)
        identifier (create-identifier groupID
                                      artifactID
                                      dep-type)]
    (println "Removing" identifier version)
    (case dep-type
      :map (dissoc deps (symbol identifier))
      :vector (vec (filter #(not= (symbol identifier) (first %)) deps)))))

(defn get-current-version
  [deps identifier]
  (cond
    (map? deps)
    (:mvn/version (get-dependency-data deps identifier))
    (vector? deps)
    (first (get-dependency-data deps identifier))))

(defn update-dependency
  [{:keys [deps id]}]
  (let [{:keys [groupID artifactID version]} (r/conform-version id)
        identifier (create-identifier groupID
                                      artifactID
                                      (get-dependency-type deps))
        current-version (get-current-version deps (symbol identifier))]
    (if (= current-version version)
      (do
        (println identifier current-version "is up-to-date. Skipping.")
        deps)
      (do
        (println "Updating" identifier current-version "->" version)
        (cond
          (map? deps) (assoc deps (symbol identifier) {:mvn/version version})
          (vector? deps) (mapv
                          #(if (= (symbol identifier) (first %))
                             (vec (concat [(symbol identifier) version]
                                          (rest (rest %))))
                             %)
                          deps))))))

(defn zip-vec-add-newlines
  "- `zloc` - a zipper object of a vector created by rewrite-clj
 
  Splits up a vector of vectors (dependencies) into multiple lines.
  Indentation is not considered."
  [zloc]
  (z/map (fn [z] (if (z/rightmost? z)
                   z
                   (z/insert-newline-right z))) zloc))

(defn replace-dependencies
  "Takes in a map with the following keys
  - `:project-type` - `:lein`, `:shadow` or `:default`
  - `:zloc` - zipper object of config file created by rewrite-clj
  - `:keys` -  a vector of keys telling Depo which dependencies to replace
  - `:new-deps` - a vector or map of the newly written dependencies
 
  Replaces the specified dependencies in a map and returns
  a string formatted by zprint"
  [{:keys [zloc keys new-deps project-type]}]
  (-> zloc
      (as-> zipper
            (case project-type
              :lein (-> zipper
                        (z/find-value z/next (first keys))
                        (z/next))
              (traverse-zip-map zipper keys)))
      (z/replace new-deps)
      (as-> zipper
            (case project-type
              :default zipper
              (zip-vec-add-newlines zipper)))
      (z/root-string)
      (zp/zprint-str {:parse-string? true
                      :style (case project-type
                               :default :map-nl
                               :indent-only)
                      :map {:sort? false
                            :hang? false}
                      :vector {:hang? false
                               :wrap-coll? nil}})))
(defn operate
  "- `operation` - `:add`,`:update` or `:remove`
  - `packet` - a map containing the keys `:deps` and `:id`
 
  Keys
  - `deps` - either a map or a vector containing the dependencies,
  as defined in `project.clj` or `deps.edn` files
  - `id` - the artifact identifier, which follows the
  `[groupID/]artifactID[@version]` schema

  Operates on the given packet and returns a new set of dependencies,
  which is either a map or vec, depending on what was given to `:deps`"
  [operation packet]
  (case operation
    :add (add-dependency packet)
    :remove (remove-dependency packet)
    :update (update-dependency packet)))

(defn apply-operation
  [{:keys [config-path id operation]}]
  (let [config-zip (z/of-string (slurp config-path))
        project-type (get-project-type config-path)
        access-keys [(create-keys project-type)]
        dep-vec-map (get-dep-vec-map {:zloc config-zip
                                      :keys access-keys
                                      :project-type project-type})
        new-deps (operate operation {:deps dep-vec-map
                                     :id id})]
    (->> (replace-dependencies {:zloc config-zip
                                :project-type project-type
                                :new-deps new-deps
                                :keys access-keys})
         (spit config-path))))
         ; (println))))

; (apply-operation {:config-path "test/resources/input/bb.edn"
;                   :id "reagent"
;                   :operation :add})
