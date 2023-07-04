(ns depo.depo-e2e-test
  (:require [clojure.test :refer [testing is use-fixtures]]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]))

(def INPUT-FOLDER "test/resources/input/")
(def TEMP-FOLDER "test/resources/temp/")
(def SNAPSHOTS-FOLDER "test/resources/snapshots/")
(def FILES ["bb.edn" "shadow-cljs.edn" "deps.edn" "project.clj"])
(def tests [{:name "add-reagent-1.2.0"
             :commands ["add" "reagent@1.2.0"]}
            {:name "add-multiple"
             :commands ["add" "reagent@1.2.0" "org.clojure/java.jdbc@0.7.12"]}])

(defn delete-directory
  "Recursively delete a directory."
  [^java.io.File file]
  (when (.isDirectory file)
    (run! delete-directory (.listFiles file)))
  (io/delete-file file))

(defn setup-directories []
  (for [{:keys [name]} tests]
    (io/make-parents (str "test/resources/temp/" name "/FILE"))))

(defn delete-directories []
  (for [{:keys [name]} tests]
    (delete-directory (io/file (str "test/resources/temp/" name "/FILE")))))

(defn before-after [f]
  (setup-directories)
  (f)
  (delete-directories))

(use-fixtures :once before-after)

(defn add-test
  "Add a test to the given namespace. The body of the test is given as
  the thunk test-fn. Useful for adding dynamically generated deftests."
  [name ns test-fn & [metadata]]
  (intern ns (with-meta (symbol name) (merge metadata {:test #(test-fn)})) (fn [])))

(defn apply-compare
  [filename target-folder & commands]
  (let [input (str INPUT-FOLDER filename)
        target (str TEMP-FOLDER "/" target-folder "/" filename)
        snapshot (str SNAPSHOTS-FOLDER "/" target-folder "/" filename)]
    (io/copy (io/file input) (io/file target))
    (apply sh (concat ["clojure" "-M" "-m" "depo.core" "-f" target] commands))
    (is (= (slurp snapshot) (slurp target)))))

(doall
 (for [{:keys [name commands]} tests]
   (do
     (io/make-parents (str TEMP-FOLDER name "/FILE"))
     (add-test name
               'depo.depo-e2e-test
               #(doall
                 (for [f FILES]
                   (testing f
                     (apply apply-compare (concat [f name] commands)))))))))
