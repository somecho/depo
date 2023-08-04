(ns ^:no-doc depo.core
  (:require [cli-matic.core :refer [run-cmd]]
            [depo.dispatch :as dd]
            [depo.errors :as e]
            [depo.zoperations :as zo]))

(defn add-cmd [{:keys [_arguments file]}]
  (let [args  _arguments
        config-path (if file file (dd/get-config))]
    (if-not (empty? args)
      (do (mapv #(spit config-path
                       (dd/apply-operation {:config-path config-path
                                            :id %
                                            :operation :add})) args)
          (println "Done!"))
      (println (e/err :no-args)))))

(defn remove-cmd [{:keys [_arguments file]}]
  (let [args  _arguments
        config-path (if file file (dd/get-config))]
    (if-not (empty? args)
      (do (mapv #(spit config-path
                       (dd/apply-operation {:config-path config-path
                                            :id %
                                            :operation :remove})) args)
          (println "Done!"))
      (println (e/err :no-args)))))

(defn update-cmd [{:keys [_arguments file]}]
  (let [args  _arguments
        config-path (if file file (dd/get-config))]
    (if (empty? args)
      (do (mapv #(spit config-path
                       (dd/apply-operation {:config-path config-path
                                            :id %
                                            :operation :update}))
                (zo/get-all-dependency-names config-path))
          (println "Done!"))
      (mapv #(spit config-path
                   (dd/apply-operation {:config-path config-path
                                        :id %
                                        :operation :update})) args))))

(def CONFIGURATION
  {:command "depo"
   :description "Manage dependencies for Clojure projects easily"
   :version "0.4.37"
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
