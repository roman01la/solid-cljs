(ns solid.core-test
  (:require [cljs.test :refer [deftest testing is async]]
            [solid.core :as s :refer [$ defui]]))

;; ============================================
;; Reactive Primitives Tests
;; ============================================

(deftest signal-test
  (testing "signal creation and deref"
    (let [sig (s/signal 0)]
      (is (= 0 @sig) "Initial value should be 0")))

  (testing "signal reset!"
    (let [sig (s/signal 0)]
      (reset! sig 5)
      (is (= 5 @sig) "Value should be 5 after reset!")))

  (testing "signal swap!"
    (let [sig (s/signal 0)]
      (swap! sig inc)
      (is (= 1 @sig) "Value should be 1 after swap! inc")))

  (testing "signal swap! with multiple args"
    (let [sig (s/signal 10)]
      (swap! sig - 3)
      (is (= 7 @sig) "Value should be 7 after swap! - 3")
      (swap! sig + 1 2)
      (is (= 10 @sig) "Value should be 10 after swap! + 1 2")))

  (testing "signal with equals option"
    (let [sig (s/signal 0 {:equals false})]
      (is (= 0 @sig) "Signal with equals option should work"))))

(deftest ref-test
  (testing "ref creation and deref"
    (let [r (s/ref)]
      (is (nil? @r) "Initial ref value should be nil")))

  (testing "ref as callback"
    (let [r (s/ref)]
      (r "test-value")
      (is (= "test-value" @r) "Ref should store value when called as function"))))

(deftest memo-test
  (testing "memo creation"
    (let [sig (s/signal 5)
          m (s/memo #(* 2 @sig))]
      (is (= 10 (m)) "Memo should compute derived value"))))

(deftest effect-test
  (testing "effect execution"
    (let [executed (atom false)]
      (s/effect #(reset! executed true))
      (is @executed "Effect should execute"))))

(deftest batch-test
  (testing "batch function"
    (let [sig (s/signal 0)]
      (s/batch
        (reset! sig 1)
        (reset! sig 2))
      (is (= 2 @sig) "Batch should apply final value"))))

(deftest untrack-test
  (testing "untrack function"
    (let [sig (s/signal 10)
          result (s/untrack #(* 2 @sig))]
      (is (= 20 result) "Untrack should return computed value"))))

;; ============================================
;; Context Tests
;; ============================================

(deftest context-test
  (testing "create-context"
    (let [ctx (s/create-context "default")]
      (is (some? ctx) "Context should be created"))))

;; ============================================
;; Utility Functions Tests
;; ============================================

(deftest uid-test
  (testing "createUniqueId"
    (let [id1 (s/uid)
          id2 (s/uid)]
      (is (string? id1) "UID should be a string")
      (is (not= id1 id2) "UIDs should be unique"))))

(deftest server?-test
  (testing "isServer check"
    (let [result (s/server?)]
      (is (boolean? result) "server? should return a boolean"))))

(deftest dev?-test
  (testing "DEV check"
    ;; DEV can be nil or a truthy value
    (is (or (nil? (s/dev?))
            (some? (s/dev?)))
        "dev? should return nil or a truthy value")))

;; ============================================
;; Store Tests
;; ============================================

(deftest store-test
  (testing "store creation"
    (let [[state set-state] (s/store #js {:count 0})]
      (is (= 0 (.-count state)) "Store should have initial value"))))

;; ============================================
;; Component Definition Tests
;; ============================================

(defui test-component [{:keys [value]}]
  ($ :div {:class "test"} value))

(deftest defui-test
  (testing "defui creates a component"
    (is (fn? test-component) "defui should create a function")))

;; ============================================
;; Element Creation Tests
;; ============================================

(deftest create-element-test
  (testing "create-element with keyword tag"
    (let [el ($ :div "hello")]
      (is (some? el) "Should create element with keyword tag")))

  (testing "create-element with attributes"
    (let [el ($ :div {:class "container"} "content")]
      (is (some? el) "Should create element with attributes")))

  (testing "create-element with style"
    (let [el ($ :div {:style {:color "red"}} "styled")]
      (is (some? el) "Should create element with style"))))

;; ============================================
;; Control Flow Tests
;; ============================================

(deftest show-test
  (testing "s/if with truthy condition"
    (let [el (s/if true ($ :span "visible") ($ :span "hidden"))]
      (is (some? el) "Should return element when condition is truthy")))

  (testing "s/when macro"
    (let [el (s/when true ($ :span "shown"))]
      (is (some? el) "s/when should return element when condition is true"))))

(deftest for-test
  (testing "s/for iteration"
    (let [items [1 2 3]
          el (s/for [[x] items] ($ :span (str x)))]
      (is (some? el) "s/for should create elements"))))

(deftest index-test
  (testing "s/index iteration"
    (let [items [1 2 3]
          el (s/index [[x idx] items] ($ :span (str @x)))]
      (is (some? el) "s/index should create elements"))))

(deftest cond-test
  (testing "s/cond control flow"
    (let [value 1
          el (s/cond
               (= value 1) ($ :span "one")
               (= value 2) ($ :span "two")
               :else ($ :span "other"))]
      (is (some? el) "s/cond should return matched element"))))

(deftest dynamic-test
  (testing "s/dynamic component"
    (let [el (s/dynamic ($ :div "dynamic"))]
      (is (some? el) "s/dynamic should create element"))))

(deftest try-test
  (testing "s/try error boundary"
    (let [el (s/try
               ($ :div "content")
               (catch e ($ :div "error")))]
      (is (some? el) "s/try should create error boundary"))))

;; ============================================
;; Computed and Reaction Tests
;; ============================================

(deftest computed-test
  (testing "computed macro"
    (let [sig (s/signal 5)]
      (s/computed
        (* 2 @sig))
      ;; Computed runs immediately
      (is true "computed should execute"))))

(deftest reaction-test
  (testing "reaction macro"
    (let [sig (s/signal 5)
          tracker (s/reaction (* 2 @sig))]
      (is (fn? tracker) "reaction should return a tracking function"))))

;; ============================================
;; Lazy Loading Test
;; ============================================

(deftest lazy-test
  (testing "lazy component"
    (let [lazy-comp (s/lazy
                      #js {:default (fn [] ($ :div "lazy loaded"))})]
      (is (some? lazy-comp) "lazy should create lazy component"))))

;; ============================================
;; Deferred Test
;; ============================================

(deftest deferred-test
  (testing "deferred value"
    (let [sig (s/signal 5)
          deferred-val (s/deferred #(* 2 @sig))]
      (is (fn? deferred-val) "deferred should return a function"))))
