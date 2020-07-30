# keechma/entitydb

EntityDB is a client side database and normalization engine.

## Motivation

In apps we're developing, we always work with the incomplete data. Data gets loaded from the server based on the route, and we always load the minimal set required to render the page. Any client side querying and filtering is minimal, and is performed with the functions from the Clojure standard library. 

Features we need:

- Normalization - only one instance of an entity should be loaded in memory
- Relationships support - getting related data present in EntityDB (which can be loaded in one or multiple queries to the server)

If your application needs more extensive feature set (like client side querying), something like [DataScript](https://github.com/tonsky/datascript) might be a better choice.

## Usage patterns

When talking about the apps, we usually refer to some datasets by their _domain_ name, like _current user_, _frontpage articles_ and similar. EntityDB supports storing of these named items and collections which are then resolved by their name. These named items and collections express interest of application in some subset of data. How data gets loaded in EntityDB is up to the developer. Any data that is not linked to some named entity or collection should be considered stale. EntityDB supports _vacuuming_ of stale data - this ensures that you can avoid an accidental memory leak where the data is always accrued, and never removed from the application state.

EntityDB requires minimal or no data transformation before ingestion. We use EntityDB with various backend APIs (GraphQL, REST, Firebase...), and we usually just put whatever is returned from the API inside EntityDB. Usage will be very similar to a system that only uses vectors and maps to store the data - with normalization layer on top.

EntityDB is implemented as a set of pure functions, and it's up to a developer to provide the storage (usually a Clojure atom).

EntityDB allows you to define an optional schema which will be used to extract related entities from any payload that enters the system.

## Examples

In this example we'll store and retreive a _current user_:

```clojure
(def db (edb/insert-named {} :user :user/current {:id 1 :username "Retro"}))
(edb/get-named db :user/current) ;; Returns {:id 1 :username "Retro" :entitydb/type :user :entitydb/id 1}
```

As you can see, we get whatever we put inside (plus some EntityDB attributes). EntityDB doesn't support partial data retrieval - whatever is placed in EntityDB will get returned.

In some cases you'll get parts of entity data from different sources. For instance, you might load a list of articles which will embed some user info, and you could get the whole user entity from a user endpoint. If you put data with the same identity in EntityDB, this data will be merged. Default identity is `:id`.

```clojure
(def db (edb/insert-named {} :user :user/current {:id 1 :username "Retro"}))
(def db-1 (edb/insert-named {} :user :user/author {:id 1 :firstName "Mihael" :lastName "Konjevic"}))
(edb/get-named db :user/current) ;; Returns {:id 1 :username "Retro" :firstName "Mihael" :lastName "Konjevic" :entitydb/type :user :entitydb/id 1}
```

Sometimes, you'll load an entity by id, and you want to put it in EntityDB to ensure that you have the latest data loaded. In that case, you can put an entity directly into EntityDB - without using a named entity. All named entities will be synchronized.

```clojure
(def db (edb/insert-named {} :user :user/current {:id 1 :username "Retro"}))
(def db-1 (edb/insert-entity {} :user {:id 1 :firstName "Mihael" :lastName "Konjevic"}))
(edb/get-named db :user/current) ;; Returns {:id 1 :username "Retro" :firstName "Mihael" :lastName "Konjevic" :entitydb/type :user :entitydb/id 1}
```

Named entities' names are contextual. For instance on the URL `/article/1`, `:user/author` might point to one entity and on the URL `/article/33` it might point to a different one. Other parts of the system (UI) don't have to care about that, and they can always load a named entity by its name.

Removing a named entity will not remove the entity from the store. It will just unlink the name from the entity.

```clojure
(def db (edb/insert-named {} :user :user/current {:id 1 :username "Retro"}))
(edb/get-named db :user/current) ;; Returns {:id 1 :username "Retro" :entitydb/type :user :entitydb/id 1}

(def db-1 (edb/remove-named {} :user/current))
(edb/get-named db-1 :user/current) ;; Returns nil
(edb/get-entity db-1 :user 1) ;; Returns {:id 1 :username "Retro" :entitydb/type :user :entitydb/id 1}
```

If you remove an entity from the store, all named entities will be cleaned up automatically.

```clojure
(def db (edb/insert-named {} :user :user/current {:id 1 :username "Retro"}))
(edb/get-named db :user/current) ;; Returns {:id 1 :username "Retro" :entitydb/type :user :entitydb/id 1}

(def db-1 (edb/remove-entity {} :user 1))
(edb/get-named db-1 :user/current) ;; Returns nil
(edb/get-entity db-1 :user 1) ;; Returns nil
```

Collections are similar to named entities, but instead of a single entity they point to a vector (or list) of entities. Insertion order is preserved. If you remove an entity from the store it will be removed from any collections too.

```clojure
(def db (edb/insert-collection {} :user :user/friends [{:id 1 :username "Retro"} {:id 2 :username "TiborKr"}]))
(edb/get-collection db :user/friends) ;; Returns [{:id 1 :username "Retro" :entitydb/id 1 :entitydb/type :user} {:id 2 :username "TiborKr" :entitydb/id 1 :entitydb/type :user}]

(def db-1 (edb/remove-entity db :user 1))
(edb/get-collection db-1 :user/friends) ;; Returns [{:id 2 :username "TiborKr" :entitydb/id 1 :entitydb/type :user}]

(def db-2 (edb/remove-collection db :user/friends))
(edb/get-collection db-2 :user/friends) ;; Returns nil
(edb/get-entity db-2 :user 2) ;; Returns {:id 2 :username "TiborKr" :entitydb/id 1 :entitydb/type :user}
```

## Schema

EntityDB doesn't require a schema before you can start using it. It will happily store the data and return it back. You'll need to define a schema in a few cases:

1. You want to change a way the identity is calculated
2. You want to define relationships
3. You want to define a processor that will be applied to some entities before they enter the system

### Example schema

```clojure
{:note {:entitydb/relations {:links {:entitydb.relation/path [:links :*]
                                     :entitydb.relation/type :link}}}
 :link {:entitydb/id :url}}
```

In this case we're defining schema for two types `:note` and `:link`. Link will be using the `:url` attr as it's identity, and each note will have a relation to multiple links. If you take a look at the way the relation is defined, you'll notice that there is no explicit definition of cardinality. You define a pattern to find _where_ inside the note will links appear. This schema will support note entities that look like this:

```clojure
{:id 1
 :title "Note #1"
 :links [{:id 1
          :url "http://www.google.com"}
         {:id 2
          :url "http://www.yahoo.com"}]}
```

EntityDB will go through each entry in the `:links` vector and place these maps in the `:link` store. EntityDB supports relation paths of arbitrary depth - and you can nest vectors and maps in any way you need. This feature was designed in this way to naturally support REST and GraphQL payloads. There is no need to process any nested GraphQL payloads before putting them in EntityDB. Let's take a look at another schema:

```clojure
{:user {:entitydb/relations
        {:urls {:entitydb.relation/path [:urls :*]
                :entitydb.relation/type :url}
         :authored-posts {:entitydb.relation/path [:authoredPosts :*]
                          :entitydb.relation/type :post}
         :favorite-posts {:entitydb.relation/path [:favoritePosts :edges :* :node]
                          :entitydb.relation/type :post}
         :posts {:entitydb.relation/path [:posts :edges :* :node]
                 :entitydb.relation/type :post}
         :group-members {:entitydb.relation/path [:groups :* :members :edges :* :node]
                         :entitydb.relation/type :user}
         :twitterProfile :twitter-profile
         :githubProfile :github-profile}}
 :post {:entitydb/id :slug}
 :url {:entitydb/id :url}
 :twitter-profile {:entitydb/id :username}
 :github-profile {:entitydb/id :username
                  :entitydb/relations
                  {:repositories {:entitydb.relation/type :github-repository
                                  :entitydb.relation/path [:repositories :edges :* :node]}}}
 :github-repository {:entitydb/relations
                     {[:committers :edges :* :node] :github-profile
                      :homepage :url}}}
```

This is closer to something you might write in real application. This is a schema that supports payloads from a GraphQL API that implements a Relay standard. Let's take a look at the `:group-members` relation of the `:user` entity type. In this case, path to the group member will require iteration through nested vectors, and then getting the `:node` attribute from the last level. Support for deep paths allows you to normalize data without needing to write bunch of join entity types. You can focus only on your access patterns, and EntityDB will take care of rest. You also don't have to define a relationship for every nested map or vector, EntityDB will not process them unless you tell it so with the schema. Our goal was to implement a client side DB that requires minimal ceremony.

With that schema defined, we can now insert a payload like this directly in EntityDB (without any preprocessing):

```clojure
{:username "retro"
 :id 1
 :posts {:pageInfo {:hasNextPage true}
         :edges [{:cursor "1"
                  :node {:slug "my-post-1"
                         :title "My Post #1"}}
                 {:cursor "2"
                  :node {:slug "my-post-2"
                         :title "My Post #2"}}]}
 :authoredPosts [{:slug "my-post-3"
                  :title "My Post #3"}]
 :favoritePosts {:pageInfo {:hasNextPage true}
                 :edges [{:cursor "1"
                          :node {:slug "my-post-3"
                                 :title "My Post #3"}}
                         {:cursor "2"
                          :node {:slug "my-post-4"
                                 :title "My Post #4"}}]}
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
                                                :name "keechma.entitydb"}}]}}
 :urls [{:name "Homepage" :url "https://retroaktive.me"}
        {:name "Keechma" :url "https://keechma.com"}]
 :groups [{:name "Keechma Developers"
           :members {:pageInfo {:hasNextPage false}
                     :edges [{:cursor 3
                              :node {:id 1}}]}}]}
```

The behavior presented previously in this document - merging of entities and cleanup of entities when they are removed from the store is supported for deeply nested payloads.

Schema needs to be inserted in EntityDB before you can start using it:

```clojure
(def db (edb/insert-schema {} {:note {:entitydb/relations {:notes {:entitydb.relation/path [:notes :*]
                                                                   :entitydb.relation/type :note}}}
                               :link {:entitydb/id :url}}))
```

## Querying relationships

Related entities are not included by default when you get data from EntityDB. They need to be included explicitly. 

```clojure
(def db (edb/insert-schema {} {:note {:entitydb/relations {:notes {:entitydb.relation/path [:notes :*]
                                                                   :entitydb.relation/type :note}}}}))
(def db-1 (edb/insert-entity db :note {:id 1
                                       :title "Note #1"
                                       :links [{:id 1
                                                :url "http://www.google.com"}
                                               {:id 2
                                                :url "http://www.yahoo.com"}]}))
(edb/get-entity db :note 1) ;; Returns {:id 1 :title "Note #1" :links [(->EntityIdent :link 1) (->EntityIdent :link 2)] :entitydb/id 1 :entitydb/type :note}
```

Getting data from EntityDB without specifying which relations to include returns only idents instead of items.

### Include query

You will use the `include` query most of the time. It is used to include related entities.

### Recur-on query

### Switch query

### Reverse-include query

