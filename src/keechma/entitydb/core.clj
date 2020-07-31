(ns keechma.entitydb.core)

(def edb-fns '{:mutation [insert-entity
                          insert-schema
                          insert-entities
                          insert-named
                          insert-collection
                          remove-entity
                          remove-named
                          remove-collection
                          vacuum]
               :query    [get-entity
                          get-named
                          get-collection]})

(defn edb-fn
  [fn-sym]
  (symbol "keechma.entitydb.core" (name fn-sym)))

(defn mutation-defn
  [path fn-sym]
  `(defn ~fn-sym [store# & args#]
    (->> (apply ~(edb-fn fn-sym) (get-in store# ~path) args#)
          (assoc-in store# ~path))))

(defn query-defn
  [path fn-sym]
  `(defn ~fn-sym [store# & args#]
     (apply ~(edb-fn fn-sym) (get-in store# ~path) args#)))

(defmacro def-adapted-api
  "Defines `entitydb` api functions in the current namespace. Adapts
  each to expect the `store` argument to be a map that embeds an
  `entity-db` at key-path `path`."
  [path]
  `(do
     ~@(map (partial mutation-defn path) (:mutation edb-fns))
     ~@(map (partial query-defn path) (:query edb-fns))))
