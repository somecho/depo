(ns depo.parser
  (:require [clojure.string :as s]
            [clojure.xml :as xml]))

(defn parse
  "Given a dependency string that follows the following schema
  [groupID/]artifactID[@version]
  Returns a map with the keys `:groupID`, `:artifactID`, `:version`"
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
