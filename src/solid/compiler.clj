(ns solid.compiler
  (:require [clojure.string :as str]))

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
  (if (map? (:class attrs))
    (let [class-list (reduce-kv #(assoc %1 %2 `(fn [] ~%3))
                                {} (:class attrs))]
      (-> attrs
          (dissoc :class)
          (assoc :class-list class-list)))
    attrs))

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
                      (update :style #(if (map? %) (to-js %) `(solid.compiler/interpret-style-map ~%)))
                      compile-directives
                      compile-class
                      camel-case-keys
                      to-js)]
        (into [attrs] children))
      args)))
