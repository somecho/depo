(ns depo.readwrite
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [depo.parser :as dp]
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
                        :map {:sort? false
                              :hang? false}})
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

(defmulti remove-dependency
  "Removes the dependency from the given configuration file"
  (fn [config-path dependency] config-path))

(defmethod remove-dependency :default
  [config-path arg]
  (let [zloc (z/of-string (slurp config-path))
        {:keys [groupID artifactID]} (dp/parse arg)
        dep-key (if (= config-path "shadow-cljs.edn") :dependencies :deps)
        identifier (symbol (str groupID "/" artifactID))]
    (-> zloc
        (z/get dep-key)
        (z/string)
        (read-string)
        (as-> dep-map
              (if-not (contains? dep-map identifier)
                (println arg "isn't a dependency. Skipping.")
                (do
                  (println "Removing" (str identifier))
                  (-> dep-map
                      (dissoc identifier)
                      (as-> new-deps
                            (-> zloc
                                (z/assoc :deps new-deps)))
                      (z/root-string)
                      (zp/zprint-str {:parse-string? true
                                      :map {:sort? false
                                            :hang? false}})
                      (as-> new-conf (spit config-path new-conf)))))))))

(defmethod remove-dependency "project.clj"
  [config-path arg]
  (let [zloc (z/of-string (slurp config-path))
        {:keys [groupID artifactID]} (dp/parse arg)
        dep-sym (symbol (if (= groupID artifactID)
                          artifactID
                          (str groupID "/" artifactID)))]
    (println "Removing" (str dep-sym))
    (-> zloc
        (z/find-value z/next :dependencies)
        (z/next)
        (z/string)
        (read-string)
        (as-> dep-vec
              (filter #(not= (first %) dep-sym) dep-vec))
        (vec)
        (as-> new-deps
              (-> zloc
                  (z/find-value z/next :dependencies)
                  (z/next)
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

(defmulti update-dependency
  "Updates a dependency in a Clojure project"
  (fn [config-path dependency] (last (str/split config-path #"/"))))

(defmethod update-dependency :default
  [config-path arg]
  (let [zloc (z/of-string (slurp config-path))
        {:keys [groupID artifactID version]} (r/conform-version arg)
        identifier (symbol (str groupID "/" artifactID))
        dep-key (if (= (last (str/split config-path #"/")) "shadow-cljs.edn") :dependencies :deps)
        dep-map (-> zloc (z/get dep-key) z/string read-string)
        dependency-exists? (contains? dep-map identifier)
        current-version (get-in dep-map [identifier :mvn/version])]
    (if-not dependency-exists?
      (println arg "isn't a dependency. Skipping.")
      (if (not= current-version version)
        (do
          (println "Updating" (str identifier))
          (println "Current version:" current-version)
          (println "Latest version:" version)
          (-> zloc
              (z/get dep-key)
              (z/replace
               (assoc dep-map identifier {:mvn/version version}))
              (z/root-string)
              (zp/zprint-str {:parse-string? true
                              :map {:sort? false
                                    :hang? false}})
              (as-> new-conf (spit config-path new-conf))))
        (println (str identifier) current-version "is up to date. Skipping.")))))

(defmethod update-dependency "project.clj"
  [config-path arg]
  (let [zloc (z/of-string (slurp config-path))
        {:keys [groupID artifactID version]} (dp/parse arg)
        identifier (symbol (if (= groupID artifactID)
                             artifactID
                             (str groupID "/" artifactID)))
        dep-vec (-> zloc
                    (z/find-value z/next :dependencies)
                    (z/next)
                    (z/string)
                    (read-string))
        dep-item (first (filter #(= (first %) identifier) dep-vec))
        current-version (second dep-item)
        dependency-exists? (seq  dep-item)
        {:keys [groupID artifactID version]} (if dependency-exists?
                                               (r/conform-version arg)
                                               nil)]
    (if-not dependency-exists?
      (println arg "isn't a dependency. Skipping.")
      (if-not (= current-version version)
        (do
          (println "Updating" (str identifier))
          (println "Current version:" current-version)
          (println "Latest version:" version)
          (-> zloc
              (z/find-value z/next :dependencies)
              (z/next)
              (z/replace (vec (map #(if (= (first %) identifier)
                                      [identifier version]
                                      %) dep-vec)))
              (as-> veczip
                    (z/map (fn [z] (if (z/rightmost? z)
                                     z
                                     (z/insert-newline-right z))) veczip))
              (z/root-string)
              (zp/zprint-str {:parse-string? true
                              :vector {:respect-nl? true
                                       :wrap-coll? nil}})
              (as-> new-conf (spit config-path new-conf))))
        (println (str identifier) current-version "is up to date. Skipping.")))))

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

(defmethod get-dep-vec-map :shadow
  [{:keys [zloc keys]}]
  (-> zloc
      (traverse-zip-map keys)
      z/string
      println))

(defn apply-operation
  [{:keys [config-path id operation]}]
  (let [config-zip (z/of-string (slurp config-path))
        project-type (get-project-type config-path)
        access-keys [(create-keys project-type)]
        dep-vec-map (get-dep-vec-map {:zloc config-zip
                                      :keys access-keys
                                      :project-type project-type})]
    dep-vec-map))

(apply-operation {:config-path "test/resources/input/shadow-cljs.edn"})
