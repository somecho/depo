(ns depo.zoperations-unit-test
  (:require [clojure.test :refer [testing deftest is]]
            [depo.zoperations :refer [append-dependency]]
            [rewrite-clj.zip :as z]))

(def DEPS "{metosin/malli {:mvn/version \"0.10.0\"}}")

(deftest append-default
  (testing "add reagent 1.2.0"
    (let [new-deps (append-dependency {:argument "reagent@1.2.0"
                                       :deps-type :map
                                       :identifier 'reagent/reagent
                                       :zloc (z/of-string DEPS)})
          item (z/get new-deps 'reagent/reagent)
          version (z/get item :mvn/version)]
      (is (-> item z/string not-empty))
      (is (= (-> version z/string read-string) "1.2.0"))))
  (testing "overwrite malli 0.11.0"
    (let [new-deps (append-dependency {:argument "metosin/malli@0.11.0"
                                       :deps-type :map
                                       :identifier 'metosin/malli
                                       :zloc (z/of-string DEPS)})
          item (z/get new-deps 'metosin/malli)
          version (z/get item :mvn/version)]
      (is (-> item z/string not-empty))
      (is (= (-> version z/string read-string) "0.11.0")))))


