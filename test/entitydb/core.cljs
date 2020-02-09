(ns entitydb.core
  (:require
    [cljs.test :refer-macros [deftest is testing use-fixtures]]
    [entitydb.internal :refer [->EntityIdent]]
    [entitydb.entitydb :as edb]
    [entitydb.query :as q]
    [entitydb.data :as data]
    [entitydb.util :refer [log]]))

(use-fixtures :once
              {:before (fn [] (js/console.clear))})

;; INSERT TESTS

(deftest insert-user-test
  (let [res (edb/insert {} :user {:id 1 :username "Retro"})]
    (is (= res {:entitydb/store {:user {1 {:id 1 :entitydb/id 1 :username "Retro" :entitydb/type :user}}}}))))

(deftest insert-item-with-custom-id-fn-test
  (let [id-fn       (fn [item] (str (:id item) "-note"))
        with-schema (edb/insert-schema {} {:note {:entitydb/id id-fn}})
        with-data   (-> with-schema
                        (edb/insert :note {:id 1 :title "Note title"}))
        res         (get-in with-data [:entitydb/store :note "1-note"])]
    (is (= res {:id 1 :entitydb/id "1-note" :title "Note title" :entitydb/type :note}))))

(deftest inserting-entity-when-exists-merges-attrs-test
  (let [store {:entitydb/store {:user {1 {:full-name "Mihael Konjevic"}}}}
        res   (edb/insert store :user {:id 1 :username "Retro"})]
    (is (= res {:entitydb/store
                {:user
                 {1 {:id 1 :entitydb/id 1 :username "Retro" :full-name "Mihael Konjevic" :entitydb/type :user}}}}))))

(deftest insert-many-test
  (let [res (edb/insert-many {} :user [{:id 1 :username "Retro"}
                                       {:id 2 :username "Tibor"}])]
    (is (= res {:entitydb/store {:user {1 {:id 1 :entitydb/id 1 :username "Retro" :entitydb/type :user}
                                        2 {:id 2 :entitydb/id 2 :username "Tibor" :entitydb/type :user}}}}))))

(deftest insert-named-item-test
  (let [with-items      (edb/insert-named-item {} :note :note/current {:id 1 :title "Note title"})
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

(deftest insert-named-item-with-meta-test
  (let [with-items      (edb/insert-named-item {} :note :note/current {:id 1 :title "Note title"} {:foo "bar"})
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
                        (edb/insert :note {:id    1
                                           :title "Note title"
                                           :user  {:id       1
                                                   :username "Retro"}
                                           :links [{:url "www.google.com"}]})
                        (edb/insert :comment {:id   1
                                              :text "This is comment"
                                              :user {:id 1}})
                        (edb/insert :note {:id    2
                                           :title "Some other title"
                                           :user  {:id       2
                                                   :username "Tibor"}
                                           :links [{:url "www.yahoo.com"}
                                                   {:url "www.google2.com"}]}))]
    (let [db-with-deleted (edb/remove-by-id with-data :note 2)
          query-1         (edb/get-by-id db-with-deleted :note 2)
          query-2         [(q/reverse-include :note [])]
          res-2           (edb/get-by-id db-with-deleted :link "www.google2.com" query-2)]
      ;; check if note with id 2 exists in store
      (is (nil? query-1))
      ;; check if note with id 2 relations exist
      (is (nil? (get (:entitydb/relations db-with-deleted) (->EntityIdent :note 2))))
      ;; check if reverse relations which point to note with id 2 exist
      (is (nil? (:entitydb.relations/reverse res-2))))))

;; GETTER TEST

(deftest get-item-by-id-test
  (let [store {:entitydb/store {:note {1 {:id 1 :title "Note title"}}}}]
    (is (= (:title (edb/get-by-id store :note 1)) "Note title"))))

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
                            (edb/insert :note {:id   1
                                               :user {:id 1 :name "Foo"}}))
        insert-user-nil (edb/insert with-data :note {:id 1 :user nil})]

    (is (not (nil? (get-in with-data [:entitydb/relations (->EntityIdent :note 1)]))))

    (is (nil? (get-in insert-user-nil [:entitydb/relations (->EntityIdent :note 1)])))

    (is (not (nil? (get-in with-data [:entitydb.relations/reverse (->EntityIdent :user 1)]))))

    (is (nil? (get-in insert-user-nil [:entitydb.relations/reverse (->EntityIdent :user 1)])))))

