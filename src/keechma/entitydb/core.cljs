(ns keechma.entitydb.core
  (:require [keechma.entitydb.query :as query]
            [medley.core :refer [dissoc-in]]
            [keechma.entitydb.util :refer [vec-remove]]
            [keechma.entitydb.internal :refer [->EntityIdent
                                               entity->entity-ident
                                               entity-ident->entity
                                               entity-ident?
                                               entitydb-ex-info]]))

(def get-entity query/get-entity)

(declare prepare-insert)

(defn assoc-entitydb-id [get-id item]
  (if (:entitydb/id item)
    item
    (assoc item :entitydb/id (get-id item))))

(defn valid-entity-id? [{:entitydb/keys [id]}]
  (if (seqable? id)
    (not (every? nil? id))
    (not (nil? id))))

(defn valid-entity-type? [store {:entitydb/keys [type]}]
  (if (get-in store [:entitydb/opts :entitydb.schema/strict?])
    (contains? (:entitydb/schema store) type)
    true))

(defn prepare-type-schema-relations [type-schema entitydb-type]
  (if-let [relations (:entitydb/relations type-schema)]
    (assoc type-schema :entitydb/relations
           (->> relations
                (map
                 (fn [[relation-name relation]]
                   (let [prepared-relation (-> (if (keyword? relation) {:entitydb.relation/type relation} relation)
                                               (update :entitydb.relation/path #(or % (vec (flatten [relation-name])))))]
                     (when (= :* (first (:entitydb.relation/path prepared-relation)))
                       (throw (entitydb-ex-info "Relation's :keechma.entitydb.relation/path can't start with :*"
                                                :invalid-schema
                                                {:entitydb/relation      prepared-relation
                                                 :entitydb.relation/name relation-name
                                                 :entitydb/type          entitydb-type})))
                     (when (nil? (:entitydb.relation/type prepared-relation))
                       (throw (entitydb-ex-info "Relation must have :keechma.entitydb.relation/type defined"
                                                :invalid-schema
                                                {:entitydb/relation      prepared-relation
                                                 :entitydb.relation/name relation-name
                                                 :entitydb/type          entitydb-type})))
                     [relation-name prepared-relation])))
                (into {})))
    type-schema))

(defn prepare-type-schema-processor [type-schema _]
  (let [get-id    (or (:entitydb/id type-schema) :id)
        processor (or (:entitydb/processor type-schema) identity)]
    (assoc type-schema :entitydb/processor (comp (partial assoc-entitydb-id get-id) processor))))

(defn prepare-schema [schema]
  (->> schema
       (map
        (fn [[t ts]]
          [t (-> ts
                 (prepare-type-schema-processor t)
                 (prepare-type-schema-relations t))]))
       (into {})))

(defn insert-schema
  ([store schema] (insert-schema store schema {}))
  ([store schema opts]
   (-> store
       (assoc-in [:entitydb/schema] (prepare-schema schema))
       (assoc-in [:entitydb/opts] opts))))

(defn get-entity-type [entity-type entity]
  (cond
    (:entitydb/type entity) (:entitydb/type entity)
    (fn? entity-type) (entity-type entity)
    :else entity-type))

(defn get-relation-entity-type [relation entity]
  (get-entity-type (:entitydb.relation/type relation) entity))

(defn prepare-relations [store current related-entities relation {:keys [iter-path path ident relation-name] :as cursor}]
  (if current
    (let [[current-iter & rest-iter-path] iter-path]
      (cond
        (and (not (seq iter-path)) (entity-ident? current))
        {:entity           current
         :related-entities (assoc related-entities [relation-name path]
                                  {:entity (entity-ident->entity current) :related-entities {}})}

        (and (not (seq iter-path)) current)
        (let [entity-type               (get-relation-entity-type relation current)
              prepared                  (prepare-insert store entity-type current)
              prepared-entity           (:entity prepared)
              prepared-related-entities (:related-entities prepared)]
          {:entity           (entity->entity-ident prepared-entity)
           :related-entities (assoc related-entities [relation-name path] {:entity prepared-entity :related-entities prepared-related-entities})})

        (entity-ident? current)
        {:entity           current
         :related-entities related-entities}

        (= :* current-iter)
        (if (and (sequential? current) (seq current))
          (reduce-kv
           (fn [m idx v]
             (let [{:keys [entity related-entities]}
                   (prepare-relations store v (:related-entities m) relation
                                      (merge cursor {:iter-path rest-iter-path :path (conj path idx)}))]
               (if entity
                 {:entity           (conj (:entity m) entity)
                  :related-entities related-entities}
                 m)))
           {:entity [] :related-entities related-entities}
           (vec current))
          {:entity           nil
           :related-entities related-entities})

        :else
        (let [{:keys [entity related-entities]}
              (prepare-relations store (get current current-iter) related-entities relation
                                 (merge cursor {:iter-path rest-iter-path :path (conj path current-iter)}))]
          {:entity           (if entity (assoc current current-iter entity) current)
           :related-entities related-entities})))
    {:entity           current
     :related-entities related-entities}))

(defn prepare-insert
  ([store entity-type entity] (prepare-insert store entity-type entity {}))
  ([store entity-type entity related-entities]
   (let [entity-type'  (get-entity-type entity-type entity)
         entity-schema (get-in store [:entitydb/schema entity-type'])
         processor     (or (:entitydb/processor entity-schema)
                           (partial assoc-entitydb-id :id))
         relations     (:entitydb/relations entity-schema)
         entity'       (-> (processor entity)
                           (assoc :entitydb/type entity-type'))]

     (reduce-kv
      (fn [{:keys [entity related-entities]} k v]
        (prepare-relations store entity related-entities v
                           {:iter-path     (:entitydb.relation/path v)
                            :path          []
                            :relation-name k
                            :ident         (entity->entity-ident entity)}))
      {:entity           entity'
       :related-entities related-entities}
      relations))))

(defn remove-invalid-relations [store entity]
  (let [entity-type        (:entitydb/type entity)
        entity-id          (:entitydb/id entity)
        entity-ident       (entity->entity-ident entity)
        entity-relations   (get-in store [:entitydb/schema entity-type :entitydb/relations])
        entity-update-keys (set (keys entity))
        entity-invalid-relations
        (reduce-kv
         (fn [m k v]
           (if (contains? entity-update-keys (first (:entitydb.relation/path v)))
             (conj m k)
             m))
         #{}
         entity-relations)]
    (reduce
     (fn [store invalid-relation]
       (let [related-entity-idents (vals (get-in store [:entitydb/relations entity-ident invalid-relation]))]
         (-> (reduce
              (fn [store related-entity-ident]
                (dissoc-in store [:entitydb.relations/reverse related-entity-ident entity-type invalid-relation entity-id]))
              store
              related-entity-idents)
             (dissoc-in [:entitydb/relations entity-ident invalid-relation]))))
     store
     entity-invalid-relations)))

(defn insert-prepared
  ([store prepared] (insert-prepared store prepared nil))
  ([store {:keys [entity related-entities]} parent-entity-ident]
   (when-not (valid-entity-id? entity)
     (throw (entitydb-ex-info "Trying to insert entity without ID" :invalid-entity {:entity entity})))
   (when-not (valid-entity-type? store entity)
     (throw (entitydb-ex-info "Trying to insert entity with invalid type" :invalid-entity {:entity entity})))
   (let [entity-id    (:entitydb/id entity)
         entity-type  (:entitydb/type entity)
         entity-merge (get-in store [:entitydb/schema entity-type :entitydb/merge] merge)
         entity-ident (entity->entity-ident entity)
         updated-store
         (as-> store store'
           (update-in store' [:entitydb/store entity-type entity-id] #(entity-merge % entity))
           (remove-invalid-relations store' entity)
           (reduce-kv
            (fn [s [relation-name path] v]
              (let [related-entity       (:entity v)
                    related-entity-type  (:entitydb/type related-entity)
                    related-entity-id    (:entitydb/id related-entity)
                    related-entity-ident (entity->entity-ident related-entity)
                    reverse-relations    (or (get-in s [:entitydb.relations/reverse related-entity-ident entity-type relation-name entity-id]) #{})
                    reverse-relations'   (conj reverse-relations path)]
                (-> s
                    (assoc-in [:entitydb/relations entity-ident relation-name path] related-entity-ident)
                    (assoc-in [:entitydb.relations/reverse related-entity-ident entity-type relation-name entity-id] reverse-relations')
                    (insert-prepared v)
                    :store)))
            store'
            related-entities))]
     {:store  updated-store
      :entity (get-in updated-store [:entitydb/store entity-type entity-id])})))

(defn insert* [store entity-type data]
  (let [prepared (prepare-insert store entity-type data)]
    (insert-prepared store prepared)))

(defn insert-entity [store entity-type data]
  (:store (insert* store entity-type data)))

(defn get-report [reverse-relations]
  (into [] (for [[type rel] reverse-relations
                 [rel-name rel-pairs] rel
                 [rel-pair-id rel-pair-paths] rel-pairs
                 rel-pair-path rel-pair-paths]
             {:entity-ident (->EntityIdent type rel-pair-id)
              :relation     rel-name
              :path         rel-pair-path})))

(defn remove-ident-from-named-items
  "Return store without named-items that contain passed entity-ident."
  [store entity-ident]
  (let [named-items' (into {} (filter (fn [[k v]] (not= entity-ident (:data v))) (:entitydb.named/item store)))]
    (assoc store :entitydb.named/item named-items')))

(defn remove-ident-from-collections
  "Return store with entity-ident cleared from collections.
   Will return empty collection if entity-ident was the only element in collection."
  [store entity-ident]
  (let [collections' (reduce-kv (fn [acc k v]
                                  (assoc acc k (assoc v :data (filter (fn [a] (not= entity-ident a)) (:data v)))))
                                {}
                                (:entitydb.named/collection store))]
    (assoc store :entitydb.named/collection collections')))

(defn remove-dangling-reverse-relations [store entity-type id]
  (let [reverse-relations  (:entitydb.relations/reverse store)
        dissoc-vals        (for [[type rel] reverse-relations
                                 [rel-name rel-pairs] rel
                                 [rel-pair-id _] rel-pairs
                                 :when (= entity-type rel-name)]
                             [type rel-name rel-pair-id])
        reverse-relations' (reduce (fn [acc v]
                                     (dissoc-in acc (conj v id))) reverse-relations dissoc-vals)]
    (if (empty? reverse-relations')
      (dissoc store :entitydb.relations/reverse)
      (assoc store :entitydb.relations/reverse reverse-relations'))))

(defn remove-by-id* [store entity-type id]
  (if-let [entity (get-entity store entity-type id)]
    (let [entity-ident      (entity->entity-ident entity)
          reverse-relations (get-in store [:entitydb.relations/reverse entity-ident])
          report            (get-report reverse-relations)]
      ;; For each reverse relation we have to clear the data on it's position in the
      ;; owner entity. We also need to clear the cached :entitydb/relation
      {:store  (-> (reduce
                    (fn [store relation-report]
                      (let [related-entity-ident     (:entity-ident relation-report)
                            {:keys [relation path]} relation-report
                            related-entity-type      (:type related-entity-ident)
                            related-entity-id        (:id related-entity-ident)
                            related-entity           (get-in store [:entitydb/store related-entity-type related-entity-id])
                            path-without-last        (drop-last path)
                            last-path-segment        (last path)
                            last-path-segment-parent (get-in related-entity path-without-last)
                            updated-last-path-segment-parent
                            (if (and (integer? last-path-segment) (sequential? last-path-segment-parent))
                              (vec-remove last-path-segment last-path-segment-parent)
                              (dissoc last-path-segment-parent last-path-segment))
                            updated-related-entity   (assoc-in related-entity path-without-last updated-last-path-segment-parent)]
                        (-> store
                            (assoc-in [:entitydb/store related-entity-type related-entity-id] updated-related-entity)
                            (dissoc-in [:entitydb/relations related-entity-ident relation path]))))
                    store
                    report)
                   (dissoc-in [:entitydb/relations entity-ident])
                   (dissoc-in [:entitydb.relations/reverse entity-ident])
                   (dissoc-in [:entitydb/store entity-type id])
                   (remove-ident-from-named-items entity-ident)
                   (remove-ident-from-collections entity-ident)
                   (remove-dangling-reverse-relations entity-type id))
       :report report})
    {:store  store
     :report []}))

(defn remove-entity [store entity-type id]
  (:store (remove-by-id* store entity-type id)))

(defn insert-entities [store entity-type entities]
  (reduce (fn [acc entity] (insert-entity acc entity-type entity)) store entities))

(defn insert-named
  ([store entity-type entity-name data]
   (insert-named store entity-type entity-name data nil))
  ([store entity-type entity-name data named-meta]
   (let [{:keys [store entity]} (insert* store entity-type data)]
     (assoc-in store
               [:entitydb.named/item entity-name]
               {:data (entity->entity-ident entity)
                :meta named-meta}))))

(defn insert-entities-for-collection [store entity-type data]
  (reduce
   (fn [{:keys [store entities]} item]
     (let [{:keys [store entity]} (insert* store entity-type item)]
       {:store    store
        :entities (conj entities entity)}))
   {:store    store
    :entities []}
   data))

(defn insert-collection
  ([store entity-type collection-name data]
   (insert-collection store entity-type collection-name data nil))
  ([store entity-type collection-name data collection-meta]
   (let [{:keys [store entities]} (insert-entities-for-collection store entity-type data)]
     (assoc-in store
               [:entitydb.named/collection collection-name]
               {:data (map entity->entity-ident entities)
                :meta collection-meta}))))

(defn append-collection
  ([store entity-type collection-name data]
   (append-collection store entity-type collection-name data ::inherit))
  ([store entity-type collection-name data collection-meta]
   (let [{:keys [store entities]} (insert-entities-for-collection store entity-type data)]
     (update-in store
                [:entitydb.named/collection collection-name]
                (fn [{:keys [data meta]}]
                  {:data (concat data (map entity->entity-ident entities))
                   :meta (if (= ::inherit collection-meta) meta collection-meta)})))))

(defn prepend-collection
  ([store entity-type collection-name data]
   (prepend-collection store entity-type collection-name data ::inherit))
  ([store entity-type collection-name data collection-meta]
   (let [{:keys [store entities]} (insert-entities-for-collection store entity-type data)]
     (update-in store
                [:entitydb.named/collection collection-name]
                (fn [{:keys [data meta]}]
                  {:data (concat (map entity->entity-ident entities) data)
                   :meta (if (= ::inherit collection-meta) meta collection-meta)})))))

(defn get-named
  ([store entity-name] (get-named store entity-name nil))
  ([store entity-name query]
   (when-let [entity-ident (get-in store [:entitydb.named/item entity-name :data])]
     (get-entity store (:type entity-ident) (:id entity-ident) query))))

(defn get-collection
  ([store collection-name] (get-collection store collection-name nil))
  ([store collection-name query]
   (when-let [entity-idents (get-in store [:entitydb.named/collection collection-name :data])]
     (mapv
      #(get-entity store (:type %) (:id %) query)
      entity-idents))))

(defn get-ident-for-named [store entity-name]
  (get-in store [:entitydb.named/item entity-name :data]))

(defn get-idents-for-collection [store collection-name]
  (get-in store [:entitydb.named/collection collection-name :data]))

(defn get-entity-from-ident
  ([store entity-ident] (get-entity-from-ident store entity-ident nil))
  ([store entity-ident query] (get-entity store (:type entity-ident) (:id entity-ident) query)))

(defn get-entities-from-idents
  ([store entity-idents] (get-entities-from-idents store entity-idents nil))
  ([store entity-idents query] (mapv #(get-entity store (:type %) (:id %) query) entity-idents)))

(defn remove-named [store entity-name]
  (dissoc-in store [:entitydb.named/item entity-name]))

(defn remove-collection [store collection-name]
  (dissoc-in store [:entitydb.named/collection collection-name]))

(defn entitydb-store->entity-idents-set [store]
  (let [store (:entitydb/store store)]
    (set (for [[type value] store
               [id _] value]
           (->EntityIdent type id)))))

(defn entitydb-named->entity-idents-set [store]
  (let [named-items                     (:entitydb.named/item store)
        named-collections               (:entitydb.named/collection store)
        named-items-entity-idents       (map :data (vals named-items))
        named-collections-entity-idents (mapcat :data (vals named-collections))]
    (set (concat named-items-entity-idents named-collections-entity-idents))))

(defn get-entity-idents-to-remove [entity-idents-to-remove relations ref-idents]
  (reduce
   (fn [acc entity-ident]
     (if (contains? acc entity-ident)
       (let [acc'                    (disj acc entity-ident)
             entity-idents-relations (get relations entity-ident)
             related-entity-idents   (for [[_ path-ident-pair] entity-idents-relations
                                           [_ related-entity-ident] path-ident-pair]
                                       related-entity-ident)]
         (get-entity-idents-to-remove acc' relations related-entity-idents))
       acc)) entity-idents-to-remove ref-idents))

(defn vacuum [store]
  (let [store-entity-idents     (entitydb-store->entity-idents-set store)
        named-entity-idents     (entitydb-named->entity-idents-set store)
        entity-idents-to-remove (get-entity-idents-to-remove store-entity-idents (:entitydb/relations store) named-entity-idents)]
    (reduce
     (fn [store' entity-ident]
       (let [entity-type (:type entity-ident)
             entity-id   (:id entity-ident)]
         (-> store'
             (dissoc-in [:entitydb/store entity-type entity-id])
             (dissoc-in [:entitydb/relations entity-ident])
             (dissoc-in [:entitydb.relations/reverse entity-ident])))) store entity-idents-to-remove)))