(ns ^:no-doc depo.core
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
              :description "adds dependencies to a Clojure project"
              :examples ["depo add org.clojure/clojure"
                         "depo add reagent@1.1.0"
                         "depo add re-frame clj-http"]
              :runs add})

(defn remove [{:keys [_arguments file]}]
  (let [args  _arguments
        config-path (if file file (rw/get-config))]
    (if-not (empty? args)
      (println "TODO")
      (println (e/err :no-args)))))

(def remove-cmd {:command "remove"
                 :description "remove dependencies from a Clojure project"
                 :runs remove})

(def CONFIGURATION
  {:command "depo"
   :description "Manage dependencies for Clojure projects easily"
   :version "0.0.12"
   :opts [{:as "path to configuration file"
           :default nil
           :option "file"
           :short "f"
           :type :string}]
   :subcommands [add-cmd]})

(defn -main
  [& args]
  (run-cmd args CONFIGURATION))
