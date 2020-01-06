(ns entitydb.entitydb
  (:require [entitydb.query :as query]
            [entitydb.internal :refer [->EntityIdent EntityIdent]]))

(def get-by-id query/get-by-id)

(declare prepare-insert*)


(defn entity->ident [entity]
  (->EntityIdent (:entitydb/type entity) (:entitydb/id entity)))

(defn assoc-entitydb-id [get-id item]
  (if (:entitydb/id item)
    item
    (assoc item :entitydb/id (get-id item))))

(defn prepare-type-schema-relations [type-schema]
  (if-let [relations (:entitydb/relations type-schema)]
    (assoc type-schema :entitydb/relations
           (->> relations
                (map 
                 (fn [[relation-name relation]]
                   [relation-name
                    (-> (if (keyword? relation) {:entitydb.relation/type relation} relation)
                        (update :entitydb.relation/path #(or % (vec (flatten [relation-name]))))
                        (update :entitydb.relation/processor #(or % identity)))]))
                (into {})))
    type-schema))

(defn prepare-type-schema-processor [type-schema]
   (let [get-id (or (:entitydb/id type-schema) :id)
         processor (or (:entitydb/processor type-schema) identity)]
     (assoc type-schema :entitydb/processor (comp (partial assoc-entitydb-id get-id) processor))))

(defn prepare-schema [schema]
  (->> schema
       (map 
        (fn [[t ts]]
          [t (-> ts 
                 prepare-type-schema-processor 
                 prepare-type-schema-relations)]))
       (into {})))

(defn insert-schema [store schema]
  (assoc-in store [:entitydb/db :entitydb/schema] (prepare-schema schema)))

(defn get-relation-entity-type [relation entity]
  (let [entity-type (:entitydb.relation/type relation)]
    (if (fn? entity-type)
      (entity-type entity)
      entity-type)))

(defn prepare-relations [store current related-entities relation {:keys [iter-path path ident] :as cursor}]
  (if current
    (let [[current-iter & rest-iter-path] iter-path]
      (cond
        (not (seq iter-path))
        (let [entity-type (get-relation-entity-type relation current)
              {:keys [entity related-entities]} (prepare-insert* store entity-type current related-entities)
              entity-ident (entity->ident entity)]
          {:entity entity-ident 
           :related-entities (conj related-entities [entity-ident entity])})
        
        (= :* current-iter)
        (if (and (sequential? current) (seq current))
          (reduce-kv 
           (fn [m idx v]
             (let [{:keys [entity related-entities]} (prepare-relations store v (:related-entities m) relation (merge cursor {:iter-path rest-iter-path :path (conj path idx)}))]
               (if entity
                 {:entity (conj (:entity m) entity)
                  :related-entities related-entities}
                 m))) 
           {:entity [] :related-entities related-entities}
           (vec current))
          {:entity nil
           :related-entities related-entities})

        :else
        (let [{:keys [entity related-entities]} (prepare-relations store (get current current-iter) related-entities relation (merge cursor {:iter-path rest-iter-path :path (conj path current-iter)}))]
          {:entity (if entity (assoc current current-iter entity) current)
           :related-entities related-entities})))
    {:entity current
     :related-entities related-entities}))

(defn prepare-insert*
  ([store entity-type entity] (prepare-insert* store entity-type entity []))
  ([store entity-type entity related-entities]
   (let [entity-schema (get-in store [:entitydb/db :entitydb/schema entity-type])
         processor (or (:entitydb/processor entity-schema)
                       (partial assoc-entitydb-id :id)) 
         relations (:entitydb/relations entity-schema)
         entity' (-> (processor entity)
                     (assoc :entitydb/type entity-type))]
     (reduce-kv  
      (fn [{:keys [entity related-entities]} k v]  
        (prepare-relations store entity related-entities v {:iter-path (:entitydb.relation/path v) :path [] :ident (entity->ident entity')}))
      {:entity entity'
       :related-entities related-entities}
      relations))))

(defn prepare-insert [store entity-type entity]
  (let [{:keys [entity related-entities] :as prepared} (prepare-insert* store entity-type entity)]
    (conj related-entities [(->EntityIdent entity-type (:entitydb/id entity)) entity])))

(defn insert-one [store entity-type entity]
  (let [prepared (prepare-insert store entity-type entity)]
    (reduce
     (fn [acc [entity-ident entity]]
       (update-in acc [:entitydb/db :entitydb/store (:type entity-ident) (:id entity-ident)] #(merge % entity)))
     store
     prepared)))

(defn insert-many [store entity-type entities]
  (reduce (fn [acc entity] (insert-one acc entity-type entity)) store entities))

(defn insert-named [store entity-type entity-name data])

(defn insert-collection [store entity-type collection-name data])






