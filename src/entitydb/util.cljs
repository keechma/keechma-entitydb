(ns entitydb.util)

(defn vec-remove
  "remove elem in coll"
  [pos coll]
  (vec (concat (subvec (vec coll) 0 pos) 
               (subvec (vec coll) (inc pos)))))

(defn nth-vals [a i m]
  (if (and (map? m) (> i 0))
    (reduce into a (map (fn [v] (nth-vals a (dec i) v)) (vals m)))
    (conj a m)))
