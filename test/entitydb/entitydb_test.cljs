(ns entitydb.entitydb-test
    (:require
     [cljs.test :refer-macros [deftest is testing use-fixtures]]
     [entitydb.entitydb :as edb]
     [entitydb.query :as q]))

(use-fixtures :once
  {:before (fn [] (js/console.clear))})

(deftest insert-one []
  (let [res (edb/insert-one {} :user {:id 1 :username "Retro"})]
    (is (= res {:entitydb/store {:user {1 {:id 1 :entitydb/id 1 :username "Retro" :entitydb/type :user}}}}))))

(deftest inserting-entity-when-exists-merges-attrs []
  (let [store {:entitydb/store {:user {1 {:full-name "Mihael Konjevic"}}}}
        res (edb/insert-one store :user {:id 1 :username "Retro"})]
    (is (= res {:entitydb/store
            {:user
             {1 {:id 1 :entitydb/id 1 :username "Retro" :full-name "Mihael Konjevic" :entitydb/type :user}}}}))))

(deftest insert-many []
  (let [res (edb/insert-many {} :user [{:id 1 :username "Retro"} 
                                       {:id 2 :username "Tibor"}])]
    (is (= res {:entitydb/store {:user {1 {:id 1 :entitydb/id 1 :username "Retro" :entitydb/type :user}
                                          2 {:id 2 :entitydb/id 2 :username "Tibor" :entitydb/type :user}}}}))))

(def data {:username "retro"
           :id 1
           :posts {:pageInfo {:hasNextPage true}
                   :edges [{:cursor "1"
                            :node {:slug "my-post-1"
                                   :title "My Post #1"}}
                           {:cursor "2"
                            :node {:slug "my-post-2"
                                   :title "My Post #2"}}]}
           :twitterProfile {:username "mihaelkonjevic"
                            :tweetCount 1234
                            :followerCount 123}
           :githubProfile {:username "retro"
                           :repositories {:pageInfo {:hasNextPage false}
                                          :edges [{:cursor "1"
                                                   :node {:id 1
                                                          :name "keechma"
                                                          :homepage {:url "https://keechma.com"
                                                                     :description "Keechma Homepage"}
                                                          :committers {:pageInfo {:hasNextPage false}
                                                                       :edges [{:cursor 1
                                                                                :node {:username "retro"}}
                                                                               {:cursor 2
                                                                                :node {:username "tiborkr"}}]}}}
                                                  {:cursor "2"
                                                   :node {:id 2
                                                          :name "entitydb"}}]}}

           :urls [{:name "Homepage" :url "https://retroaktive.me"}
                  {:name "Keechma" :url "https://keechma.com"}]
           :groups [{:name "Keechma Developers"
                     :members {:pageInfo {:hasNextPage false}
                               :edges [{:cursor 3
                                        :node {:id 1}}]}}]})

(def data-2 {:username "tiborkr"
             :id 2
             :posts {:pageInfo {:hasNextPage true}
                     :edges [{:cursor "1"
                              :node {:slug "my-post-1"
                                     :title "My Post #1"}}]}})

(def data-3 {:username "retro"
             :id 1
             :posts {:pageInfo {:hasNextPage true}
                   :edges [{:cursor "1"
                            :node {:slug "my-post-3"
                                   :title "My Post #3"}}]}})

(def schema {:user              {:entitydb/relations 
                                 {:urls           {:entitydb.relation/path [:urls :*]
                                                   :entitydb.relation/type :url}
                                  :posts          {:entitydb.relation/path [:posts :edges :* :node]     
                                                   :entitydb.relation/type :post}
                                  :group-members  {:entitydb.relation/path [:groups :* :members :edges :* :node]
                                                   :entitydb.relation/type :user}
                                  :twitterProfile :twitter-profile
                                  :githubProfile  :github-profile}
                                 :entitydb/processor (fn [item]
                                                       (if (contains? item :username)
                                                         (update item :username #(str "USERNAME:" %))
                                                         item))}
             :post              {:entitydb/id :slug}
             :url               {:entitydb/id :url}
             :twitter-profile   {:entitydb/id :username}
             :github-profile    {:entitydb/id :username
                                 :entitydb/relations
                                 {:repositories {:entitydb.relation/type :github-repository
                                                 :entitydb.relation/path [:repositories :edges :* :node]}}}
             :github-repository {:entitydb/relations 
                                 {[:committers :edges :* :node] :github-profile}}})

(deftest relations
  (let [with-schema (edb/insert-schema {} schema)
        with-data (-> with-schema
                      (edb/insert-one :user data)
                      (edb/insert-one :user data-2)
                      (edb/insert-one :user data-3))]
    (js/console.log (with-out-str (cljs.pprint/pprint with-data)))
    (js/console.log "------------------------------")
    (js/console.log (with-out-str (cljs.pprint/pprint (get-in with-data [:entitydb.relations/reverse :post]))))
    (js/console.log "------------------------------")
   ;; (js/console.log (with-out-str (cljs.pprint/pprint (edb/get-by-id with-data :user 1))))
    (let [query [:urls
                 :posts
                 :group-members
                 (q/include :githubProfile 
                            [(q/include :repositories 
                                        [(q/include [:committers :edges :* :node])])])]
          res (edb/get-by-id with-data :user 1 query)]
     ;; (js/console.log (with-out-str (cljs.pprint/pprint res)))
      )))

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

;; (deftest recursive-relations
;;   (let [with-schema (edb/insert-schema {} recursive-schema)
;;         with-data (edb/insert-one with-schema :folder recursive-data)]
;;     ;;(js/console.log (with-out-str (cljs.pprint/pprint with-data)))
;;     ;;(js/console.log (with-out-str (cljs.pprint/pprint (edb/get-by-id with-data :user 1))))
;;     (let [query [:files (q/recur-on :folders)] 
;;           res (edb/get-by-id with-data :folder "Root" query)]
;;       (js/console.log (with-out-str (cljs.pprint/pprint res))))))

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
;;         with-data (edb/insert-one with-schema :person recursive-data-2)]
;;     ;;(js/console.log (with-out-str (cljs.pprint/pprint with-data)))
;;     ;;(js/console.log (with-out-str (cljs.pprint/pprint (edb/get-by-id with-data :user 1))))
;;     (let [query [(q/recur-on :friends 2) (q/recur-on :enemies 2)] 
;;           res (edb/get-by-id with-data :person "Foo" query)]
;;       (js/console.log (with-out-str (cljs.pprint/pprint res))))))
