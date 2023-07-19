(ns depo.dispatch-test
  (:require [depo.dispatch :refer [create-procedure
                                   dispatch]]
            [rewrite-clj.zip :as z]
            [clojure.test :refer [deftest testing is]]))

(def DEFAULT-PATH "test/resources/input/deps.edn")

(deftest add-default
  (testing "add reagent 1.2.0"
    (let [procedure (create-procedure {:config-path DEFAULT-PATH
                                       :id "reagent@1.2.0"
                                       :operation :add})
          new-deps (-> procedure
                       dispatch
                       z/root-string
                       z/of-string
                       (z/get :deps))
          item (z/get new-deps 'reagent/reagent)
          version (z/get item :mvn/version)]
      (is (-> item z/string not-empty))
      (is (= (-> version z/string read-string) "1.2.0"))))
  (testing "add non-existent dependency"
    (let [procedure (create-procedure {:config-path DEFAULT-PATH
                                       :id "idonotexist/idonotexist"
                                       :operation :add})
          new-deps (-> procedure
                       dispatch
                       z/root-string)]
      (is (= new-deps (slurp DEFAULT-PATH))))))

(deftest remove-default
  (testing "remove selmer"
    (let [procedure (create-procedure {:config-path DEFAULT-PATH
                                       :id "selmer"
                                       :operation :remove})
          new-deps (-> procedure
                       dispatch
                       z/root-string
                       z/of-string
                       (z/get :deps))
          item (z/get new-deps 'selmer/selmer)]
      (is (nil? item))))
  (testing "remove non-existent dependency"
    (let [procedure (create-procedure {:config-path DEFAULT-PATH
                                       :id "idonotexist/idonotexist"
                                       :operation :remove})
          new-deps (-> procedure
                       dispatch
                       z/root-string)]
      (is (= new-deps (slurp DEFAULT-PATH))))))


