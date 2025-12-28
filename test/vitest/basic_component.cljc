(ns vitest.basic-component
  (:require-macros [solid.core])
  (:require
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

(defui button-with-signal [{:keys [label]}]
  (let [sig (s/signal 0)]
    ($ :label {:class "test"} label
       ($ :button {:on-click #(swap! sig + 1)} "Count: " @sig))))

(test
  "Component signal updates on user event",
  (fn []
    (j/let [^:js {:keys [baseElement]} (render #($ button-with-signal {:label "Button"}))
            screen (.. page (elementLocator baseElement))
            incrementButton (.. screen (getByRole "button"))]
      (->
        (.. expect (element (.. screen (getByText "Count: 0"))) toBeInTheDocument)
        (.then (fn [] (.click incrementButton)))
        (.then (fn [] (.. expect (element (.. screen (getByText "Count: 1"))) toBeInTheDocument)))))))

