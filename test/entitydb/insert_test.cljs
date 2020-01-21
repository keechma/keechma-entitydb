(ns entitydb.insert-test
  (:require
    [cljs.test :refer-macros [deftest is testing use-fixtures]]
    [entitydb.entitydb :as edb]
    [entitydb.query :as q]))

(deftest insert-user-test
  (let [res (edb/insert {} :user {:id 1 :username "Retro"})]
    (is (= res {:entitydb/store {:user {1 {:id 1 :entitydb/id 1 :username "Retro" :entitydb/type :user}}}}))))

(deftest inserting-entity-when-exists-merges-attrs-test
  (let [store {:entitydb/store {:user {1 {:full-name "Mihael Konjevic"}}}}
        res   (edb/insert store :user {:id 1 :username "Retro"})]
    (is (= res {:entitydb/store
                {:user
                 {1 {:id 1 :entitydb/id 1 :username "Retro" :full-name "Mihael Konjevic" :entitydb/type :user}}}}))))

(deftest insert-many
  (let [res (edb/insert-many {} :user [{:id 1 :username "Retro"}
                                       {:id 2 :username "Tibor"}])]
    (is (= res {:entitydb/store {:user {1 {:id 1 :entitydb/id 1 :username "Retro" :entitydb/type :user}
                                        2 {:id 2 :entitydb/id 2 :username "Tibor" :entitydb/type :user}}}}))))
