(ns entitydb.util)

(defn vec-remove
  "remove elem in coll"
  [pos coll]
  (vec (concat (subvec (vec coll) 0 pos) 
               (subvec (vec coll) (inc pos)))))

(defn nth-vals [a i m]
  "Collect all nested values from map m in accumulator a up to i level deep"
  (if (and (map? m) (> i 0))
    (reduce into a (map (fn [v] (nth-vals a (dec i) v)) (vals m)))
    (conj a m)))

(defn vals-three-levels-deep [m]
  "Collect all nested values from map m three levels deep"
  (for [[_ v] m
        [_ v'] v
        [_ v''] v'] 
    v''))

(defn log
  ([args] (log "" args))
  ([text args] (js/console.log text (with-out-str (cljs.pprint/pprint args)))))
