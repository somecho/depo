(ns depo.core
  (:require [cli-matic.core :refer [run-cmd]]
            [depo.resolver :as r]
            [depo.errors :as e]))

(defn add [{:keys [_arguments]}]
  (let [args  _arguments]
    (if-not (empty? args)
      (let [dep-maps (map r/form-dependency args)
            paths (map r/form-path dep-maps)]
        paths)
      (e/exit {:msg (:no-args e/errors) :code 1}))))

(def add-cmd {:command "add"
              :description "adds dependencies to a Clojure project."
              :runs add})

(def CONFIGURATION
  {:command "depo"
   :description "manage Clojure dependencies easily"
   :version "0.0.1"
   :subcommands [add-cmd]})

(defn -main
  [& args]
  (run-cmd args CONFIGURATION))

(defn argify [& args]
  {:_arguments args})

(add (argify "reagent" "re-frame"))
