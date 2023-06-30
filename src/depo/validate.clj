(ns depo.validate
  (:require [malli.core :as m]))

(defn valid-id?
  "Checks whether the groupID or artifactID are valid IDs"
  [id]
  (m/validate [:re #"^[a-zA-Z0-9\-\.]+$"] id))

(defn valid-version?
  "Checks whether the version string is a valid version string"
  [ver]
  (m/validate [:re #"^[0-9/.]+[/-]?[a-zA-Z]*$"] ver))

(defn valid-dependency-map?
  "Given a map with dependency coordinate data, return true if valid. "
  [{:keys [groupID artifactID version]}]
  (every? true? [(valid-id? groupID)
                 (valid-id? artifactID)
                 (if version (valid-version? version) true)]))
