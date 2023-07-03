(ns depo.depo-e2e-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]))

(def INPUT-FOLDER "test/resources/input/")
(def TEMP-FOLDER "test/resources/temp/")
(def SNAPSHOTS-FOLDER "test/resources/snapshots/")

(defn delete-directory
  "Recursively delete a directory."
  [^java.io.File file]
  (when (.isDirectory file)
    (run! delete-directory (.listFiles file)))
  (io/delete-file file))

(defn before-after [f]
  (io/make-parents "test/resources/temp/FILE")
  (f)
  (delete-directory (io/file "test/resources/temp")))

(use-fixtures :each before-after)

(defn apply-compare
  [filename target-folder & commands]
  (let [input (str INPUT-FOLDER filename)
        target (str TEMP-FOLDER "/" target-folder "/" filename)
        snapshot (str SNAPSHOTS-FOLDER "/" target-folder "/" filename)]
    (io/copy (io/file input) (io/file target))
    (apply sh (concat ["clojure" "-M" "-m" "depo.core" "-f" target] commands))
    (= (slurp snapshot) (slurp target))))

(deftest add-multiple
  (io/make-parents (str TEMP-FOLDER "add-multiple/FILE"))
  (testing "project.clj"
    (is (apply-compare "project.clj" "add-multiple" "add" "reagent@1.2.0" "org.clojure/java.jdbc@0.7.12")))
  (testing "deps.edn"
    (is (apply-compare "deps.edn" "add-multiple" "add" "reagent@1.2.0" "org.clojure/java.jdbc@0.7.12")))
  (testing "shadow-cljs.edn"
    (is (apply-compare "shadow-cljs.edn" "add-multiple" "add" "reagent@1.2.0" "org.clojure/java.jdbc@0.7.12")))
  (testing "bb.edn"
    (is (apply-compare "bb.edn" "add-multiple" "add" "reagent@1.2.0" "org.clojure/java.jdbc@0.7.12"))))

(deftest add-reagent-1.2.0
  (io/make-parents (str TEMP-FOLDER "add-reagent-1.2.0/FILE"))
  (testing "project.clj"
    (is (apply-compare "project.clj" "add-reagent-1.2.0" "add" "reagent@1.2.0")))
  (testing "deps.edn"
    (is (apply-compare "deps.edn" "add-reagent-1.2.0" "add" "reagent@1.2.0")))
  (testing "shadow-cljs.edn"
    (is (apply-compare "shadow-cljs.edn" "add-reagent-1.2.0" "add" "reagent@1.2.0")))
  (testing "bb.edn"
    (is (apply-compare "bb.edn" "add-reagent-1.2.0"  "add" "reagent@1.2.0"))))
