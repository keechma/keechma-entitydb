(ns keechma.entitydb.core-test
  (:require
   [cljs.test :refer-macros [deftest is testing use-fixtures]]
   [keechma.entitydb.internal :refer [->EntityIdent]]
   [keechma.entitydb.core :as edb]
   [keechma.entitydb.query :as q]
   [keechma.entitydb.data :as data]
   [keechma.entitydb.util :refer [log]]))

(use-fixtures :once
  {:before (fn [] (js/console.clear))})

;; INSERT TESTS


(deftest insert-user-test
  (let [res (edb/insert-entity {} :user {:id 1 :username "Retro"})]
    (is (= res {:entitydb/store {:user {1 {:id 1 :entitydb/id 1 :username "Retro" :entitydb/type :user}}}}))))

(deftest insert-entity-with-custom-id-fn-test
  (let [id-fn       (fn [item] (str (:id item) "-note"))
        with-schema (edb/insert-schema {} {:note {:entitydb/id id-fn}})
        with-data   (-> with-schema
                        (edb/insert-entity :note {:id 1 :title "Note title"}))
        res         (get-in with-data [:entitydb/store :note "1-note"])]
    (is (= res {:id 1 :entitydb/id "1-note" :title "Note title" :entitydb/type :note}))))

(deftest inserting-entity-when-exists-merges-attrs-test
  (let [store {:entitydb/store {:user {1 {:full-name "Mihael Konjevic"}}}}
        res   (edb/insert-entity store :user {:id 1 :username "Retro"})]
    (is (= res {:entitydb/store
                {:user
                 {1 {:id 1 :entitydb/id 1 :username "Retro" :full-name "Mihael Konjevic" :entitydb/type :user}}}}))))

(deftest insert-many-test
  (let [res (edb/insert-entities {} :user [{:id 1 :username "Retro"}
                                           {:id 2 :username "Tibor"}])]
    (is (= res {:entitydb/store {:user {1 {:id 1 :entitydb/id 1 :username "Retro" :entitydb/type :user}
                                        2 {:id 2 :entitydb/id 2 :username "Tibor" :entitydb/type :user}}}}))))

(deftest insert-named-test
  (let [with-items      (edb/insert-named {} :note :note/current {:id 1 :title "Note title"})
        expected-layout {:entitydb/store
                         {:note
                          {1
                           {:id            1
                            :title         "Note title"
                            :entitydb/id   1
                            :entitydb/type :note}}}
                         :entitydb.named/item
                         {:note/current
                          {:data (->EntityIdent :note 1)
                           :meta nil}}}]
    (is (= with-items expected-layout))))

(deftest insert-named-with-meta-test
  (let [with-items      (edb/insert-named {} :note :note/current {:id 1 :title "Note title"} {:foo "bar"})
        expected-layout {:entitydb/store
                         {:note
                          {1
                           {:id            1
                            :title         "Note title"
                            :entitydb/id   1
                            :entitydb/type :note}}}
                         :entitydb.named/item
                         {:note/current
                          {:data (->EntityIdent :note 1)
                           :meta {:foo "bar"}}}}]
    (is (= with-items expected-layout))))

;; REMOVE TESTS

(deftest remove-item-test
  (let [with-schema (edb/insert-schema {} {:note    {:entitydb/relations
                                                     {:user  :user
                                                      :links {:entitydb.relation/path [:links :*]
                                                              :entitydb.relation/type :link}}}
                                           :comment {:entitydb/relations
                                                     {:user :user}}
                                           :link    {:entitydb/id :url}})
        with-data   (-> with-schema
                        (edb/insert-entity :note {:id    1
                                                  :title "Note title"
                                                  :user  {:id       1
                                                          :username "Retro"}
                                                  :links [{:url "www.google.com"}]})
                        (edb/insert-entity :comment {:id   1
                                                     :text "This is comment"
                                                     :user {:id 1}})
                        (edb/insert-entity :note {:id    2
                                                  :title "Some other title"
                                                  :user  {:id       2
                                                          :username "Tibor"}
                                                  :links [{:url "www.yahoo.com"}
                                                          {:url "www.google2.com"}]}))]
    (let [db-with-deleted (edb/remove-entity with-data :note 2)
          query-1         (edb/get-entity db-with-deleted :note 2)
          query-2         [(q/reverse-include :note [])]
          res-2           (edb/get-entity db-with-deleted :link "www.google2.com" query-2)]
      ;; check if note with id 2 exists in store
      (is (nil? query-1))
      ;; check if note with id 2 relations exist
      (is (nil? (get (:entitydb/relations db-with-deleted) (->EntityIdent :note 2))))
      ;; check if reverse relations which point to note with id 2 exist
      (is (nil? (:entitydb.relations/reverse res-2))))))

;; GETTER TEST

(deftest get-entity-test
  (let [store {:entitydb/store {:note {1 {:id 1 :title "Note title"}}}}]
    (is (= (:title (edb/get-entity store :note 1)) "Note title"))))

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
                           :meta nil}}}]
    (is (= (edb/get-named store-with-item :note/current)
           {:entitydb/id 1 :entitydb/type :note :id 1 :title "Note title"}))))

