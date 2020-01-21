(ns entitydb.config
  (:require
    [cljs.test :refer-macros [deftest is testing use-fixtures]]
    [entitydb.data :refer [schema user-1-data]]
    [entitydb.entitydb :as edb]
    [entitydb.query :as q]))

(use-fixtures :once
  {:before (fn [] (js/console.clear))})

(deftest clear-console
  (is true))
