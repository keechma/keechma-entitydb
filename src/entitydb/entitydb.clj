(ns entitydb.entitydb)

(defn wrap-mutation-fn [path name])
(defn wrap-query-fn [path name])

(def fns {:mutation #{"insert"
                      "insert-schema"
                      "insert-many"
                      "insert-named-item"
                      "insert-collection"
                      "remove-by-id"
                      "remove-named"
                      "remove-collection"
                      "vacuum"}
          :query    #{"get-by-id"
                      "get-named"
                      "get-collection"}})

(defmacro export-api! [path]
  (let [mutation-defs (map
                        (fn [n]
                          (let [fn-name (symbol n)
                                origin-name (symbol (str "entitydb.entitydb/" n))]
                            `(def ~fn-name (entitydb.entitydb/wrap-mutation-fn ~path ~origin-name))))
                        (:mutation fns))
        query-defs (map
                        (fn [n]
                          (let [fn-name (symbol n)
                                origin-name (symbol (str "entitydb.entitydb/" n))]
                            `(def ~fn-name (entitydb.entitydb/wrap-query-fn ~path ~origin-name))))
                        (:query fns))]
    `(do
       ~@(concat mutation-defs query-defs))))