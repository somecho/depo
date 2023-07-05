(ns depo.utils-unit-test
  (:require [clojure.test :refer [testing deftest is]]
            [depo.utils :as dutils]))

(deftest parse
  (testing "artifactID only"
    (is (= (dutils/parse "reagent") {:groupID "reagent"
                                     :artifactID "reagent"
                                     :version nil})))
  (testing "groupID and artifactID only"
    (is (= (dutils/parse "org.clojure/clojure") {:groupID "org.clojure"
                                                 :artifactID "clojure"
                                                 :version nil})))
  (testing "artifactID and version only"
    (is (= (dutils/parse "emotion-cljs@0.2.0") {:groupID "emotion-cljs"
                                                :artifactID "emotion-cljs"
                                                :version "0.2.0"})))
  (testing "artifactID and version with qualifier only"
    (is (= (dutils/parse "org.clojure/clojure@1.11.1-rc1") {:groupID "org.clojure"
                                                            :artifactID "clojure"
                                                            :version "1.11.1-rc1"})))
  (testing "groupID, artifactID and version"
    (is (= (dutils/parse "org.clojure/data.xml@0.0.8") {:groupID "org.clojure"
                                                        :artifactID "data.xml"
                                                        :version "0.0.8"})))
  (testing "groupID, artifactID and version with qualifier"
    (is (= (dutils/parse "org.clojure/data.xml@0.0.1-beta1") {:groupID "org.clojure"
                                                              :artifactID "data.xml"
                                                              :version "0.0.1-beta1"}))))

