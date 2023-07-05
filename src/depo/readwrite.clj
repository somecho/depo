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
                  cur
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
    (println "Adding" identifier version)
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
        z/root-string)))

(defn remove-dependency
  [{:keys [zloc keys project-type id]}]
  (let [{:keys [groupID artifactID version]} (r/conform-version id)
        dep-type (case project-type :default :map :vector)
        identifier (symbol (create-identifier groupID
                                              artifactID
                                              dep-type))
        dep-zloc (get-deps zloc keys project-type)
        dep-exists (dep-exists? dep-zloc identifier)]
    (if-not dep-exists
      (do (println identifier "is not a dependency. Skipping.")
          dep-zloc)
      (do (println "Removing" identifier)
          (-> dep-zloc
              (as-> dep-zloc
                    (case dep-type
                      :vector (loop [cur (z/down dep-zloc)]
                                (if (= (z/string (z/down cur)) (str identifier))
                                  (z/remove cur)
                                  (when-not (z/rightmost? cur)
                                    (recur (z/right cur)))))
                      :map (-> dep-zloc
                               (z/get identifier)
                               (z/remove)
                               (z/remove))))
              (z/root-string))))))

(defn update-dependency
  [{:keys [zloc keys project-type id]}]
  (let [{:keys [groupID artifactID version]} (r/conform-version id)
        dep-type (case project-type :default :map :vector)
        identifier (symbol (create-identifier groupID
                                              artifactID
                                              dep-type))
        dep-zloc (get-deps zloc keys project-type)
        dep-exists (dep-exists? dep-zloc identifier)
        cur-version (when dep-exists
                      (case dep-type
                        :map (-> dep-exists
                                 (z/get :mvn/version)
                                 z/string
                                 read-string)
                        :vector (-> dep-exists
                                    z/down
                                    z/right
                                    z/string
                                    read-string)))]
    (if-not dep-exists
      (do (println identifier "is not a dependency. Skipping.")
          (z/root-string dep-zloc))
      (if (= cur-version version)
        (println identifier version "is up-to-date")
        (do (println "Updating" identifier cur-version "->" version)
            (-> dep-zloc
                (as-> dep-zloc
                      (case dep-type
                        :vector  (-> dep-zloc
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
                                                   (recur (z/right cur))))))))
                        :map (-> dep-zloc
                                 (as-> dz
                                       (if dep-exists
                                         dz
                                         (-> (z/down dz)
                                             (z/rightmost)
                                             (z/insert-newline-right)
                                             (z/up))))
                                 (z/assoc (symbol identifier) {:mvn/version version}))))
                (z/root-string)))))))

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
        access-keys [(create-keys project-type)]]
    (-> (operate operation {:id id
                            :zloc config-zip
                            :project-type project-type
                            :keys access-keys})
        (zp/zprint-str {:parse-string? true
                        :style :indent-only})
        (as-> newconf (spit config-path newconf)))))
        ; println)))

(apply-operation {:config-path "deps.edn"
                  :id "org.flatland/ordered"
                  :operation :add})
