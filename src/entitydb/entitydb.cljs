(ns ^:figwheel-hooks entitydb.entitydb)

(defrecord EntityIdent [type id])

(declare prepare-insert*)
(declare get-by-id)

(defn assoc-entitydb-id [get-id item]
  (if (:entitydb/id item)
    item
    (assoc item :entitydb/id (get-id item))))

(defn get-relation-entity-type [relation entity]
  (let [entity-type (:entitydb.relation/type relation)]
    (if (fn? entity-type)
      (entity-type entity)
      entity-type)))

(defn prepare-relations [store current related-entities relation {:keys [iter-path path] :as cursor}]
  (if current
    (let [[current-iter & rest-iter-path] iter-path]
      (cond
        (not (seq iter-path))
        (let [entity-type (get-relation-entity-type relation current)
              {:keys [entity related-entities]} (prepare-insert* store entity-type current related-entities)
              entity-ident (->EntityIdent entity-type (:entitydb/id entity))]
          {:entity entity-ident 
           :related-entities (conj related-entities [entity-ident entity])})
        
        (= :* current-iter)
        (if (and (sequential? current) (seq current))
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
        (prepare-relations store entity related-entities v {:iter-path (:entitydb.relation/path v) :path []}))
      {:entity entity'
       :related-entities related-entities}
      relations))))

(defn prepare-insert [store entity-type entity]
  (let [{:keys [entity related-entities]} (prepare-insert* store entity-type entity)]
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


(defn resolve-relation 
  ([store current relation pull-relations]
   (resolve-relation store current relation pull-relations {:iter-path (:entitydb.relation/path relation) :path []}))
  ([store current relation pull-relations {:keys [iter-path path] :as cursor}]
   (when current
     (let [[current-iter & rest-iter-path] iter-path]
       (cond
         (and (not (seq iter-path)) (= EntityIdent (type current)))
         (get-by-id store (:type current) (:id current) pull-relations)
         
         (= :* current-iter)
         (map-indexed
          (fn [idx c]
            (resolve-relation store c relation pull-relations {:iter-path rest-iter-path :path (conj path idx)}))
          current)
         
         :else
         (let [resolved (resolve-relation store (get current current-iter) relation pull-relations {:iter-path rest-iter-path :path (conj path current-iter)})]
           (assoc current current-iter resolved)))))))

(defn resolve-relations [store entity-type entity pull-relations]
  (if (seq pull-relations)
    (reduce
     (fn [entity pull-relation]
       (let [[pull-relation' nested-pull-relations]
             (if (map? pull-relation) 
               (first (into [] pull-relation)) 
               [pull-relation])
             relation (get-in store [:entitydb/db :entitydb/schema entity-type :entitydb/relations pull-relation'])]
         (if relation
           (resolve-relation store entity relation nested-pull-relations)
           entity)))
     entity
     pull-relations)
    entity))

(defn get-by-id 
  ([store entity-type id] (get-by-id store entity-type id nil))
  ([store entity-type id pull-relations]
   (when-let [entity (get-in store [:entitydb/db :entitydb/store entity-type id])]
     (resolve-relations store entity-type entity pull-relations))))

(defn insert-named [store entity-type entity-name data]
  (let [id ()]))

(defn insert-collection [store entity-type collection-name data])

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