(deftest inserting-item-with-no-related-data-leaves-existing-relations-intact
  (let [with-schema      (edb/insert-schema {} {:note {:entitydb/relations
                                                       {:user :user}}})
        with-data        (-> with-schema
                             (edb/insert :note {:id   1
                                                :user {:id 1 :name "Foo"}}))
        insert-note-data (edb/insert with-data :note {:id 1 :title "Note title"})]

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
                                    (edb/insert :note {:id    1
                                                       :title "Note #1"
                                                       :links [{:id 1}
                                                               {:id 2}]})
                                    (edb/insert :note {:id    2
                                                       :title "Note #2"
                                                       :links [{:id 2}]})
                                    (edb/insert :note {:id    3
                                                       :title "Note #3"
                                                       :links [{:id 1}
                                                               {:id 3}]})
                                    (edb/insert :link {:id    1
                                                       :url   "http://google.com"
                                                       :notes [{:id 1}
                                                               {:id 3}]})
                                    (edb/insert :link {:id    2
                                                       :url   "http://bing.com"
                                                       :notes [{:id 2}
                                                               {:id    4
                                                                :title "Note #4"}]})
                                    (edb/insert :link {:id    3
                                                       :url   "http://yahoo.com"
                                                       :notes [{:id 3}]}))
     note-1                     (edb/get-by-id with-data :note 1 [(q/include :links)])
     note-1-reverse-from-link-1 (edb/get-by-id with-data :link 1 [(q/reverse-include :note)])
     link-1                     (edb/get-by-id with-data :link 1 [(q/include :notes [(q/include :links [(q/include :notes)])])])
     link-1-reverse-from-note-1 (edb/get-by-id with-data :note 1 [(q/reverse-include :link)])
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
                        (edb/insert :user data/user-1-data))]
    (let [append-data                                    (-> with-data
                                                             (edb/get-by-id :user 1)
                                                             (update-in [:posts :edges] (fn [i] (conj i {:cursor "3"
                                                                                                         :node   {:slug  "my-post-5"
                                                                                                                  :title "My Post #5"}}))))
          prepend-data                                   (-> with-data
                                                             (edb/get-by-id :user 1)
                                                             (update-in [:posts :edges] (fn [i] (concat [{:cursor "3"
                                                                                                          :node   {:slug  "my-post-5"
                                                                                                                   :title "My Post #5"}}]
                                                                                                        i))))

          with-appended-data                             (edb/insert with-data :user append-data)
          with-prepended-data                            (edb/insert with-data :user prepend-data)
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
          appended-post-data                             (edb/get-by-id with-appended-data :post "my-post-5")
          prepended-post-data                            (edb/get-by-id with-prepended-data :post "my-post-5")
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
             (get-in (edb/get-by-id with-appended-data :user 1) [:posts :edges])))
      (is (= expected-user-posts-with-prepended-data
             (get-in (edb/get-by-id with-prepended-data :user 1) [:posts :edges])))
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
                           (edb/insert :note {:id    1
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
    (let [note-from-store (edb/get-by-id with-data :note 1 [(q/include :user)])
          user-from-db    (edb/get-by-id with-data :user 1 [(q/reverse-include :note)])
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
                           (edb/insert :note {:id    1
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
    (is (= (edb/get-by-id with-data :link 1)
           {:id            1
            :url           "http://google.com"
            :entitydb/id   1
            :entitydb/type :link}))
    (let [note-from-store (edb/get-by-id with-data :note 1 [(q/include :links)])]
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
                                      (edb/insert :note {:id    1
                                                         :title "Note title"
                                                         :links [{:id 1}]})
                                      (edb/insert :user {:id       1
                                                         :username "Retro"})
                                      (edb/insert :link {:id   1
                                                         :url  "http://google.com"
                                                         :user {:id 1}}))
        link-1-relation-from-note (-> (edb/get-by-id with-data :note 1 [(q/include :links [(q/include :user)])])
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
                        (edb/insert-many :note [{:id    1
                                                 :title "Note #1"
                                                 :notes [{:id 2}]}
                                                {:id    2
                                                 :title "Note #2"
                                                 :notes [{:id 3}]}
                                                {:id    3
                                                 :title "Note #3"}]))
        note-1 (edb/get-by-id with-data :note 1 [(q/include :notes [(q/include :notes)])])]
    (is (= "Note #2"
           (get-in note-1 [:notes 0 :title])))
    (is (= "Note #3"
           (get-in note-1 [:notes 0 :notes 0 :title])))))

#_(deftest relations-between-users
    (let [with-schema (edb/insert-schema {} data/schema)
          with-data   (-> with-schema
                          (edb/insert :user data/user-1-data)
                          (edb/insert :user data/user-2-data)
                          (edb/insert-named-item
                            :post
                            :current
                            {:slug  "my-post-3"
                             :title "My Post #3"})
                          (edb/insert-named-item
                            :post :favorite
                            {:slug  "my-post-4"
                             :title "My Post #4"})
                          (edb/insert-collection
                            :github-repository
                            :current
                            data/github-repositories-data-1)
                          (edb/insert-collection
                            :github-repository
                            :archived
                            data/github-repositories-data-2))
          ;;_ (log "BEFORE VACUUM")
          _           (log with-data)
          with-data'  (edb/vacuum with-data)
          ;;_ (log "AFTER VACUUM")
          ;;_ (log with-data')
          ]
      (is (= 1 1))))


;(deftest relations
;  (let [with-schema (edb/insert-schema {} schema)
;        with-data   (-> with-schema
;                        (edb/insert :user data)
;                        (edb/remove-by-id :post "my-post-3")
;                        (edb/remove-by-id :url "https://keechma.com")
;                        ;;(edb/remove-named :favorite)
;                        )]
;              ;;(js/console.log (with-out-str (cljs.pprint/pprint with-data)))
;              ;;(js/console.log "------------------------------")
;              ;;(js/console.log (with-out-str (cljs.pprint/pprint (into {} (filter (fn [[ident _]] (= :post (:type ident))) (get-in with-data [:entitydb.relations/reverse]))))))
;              ;;(js/console.log "------------------------------")
;              ;; (js/console.log (with-out-str (cljs.pprint/pprint (edb/get-by-id with-data :user 1))))
;              (let [query   [:urls
;                             :posts
;                             :group-members
;                             (q/include :githubProfile
;                                        [(q/include :repositories
;                                                    [(q/include [:committers :edges :* :node])])])]
;                    query-1 [(q/switch {:user [:group-members]})]
;                    res-1   (edb/get-by-id with-data :user 1 query-1)
;                    query-2 [(q/reverse-include :user [:urls (q/include :posts [(q/reverse-include :user)])])]
;                    res-2   (edb/get-by-id with-data :post "my-post-3" query)
;                    ])))


;; (def recursive-data
;;   {:name "Root"
;;    :files {:edges [{:node {:name "File Root: 1"}}]}
;;    :folders {:edges [{:node {:name "1"
;;                              :files {:edges [{:node {:name "File 1: 1"}}]}}}
;;                      {:node {:name "2"
;;                              :folders {:edges [{:node {:name "2 / 1"}}
;;                                                {:node {:name "2 / 2"
;;                                                        :folders {:edges [{:node {:name "2 / 2 / 1"}}
;;                                                                          {:node {:name "2 / 2 / 2"}}]}}}]}}}]}})


;; (def recursive-schema
;;   {:folder {:entitydb/id :name
;;             :entitydb/relations {:folders {:entitydb.relation/path [:folders :edges :* :node]
;;                                            :entitydb.relation/type :folder}
;;                                  :files {:entitydb.relation/path [:files :edges :* :node]
;;                                          :entitydb.relation/type  :file}}}
;;    :file {:entitydb/id :name}})

;(deftest recursive-relations
;  (let [with-schema (edb/insert-schema {} recursive-schema)
;        with-data (edb/insert with-schema :folder recursive-data)]
;    ;;(js/console.log (with-out-str (cljs.pprint/pprint with-data)))
;    ;;(js/console.log (with-out-str (cljs.pprint/pprint (edb/get-by-id with-data :user 1))))
;    (let [query [:files (q/recur-on :folders)]
;          res (edb/get-by-id with-data :folder "Root" query)]
;      (js/console.log (with-out-str (cljs.pprint/pprint res))))))

;; (def recursive-data-2
;;   {:name "Foo"
;;    :enemies [{:name "Qux"
;;               :enemies [{:name "Bar"}]
;;               :friends [{:name "Baz"
;;                          :enemies [{:name "Foo"}
;;                                    {:name  "Bar"
;;                                     :friends [{:name "Qux"}]}]}]}]
;;    :friends [{:name "Bar"
;;               :enemies [{:name "Qux"}]
;;               :friends [{:name "Baz"
;;                          :friends [{:name "Qux"
;;                                     :enemies [{:name "Bar"}
;;                                               {:name "Foo"}]}]}]}]})

;; (def recursive-schema-2
;;   {:person {:entitydb/id :name
;;             :entitydb/relations {:enemies {:entitydb.relation/type :person
;;                                            :entitydb.relation/path [:enemies :*]}
;;                                  :friends {:entitydb.relation/type :person
;;                                            :entitydb.relation/path [:friends :*]}}}})


;; (deftest recursive-relations-2
;;   (let [with-schema (edb/insert-schema {} recursive-schema-2)
;;         with-data (edb/insert with-schema :person recursive-data-2)]
;;     ;;(js/console.log (with-out-str (cljs.pprint/pprint with-data)))
;;     ;;(js/console.log (with-out-str (cljs.pprint/pprint (edb/get-by-id with-data :user 1))))
;;     (let [query [(q/recur-on :friends 2) (q/recur-on :enemies 2)]
;;           res (edb/get-by-id with-data :person "Foo" query)]
;;       (js/console.log (with-out-str (cljs.pprint/pprint res))))))