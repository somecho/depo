(ns depo.resolver-test
  (:require [clojure.test :refer [testing deftest is]]
            [malli.core :as m]
            [depo.resolver :as r]))

(deftest form-path
  (testing "org.clojure/clojure"
    (is (= "org/clojure/clojure"
           (r/form-path {:groupID "org.clojure"
                         :artifactID "clojure"}))))
  (testing "org.clojars.some/depo"
    (is (= "org/clojars/some/depo"
           (r/form-path {:groupID "org.clojars.some"
                         :artifactID "depo"}))))
  (testing "reagent/reagent"
    (is (= "reagent/reagent"
           (r/form-path {:groupID "reagent"
                         :artifactID "reagent"}))))
  (testing "reagent/reagent@1.2.0"
    (is (= "reagent/reagent"
           (r/form-path {:groupID "reagent"
                         :artifactID "reagent"
                         :version "1.2.0"})))))

(deftest conform-version
  (testing "reagent"
    (let [result (r/conform-version "reagent")]
      (is (m/validate [:map
                       {:closed true}
                       [:groupID :string]
                       [:artifactID :string]
                       [:version :string]] result))))
  (testing "org.clojure/clojure"
    (let [result (r/conform-version "org.clojure/clojure")]
      (is (m/validate [:map
                       {:closed true}
                       [:groupID :string]
                       [:artifactID :string]
                       [:version :string]] result))))
  (testing "reagent@1.2.0"
    (let [result (r/conform-version "reagent@1.2.0")]
      (is (m/validate [:map
                       {:closed true}
                       [:groupID :string]
                       [:artifactID :string]
                       [:version [:= "1.2.0"]]] result))))
  (testing "reagent@1.2.0!!!"
    (is (nil? (r/conform-version "reagent@1.2.0!!!")))))

