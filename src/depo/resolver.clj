(ns depo.resolver
  (:require [clojure.string :as s]
            [clj-http.client :as client]
            [depo.parser :as p]
            [depo.errors :as e]
            [depo.validate :as v]))

(def repos {:clojars "https://repo.clojars.org"
            :central "https://repo1.maven.org/maven2"})

(defn form-path
  "Returns a string path that can be used in urls given a dependency map"
  [{:keys [groupID artifactID]}]
  (s/join "/" [(s/replace groupID "." "/") artifactID]))

(defn get-versioning
  " `arg` - [groupID/]artifactID
  Returns a vec containing versioning metadata"
  [arg]
  (let [dep-map (p/parse arg)
        path (form-path dep-map)
        urls (map #(s/join "/" [(val %) path "maven-metadata.xml"]) repos)
        metadata (-> (try (client/get (first urls))
                          (catch Exception _ (client/get (second urls))))
                     :body
                     p/xml->map)]
    (-> (:content metadata)
        (as-> content
              (filter #(= (:tag %) :versioning) content))
        (first)
        (:content))))

(defn get-version
  "`arg` - [groupID/]artifactID
  `key`- either `:latest`, `:release` or `:versions`"
  [arg key]
  (-> (filter #(= (:tag %) key) (get-versioning arg))
      (first)
      (:content)
      (as-> content
            (if (= key :versions)
              content
              (first content)))))

(defn get-latest-version
  "Takes in an artifact argument [groupID/]artifactID
  and returns the latest version. May return nil. Use release version instead."
  [arg] (get-version arg :latest))

(defn get-release-version
  "Takes in an artifact argument [groupID/]artifactID
  and returns the release version."
  [arg] (get-version arg :release))

(defn get-all-versions
  "Takes in an artifact argument [groupID/]artifactID
  and returns the release version."
  [arg] (get-version arg :versions))

(defn version-exists?
  "`arg` - [groupID/]artifactID"
  [arg version]
  (let [versions (get-all-versions arg)
        matches (filter #(= (first (:content %)) version) versions)]
    (> (count matches) 0)))

(defn conform-version
  "`arg` - [groupID/]artifactID
  Returns a map with `:groupID`, `:artifactID` and `:version`.
  If a version is not provided, the version used will be the release version."
  [arg]
  (let [dep-map (p/parse arg)]
    (if-not (v/valid-dependency-map? dep-map)
      (println (e/err :invalid-argument {:argument arg}))
      (if-not (:version dep-map)
        (assoc dep-map :version (get-release-version arg))
        (if (version-exists? arg (:version dep-map))
          dep-map
          (let [release-version (get-release-version arg)]
            (println (e/err :version-not-exist dep-map))
            (println "Using version" release-version "instead")
            (assoc dep-map :version release-version)))))))
