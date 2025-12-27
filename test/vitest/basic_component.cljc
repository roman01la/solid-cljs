(ns vitest.basic-component
  (:require-macros [solid.core])
  (:require
    [shadow.cljs.modern :refer (js-await)]
    [applied-science.js-interop :as j]
    [solid.core :as s :refer [$ defui]]
    ["vitest/browser" :refer [page userEvent]]
    ["@solidjs/testing-library" :as st :refer [render]]
    ["vitest" :refer [expect test]]))

(defui simple-label-component [{:keys [label]}]
  ($ :label label))

(test
  "Render simple component with prop",
  (fn []
    (j/let [^:js {:keys [baseElement]} (render #($ simple-label-component {:label "Simple Test"}))
            screen (.. page (elementLocator baseElement))
            gotten (.. screen (getByText "Simple"))]
      (.. expect (element gotten) toBeInTheDocument))))