(deftest get-collection-test
  (let [with-data       {:entitydb/store {:note
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
                         {:note/favorites
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
    (is (= (edb/get-collection with-data :note/favorites) expected-layout))))

;; RELATIONS TEST

;; COLLECTIONS

(deftest inserting-nil-relation-one-removes-existing-relation-one
  (let [with-schema     (edb/insert-schema {} {:note {:entitydb/relations
                                                      {:user :user}}})
        with-data       (-> with-schema
                            (edb/insert-entity :note {:id   1
                                                      :user {:id 1 :name "Foo"}}))
        insert-user-nil (edb/insert-entity with-data :note {:id 1 :user nil})]

    (is (not (nil? (get-in with-data [:entitydb/relations (->EntityIdent :note 1)]))))

    (is (nil? (get-in insert-user-nil [:entitydb/relations (->EntityIdent :note 1)])))

    (is (not (nil? (get-in with-data [:entitydb.relations/reverse (->EntityIdent :user 1)]))))

    (is (nil? (get-in insert-user-nil [:entitydb.relations/reverse (->EntityIdent :user 1)])))))

(deftest inserting-entity-with-no-related-data-leaves-existing-relations-intact
  (let [with-schema      (edb/insert-schema {} {:note {:entitydb/relations
                                                       {:user :user}}})
        with-data        (-> with-schema
                             (edb/insert-entity :note {:id   1
                                                       :user {:id 1 :name "Foo"}}))
        insert-note-data (edb/insert-entity with-data :note {:id 1 :title "Note title"})]

    (is (= (get-in with-data [:entitydb/relations (->EntityIdent :note 1)])
           (get-in insert-note-data [:entitydb/relations (->EntityIdent :note 1)])))

    (is (= (get-in with-data [:entitydb.relations/reverse (->EntityIdent :user 1)])
           (get-in insert-note-data [:entitydb.relations/reverse (->EntityIdent :user 1)])))))

(deftest circular-relations-test
  (let
   [with-schema                (edb/insert-schema {} {:note {:entitydb/relations
                                                             {:links {:entitydb.relation/path [:links :*]
                                                                      :entitydb.relation/type :link}}}
                                                      :link {:entitydb/relations
                                                             {:notes {:entitydb.relation/path [:notes :*]
                                                                      :entitydb.relation/type :note}}}})
    with-data                  (-> with-schema
                                   (edb/insert-entity :note {:id    1
                                                             :title "Note #1"
                                                             :links [{:id 1}
                                                                     {:id 2}]})
                                   (edb/insert-entity :note {:id    2
                                                             :title "Note #2"
                                                             :links [{:id 2}]})
                                   (edb/insert-entity :note {:id    3
                                                             :title "Note #3"
                                                             :links [{:id 1}
                                                                     {:id 3}]})
                                   (edb/insert-entity :link {:id    1
                                                             :url   "http://google.com"
                                                             :notes [{:id 1}
                                                                     {:id 3}]})
                                   (edb/insert-entity :link {:id    2
                                                             :url   "http://bing.com"
                                                             :notes [{:id 2}
                                                                     {:id    4
                                                                      :title "Note #4"}]})
                                   (edb/insert-entity :link {:id    3
                                                             :url   "http://yahoo.com"
                                                             :notes [{:id 3}]}))
    note-1                     (edb/get-entity with-data :note 1 [(q/include :links)])
    note-1-reverse-from-link-1 (edb/get-entity with-data :link 1 [(q/reverse-include :note)])
    link-1                     (edb/get-entity with-data :link 1 [(q/include :notes [(q/include :links [(q/include :notes)])])])
    link-1-reverse-from-note-1 (edb/get-entity with-data :note 1 [(q/reverse-include :link)])
    link-1-from-note-1         (get-in note-1 [:links 0])
    note-1-from-link-1         (get-in link-1 [:notes 0])]
    (is (= "http://google.com"
           (:url link-1-from-note-1)))
    (is (= {:id            1
            :title         "Note #1"
            :entitydb/type :note
            :entitydb/id   1}
           (dissoc note-1-from-link-1 :links :entitydb.relations/reverse)))
    (is (= (dissoc note-1 :links)
           (dissoc (get-in note-1-reverse-from-link-1 [:entitydb.relations/reverse :note :links 1]) :links)))
    (is (= (dissoc link-1 :notes)
           (dissoc (get-in link-1-reverse-from-note-1 [:entitydb.relations/reverse :link :notes 1]) :notes)))
    (is (= {:id            4
            :title         "Note #4"
            :entitydb/type :note
            :entitydb/id   4}
           (get-in link-1 [:notes 0 :links 1 :notes 1])))))


(deftest add-to-collection-test
  (let [with-schema (edb/insert-schema {} data/schema)
        with-data   (-> with-schema
                        (edb/insert-entity :user data/user-1-data))]
    (let [append-data                                    (-> with-data
                                                             (edb/get-entity :user 1)
                                                             (update-in [:posts :edges] (fn [i] (conj i {:cursor "3"
                                                                                                         :node   {:slug  "my-post-5"
                                                                                                                  :title "My Post #5"}}))))
          prepend-data                                   (-> with-data
                                                             (edb/get-entity :user 1)
                                                             (update-in [:posts :edges] (fn [i] (concat [{:cursor "3"
                                                                                                          :node   {:slug  "my-post-5"
                                                                                                                   :title "My Post #5"}}]
                                                                                                        i))))

          with-appended-data                             (edb/insert-entity with-data :user append-data)
          with-prepended-data                            (edb/insert-entity with-data :user prepend-data)
          expected-post-data                             {:slug          "my-post-5"
                                                          :title         "My Post #5"
                                                          :entitydb/id   "my-post-5"
                                                          :entitydb/type :post}
          expected-user-posts-with-appended-data         [{:cursor "1" :node (->EntityIdent :post "my-post-1")}
                                                          {:cursor "2" :node (->EntityIdent :post "my-post-2")}
                                                          {:cursor "3" :node (->EntityIdent :post "my-post-5")}]
          expected-user-posts-with-prepended-data        [{:cursor "3" :node (->EntityIdent :post "my-post-5")}
                                                          {:cursor "1" :node (->EntityIdent :post "my-post-1")}
                                                          {:cursor "2" :node (->EntityIdent :post "my-post-2")}]
          appended-post-data                             (edb/get-entity with-appended-data :post "my-post-5")
          prepended-post-data                            (edb/get-entity with-prepended-data :post "my-post-5")
          expected-relations-with-appended-data          {[:posts :edges 0 :node] (->EntityIdent :post "my-post-1")
                                                          [:posts :edges 1 :node] (->EntityIdent :post "my-post-2")
                                                          [:posts :edges 2 :node] (->EntityIdent :post "my-post-5")}
          expected-relations-with-prepended-data         {[:posts :edges 0 :node] (->EntityIdent :post "my-post-5")
                                                          [:posts :edges 1 :node] (->EntityIdent :post "my-post-1")
                                                          [:posts :edges 2 :node] (->EntityIdent :post "my-post-2")}
          expected-reverse-relations-with-appended-data  {:user {:posts {1 #{[:posts :edges 2 :node]}}}}
          expected-reverse-relations-with-prepended-data {:user {:posts {1 #{[:posts :edges 0 :node]}}}}]
      (is (= expected-post-data
             appended-post-data))
      (is (= expected-post-data
             prepended-post-data))
      (is (= expected-user-posts-with-appended-data
             (get-in (edb/get-entity with-appended-data :user 1) [:posts :edges])))
      (is (= expected-user-posts-with-prepended-data
             (get-in (edb/get-entity with-prepended-data :user 1) [:posts :edges])))
      (is (= expected-relations-with-appended-data
             (get-in with-appended-data [:entitydb/relations (->EntityIdent :user 1) :posts])))
      (is (= expected-relations-with-prepended-data
             (get-in with-prepended-data [:entitydb/relations (->EntityIdent :user 1) :posts])))
      (is (= expected-reverse-relations-with-appended-data
             (get-in with-appended-data [:entitydb.relations/reverse (->EntityIdent :post "my-post-5")])))
      (is (= expected-reverse-relations-with-prepended-data
             (get-in with-prepended-data [:entitydb.relations/reverse (->EntityIdent :post "my-post-5")]))))))

(deftest relation-one-test
  (let [with-schema    (edb/insert-schema {} {:note {:entitydb/relations
                                                     {:user :user}}})
        with-data      (-> with-schema
                           (edb/insert-entity :note {:id    1
                                                     :title "Note title"
                                                     :user  {:id       1
                                                             :username "Retro"}}))
        expected-store {:note {1 {:id            1
                                  :title         "Note title"
                                  :user          (->EntityIdent :user 1)
                                  :entitydb/id   1
                                  :entitydb/type :note}}
                        :user {1 {:id            1
                                  :username      "Retro"
                                  :entitydb/id   1
                                  :entitydb/type :user}}}]
    (is (= expected-store
           (:entitydb/store with-data)))
    (let [note-from-store (edb/get-entity with-data :note 1 [(q/include :user)])
          user-from-db    (edb/get-entity with-data :user 1 [(q/reverse-include :note)])
          expected-note   {:id            1
                           :title         "Note title"
                           :user          {:id            1
                                           :username      "Retro"
                                           :entitydb/id   1
                                           :entitydb/type :user}
                           :entitydb/id   1
                           :entitydb/type :note}
          expected-user   {:id            1
                           :username      "Retro"
                           :entitydb/id   1
                           :entitydb/type :user}]
      (is (= note-from-store
             expected-note))
      (is (= (dissoc user-from-db :entitydb.relations/reverse)
             expected-user))
      (is (= (dissoc user-from-db :entitydb.relations/reverse)
             (:user note-from-store)))
      (is (= user-from-db
             (-> expected-user
                 (assoc :entitydb.relations/reverse {:note {:user {1 (-> expected-note
                                                                         (dissoc :user)
                                                                         (assoc :user (->EntityIdent :user 1)))}}})))))))


(deftest relation-many-test
  (let [with-schema    (edb/insert-schema {} data/note-user-link-schema)
        with-data      (-> with-schema
                           (edb/insert-entity :note {:id    1
                                                     :title "Note title"
                                                     :user  {:id       1
                                                             :username "Retro"}
                                                     :links [{:id  1
                                                              :url "http://google.com"}
                                                             {:id  2
                                                              :url "http://yahoo.com"}]}))
        expected-store {:note {1 {:id            1
                                  :title         "Note title"
                                  :entitydb/id   1
                                  :entitydb/type :note
                                  :user          (->EntityIdent :user 1)
                                  :links         [(->EntityIdent :link 1)
                                                  (->EntityIdent :link 2)]}}
                        :user {1 {:id            1
                                  :username      "Retro"
                                  :entitydb/id   1
                                  :entitydb/type :user}}
                        :link {1 {:id            1
                                  :url           "http://google.com"
                                  :entitydb/id   1
                                  :entitydb/type :link}
                               2 {:id            2
                                  :url           "http://yahoo.com"
                                  :entitydb/id   2
                                  :entitydb/type :link}}}]
    (is (= expected-store
           (:entitydb/store with-data)))
    (is (= (edb/get-entity with-data :link 1)
           {:id            1
            :url           "http://google.com"
            :entitydb/id   1
            :entitydb/type :link}))
    (let [note-from-store (edb/get-entity with-data :note 1 [(q/include :links)])]
      (is (= (dissoc note-from-store :links :user)
             {:id            1
              :title         "Note title"
              :entitydb/id   1
              :entitydb/type :note}))
      (is (= (:links note-from-store)
             [{:id            1
               :url           "http://google.com"
               :entitydb/id   1
               :entitydb/type :link}
              {:id            2
               :url           "http://yahoo.com"
               :entitydb/id   2
               :entitydb/type :link}])))))

(deftest nested-relations-test
  (let [with-schema               (edb/insert-schema {} (assoc data/note-user-link-schema :link {:entitydb/relations
                                                                                                 {:user :user}}))
        with-data                 (-> with-schema
                                      (edb/insert-entity :note {:id    1
                                                                :title "Note title"
                                                                :links [{:id 1}]})
                                      (edb/insert-entity :user {:id       1
                                                                :username "Retro"})
                                      (edb/insert-entity :link {:id   1
                                                                :url  "http://google.com"
                                                                :user {:id 1}}))
        link-1-relation-from-note (-> (edb/get-entity with-data :note 1 [(q/include :links [(q/include :user)])])
                                      (get-in [:links 0]))
        user-from-link-1-relation (:user link-1-relation-from-note)
        expected-user             {:id            1
                                   :username      "Retro"
                                   :entitydb/id   1
                                   :entitydb/type :user}]
    (is (= expected-user
           user-from-link-1-relation))))

(deftest tree-relations-test
  (let [with-schema (edb/insert-schema {} {:note {:entitydb/relations
                                                  {:notes {:entitydb.relation/path [:notes :*]
                                                           :entitydb.relation/type :note}}}})
        with-data   (-> with-schema
                        (edb/insert-entities :note [{:id    1
                                                     :title "Note #1"
                                                     :notes [{:id 2}]}
                                                    {:id    2
                                                     :title "Note #2"
                                                     :notes [{:id 3}]}
                                                    {:id    3
                                                     :title "Note #3"}]))
        note-1      (edb/get-entity with-data :note 1 [(q/include :notes [(q/include :notes)])])]
    (is (= "Note #2"
           (get-in note-1 [:notes 0 :title])))
    (is (= "Note #3"
           (get-in note-1 [:notes 0 :notes 0 :title])))))

(deftest remove-collection-test
  (let [with-schema         (edb/insert-schema {} {})
        with-data           (-> with-schema
                                (edb/insert-entity :note {:id    1
                                                          :title "Note title"})
                                (edb/insert-named :note :note/current {:id 1})
                                (edb/insert-named :note :note/pinned {:id 2})
                                (edb/insert-collection :note :note/latest [{:id 1}
                                                                           {:id 2}])
                                (edb/insert-collection :note :note/starred [{:id 2}
                                                                            {:id 3}]))
        with-deleted-data   (-> with-data
                                (edb/remove-named :note/current)
                                (edb/remove-collection :note/latest))
        expected-store      {:note {1 {:id            1
                                       :title         "Note title"
                                       :entitydb/id   1
                                       :entitydb/type :note}
                                    2 {:id            2
                                       :entitydb/id   2
                                       :entitydb/type :note}
                                    3 {:id            3
                                       :entitydb/id   3
                                       :entitydb/type :note}}}
        expected-named      {:note/pinned
                             {:data (->EntityIdent :note 2)
                              :meta nil}}
        expected-collection {:note/starred
                             {:data [(->EntityIdent :note 2)
                                     (->EntityIdent :note 3)]
                              :meta nil}}]
    (is (= expected-store
           (:entitydb/store with-deleted-data)))
    (is (= expected-named
           (:entitydb.named/item with-deleted-data)))
    (is (= expected-collection
           (:entitydb.named/collection with-deleted-data)))))

(deftest append-collection-test
  (let [with-schema         (edb/insert-schema {} {})
        with-data           (-> with-schema
                                (edb/insert-collection :note :note/latest [{:id 1}
                                                                           {:id 2}])
                                (edb/append-collection :note :note/latest [{:id 3}
                                                                           {:id 4}]))
        expected-store      {:note {1 {:id            1
                                       :entitydb/id   1
                                       :entitydb/type :note}
                                    2 {:id            2
                                       :entitydb/id   2
                                       :entitydb/type :note}
                                    3 {:id            3
                                       :entitydb/id   3
                                       :entitydb/type :note}
                                    4 {:id            4
                                       :entitydb/id   4
                                       :entitydb/type :note}}}
        expected-collection {:note/latest
                             {:data [(->EntityIdent :note 1)
                                     (->EntityIdent :note 2)
                                     (->EntityIdent :note 3)
                                     (->EntityIdent :note 4)]
                              :meta nil}}]
    (is (= expected-store
           (:entitydb/store with-data)))
    (is (= expected-collection
           (:entitydb.named/collection with-data)))))

(deftest prepend-collection-test
  (let [with-schema         (edb/insert-schema {} {})
        with-data           (-> with-schema
                                (edb/insert-collection :note :note/latest [{:id 1}
                                                                           {:id 2}])
                                (edb/prepend-collection :note :note/latest [{:id 3}
                                                                            {:id 4}]))
        expected-store      {:note {1 {:id            1
                                       :entitydb/id   1
                                       :entitydb/type :note}
                                    2 {:id            2
                                       :entitydb/id   2
                                       :entitydb/type :note}
                                    3 {:id            3
                                       :entitydb/id   3
                                       :entitydb/type :note}
                                    4 {:id            4
                                       :entitydb/id   4
                                       :entitydb/type :note}}}
        expected-collection {:note/latest
                             {:data [(->EntityIdent :note 3)
                                     (->EntityIdent :note 4)
                                     (->EntityIdent :note 1)
                                     (->EntityIdent :note 2)]
                              :meta nil}}]
    (is (= expected-store
           (:entitydb/store with-data)))
    (is (= expected-collection
           (:entitydb.named/collection with-data)))))

(deftest vacuum-collection-test
  (let [with-schema         (edb/insert-schema {} {})
        with-data           (-> with-schema
                                (edb/insert-entity :note {:id    1
                                                          :title "Note title"})
                                (edb/insert-named :note :note/current {:id 1})
                                (edb/insert-named :note :note/pinned {:id 2})
                                (edb/insert-collection :note :note/latest [{:id 1}
                                                                           {:id 2}])
                                (edb/insert-collection :note :note/starred [{:id 2}
                                                                            {:id 3}]))
        with-deleted-data   (-> with-data
                                (edb/remove-named :note/current)
                                (edb/remove-collection :note/latest))
        expected-store      {:note {2 {:id            2
                                       :entitydb/id   2
                                       :entitydb/type :note}
                                    3 {:id            3
                                       :entitydb/id   3
                                       :entitydb/type :note}}}
        expected-named      {:note/pinned
                             {:data (->EntityIdent :note 2)
                              :meta nil}}
        expected-collection {:note/starred
                             {:data [(->EntityIdent :note 2)
                                     (->EntityIdent :note 3)]
                              :meta nil}}
        with-vacuumed-data  (-> with-deleted-data
                                (edb/vacuum))]
    (is (= expected-store
           (:entitydb/store with-vacuumed-data)))
    (is (= expected-named
           (:entitydb.named/item with-vacuumed-data)))
    (is (= expected-collection
           (:entitydb.named/collection with-vacuumed-data)))))

(deftest removing-entity-removes-its-id-from-collections-test
  (let [with-schema         (edb/insert-schema {} {})
        with-data           (-> with-schema
                                (edb/insert-entities :note [{:id 1}
                                                            {:id 2}])
                                (edb/insert-named :note :note/current {:id 1})
                                (edb/insert-collection :note :note/list [{:id 1} {:id 2}]))
        with-deleted-data   (edb/remove-entity with-data :note 1)
        expected-store      {:note {2 {:id            2
                                       :entitydb/id   2
                                       :entitydb/type :note}}}
        expected-collection {:note/list
                             {:data [(->EntityIdent :note 2)]
                              :meta nil}}]
    (is (= expected-store
           (:entitydb/store with-deleted-data)))
    (is (= expected-collection
           (:entitydb.named/collection with-deleted-data)))))

(deftest getting-nil-entity-with-relations-should-return-nil-test
  (let [with-schema (edb/insert-schema {} {:user {:entitydb/relations
                                                  {:notes {:entitydb.relation/path [:notes :*]
                                                           :entitydb.relation/type :note}}}})]
    (is (nil? (edb/get-named with-schema :user/current)))))

(deftest manipulating-relation-many-test
  (let [with-schema                  (edb/insert-schema {} {:note {:entitydb/relations
                                                                   {:links {:entitydb.relation/path [:links :*]
                                                                            :entitydb.relation/type :link}}}})
        with-data                    (-> with-schema
                                         (edb/insert-entity :note {:id    1
                                                                   :title "Note #1"
                                                                   :links [{:id  1
                                                                            :url "http://www.google.com"}
                                                                           {:id  2
                                                                            :url "http://www.yahoo.com"}]}))
        expected-store               {:note {1 {:id            1
                                                :title         "Note #1"
                                                :links         [(->EntityIdent :link 1)
                                                                (->EntityIdent :link 2)]
                                                :entitydb/id   1
                                                :entitydb/type :note}}
                                      :link {1 {:id            1
                                                :url           "http://www.google.com"
                                                :entitydb/id   1
                                                :entitydb/type :link}
                                             2 {:id            2
                                                :url           "http://www.yahoo.com"
                                                :entitydb/id   2
                                                :entitydb/type :link}}}
        expected-store-after-append  {:note {1 {:id            1
                                                :title         "Note #1"
                                                :links         [(->EntityIdent :link 1)
                                                                (->EntityIdent :link 2)
                                                                (->EntityIdent :link 3)]
                                                :entitydb/id   1
                                                :entitydb/type :note}}
                                      :link {1 {:id            1
                                                :url           "http://www.google.com"
                                                :entitydb/id   1
                                                :entitydb/type :link}
                                             2 {:id            2
                                                :url           "http://www.yahoo.com"
                                                :entitydb/id   2
                                                :entitydb/type :link}
                                             3 {:id            3
                                                :url           "http://www.bing.com"
                                                :entitydb/id   3
                                                :entitydb/type :link}}}
        expected-store-after-prepend {:note {1 {:id            1
                                                :title         "Note #1"
                                                :links         [(->EntityIdent :link 4)
                                                                (->EntityIdent :link 1)
                                                                (->EntityIdent :link 2)]
                                                :entitydb/id   1
                                                :entitydb/type :note}}
                                      :link {1 {:id            1
                                                :url           "http://www.google.com"
                                                :entitydb/id   1
                                                :entitydb/type :link}
                                             2 {:id            2
                                                :url           "http://www.yahoo.com"
                                                :entitydb/id   2
                                                :entitydb/type :link}
                                             4 {:id            4
                                                :url           "http://www.altavista.com"
                                                :entitydb/id   4
                                                :entitydb/type :link}}}
        expected-store-after-remove  {:note {1 {:id            1
                                                :title         "Note #1"
                                                :links         [(->EntityIdent :link 2)]
                                                :entitydb/id   1
                                                :entitydb/type :note}}
                                      :link {2 {:id            2
                                                :url           "http://www.yahoo.com"
                                                :entitydb/id   2
                                                :entitydb/type :link}}}
        append-data                  (-> with-data
                                         (edb/get-entity :note 1)
                                         (update :links (fn [i] (conj i {:id  3
                                                                         :url "http://www.bing.com"}))))
        with-appended-data           (edb/insert-entity with-data :note append-data)
        prepend-data                 (-> with-data
                                         (edb/get-entity :note 1)
                                         (update :links (fn [i] (concat [{:id  4
                                                                          :url "http://www.altavista.com"}]
                                                                        i))))
        with-prepended-data          (edb/insert-entity with-data :note prepend-data)]
    (is (= expected-store
           (:entitydb/store with-data)))
    (is (= {:id 1 :url "http://www.google.com" :entitydb/id 1 :entitydb/type :link}
           (edb/get-entity with-data :link 1)))
    (is (= expected-store-after-append
           (:entitydb/store with-appended-data)))
    (is (= expected-store-after-prepend
           (:entitydb/store with-prepended-data)))
    (is (= expected-store-after-remove
           (:entitydb/store (edb/remove-entity with-data :link 1))))))

(deftest get-relation-path-test
  (let [with-schema                     (edb/insert-schema {} data/note-user-link-schema)
        with-data                       (-> with-schema
                                            (edb/insert-entity :note {:id    1
                                                                      :user  {:id 1}
                                                                      :links [{:id 1}]}))
        expected-note-relations         {(->EntityIdent :note 1) {:user  {[:user] (->EntityIdent :user 1)}
                                                                  :links {[:links 0] (->EntityIdent :link 1)}}}
        expected-note-reverse-relations {(->EntityIdent :user 1) {:note {:user {1 #{[:user]}}}}
                                         (->EntityIdent :link 1) {:note {:links {1 #{[:links 0]}}}}}]
    (is (= expected-note-relations
           (:entitydb/relations with-data)))
    (is (= expected-note-reverse-relations
           (:entitydb.relations/reverse with-data)))))

(deftest insert-empty-collection-will-remove-existing-data-test
  (let [with-schema         (edb/insert-schema {} {})
        with-data           (-> with-schema
                                (edb/insert-collection :note :note/list [{:id 1} {:id 2}]))
        expected-store      {:note {1 {:id            1
                                       :entitydb/id   1
                                       :entitydb/type :note}
                                    2 {:id            2
                                       :entitydb/id   2
                                       :entitydb/type :note}}}
        expected-collection {:note/list
                             {:data [(->EntityIdent :note 1)
                                     (->EntityIdent :note 2)]
                              :meta nil}}
        with-removed-data   (edb/insert-collection with-data :note :note/list [])]
    (is (= expected-store
           (:entitydb/store with-data)))
    (is (= expected-collection
           (:entitydb.named/collection with-data)))
    (is (= expected-store
           (:entitydb/store with-removed-data)))
    (is (empty? (get-in (:entitydb.named/collection with-removed-data) [:note/list :data])))))

(deftest insert-empty-collection-to-relation-will-remove-existing-data-test
  (let [with-schema                    (edb/insert-schema {} data/note-user-link-schema)
        with-data                      (-> with-schema
                                           (edb/insert-entity :note {:id    1
                                                                     :title "Note #1"
                                                                     :links [{:id  1
                                                                              :url "http://www.google.com"}
                                                                             {:id  2
                                                                              :url "http://www.yahoo.com"}]}))
        expected-store                 {:note {1 {:id            1
                                                  :title         "Note #1"
                                                  :links         []
                                                  :entitydb/id   1
                                                  :entitydb/type :note}}
                                        :link {1 {:id            1
                                                  :url           "http://www.google.com"
                                                  :entitydb/id   1
                                                  :entitydb/type :link}
                                               2 {:id            2
                                                  :url           "http://www.yahoo.com"
                                                  :entitydb/id   2
                                                  :entitydb/type :link}}}

        with-inserted-empty-collection (-> with-data
                                           (edb/insert-entity :note {:id    1
                                                                     :title "Note #1"
                                                                     :links []}))]
    (is (= expected-store
           (:entitydb/store with-inserted-empty-collection)))))

(deftest resolve-relations-test
  (let [with-schema       (edb/insert-schema {} (assoc
                                                 data/note-user-link-schema
                                                 :link
                                                 {:entitydb/relations {:user :user}}))
        with-data         (-> with-schema
                              (edb/insert-entity :note {:id    1
                                                        :title "Note #1"
                                                        :links [{:id 1}]
                                                        :user  {:id 1}})
                              (edb/insert-entity :user {:id       1
                                                        :username "Retro"})
                              (edb/insert-entity :link {:id   1
                                                        :url  "http://www.google.com"
                                                        :user {:id 1}}))
        expected-data     {:id            1
                           :title         "Note #1"
                           :entitydb/id   1
                           :entitydb/type :note
                           :links         [{:id            1
                                            :url           "http://www.google.com"
                                            :entitydb/id   1
                                            :entitydb/type :link
                                            :user          (->EntityIdent :user 1)}]
                           :user          {:id            1
                                           :username      "Retro"
                                           :entitydb/id   1
                                           :entitydb/type :user}}
        note-1-from-store (edb/get-entity with-data :note 1 [:links :user])]
    (is (= expected-data
           note-1-from-store))))

(deftest resolve-relations-for-named-test
  (let [with-schema   (edb/insert-schema {} (assoc
                                             data/note-user-link-schema
                                             :link
                                             {:entitydb/relations {:user :user}}))
        with-data     (-> with-schema
                          (edb/insert-named :note :note/current {:id    1
                                                                 :title "Note #1"
                                                                 :links [{:id 1}]
                                                                 :user  {:id 1}})
                          (edb/insert-entity :user {:id       1
                                                    :username "Retro"})
                          (edb/insert-entity :link {:id   1
                                                    :url  "http://www.google.com"
                                                    :user {:id 1}}))
        expected-data {:id            1
                       :title         "Note #1"
                       :entitydb/id   1
                       :entitydb/type :note
                       :links         [{:id            1
                                        :url           "http://www.google.com"
                                        :entitydb/id   1
                                        :entitydb/type :link
                                        :user          (->EntityIdent :user 1)}]
                       :user          {:id            1
                                       :username      "Retro"
                                       :entitydb/id   1
                                       :entitydb/type :user}}
        current-note  (edb/get-named with-data :note/current [:links :user])]
    (is (= expected-data
           current-note))))

(deftest resolve-relations-for-collection-test
  (let [with-schema   (edb/insert-schema {} (assoc
                                             data/note-user-link-schema
                                             :link
                                             {:entitydb/relations {:user :user}}))
        with-data     (-> with-schema
                          (edb/insert-collection :note :note/list [{:id    1
                                                                    :title "Note #1"
                                                                    :links [{:id 1}]
                                                                    :user  {:id 1}}])
                          (edb/insert-entity :user {:id       1
                                                    :username "Retro"})
                          (edb/insert-entity :link {:id   1
                                                    :url  "http://www.google.com"
                                                    :user {:id 1}}))
        expected-data {:id            1
                       :title         "Note #1"
                       :entitydb/id   1
                       :entitydb/type :note
                       :links         [{:id            1
                                        :url           "http://www.google.com"
                                        :entitydb/id   1
                                        :entitydb/type :link
                                        :user          (->EntityIdent :user 1)}]
                       :user          {:id            1
                                       :username      "Retro"
                                       :entitydb/id   1
                                       :entitydb/type :user}}
        current-note  (edb/get-collection with-data :note/list [:links :user])]
    (is (= [expected-data]
           current-note))))

(deftest processor-test
  (let [with-schema          (edb/insert-schema {} {:user {:entitydb/relations
                                                           {:roles {:entitydb.relation/path [:roles :*]
                                                                    :entitydb.relation/type :role}}}
                                                    :role {:entitydb/processor (fn [r] {:id r})}})
        with-data            (-> with-schema
                                 (edb/insert-entity :user {:id       1
                                                           :username "Retro"
                                                           :roles    ["ADMIN" "EDITOR"]})
                                 (edb/insert-entity :user {:id       2
                                                           :username "Tibor"
                                                           :roles    ["EDITOR"]}))
        expected-admin-role  (:id (edb/get-entity with-data :role "ADMIN"))
        expected-editor-role (-> with-data
                                 (edb/get-entity :user 1 [(q/include :roles)])
                                 (get-in [:roles 1 :id]))
        expected-users       (-> with-data
                                 (edb/get-entity :role "EDITOR" [(q/reverse-include :user)])
                                 (get-in [:entitydb.relations/reverse :user :roles])
                                 keys)]
    (is (= "ADMIN"
           expected-admin-role))
    (is (= "EDITOR"
           expected-editor-role))
    (is (= '(1 2)
           expected-users))))

(def recursive-data
  {:name    "Root"
   :files   {:edges [{:node {:name "File Root: 1"}}]}
   :folders {:edges [{:node {:name  "1"
                             :files {:edges [{:node {:name "File 1: 1"}}]}}}
                     {:node {:name    "2"
                             :folders {:edges [{:node {:name "2 / 1"}}
                                               {:node {:name    "2 / 2"
                                                       :folders {:edges [{:node {:name "2 / 2 / 1"}}
                                                                         {:node {:name "2 / 2 / 2"}}]}}}]}}}]}})

(def recursive-schema
  {:folder {:entitydb/id        :name
            :entitydb/relations {:folders {:entitydb.relation/path [:folders :edges :* :node]
                                           :entitydb.relation/type :folder}
                                 :files   {:entitydb.relation/path [:files :edges :* :node]
                                           :entitydb.relation/type :file}}}
   :file   {:entitydb/id :name}})

(def rec-res
  {:name          "Root"
   :files         {:edges
                   [{:node
                     {:name          "File Root: 1"
                      :entitydb/id   "File Root: 1"
                      :entitydb/type :file}}]}
   :folders       {:edges
                   [{:node
                     {:name          "1"
                      :files         {:edges
                                      [{:node
                                        {:name          "File 1: 1"
                                         :entitydb/id   "File 1: 1"
                                         :entitydb/type :file}}]}
                      :entitydb/id   "1"
                      :entitydb/type :folder}}
                    {:node
                     {:name          "2"
                      :folders       {:edges
                                      [{:node
                                        {:name "2 / 1" :entitydb/id "2 / 1" :entitydb/type :folder}}
                                       {:node
                                        {:name          "2 / 2"
                                         :folders       {:edges
                                                         [{:node
                                                           {:name          "2 / 2 / 1"
                                                            :entitydb/id   "2 / 2 / 1"
                                                            :entitydb/type :folder}}
                                                          {:node
                                                           {:name          "2 / 2 / 2"
                                                            :entitydb/id   "2 / 2 / 2"
                                                            :entitydb/type :folder}}]}
                                         :entitydb/id   "2 / 2"
                                         :entitydb/type :folder}}]}
                      :entitydb/id   "2"
                      :entitydb/type :folder}}]}
   :entitydb/id   "Root"
   :entitydb/type :folder})

(deftest recursive-relations
  (let [with-schema (edb/insert-schema {} recursive-schema)
        with-data   (edb/insert-entity with-schema :folder recursive-data)]
    (let [query [:files (q/recur-on :folders)]
          res   (edb/get-entity with-data :folder "Root" query)]
      (is (= res
             rec-res)))))

(def recursive-data-2
  {:name    "Foo"
   :enemies [{:name    "Qux"
              :enemies [{:name "Bar"}]
              :friends [{:name    "Baz"
                         :enemies [{:name "Foo"}
                                   {:name    "Bar"
                                    :friends [{:name "Qux"}]}]}]}]
   :friends [{:name    "Bar"
              :enemies [{:name "Qux"}]
              :friends [{:name    "Baz"
                         :friends [{:name    "Qux"
                                    :enemies [{:name "Bar"}
                                              {:name "Foo"}]}]}]}]})

(def recursive-schema-2
  {:person {:entitydb/id        :name
            :entitydb/relations {:enemies {:entitydb.relation/type :person
                                           :entitydb.relation/path [:enemies :*]}
                                 :friends {:entitydb.relation/type :person
                                           :entitydb.relation/path [:friends :*]}}}})

(def baz-res
  {:name          "Baz"
   :enemies       [{:name          "Foo"
                    :enemies       [{:name          "Qux"
                                     :enemies       [(->EntityIdent :person "Bar") (->EntityIdent :person "Foo")]
                                     :friends       [(->EntityIdent :person "Baz")]
                                     :entitydb/id   "Qux"
                                     :entitydb/type :person}]
                    :friends       [{:name          "Bar"
                                     :entitydb/id   "Bar"
                                     :entitydb/type :person
                                     :friends       [(->EntityIdent :person "Baz")]
                                     :enemies       [(->EntityIdent :person "Qux")]}]
                    :entitydb/id   "Foo"
                    :entitydb/type :person}
                   {:name          "Bar"
                    :entitydb/id   "Bar"
                    :entitydb/type :person
                    :friends       [{:name          "Baz"
                                     :enemies       [(->EntityIdent :person "Foo") (->EntityIdent :person "Bar")]
                                     :entitydb/id   "Baz"
                                     :entitydb/type :person
                                     :friends       [(->EntityIdent :person "Qux")]}]
                    :enemies       [{:name          "Qux"
                                     :enemies       [(->EntityIdent :person "Bar") (->EntityIdent :person "Foo")]
                                     :friends       [(->EntityIdent :person "Baz")]
                                     :entitydb/id   "Qux"
                                     :entitydb/type :person}]}]
   :entitydb/id   "Baz"
   :entitydb/type :person
   :friends       [{:name          "Qux"
                    :enemies       [{:name          "Bar"
                                     :entitydb/id   "Bar"
                                     :entitydb/type :person
                                     :friends       [(->EntityIdent :person "Baz")]
                                     :enemies       [(->EntityIdent :person "Qux")]}
                                    {:name          "Foo"
                                     :enemies       [(->EntityIdent :person "Qux")]
                                     :friends       [(->EntityIdent :person "Bar")]
                                     :entitydb/id   "Foo"
                                     :entitydb/type :person}]
                    :friends       [{:name          "Baz"
                                     :enemies       [(->EntityIdent :person "Foo") (->EntityIdent :person "Bar")]
                                     :entitydb/id   "Baz"
                                     :entitydb/type :person
                                     :friends       [(->EntityIdent :person "Qux")]}]
                    :entitydb/id   "Qux"
                    :entitydb/type :person}]})

(deftest recursive-circular-relations
  (let [with-schema (edb/insert-schema {} recursive-schema-2)
        with-data   (edb/insert-entity with-schema :person recursive-data-2)]
    (let [query [(q/recur-on :friends 2) (q/recur-on :enemies 2)]
          res   (edb/get-entity with-data :person "Baz" query)]
      (is (= res baz-res)))))

;;;;;;

(def switch-schema
  {:image {:entitydb/id        :src
           :entitydb/relations {:comments {:entitydb.relation/path [:comments :*]
                                           :entitydb.relation/type :comment}}}
   :post  {:entitydb/relations {:author :user}}})

(def switch-res
  [{:id 1
    :title "Post 1"
    :author {:id 1, :name "Retro", :entitydb/id 1, :entitydb/type :user}
    :entitydb/type :post
    :entitydb/id 1}
   {:id 2
    :title "Post 2"
    :author {:id 1, :name "Retro", :entitydb/id 1, :entitydb/type :user}
    :entitydb/type :post
    :entitydb/id 2}
   {:src "some-source"
    :title "Image 1"
    :comments
    [{:id 1, :body "Comment 1", :entitydb/id 1, :entitydb/type :comment}
     {:id 3
      :body "Comment 3"
      :entitydb/id 3
      :entitydb/type :comment}]
    :entitydb/type :image
    :entitydb/id "some-source"}])

(deftest switch-test
  (let [with-schema (edb/insert-schema {} switch-schema)
        with-data   (-> with-schema
                        (edb/insert-entity :user {:id   1
                                                  :name "Retro"})
                        (edb/insert-entities :comment [{:id   1
                                                        :body "Comment 1"}
                                                       {:id   2
                                                        :body "Comment 2"}
                                                       {:id   3
                                                        :body "Comment 3"}])
                        (edb/insert-collection :media :media/favorites [{:id            1
                                                                         :title         "Post 1"
                                                                         :author        {:id 1}
                                                                         :entitydb/type :post}
                                                                        {:id            2
                                                                         :title         "Post 2"
                                                                         :author        {:id 1}
                                                                         :entitydb/type :post}
                                                                        {:src           "some-source"
                                                                         :title         "Image 1"
                                                                         :comments      [{:id 1} {:id 3}]
                                                                         :entitydb/type :image}]))
        res         (edb/get-collection with-data :media/favorites [(q/switch {:post  [(q/include :author)]
                                                                               :image [(q/include :comments)]})])]
    (is (= res switch-res))))


(deftest custom-merge
  (let [schema      {:user {:entitydb/merge (fn [prev-value next-value]
                                              (let [profiles (or (:profiles next-value)
                                                                 (:profiles prev-value))]
                                                (-> (merge prev-value next-value)
                                                    (assoc :profiles profiles))))}}
        with-schema (edb/insert-schema {} schema)
        with-data (-> with-schema
                      (edb/insert-entity :user {:id 1
                                                :username "retro"
                                                :profiles {:twitter {:username "mihaelkonjevic"}}})
                      (edb/insert-entity :user {:id 1
                                                :username "retro"
                                                :profiles nil}))
        res (edb/get-entity with-data :user 1)]
    (is (= res
           {:id 1
            :entitydb/id 1
            :entitydb/type :user
            :username "retro"
            :profiles {:twitter {:username "mihaelkonjevic"}}}))))

(deftest custom-merge-with-relations
  (let [schema      {:user {:entitydb/merge (fn [prev-value next-value]
                                              (let [profiles (or (:profiles next-value)
                                                                 (:profiles prev-value))]
                                                (-> (merge prev-value next-value)
                                                    (assoc :profiles profiles))))
                            :entitydb/relations {:posts {:entitydb.relation/path [:posts :*]
                                                         :entitydb.relation/type :post}}}}
        with-schema (edb/insert-schema {} schema)
        with-data (-> with-schema
                      (edb/insert-entity :user {:id 1
                                                :username "retro"
                                                :posts [{:id 1 :title "Post 1"}]
                                                :profiles {:twitter {:username "mihaelkonjevic"}}})
                      (edb/insert-entity :user {:id 1
                                                :username "retro"
                                                :profiles nil}))
        res (edb/get-entity with-data :user 1)]
    (is (= res
           {:id 1
            :entitydb/id 1
            :entitydb/type :user
            :posts [(->EntityIdent :post 1)]
            :username "retro"
            :profiles {:twitter {:username "mihaelkonjevic"}}}))))

(deftest inserting-entity-with-nil-id-throws-1
  (let [schema {:user {:entitydb/id :non-existing-attr}}
        with-schema (edb/insert-schema {} schema)]
    (is (thrown? js/Error (edb/insert-entity with-schema :user {})))
    (is (thrown? js/Error (edb/insert-named with-schema :user :user/current {})))
    (is (thrown? js/Error (edb/insert-collection with-schema :user :user/list [{}])))))

(deftest inserting-entity-with-nil-id-throws-2
  (let [schema {:user {:entitydb/id (fn [entity] (:non-existing-attr entity))}}
        with-schema (edb/insert-schema {} schema)]
    (is (thrown? js/Error (edb/insert-entity with-schema :user {})))
    (is (thrown? js/Error (edb/insert-named with-schema :user :user/current {})))
    (is (thrown? js/Error (edb/insert-collection with-schema :user :user/list [{}])))))

(deftest inserting-entity-with-nil-id-throws-3
  (let [schema {:user {:entitydb/id (fn [entity] [(:foo entity) (:bar entity)])}}
        with-schema (edb/insert-schema {} schema)]
    (is (thrown? js/Error (edb/insert-entity with-schema :user {})))
    (is (thrown? js/Error (edb/insert-named with-schema :user :user/current {})))
    (is (thrown? js/Error (edb/insert-collection with-schema :user :user/list [{}])))))

(deftest inserting-entity-with-wrong-type-throws
  (let [schema {}
        with-schema (edb/insert-schema {} schema {:entitydb.schema/strict? true})]
    (is (thrown? js/Error (edb/insert-entity with-schema :user {:id 1})))
    (is (thrown? js/Error (edb/insert-named with-schema :user :user/current {:id 1})))
    (is (thrown? js/Error (edb/insert-collection with-schema :user :user/list [{:id 1}])))))

(deftest using-ident-with-named-item
  (let [with-schema     (edb/insert-schema {} {:note {:entitydb/relations
                                                      {:user :user}}})
        db      (edb/insert-named with-schema :note :note/current {:id 1 :title "Note title"
                                                                   :user {:id 1 :name "Foo"}})
        entity-ident (edb/get-ident-for-named db :note/current)
        expected-entity-ident (->EntityIdent :note 1)
        entity (edb/get-entity-from-ident db entity-ident)
        expected-entity  {:id            1
                          :title         "Note title"
                          :entitydb/id   1
                          :entitydb/type :note
                          :user (->EntityIdent :user 1)}
        entity-with-rel (edb/get-entity-from-ident db entity-ident [:user])
        expected-entity-with-rel {:id            1
                                  :title         "Note title"
                                  :entitydb/id   1
                                  :entitydb/type :note
                                  :user {:id 1
                                         :name "Foo"
                                         :entitydb/id 1
                                         :entitydb/type :user}}]
    (is (= entity-ident expected-entity-ident))
    (is (= entity expected-entity))
    (is (= entity-with-rel expected-entity-with-rel))))


(deftest using-idents-with-collection
  (let [with-schema     (edb/insert-schema {} {:note {:entitydb/relations
                                                      {:user :user}}})
        db      (edb/insert-collection with-schema :note :note/list [{:id 1 :title "Note title"
                                                                      :user {:id 1 :name "Foo"}}
                                                                     {:id 2 :title "Note title #2"
                                                                      :user {:id 2 :name "Bar"}}])
        entity-idents (edb/get-idents-for-collection db :note/list)
        expected-entity-idents [(->EntityIdent :note 1) (->EntityIdent :note 2)]
        entities (edb/get-entities-from-idents db entity-idents)
        expected-entities [{:id            1
                            :title         "Note title"
                            :entitydb/id   1
                            :entitydb/type :note
                            :user (->EntityIdent :user 1)}
                           {:id            2
                            :title         "Note title #2"
                            :entitydb/id   2
                            :entitydb/type :note
                            :user (->EntityIdent :user 2)}]
        entities-with-rel (edb/get-entities-from-idents db entity-idents [:user])
        expected-entities-with-rel [{:id            1
                                     :title         "Note title"
                                     :entitydb/id   1
                                     :entitydb/type :note
                                     :user {:id 1
                                            :name "Foo"
                                            :entitydb/id 1
                                            :entitydb/type :user}}
                                    {:id            2
                                     :title         "Note title #2"
                                     :entitydb/id   2
                                     :entitydb/type :note
                                     :user {:id 2
                                            :name "Bar"
                                            :entitydb/id 2
                                            :entitydb/type :user}}]]
    (is (= entity-idents expected-entity-idents))
    (is (= entities expected-entities))
    (is (= entities-with-rel expected-entities-with-rel))))