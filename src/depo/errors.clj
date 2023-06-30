(ns depo.errors)

(def errors {:no-args "Requires atleast one argument"
             :invalid-id "The groupID or artifactID is malformed"
             :invalid-version "The version is malformed"
             :version-not-exist "The version does not exist"})

(defn err [error-key & opts]
  (let [opts (first opts)]
    (case error-key
      :invalid-argument
      (str "The argument " (:argument opts) " is invalid")
      :version-not-exist
      (str (:artifactID opts) " version " (:version opts) " does not exist"))))

(defn exit
  "Exits the program with the given code and prints a message if any."
  [{:keys [msg code]}]
  (when msg (println msg))
  (System/exit code))
