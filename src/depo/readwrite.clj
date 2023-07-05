(ns depo.readwrite
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [depo.resolver :as r]
            [zprint.core :as zp]
            [rewrite-clj.zip :as z]))

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

(defn get-dep-vec-map
  [{:keys [zloc keys project-type]}]
  (-> zloc
      (as-> zipper
            (case project-type
              :lein (-> (z/find-value zipper z/next (first keys))
                        (z/next)
                        (as-> zpos
                              (if (not-empty (rest keys))
                                (traverse-zip-map zpos (rest keys))
                                zpos)))
              (traverse-zip-map zipper keys)))
      (z/string)
      (read-string)))

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

(defn get-all-dependency-names
  [config-path]
  (let [zloc (z/of-string (slurp config-path))
        project-type (get-project-type config-path)
        deps-key (case project-type
                   (or :shadow :lein) :dependencies
                   :deps)]
    (-> zloc
        (as-> zipper
              (case project-type
                :lein (-> zipper
                          (z/find-value z/next deps-key)
                          (z/next))
                (z/get zipper deps-key)))
        (z/string)
        (read-string)
        (as-> vec-map
              (case (get-dependency-type vec-map)
                :map (keys vec-map)
                :vector (map #(str (first %)) vec-map))))))

; (defn add-dependency
;   [{:keys [deps id]}]
;   (let [{:keys [groupID artifactID version]} (r/conform-version id)
;         dep-type (get-dependency-type deps)
;         identifier (create-identifier groupID
;                                       artifactID
;                                       dep-type)]
;     (println "Adding" identifier version)
;     (case dep-type
;       :map (assoc deps (symbol identifier) {:mvn/version version})
;       :vector (let [dep-exists?  (-> #(= (symbol identifier) (first %))
;                                      (filter deps)
;                                      (seq))]
;                 (if dep-exists?
;                   (vec (map #(if (= (symbol identifier) (first %))
;                                (vec (concat [(symbol identifier) version] (rest (rest %))))
;                                %) deps))
;                   (-> (conj deps [(symbol identifier) version])
;                       (distinct)
;                       (vec)))))))

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

(defn get-deps
  [zloc keys project-type]
  (case project-type
    :lein (-> (z/find-value zloc z/next (first keys))
              (z/next)
              (as-> zpos
                    (if (not-empty (rest keys))
                      (traverse-zip-map zpos (rest keys))
                      zpos)))
    (traverse-zip-map zloc keys)))

(defn dep-exists?
  [zloc identifier]
  (let [deps-type (cond
                    (z/map? zloc) :map
                    (z/vector? zloc) :vector)]
    (case deps-type
      :map (z/get zloc identifier)
      :vector (loop [cur (z/down zloc)]
                (if (= (z/string (z/down cur)) (str identifier))
                  true
                  (when-not (z/rightmost? cur)
                    (recur (z/right cur))))))))

(defn add-dependency
  [{:keys [zloc keys project-type id]}]
  (let [{:keys [groupID artifactID version]} (r/conform-version id)
        dep-type (case project-type :default :map :vector)
        identifier (symbol (create-identifier groupID
                                              artifactID
                                              dep-type))
        dep-zloc (get-deps zloc keys project-type)
        dep-exists (dep-exists? dep-zloc identifier)]
    (-> dep-zloc
        (as-> dz
              (case dep-type
                :map (-> dz
                         (as-> dz
                               (if dep-exists
                                 dz
                                 (-> (z/down dz)
                                     (z/rightmost)
                                     (z/insert-newline-right)
                                     (z/up))))
                         (z/assoc (symbol identifier) {:mvn/version version}))
                :vector (-> dz
                            (as-> dz
                                  (if-not dep-exists
                                    (-> (z/down dz)
                                        (z/rightmost)
                                        (z/insert-newline-right)
                                        (z/up)
                                        (z/append-child [identifier version]))
                                    (loop [cur (z/down dz)]
                                      (if (= (z/string (z/down cur)) (str identifier))
                                        (-> (z/down cur)
                                            (z/next)
                                            (z/replace version))
                                        (when-not (z/rightmost? cur)
                                          (recur (z/right cur))))))))))
        z/print-root)))

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
                                      :project-type project-type})]
    (-> (operate operation {:deps dep-vec-map
                            :id id
                            :zloc config-zip
                            :project-type project-type
                            :keys access-keys}))))

(apply-operation {:config-path "test/resources/input/bb.edn"
                  :id "reagent"
                  :operation :add})
