(ns depo.utils
  (:require [clojure.string :as s]
            [clojure.xml :as xml]))

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

(defn get-project-type
  "- `config-path` - the full path to the config file as a string

  Returns
  - `:shadow` for shadow-cljs.edn
  - `:lein` for project.clj
  - `:default` for everything else
  "
  [config-path]
  (let [config-name (-> config-path
                        (s/split #"/")
                        (last))]
    (case config-name
      "shadow-cljs.edn" :shadow
      "project.clj" :lein
      :default)))

(defn parse
  "Given a dependency string that follows the following schema
  `[groupID/]artifactID[@version]`

  Returns a map with the keys `:groupID`, `:artifactID`, `:version`
  
  ### Examples: 
  ```clj
  (parse \"reagent\")
  ; returns {:groupdID \"reagent\" :artifactID \"reagent\" :version nil}

  (parse \"emotion-cljs@0.2.0\"'
  ; returns {:groupdID \"emotion-cljs\" :artifactID \"emotion-cljs\" :version 0.2.0}
  ```"
  [arg]
  (let [[group-artifact version]  (if (s/includes? arg "@")
                                    (s/split arg #"@")
                                    [arg nil])
        artifactID (last (s/split group-artifact #"/"))
        groupID (if (s/includes? group-artifact "/")
                  (first (s/split group-artifact #"/"))
                  artifactID)]
    {:groupID groupID
     :artifactID artifactID
     :version version}))

(defn xml->map
  "Converts an xml string into Clojure map structure"
  [doc]
  (-> doc .getBytes java.io.ByteArrayInputStream. xml/parse))
