(ns depo.core
  (:require [cli-matic.core :refer [run-cmd]]
            [depo.readwrite :as rw]
            [depo.errors :as e]))

(defn add [{:keys [_arguments file]}]
  (let [args  _arguments
        config-path (if file file (rw/get-config))]
    (if-not (empty? args)
      (do (mapv #(rw/write-dependency config-path %) args)
          (println "Done!"))
      (println (e/err :no-args)))))

(def add-cmd {:command "add"
              :description "adds dependencies to a Clojure project."
              :runs add})

(def CONFIGURATION
  {:command "depo"
   :description "manage Clojure dependencies easily"
   :version "0.0.8"
   :opts [{:as "path to configuration file"
           :default nil
           :option "file"
           :short "f"
           :type :string}]
   :subcommands [add-cmd]})

(defn -main
  [& args]
  (run-cmd args CONFIGURATION))
