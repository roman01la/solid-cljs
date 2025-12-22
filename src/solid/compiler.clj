(ns solid.compiler
  (:require [clojure.string :as str]))

(defn literal?
  "Returns true if the expression is a compile-time literal that cannot be reactive.
  Literals include: strings, numbers, keywords, nil, booleans."
  [expr]
  (or (string? expr)
      (number? expr)
      (keyword? expr)
      (nil? expr)
      (true? expr)
      (false? expr)))

(defn- wrap-reactive
  "Wraps an expression in a function unless it's a literal.
  This ensures fine-grained reactivity in Solid.js - only the specific
  attribute will update when signals change, not the entire element.
  Mirrors the behavior of Solid's JSX compiler."
  [expr]
  (if (literal? expr)
    expr
    `(fn [] ~expr)))

(defn- wrap-reactive-attrs
  "Wraps attribute values in functions for fine-grained reactivity.
  Excludes:
  - Event handlers (on-* attributes) since they are already functions
  - :ref attribute since it's handled specially by compile-directives
  - :style attribute since it has special map handling with its own reactivity
  - :class attribute since it has special map handling with its own reactivity
  - Literal values (strings, numbers, keywords, nil, booleans)"
  [attrs]
  (if (map? attrs)
    (reduce-kv
      (fn [m k v]
        (let [k-name (if (keyword? k) (name k) (str k))]
          (assoc m k
                 (if (or (str/starts-with? k-name "on-")
                         (= k :ref)
                         (= k :style)
                         (= k :class))
                   v  ; Don't wrap these - they have special handling
                   (wrap-reactive v)))))
      {}
      attrs)
    attrs))

(defn- wrap-reactive-style-value
  "Wraps style map values in functions for fine-grained reactivity."
  [v]
  (if (literal? v)
    v
    `(fn [] ~v)))

(defn- wrap-reactive-style
  "Wraps values inside a style map for fine-grained reactivity."
  [style]
  (if (map? style)
    (reduce-kv
      (fn [m k v]
        (assoc m k (wrap-reactive-style-value v)))
      {}
      style)
    style))

(defmulti to-js
          (fn [x]
            (cond
              (map? x) :map
              (vector? x) :vector
              (keyword? x) :keyword
              :else (class x))))

(defn- to-js-map [m shallow?]
  (cond
    (nil? m) nil
    (empty? m) `(cljs.core/js-obj)
    :else (let [kvs-str (->> (mapv to-js (keys m))
                             (mapv #(-> (str \' % "':~{}")))
                             (interpose ",")
                             (apply str))]
            (vary-meta
              (list* 'js* (str "{" kvs-str "}")
                     (if shallow?
                       (vals m)
                       (mapv to-js (vals m))))
              assoc :tag 'object))))

(defmethod to-js :keyword [x] (name x))

(defmethod to-js :map [m] (to-js-map m false))

(defmethod to-js :vector [xs]
  (apply list 'cljs.core/array (mapv to-js xs)))

(defmethod to-js :default [x] x)

(defn- camel-case-dom
  "Turns kebab-case keyword into camel-case keyword,
  kebab-cased DOM attributes aria-* and data-* are not converted"
  [k]
  (if (keyword? k)
    (let [[first-word & words] (str/split (name k) #"-")]
      (if (or (empty? words)
              (= "aria" first-word)
              (= "data" first-word))
        k
        (-> (map str/capitalize words)
            (conj first-word)
            str/join
            keyword)))
    k))

(defn- camel-case-keys
  "Takes map of attributes and returns same map with camel-cased keys"
  [m]
  (if (map? m)
    (reduce-kv #(assoc %1 (camel-case-dom %2) %3) {} m)
    m))

(defn- compile-class [attrs]
  (let [class-val (:class attrs)]
    (cond
      ;; Map of class -> boolean expression (conditional classes)
      (map? class-val)
      (let [class-list (reduce-kv #(assoc %1 %2 `(fn [] ~%3))
                                  {} class-val)]
        (-> attrs
            (dissoc :class)
            (assoc :class-list class-list)))
      
      ;; Vector of class names: [:foo :bar "baz"] -> "foo bar baz"
      (vector? class-val)
      (let [class-str (->> class-val
                           (map #(if (keyword? %) (name %) (str %)))
                           (str/join " "))]
        (assoc attrs :class class-str))
      
      ;; Otherwise leave as-is (string, keyword, or expression)
      :else attrs)))

(defn- compile-directives [attrs]
  (let [ref (:ref attrs)
        directives (filterv (fn [[k _]] (symbol? k)) attrs)]
    (-> (into {} (filter (fn [[k _]] (not (symbol? k))) attrs))
        (assoc :ref `(fn [el#]
                       (let [ref# (or ~ref identity)]
                         (ref# el#)
                         (doseq [[f# v#] ~directives]
                           (f# el# v#))))))))

(defn compile-attrs [args]
  (let [[attrs & children] args]
    (if (map? attrs)
      (let [attrs (-> attrs
                      wrap-reactive-attrs
                      (update :style #(if (map? %)
                                        (to-js (wrap-reactive-style %))
                                        `(solid.compiler/interpret-style-map ~%)))
                      compile-directives
                      compile-class
                      camel-case-keys
                      to-js)]
        (into [attrs] children))
      args)))
