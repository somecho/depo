(ns ^:no-doc depo.core
  (:require [cli-matic.core :refer [run-cmd]]
            [depo.readwrite :as rw]
            [depo.errors :as e]))

(defn add-cmd [{:keys [_arguments file]}]
  (let [args  _arguments
        config-path (if file file (rw/get-config))]
    (if-not (empty? args)
      (do (mapv #(rw/write-dependency config-path %) args)
          (println "Done!"))
      (println (e/err :no-args)))))

(defn remove-cmd [{:keys [_arguments file]}]
  (let [args  _arguments
        config-path (if file file (rw/get-config))]
    (if-not (empty? args)
      (do (mapv #(rw/remove-dependency config-path %) args)
          (println "Done!"))
      (println (e/err :no-args)))))

(defn update-cmd [{:keys [_arguments file]}]
  (let [args  _arguments
        config-path (if file file (rw/get-config))]
    (println args)))

(def CONFIGURATION
  {:command "depo"
   :description "Manage dependencies for Clojure projects easily"
   :version "0.0.19"
   :opts [{:as "path to configuration file"
           :default nil
           :option "file"
           :short "f"
           :type :string}]
   :subcommands [{:command "add"
                  :description "Adds dependencies to a Clojure project"
                  :examples ["depo add org.clojure/clojure"
                             "depo add reagent@1.1.0"
                             "depo add re-frame clj-http"]
                  :runs add-cmd}
                 {:command "remove"
                  :description "Remove dependencies from a Clojure project"
                  :runs remove-cmd}
                 {:command "update"
                  :description "Update dependencies of a Clojure project"
                  :runs update-cmd}]})

(defn -main
  [& args]
  (run-cmd args CONFIGURATION))
