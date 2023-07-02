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

(deftest update-all
  (io/make-parents (str TEMP-FOLDER "update-all/FILE"))
  (testing "project.clj"
    (is (apply-compare "project.clj" "update-all" "update")))
  (testing "deps.edn"
    (is (apply-compare "project.clj" "update-all" "update")))
  (testing "shadow-cljs.edn"
    (is (apply-compare "project.clj" "update-all" "update")))
  (testing "bb.edn"
    (is (apply-compare "project.clj" "update-all" "update"))))
