(ns entitydb.core-two
  (:require
    [cljs.test :refer-macros [deftest is testing use-fixtures]]
    [entitydb.internal :refer [->EntityIdent]]
    [entitydb.entitydb :as edb]
    [entitydb.query :as q]
    [entitydb.data :as data]
    [entitydb.util :refer [log]]))

(use-fixtures :once
              {:before (fn [] (js/console.clear))})

(deftest get-named-test
  (let [store-with-item {:entitydb/store
                         {:note
                          {1
                           {:id            1
                            :title         "Note title"
                            :entitydb/id   1
                            :entitydb/type :note}}}
                         :entitydb.named/item
                         {:note/current
                          {:data (->EntityIdent :note 1)
                           :meta nil}}}
        ]
    (is (= (edb/get-named store-with-item :note/current)
           {:entitydb/id 1 :entitydb/type :note :id 1 :title "Note title"}))))

(deftest get-collection-test
  (let [db {:entitydb/store {:note
                             {1
                              {:id            1
                               :title         "Note title"
                               :entitydb/id   1
                               :entitydb/type :note}
                              2
                              {:id            2
                               :title         "Note title"
                               :entitydb/id   2
                               :entitydb/type :note}}}
            :entitydb.named/collection
                            {:note/favourites
                             {:data
                                    [(->EntityIdent :note 1)
                                     (->EntityIdent :note 2)]
                              :meta nil}}}

        expected-layout [{:id            1
                          :title         "Note title"
                          :entitydb/id   1
                          :entitydb/type :note}
                         {:id            2
                          :title         "Note title"
                          :entitydb/id   2
                          :entitydb/type :note}]]
    (is (= (edb/get-collection db :note/favourites) expected-layout))))

(deftest inserting-nil-relation-one-removes-existing-relation-one
  (let [with-schema (edb/insert-schema {} {:note {:entitydb/relations
                                                  {:user :user}}})
        with-data (-> with-schema
                      (edb/insert :note {:id   1
                                         :user {:id 1 :name "Foo"}}))
        insert-user-nil (edb/insert with-data :note {:id 1 :user nil})]

    (is (not (nil? (get-in with-data [:entitydb/relations (->EntityIdent :note 1)]))))

    (is (nil? (get-in insert-user-nil [:entitydb/relations (->EntityIdent :note 1)])))

    (is (not (nil? (get-in with-data [:entitydb.relations/reverse (->EntityIdent :user 1)]))))

    (is (nil? (get-in insert-user-nil [:entitydb.relations/reverse (->EntityIdent :user 1)])))))


(deftest inserting-item-with-no-related-data-leaves-existing-relations-intact
  (let [with-schema (edb/insert-schema {} {:note {:entitydb/relations
                                                  {:user :user}}})
        with-data (-> with-schema
                      (edb/insert :note {:id   1
                                         :user {:id 1 :name "Foo"}}))
        insert-note-data (edb/insert with-data :note {:id 1 :title "Note title"})]

    (is (= (get-in with-data [:entitydb/relations (->EntityIdent :note 1)])
           (get-in insert-note-data [:entitydb/relations (->EntityIdent :note 1)])))

    (is (= (get-in with-data [:entitydb.relations/reverse (->EntityIdent :user 1)])
           (get-in insert-note-data [:entitydb.relations/reverse (->EntityIdent :user 1)])))))

(deftest circular-relations-test
  (let
    [with-schema (edb/insert-schema {} {:note {:entitydb/relations
                                               {:user :user}}
                                        :user {:entitydb/relations
                                               {:note :note}}})
     db (-> with-schema
            (edb/insert :note {:id    1
                               :title "Note 1"
                               :user  {:id 1 :name "Foo"}})
            (edb/insert :note {:id    2
                               :title "Note 2"
                               :user  {:id 2 :name "Bar"}})
            (edb/insert :note {:id    3
                               :title "Note 3"
                               :user  {:id 3 :name "Baz"}})
            (edb/insert :user {:id   1
                               :name "Foo"
                               :note {:id    2
                                      :title "Note 2"}})
            (edb/insert :user {:id   2
                               :name "Bar"
                               :note {:id    3
                                      :title "Note 3"}})
            (edb/insert :user {:id   3
                               :name "Baz"
                               :note {:id    3
                                      :title "Note 3"}}))
     note-from-db (edb/get-by-id db :note 3)
     _ (log note-from-db)
     note-user (edb/get-by-id db :user (:id (:user note-from-db)))
     _ (log note-user)
     note-user-note (edb/get-by-id db :note (:id (:note note-user)))
     _ (log note-user-note)]
    (is (= (:name note-user) "Baz"))
    (is (= note-from-db note-user-note))
    ))

#_(let [note-from-db (edb/get-item-by-id schema db-with-items :notes 1)
      note-link (get ((:links note-from-db)) 0)
      note-link-note (get ((:notes note-link)) 0)]
  (= (:url note-link) "http://www.google.com")
  (= note-from-db note-link-note))