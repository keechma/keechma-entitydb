(ns keechma.entitydb.internal
  "Collection of functions used for managing Entity idents. EntityIdent is defined as a record consisting of fields
  `type` and `id`. ")

(defrecord EntityIdent [type id])

(defn entity-ident?
  "Check if `leaf` is of record `EntityIdent`"
  [leaf]
  (= EntityIdent (type leaf)))

(defn entity?
  "Check if `leaf` has params `:entitydb/type` and `:entitydb/id` which constitute an entity"
  [leaf]
  (and (:entitydb/type leaf)
       (:entitydb/id leaf)))

(defn entity->entity-ident
  "Returns `EntityIdent` record constructed from `Entity` params"
  [entity]
  (->EntityIdent (:entitydb/type entity) (:entitydb/id entity)))

(defn entity-ident->entity
  "Returns `Entity` map constructed from `EntityIdent` records fields"
  [entity-ident]
  {:entitydb/type (:type entity-ident)
   :entitydb/id (:id entity-ident)})

(defn entitydb-ex-info
  "Creates exception info instance for entitydb anomalies"
  ([message anomaly] (entitydb-ex-info message anomaly {}))
  ([message anomaly props]
   (ex-info message (assoc props
                           :entitydb.anomalies/category anomaly
                           :entitydb.anomalies/message message))))
