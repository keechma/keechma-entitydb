(ns keechma.entitydb.query
  (:require [keechma.entitydb.internal :refer [entity->entity-ident]]))

(declare get-entity)
(declare resolve-queries)

(defn include
  "Includes a relationship by name"
  ([relation] (include relation nil))
  ([relation subquery]
   {::type             :include
    ::subquery         subquery
    :entitydb/relation relation}))

(defn recur-on
  "Recurs on a relationship by name and repeats a query. Recursion is unbounded by default, but can be limited in case of circular relationships."
  ([relation] (recur-on relation js/Infinity))
  ([relation recur-limit]
   {::type             :recur-on
    ::recur-limit      recur-limit
    :entitydb/relation relation}))

(defn reverse-include
  "Includes all entities of a given type that point to the current entity."
  ([entity-type] (reverse-include entity-type nil))
  ([entity-type subquery]
   {::type :reverse
    ::subquery subquery
    :entitydb/type entity-type}))

(defn switch
  "Based on the relation entity type, it will perform a different query"
  [switch-queries]
  {::type :switch
   ::switch-queries switch-queries})

(defn resolve-relation [store entity relation queries]
  (let [entity-ident (entity->entity-ident entity)
        related-entities (get-in store [:entitydb/relations entity-ident relation])]
    (reduce-kv
     (fn [entity' path related-entity-ident]
       (assoc-in entity' path (get-entity store (:type related-entity-ident) (:id related-entity-ident) queries)))
     entity
     related-entities)))

(defmulti resolve-query (fn [store entity query parent-queries] (::type query)))

(defmethod resolve-query :default [_ entity _ _] entity)

(defmethod resolve-query :switch [store entity query _]
  (let [entity-type (:entitydb/type entity)
        queries (get-in query [::switch-queries entity-type])]
    (resolve-queries store entity queries)))

(defmethod resolve-query :include [store entity query _]
  (resolve-relation store entity (:entitydb/relation query) (::subquery query)))

(defmethod resolve-query :reverse [store entity query _]
  (let [entity-ident (entity->entity-ident entity)
        reverse-entity-type (:entitydb/type query)
        reverse-related (get-in store [:entitydb.relations/reverse entity-ident reverse-entity-type])
        subquery (::subquery query)]
    (reduce-kv
     (fn [entity relation-name relation-data]
       (let [reverse-related-ids (keys relation-data)
             reverse-related-entities
             (into {} (map (fn [id] [id (get-entity store reverse-entity-type id subquery)]) reverse-related-ids))]
         (assoc-in entity [:entitydb.relations/reverse reverse-entity-type relation-name] reverse-related-entities)))
     entity
     reverse-related)))

(defmethod resolve-query :recur-on [store entity query parent-queries]
  (if (pos? (::recur-limit query))
    (resolve-relation store entity (:entitydb/relation query) (map (fn [q] (if (= (:recur-on (::type q))) (update q ::recur-limit dec) q)) parent-queries))
    entity))

(defn optimize-queries* [queries]
  ;; Query optimizer removes redundant queries and combines same queries
  ;; if they were included (possibly by mistake).
  ;;
  ;; If there is a recursive query present on the relation, then we have
  ;; to remove any include queries on the same relation.
  ;;
  ;; If there are multiple recursive queries on the same relation, optimizer
  ;; will pick the one with the biggest recursion boundary.
  ;;
  ;; If there are multiple include queries on the same relation, optimizer
  ;; will combine their subqueries (which will be optimized when the resolver
  ;; runs them)
  (let [queries' (map #(if (keyword? %) (include %) %) queries)
        recur-on-queries-map (reduce
                              (fn [acc q]
                                (if (= :recur-on (::type q))
                                  (let [relation (:entitydb/relation q)
                                        current (get acc relation)]
                                    (if (> (::recur-limit q) (::recur-limit current))
                                      (assoc acc (:entitydb/relation q) q)
                                      acc))
                                  acc))
                              {}
                              queries')
        include-queries-map (reduce
                             (fn [acc q]
                               (if (and (= :include (::type q))
                                        (not (contains? recur-on-queries-map (:entitydb/relation q))))
                                 (let [relation (:entitydb/relation q)
                                       current (get acc relation)]
                                   (assoc acc relation (update q ::subquery #(concat % (::subquery current)))))
                                 acc))
                             {}
                             queries')
        other-queries (filter #(and (not= :recur-on (::type %)) (not= :include (::type %))) queries')]
    (set (concat (vals recur-on-queries-map) (vals include-queries-map) other-queries))))

(def optimize-queries (memoize optimize-queries*))

(defn resolve-queries [store entity queries]
  (let [queries' (optimize-queries queries)]
    (reduce
     (fn [entity query]
       (resolve-query store entity query queries'))
     entity
     queries')))

(defn get-entity
  ([store entity-type id] (get-entity store entity-type id nil))
  ([store entity-type id queries]
   (let [entity (get-in store [:entitydb/store entity-type id])]
     (resolve-queries store entity queries))))
