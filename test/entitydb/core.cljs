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

(deftest insert-item-with-custom-id-fn
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

;; REMOVE TESTS

(deftest remove-item
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

;; RELATIONS TEST

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