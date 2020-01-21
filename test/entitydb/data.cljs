(ns entitydb.data
  (:require [entitydb.entitydb :as edb]))


(def user-1-data
  {:username       "retro"
   :id             1
   :posts          {:pageInfo {:hasNextPage true}
                    :edges    [{:cursor "1"
                                :node   {:slug  "my-post-1"
                                         :title "My Post #1"}}
                               {:cursor "2"
                                :node   {:slug  "my-post-2"
                                         :title "My Post #2"}}]}
   :authoredPosts  [{:slug  "my-post-3"
                     :title "My Post #3"}]
   :favoritePosts  {:pageInfo {:hasNextPage true}
                    :edges    [{:cursor "1"
                                :node   {:slug  "my-post-3"
                                         :title "My Post #3"}}
                               {:cursor "2"
                                :node   {:slug  "my-post-4"
                                         :title "My Post #4"}}]}
   :twitterProfile {:username      "mihaelkonjevic"
                    :tweetCount    1234
                    :followerCount 123}
   :githubProfile  {:username     "retro"
                    :repositories {:pageInfo {:hasNextPage false}
                                   :edges    [{:cursor "1"
                                               :node   {:id         1
                                                        :name       "keechma"
                                                        :homepage   {:url         "https://keechma.com"
                                                                     :description "Keechma Homepage"}
                                                        :committers {:pageInfo {:hasNextPage false}
                                                                     :edges    [{:cursor 1
                                                                                 :node   {:username "retro"}}
                                                                                {:cursor 2
                                                                                 :node   {:username "tiborkr"}}]}}}
                                              {:cursor "2"
                                               :node   {:id   2
                                                        :name "entitydb"}}]}}
   :urls           [{:name "Homepage" :url "https://retroaktive.me"}
                    {:name "Keechma" :url "https://keechma.com"}]
   :groups         [{:name    "Keechma Developers"
                     :members {:pageInfo {:hasNextPage false}
                               :edges    [{:cursor 3
                                           :node   {:id 1}}]}}]})

(def user-2-data 
  {:username      "tiborkr"
   :id            2
   :favoritePosts {:pageInfo {:hasNextPage true}
                   :edges    [{:cursor "1"
                               :node   {:slug  "my-post-3"
                                        :title "My Post #3"}}
                              {:cursor "2"
                               :node   {:slug  "my-post-4"
                                        :title "My Post #4"}}]}
   :posts         {:pageInfo {:hasNextPage true}
                   :edges    [{:cursor "1"
                               :node   {:slug  "my-post-1"
                                        :title "My Post #1"}}
                              {:cursor "3"
                               :node   {:slug  "my-post-3"
                                        :title "My Post #3"}}]}})

(def user-1-post-data-update 
  {:username "retro"
   :id        1
   :posts     {:pageInfo {:hasNextPage true}
               :edges    [{:cursor "1"
                           :node   {:slug  "my-post-3"
                                    :title "My Post #3"}}
                          {:cursor "3"
                           :node   {:slug  "my-post-2"
                                    :title "My Post #2"}}
                          {:cursor "1"
                           :node   {:slug  "my-post-3"
                                    :title "My Post #3"}}]}})

(def user-3-data 
  {:username      "dario"
   :id            3
   :favoritePosts {:pageInfo {:hasNextPage true}
                   :edges    [ {:cursor "3"
                                :node   {:slug  "my-post-2"
                                         :title "My Post #2"}}
                              {:cursor "1"
                               :node   {:slug  "my-post-3"
                                        :title "My Post #3"}}
                              {:cursor "2"
                               :node   {:slug  "my-post-4"
                                        :title "My Post #4"}}]}
   :posts         {:pageInfo {:hasNextPage true}
                   :edges    [{:cursor "1"
                               :node   {:slug  "my-post-1"
                                        :title "My Post #1"}}
                              {:cursor "2"
                               :node   {:slug  "my-post-1"
                                        :title "My Post #1"}}
                              {:cursor "3"
                               :node   {:slug  "my-post-2"
                                        :title "My Post #2"}}]}})

(def github-repositories-data-1
  [{:id       1
    :name     "keechma"
    :homepage {:url "https://keechma.com" :description "Keechma Homepage"}
    :committers
    {:pageInfo {:hasNextPage false}
     :edges
     [{:cursor 1 :node {:username "retro"}}
      {:cursor 2 :node {:username "tiborkr"}}]}}
   {:id   2
    :name "entitydb"}])

(def github-repositories-data-2
  [{:id   3
    :name "entitydb"}])

(def schema 
  {:user              {:entitydb/relations
                       {:urls           {:entitydb.relation/path [:urls :*]
                                         :entitydb.relation/type :url}
                        :authored-posts {:entitydb.relation/path [:authoredPosts :*]
                                         :entitydb.relation/type :post}
                        :favorite-posts {:entitydb.relation/path [:favoritePosts :edges :* :node]
                                         :entitydb.relation/type :post}
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
                       {[:committers :edges :* :node] :github-profile
                        :homepage :url}}})
