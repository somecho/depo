(ns depo.errors)

(defn err
  "Given a key identifying the type of error, returns a string
  containing the error message. `opts` required for certain error
  messages.

  Possible `error-key`s
  - `:no-args` - \"requires atleast one argument\"
  - `:invalid-argument` - \"The argument `(:argument opts)` is invalid\"
  - `:version-not-exist` - \"`(:artifactID opts) version `(:version opts`) does not exist\" "
  [error-key & opts]
  (let [opts (first opts)]
    (case error-key
      :no-args
      "Requires atleast one argument"
      :invalid-argument
      (str "The argument " (:argument opts) " is invalid")
      :version-not-exist
      (str (:artifactID opts) " version " (:version opts) " does not exist"))))
