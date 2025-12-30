(ns vitest.basic-component
  (:require-macros [solid.core])
  (:require
    [applied-science.js-interop :as j]
    [solid.core :as s :refer [$ defui]]
    ["vitest/browser" :refer [page]]
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

(defui label-with-attrs [{:keys [data-testid children]}  attrs]
  ($ :label {:data-testid data-testid} children))

(test
  "Component receives map attributes as a runtime variable, wrapped in vector literal",
  (fn []
    (j/let [title "the label"
            input ($ :input)
            attrs {:data-testid "label-with-attrs"}
            ^:js {:keys [baseElement]} (render #($ label-with-attrs attrs title input))
            screen (.. page (elementLocator baseElement))
            label (.. screen (getByTestId "label-with-attrs"))]
      (->
        (.. expect (element (.. label (getByText "the label"))) toBeInTheDocument)))))

(defui simple-label [{:keys [children]}  attrs]
  ($ :label {:data-testid "simple-label"} children))

(test
  "Component without any props or children",
  (fn []
    (j/let [^:js {:keys [baseElement]} (render #($ simple-label))
            screen (.. page (elementLocator baseElement))]
      (->
        (.. expect (element (.. screen (getByTestId "simple-label"))) toBeInTheDocument)))))

(test
  "Component with a single variable-bound child-node",
  (fn []
    (j/let [title "single child node"
            ^:js {:keys [baseElement]} (render #($ :<> ($ simple-label title)))
            screen (.. page (elementLocator baseElement))]
      (->
        (.. expect (element (.. screen (getByText "single child node"))) toBeInTheDocument)))))

(defui button-with-reactive-prop [{:keys [label sig unique-id]}]
  ($ :label label
     ($ :button {:on-click #(swap! sig + 1)
                 :reactive-prop @sig
                 :data-testid unique-id}
        unique-id "Count: " @sig)))

(test
  "Component receives signal as prop and updates accordingly",
  (fn []
    (j/let [sig (s/signal 0)
            unique-id (s/uid)
            ^:js {:keys [baseElement]} (render #($ button-with-reactive-prop
                                                   {:label "Button"
                                                    :unique-id unique-id
                                                    :sig sig}))
            screen (.. page (elementLocator baseElement))
            incrementButton (.. screen (getByTestId unique-id))]
      (->
        (.. expect (element (.. screen (getByText (str unique-id "Count: 0")))) toBeInTheDocument)
        (.then (fn [] (.click incrementButton)))
        (.then (fn [] (.. expect (element (.. screen (getByText (str unique-id "Count: 1")))) toBeInTheDocument)))))))
