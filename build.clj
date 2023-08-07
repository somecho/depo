(ns build
  (:require [clojure.tools.build.api :as b]
            [clojure.java.shell :refer [sh]]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'org.clojars.some/depo)
(def version "0.4.39")
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def class-dir "target/classes")
(def url "https://github.com/somecho/depo")
(def connection (str "scm:git:" url ".git"))
(def basis (b/create-basis {:project "deps.edn"}))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis (b/create-basis {:project "deps.edn"})
                :scm {:connection connection
                      :developerConnection connection
                      :url url
                      :tag (b/git-process {:git-args "rev-parse HEAD"})}
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir "target/classes"
          :jar-file jar-file}))

(defn deploy [_]
  (dd/deploy {:installer :remote
              :artifact jar-file
              :pom-file (b/pom-path {:lib lib :class-dir class-dir})}))

(defn uberjar [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file jar-file
           :basis basis
           :main 'depo.core}))

(defn native-image [_]
  (println "creating uberjar...")
  (sh "clojure" "-T:build" "uberjar")
  (println "Uberjar created")
  (println "creating native image")
  (sh  "native-image"
       "-jar" (str "target/depo-" version ".jar")
       "--no-fallback"
       "--features=clj_easy.graal_build_time.InitClojureClasses"
       "--enable-https"
       "-H:ReflectionConfigurationFiles=reflect-config.json"
       "target/depo")
  (println "Native image created"))

