(ns entitydb.internal)

(defrecord EntityIdent [type id])

(defn entity-ident? [leaf]
  (= EntityIdent (type leaf)))

(defn entity? [leaf]
  (and (:entitydb/type leaf)
       (:entitydb/id leaf)))
