(ns depo.core
  (:require [cli-matic.core :refer [run-cmd]]
            [depo.readwrite :as rw]
            [depo.errors :as e]))

(defn add [{:keys [_arguments file]}]
  (println file)
  (let [args  _arguments
        config-path (if file file (rw/get-config))]
    (if-not (empty? args)
      (mapv #(rw/write-dependency config-path %) args)
      (e/exit {:msg (:no-args e/errors) :code 1}))))

(def add-cmd {:command "add"
              :description "adds dependencies to a Clojure project."
              :runs add})

(def CONFIGURATION
  {:command "depo"
   :description "manage Clojure dependencies easily"
   :version "0.0.4"
   :opts [{:as "path to configuration file"
           :default nil
           :option "file"
           :short "f"
           :type :string}]
   :subcommands [add-cmd]})

(defn -main
  [& args]
  (run-cmd args CONFIGURATION))
