(ns entitydb.query
  (:require [entitydb.internal :refer [EntityIdent entity? entity-ident?]]))

(declare get-by-id)

(defn include
  ([relation] (include relation nil))
  ([relation subquery]
   {::type             :include
    ::subquery         subquery
    :entitydb/relation relation}))

(defn recur-on
  ([relation] (recur-on relation js/Infinity))
  ([relation recur-limit]
   {::type             :recur-on
    ::recur-limit      recur-limit
    :entitydb/relation relation}))

(defn resolve-relation 
  ([store current relation queries]
   (resolve-relation store current relation queries {:iter-path (:entitydb.relation/path relation) :path []}))
  ([store current relation queries {:keys [iter-path path] :as cursor}]
   (when current
     (let [[current-iter & rest-iter-path] iter-path]
       (cond
         (and (seq path) (entity? current))
         current
         
         (and (not (seq iter-path)) (entity-ident? current))
         (get-by-id store (:type current) (:id current) queries)

         (entity-ident? current)
         current

         (= :* current-iter)
         (map-indexed
          (fn [idx c]
            (resolve-relation store c relation queries {:iter-path rest-iter-path :path (conj path idx)}))
          current)
         
         :else
         (let [resolved (resolve-relation store (get current current-iter) relation queries {:iter-path rest-iter-path :path (conj path current-iter)})]
           (if resolved (assoc current current-iter resolved) current)))))))

(defmulti resolve-query (fn [store entity query parent-queries] (::type query)))

(defmethod resolve-query :default [_ entity _ _] entity)

(defmethod resolve-query :include [store entity query parent-queries]
  (let [relation (get-in store [:entitydb/db :entitydb/schema (:entitydb/type entity) :entitydb/relations (:entitydb/relation query)])]
    (resolve-relation store entity relation (::subquery query))))

(defmethod resolve-query :recur-on [store entity query parent-queries]
  (if (pos? (::recur-limit query))
    (let [relation (get-in store [:entitydb/db :entitydb/schema (:entitydb/type entity) :entitydb/relations (:entitydb/relation query)])]
      (resolve-relation store entity relation (map (fn [q] (if (= (:recur-on (::type q))) (update q ::recur-limit dec) q)) parent-queries)))
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

(defn get-by-id 
  ([store entity-type id] (get-by-id store entity-type id nil))
  ([store entity-type id queries]
   (let [entity (get-in store [:entitydb/db :entitydb/store entity-type id])]
     (resolve-queries store entity queries))))
