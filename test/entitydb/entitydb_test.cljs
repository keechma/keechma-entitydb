(ns entitydb.entitydb-test
    (:require
     [cljs.test :refer-macros [deftest is testing]]
     [entitydb.entitydb :as edb]))

(deftest insert-one []
  (let [res (edb/insert-one {} :user {:id 1 :username "Retro"})]
    (is (= res {:entitydb/db {:entitydb/store {:user {1 {:id 1 :entitydb/id 1 :username "Retro"}}}}}))))

(deftest inserting-entity-when-exists-merges-attrs []
  (let [store {:entitydb/db {:entitydb/store {:user {1 {:full-name "Mihael Konjevic"}}}}}
        res (edb/insert-one store :user {:id 1 :username "Retro"})]
    (is (= res {:entitydb/db 
                {:entitydb/store
                 {:user
                  {1 {:id 1 :entitydb/id 1 :username "Retro" :full-name "Mihael Konjevic"}}}}}))))

(deftest insert-many []
  (let [res (edb/insert-many {} :user [{:id 1 :username "Retro"} 
                                           {:id 2 :username "Tibor"}])]
    (is (= res {:entitydb/db {:entitydb/store {:user {1 {:id 1 :entitydb/id 1 :username "Retro"}
                                                      2 {:id 2 :entitydb/id 2 :username "Tibor"}}}}}))))

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

(def schema {:user              {:entitydb/relations {:urls           {:entitydb.relation/path [:urls :*]
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
             :github-profile    {:entitydb/id        :username
                                 :entitydb/relations {:repositories {:entitydb.relation/type :github-repository
                                                                     :entitydb.relation/path [:repositories :edges :* :node]}}}
             :github-repository {:entitydb/relations {[:committers :edges :* :node] :github-profile}}})

(deftest relations
  (let [with-schema (edb/insert-schema {} schema)
        with-data (edb/insert-one with-schema :user data)]
    (js/console.log (with-out-str (cljs.pprint/pprint with-data)))
    (js/console.log (with-out-str (cljs.pprint/pprint (edb/get-by-id with-data :user 1))))
    (js/console.log "------------------------->" (with-out-str (cljs.pprint/pprint (edb/get-by-id with-data :user 1 [:urls :posts :group-members {:githubProfile [{:repositories [[:committers :edges :* :node]]}]}]))))))
