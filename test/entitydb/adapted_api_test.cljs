(ns entitydb.adapted-api-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [entitydb.entitydb :as edb :include-macros true]))

(def store {:inside {:another {:map {:entity-db {}}}}})

(def edb-path [:inside :another :map :entity-db])

(edb/def-adapted-api edb-path)

(deftest sample-mutations-and-queries
  (let [user {:id 1 :username "Retro"}
        store-1 (insert store :user user)
        player {:id 35 :name "Bill"}
        store-2 (insert-named-item store-1 :player :player/up player {:hits 4})
        expected-item {:id 35 :name "Bill" :entitydb/id 35
                       :entitydb/type :player}]
    (testing "adapted edb-fns use edb-path correctly"
      (is (contains? (get-in store-2 edb-path) :entitydb/store)))
    (testing "edb operations spot check"
      (is (= "Retro" (:username (get-by-id store-2 :user 1))))
      (is (= expected-item (get-named-item store-2 :player/up)))
      (is (nil? (meta (get-named-item store-2 :player/up)))))))
