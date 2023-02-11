(ns solid.core
  (:refer-clojure :exclude [if when for cond try])
  (:require [clojure.core :as core]
            [solid.compiler :as sc]))

(defmacro defui [sym [props] & body]
  `(defn ~sym [props#]
     (let [~(or props (gensym "props")) (solid.core/-props props#)]
       ~@body)))

(defn- wrap-children [children]
  (core/for [x children]
    `(fn [] ~x)))

(defmacro $ [tag & args]
  (if (keyword? tag)
    (let [[attrs & children] (sc/compile-attrs args)]
      `(create-element ~(name tag) ~attrs ~@(wrap-children children)))
    (let [[attrs & children] args]
      (if (map? attrs)
        `(create-element ~tag (cljs.core/js-obj "props" ~attrs) ~@(wrap-children children))
        `(create-element ~tag ~@(wrap-children args))))))

(defmacro if
  ([test then]
   `(solid.core/if ~test ~then nil))
  ([test then else]
   `(-show (fn [] ~test) ~then ~else)))

(defmacro when [test then]
  `(solid.core/if ~test ~then))

(defmacro for
  "Solid's `For` component in Clojure’s `for` syntax
  ```clojure
  (s/for [[x idx] xs]
    ($ :li x))
  ```"
  [[[v idx] expr] body]
  `(-for (fn [] (js/Array.from ~expr))
         (fn [~v idx#]
           (let [~idx (-wrap idx#)]
             ~body))))

(defmacro index
  "Solid's `Index` component in Clojure’s `for` syntax
  ```clojure
  (s/index [[x idx] xs]
    ($ :li @x))
  ```"
  [[[v idx] expr] body]
  `(-index (fn [] (js/Array.from ~expr))
           (fn [v# ~idx]
             (let [~v (-wrap v#)]
               ~body))))

(defmacro cond
  "Solid's `Switch` and `Match` components in Clojure’s `cond` syntax
  ```clojure
  (s/cond
    (= x 1) ($ button {})
    (= y 2) ($ link {})
    :else ($ text {}))
  ```"
  [& pairs]
  (let [else (last pairs)
        marker (last (butlast pairs))]
    (assert (= :else marker) "The fallback condition has to be marked with :else")
    `(-switch ~else
              ~(->> pairs
                    (drop-last 2)
                    (partition 2)
                    (mapv (fn [[test then]]
                            [`(fn [] ~test) then]))))))

(defmacro dynamic [expr]
  `(-dynamic (fn [] ~expr)))

(defmacro children [& expr]
  `(-children (fn [] ~@expr)))

(defmacro try
  "Solid's `ErrorBoundary` component in Clojure’s `try...catch` syntax
  ```clojure
  (s/try
    ($ my-component {})
    (catch err
      ($ error-view {})))
  ```"
  [& exprs]
  (let [[marker e & body] (last exprs)
        children (butlast exprs)]
    (assert (= 'catch marker) "should have `catch` marker")
    `(-error-boundary (fn [~e] ~@body) ~@children)))

(defmacro batch [& body]
  `(-batch (fn [] ~@body)))

(defmacro computed [& body]
  `(-computed (fn [] ~@body)))

(defmacro reaction [& body]
  `(-reaction (fn [] ~@body)))

(defmacro lazy [& body]
  `(-lazy (fn [] ~@body)))
