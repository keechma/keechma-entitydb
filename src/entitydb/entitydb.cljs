(ns ^:figwheel-hooks entitydb.entitydb)

(defrecord EntityIdent [type id])

(declare prepare-insert*)

(defn assoc-entitydb-id [get-id item]
  (if (:entitydb/id item)
    item
    (assoc item :entitydb/id (get-id item))))

(defn prepare-relations [store current related-entities relation {:keys [iter-path path] :as cursor}]
  (if current
    (let [[current-iter & rest-iter-path] iter-path]
      (cond
        (not (seq iter-path))
        (let [entity-type (:entitydb/type relation)
              {:keys [entity related-entities]} (prepare-insert* store entity-type current related-entities)
              entity-ident (->EntityIdent entity-type (:entitydb/id entity))]
          {:entity entity-ident 
           :related-entities (conj related-entities [entity-ident entity])})
        
        (= :* current-iter)
        (if (seq current)
          (reduce-kv 
           (fn [m idx v]
             (let [{:keys [entity related-entities]} (prepare-relations store v (:related-entities m) relation {:iter-path rest-iter-path :path (conj path idx)})]
               (if entity
                 {:entity (conj (:entity m) entity)
                  :related-entities related-entities}
                 m))) 
           {:entity [] :related-entities related-entities}
           (vec current))
          {:entity nil
           :related-entities related-entities})

        :else
        (let [{:keys [entity related-entities]} (prepare-relations store (get current current-iter) related-entities relation {:iter-path rest-iter-path :path (conj path current-iter)})]
          {:entity (if entity (assoc current current-iter entity) current)
           :related-entities related-entities})))
    {:entity current
     :related-entities related-entities}))

(defn prepare-insert*
  ([store entity-type entity] (prepare-insert* store entity-type entity []))
  ([store entity-type entity related-entities]
   (js/console.log "Prepare Insert" entity)
   (let [entity-schema (get-in store [:entitydb/db :entitydb/schema entity-type])
         processor (or (:entitydb/processor entity-schema)
                       (partial assoc-entitydb-id :id)) 
         relations (:entitydb/relations entity-schema)
         entity' (processor entity)]
     (reduce-kv  
      (fn [{:keys [entity related-entities]} k v]  
        (prepare-relations store entity related-entities v {:iter-path k :path []}))
      {:entity entity'
       :related-entities related-entities}
      relations))))

(defn prepare-insert [store entity-type entity]
  (let [{:keys [entity related-entities]} (prepare-insert* store entity-type entity)]
    (conj related-entities [(->EntityIdent entity-type (:entitydb/id entity)) entity])))

(defn insert-entity [store entity-type entity]
  (let [prepared (prepare-insert store entity-type entity)]
    (reduce
     (fn [acc [entity-ident entity]]
       (update-in acc [:entitydb/db :entitydb/store (:type entity-ident) (:id entity-ident)] #(merge % entity)))
     store
     prepared)))

(defn insert-entities [store entity-type entities]
  (reduce (fn [acc entity] (insert-entity acc entity-type entity)) store entities))

(defn insert-named-entity [store entity-type entity-name data]
  (let [id ()]))

(defn insert-collection [store entity-type collection-name data])

(defn prepare-type-schema-relations [type-schema]
  (if-let [relations (:entitydb/relations type-schema)]
    (assoc type-schema :entitydb/relations
           (->> relations
                (map 
                 (fn [[path relation]]
                   [(vec (flatten [path]))
                    (if (keyword? relation) {:entitydb/type relation} relation)]))
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
