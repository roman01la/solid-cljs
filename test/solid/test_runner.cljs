(ns solid.test-runner
  (:require [cljs.test :refer [run-tests]]
            [solid.core-test]))

(defn ^:export init []
  (run-tests 'solid.core-test))
