(ns depo.errors)

(def errors {:no-args "Requires atleast one argument"
             :invalid-id "The groupID or artifactID is malformed"
             :invalid-version "The version is malformed"
             :version-not-exist "The version does not exist"})

(defn err
  "Given a key identifying the type of error, returns a string
  containing the error message."
  [error-key & opts]
  (let [opts (first opts)]
    (case error-key
      :no-args
      "Requires atleast one argument"
      :invalid-argument
      (str "The argument " (:argument opts) " is invalid")
      :version-not-exist
      (str (:artifactID opts) " version " (:version opts) " does not exist"))))
