(ns build
  (:require [clojure.tools.build.api :as b]
            [clojure.string :as str]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'org.clojars.some/depo)
(def version "0.0.18")
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def class-dir "target/classes")
(def url "https://github.com/somecho/depo")
(def connection (str "scm:git:" url ".git"))

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
