(ns keechma.entitydb.internal)

(defrecord EntityIdent [type id])

(defn entity-ident? [leaf]
  (= EntityIdent (type leaf)))

(defn entity? [leaf]
  (and (:entitydb/type leaf)
       (:entitydb/id leaf)))

(defn entity->entity-ident [entity]
  (->EntityIdent (:entitydb/type entity) (:entitydb/id entity)))

(defn entity-ident->entity [entity-ident]
  {:entitydb/type (:type entity-ident)
   :entitydb/id (:id entity-ident)})

(defn entitydb-ex-info
  ([message anomaly] (entitydb-ex-info message anomaly {}))
  ([message anomaly props]
   (ex-info message (assoc props 
                           :entitydb.anomalies/category anomaly
                           :entitydb.anomalies/message message))))
