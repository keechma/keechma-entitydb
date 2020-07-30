(ns keechma.entitydb.core)

(defn wrap-mutation-fn [path name])
(defn wrap-query-fn [path name])

(def fns {:mutation #{"insert"
                      "insert-schema"
                      "insert-many"
                      "insert-named-item"
                      "insert-collection"
                      "remove-entity"
                      "remove-named"
                      "remove-collection"
                      "vacuum"}
          :query    #{"get-entity"
                      "get-named"
                      "get-collection"}})

(defmacro export-api! [path]
  (let [mutation-defs (map
                        (fn [n]
                          (let [fn-name (symbol n)
                                origin-name (symbol (str "keechma.entitydb.core/" n))]
                            `(def ~fn-name (keechma.entitydb.core/wrap-mutation-fn ~path ~origin-name))))
                        (:mutation fns))
        query-defs (map
                        (fn [n]
                          (let [fn-name (symbol n)
                                origin-name (symbol (str "keechma.entitydb.core/" n))]
                            `(def ~fn-name (keechma.entitydb.core/wrap-query-fn ~path ~origin-name))))
                        (:query fns))]
    `(do
       ~@(concat mutation-defs query-defs))))