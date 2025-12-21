(ns solid.test-runner
  (:require [cljs.test :refer [run-tests]]
            [solid.core-test]))

(defn main [& args]
  (run-tests 'solid.core-test))
