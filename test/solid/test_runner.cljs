(ns solid.test-runner
  (:require [cljs.test :refer [run-tests]]
            [solid.core-test]))

(defn main [& args]
  (run-tests 'solid.core-test)
  ; `createDeferred` will hang the thread for some odd reason,
  ; use process.exit to finish the run.
  ; - https://github.com/solidjs/solid/issues/2570
  (js/process.exit 0))
