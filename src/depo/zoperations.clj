(ns depo.zoperations
  "Methods that operate on zipper objects created by
  rewrite-clj."
  (:require [depo.resolver :as r]
            [depo.utils :as dutils]
            [rewrite-clj.zip :as z]))

(defn get-dependency-type
  "TO DEPRECATE"
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

(defn get-all-dependency-names
  "TO REWRITE"
  [config-path]
  (let [zloc (z/of-string (slurp config-path))
        project-type (dutils/get-project-type config-path)
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
  "- `zloc` - the configuration file parsed as a zipper object
  - `keys` - a vector of keys to locate the dependencies in the configuration file
  - `project-type` - either `:shadow`, `:lein` or `:default` 
 
  Gets the dependency zipper object from `zloc`"
  [zloc keys project-type]
  (case project-type
    :lein (-> (z/find-value zloc z/next (first keys))
              (z/next)
              (as-> zpos
                    (if (not-empty (rest keys))
                      (traverse-zip-map zpos (rest keys))
                      zpos)))
    (traverse-zip-map zloc keys)))

(defn get-dependency-data
  "Gets the corresponding data from a dependency zipper object.
 
  This would return
  - `{:mvn/version ...}` or `{:local/root}` if `zloc` is a map zipper
  - the version number as a string if `zloc` is a vector zipper"
  [zloc identifier]
  (let [deps-type (cond
                    (z/map? zloc) :map
                    (z/vector? zloc) :vector)]
    (case deps-type
      :map (-> (z/get zloc identifier)
               (z/string)
               (as-> s
                     (if (nil? s) nil (read-string s))))
      :vector (loop [cur (z/down zloc)]
                (if (= (z/string (z/down cur)) (str identifier))
                  (-> cur z/down z/right z/string read-string)
                  (when-not (z/rightmost? cur)
                    (recur (z/right cur))))))))

(defn append-dependency
  "Takes a map that satisfies the `depo.schema/PROCEDURE` spec.
  Appends a dependency to the dependency zipper object and returns it."
  [{:keys [argument deps-type identifier zloc]}]
  (let [{:keys [version]} (r/conform-version argument)]
    (println "Adding" identifier version)
    (case deps-type
      :map (-> zloc
               (z/down)
               (z/rightmost)
               (z/insert-newline-right)
               (z/up)
               (z/assoc identifier {:mvn/version version}))
      :vector (-> zloc
                  (z/down)
                  (z/rightmost)
                  (z/insert-newline-right)
                  (z/up)
                  (z/append-child [identifier version])))))

(defn update-dependency
  "Takes a map that satisfies the `depo.schema/PROCEDURE` spec.
  Updates a dependency in the dependency zipper object and returns it."
  [{:keys [argument deps-type identifier zloc dep-data]}]
  (let [{:keys [version]} (r/conform-version argument)
        current-version (case deps-type
                          :map (:mvn/version dep-data)
                          :vector dep-data)]
    (println "Updating" identifier current-version "->" version)
    (case deps-type
      :map (z/assoc zloc identifier {:mvn/version version})
      :vector (loop [cur (z/down zloc)]
                (if (= (z/string (z/down cur)) (str identifier))
                  (-> (z/down cur)
                      (z/next)
                      (z/replace version))
                  (when-not (z/rightmost? cur)
                    (recur (z/right cur))))))))

(defn remove-dependency
  "Takes a map that satisfies the `depo.schema/PROCEDURE` spec.
  Removes a dependency the dependency zipper object and returns it."
  [{:keys [deps-type identifier zloc]}]
  (println "Removing" identifier)
  (case deps-type
    :map (-> zloc
             (z/get identifier)
             (z/remove)
             (z/remove))
    :vector (loop [cur (z/down zloc)]
              (if (= (z/string (z/down cur)) (str identifier))
                (z/remove cur)
                (when-not (z/rightmost? cur)
                  (recur (z/right cur)))))))
