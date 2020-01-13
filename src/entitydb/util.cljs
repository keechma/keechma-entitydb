(ns entitydb.util)

(defn vec-remove
  "remove elem in coll"
  [pos coll]
  (vec (concat (subvec (vec coll) 0 pos) 
               (subvec (vec coll) (inc pos)))))
