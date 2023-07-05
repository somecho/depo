(ns depo.schema
  (:require [malli.core :as m]))

(def PROCEDURE [:map
                [:zloc [:vector :any]]
                [:dep-exists :boolean]
                [:identifier :symbol]
                [:argument :string]
                [:project-type [:enum :lein :shadow :default]]
                [:deps-type [:enum :map :vector]]
                [:dep-data :any]
                [:operation [:enum :add :remove :update]]])

(defn valid-id?
  "Returns `true` if the groupID or artifactID are valid IDs.
  `false` otherwise."
  [id]
  (m/validate [:re #"^[a-zA-Z0-9\-\.]+$"] id))

(defn valid-version?
  "Returns `true` if the version number is a valid version `String`. `false` otherwise."
  [ver]
  (m/validate [:re #"^(\d+\.)+\d[/-]?[a-zA-Z0-9]*$"] ver))

(defn valid-dependency-map?
  "Given a map with dependency coordinate data, return true if valid."
  [{:keys [groupID artifactID version]}]
  (every? true? [(valid-id? groupID)
                 (valid-id? artifactID)
                 (if version (valid-version? version) true)]))

