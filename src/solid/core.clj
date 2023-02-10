(ns solid.core
  (:refer-clojure :exclude [if when for cond try])
  (:require
    [clojure.core :as core]
    [clojure.string :as str]))

(defmacro defui [sym [props] & body]
  `(defn ~sym [props#]
     (let [~(or props (gensym "props")) (solid.core/-props props#)]
       ~@body)))

(defmulti to-js
          (fn [x]
            (core/cond
              (map? x) :map
              (vector? x) :vector
              (keyword? x) :keyword
              :else (class x))))

(defn- to-js-map [m shallow?]
  (core/cond
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

(defn- compile-attrs [args]
  (let [[attrs & children] args]
    (if (map? attrs)
      (let [attrs (-> attrs
                      (update :style to-js)
                      compile-directives
                      compile-class
                      camel-case-keys
                      to-js)]
        (into [attrs] children))
      args)))

(defn- wrap-children [children]
  (core/for [x children]
    `(fn [] ~x)))

(defmacro $ [tag & args]
  (if (keyword? tag)
    (let [[attrs & children] (compile-attrs args)]
      `(create-element ~(name tag) ~attrs ~@(wrap-children children)))
    (let [[attrs & children] args]
      (if (map? attrs)
        `(create-element ~tag (cljs.core/js-obj "props" ~attrs) ~@(wrap-children children))
        `(create-element ~tag ~@(wrap-children args))))))

(defmacro effect [& body]
  `(-effect (fn [] ~@body)))

(defmacro on-mount [& body]
  `(-on-mount (fn [] ~@body)))

(defmacro on-cleanup [& body]
  `(-on-cleanup (fn [] ~@body)))

(defmacro memo [& body]
  `(-memo (fn [] ~@body)))

(defmacro if
  ([test then]
   `(solid.core/if ~test ~then nil))
  ([test then else]
   `(-show (fn [] ~test) ~then ~else)))

(defmacro when [test then]
  `(solid.core/if ~test ~then))

(defmacro for [[[v idx] expr] body]
  `(-for (fn [] (js/Array.from ~expr))
         (fn [~v idx#]
           (let [~idx (-wrap idx#)]
             ~body))))

(defmacro index [[[v idx] expr] body]
  `(-index (fn [] (js/Array.from ~expr))
           (fn [v# ~idx]
             (let [~v (-wrap v#)]
               ~body))))

(defmacro cond [& pairs]
  (let [else (last pairs)]
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

(defmacro try [exprs]
  (let [[_ e & body] (last exprs)
        children (butlast exprs)]
    `(-error-boundary (fn [~e] ~@body) ~@children)))

(defmacro batch [& body]
  `(-batch (fn [] ~@body)))

(defmacro computed [& body]
  `(-computed (fn [] ~@body)))

(defmacro reaction [& body]
  `(-reaction (fn [] ~@body)))
