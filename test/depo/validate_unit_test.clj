(ns depo.validate-unit-test
  (:require [clojure.test :refer  [testing deftest is]]
            [depo.validate :as v]))

(deftest valid-id
  (testing "alpha is valid" (is (v/valid-id? "alpha")))
  (testing "com.cognitect is valid" (is (v/valid-id? "com.cognitect")))
  (testing "org/clojure is not valid" (is (not (v/valid-id? "org/clojure"))))
  (testing "org@clojure is not valid" (is (not (v/valid-id? "org@clojure"))))
  (testing "an empty string is not valid" (is (not (v/valid-id? ""))))
  (testing "s4lj.s4lj is valid" (is (v/valid-id? "s4lj.s4lj"))))

(deftest valid-version
  (testing "0.0.0 is valid" (is (v/valid-version? "0.0.0")))
  (testing "0.0.0rc is valid" (is (v/valid-version? "0.0.0rc")))
  (testing "0.0.0-rc is valid" (is (v/valid-version? "0.0.0-rc")))
  (testing "0.0.0-beta1 is valid" (is (v/valid-version? "0.0.0-beta1")))
  (testing "1 is not valid" (is (not (v/valid-version? "1"))))
  (testing "alpha is not valid" (is (not (v/valid-version? "alpha"))))
  (testing "an empty string is not valid" (is (not (v/valid-version? ""))))
  (testing "0.0.0! is not valid" (is (not (v/valid-version? "0.0.0!"))))
  (testing "0.0.0-- is not valid" (is (not (v/valid-version? "0.0.0--"))))
  (testing ". is not valid" (is (not (v/valid-version? "."))))
  (testing "... is not valid" (is (not (v/valid-version? "...")))))

(deftest valid-dependency
  (testing "reagent reagent nil is valid"
    (is (v/valid-dependency-map? {:groupID "reagent"
                                  :artifactID "reagent"
                                  :version nil})))
  (testing "com.cognitect anomalies 0.1.7 is valid"
    (is (v/valid-dependency-map? {:groupID "com.cognitect"
                                  :artifactID "anomalies"
                                  :version "0.1.7"})))
  (testing "org.clojure clojure 0.11.1-alpha is valid"
    (is (v/valid-dependency-map? {:groupID "org.clojure"
                                  :artifactID "clojure"
                                  :version "0.11.1-alpha"})))
  (testing "a missing groupID is not valid"
    (is (not (v/valid-dependency-map? {:artifactID "clojure"
                                       :version "0.11.1-alpha"}))))
  (testing "a missing artifactID is not valid"
    (is (not (v/valid-dependency-map? {:groupID "org.clojure"
                                       :version "0.11.1-alpha"}))))
  (testing "a missing version is valid"
    (is (v/valid-dependency-map? {:groupID "reagent"
                                  :artifactID "reagent"}))))
