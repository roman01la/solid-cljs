(ns solid.compiler
  (:require [clojure.string :as str]))

(def ^:private cc-regexp (js/RegExp. "-(\\w)" "g"))

;; Marker type for reactive prop values
;; This distinguishes reactive getters from regular callbacks
(deftype ReactiveProp [getter])

(defn reactive-prop
  "Wraps a function as a reactive prop getter.
  Used internally by the $ macro for component props."
  [f]
  (->ReactiveProp f))

(defn reactive-prop?
  "Returns true if x is a reactive prop wrapper."
  [x]
  (instance? ReactiveProp x))

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

(defn wrap-component-props
  "Same intention as the compile-time `wrap-component-props`, however this
  one is meant to be used at runtime."
  [attrs]
  (reduce-kv
    (fn [m k v]
      (assoc m k
             (cond
               (literal? v) v
               (fn? v) v
               :else (reactive-prop (fn [] v)))))
    {}
    attrs))

(defn- cc-fn [s]
  (str/upper-case (aget s 1)))

(defn- ^string dash-to-camel [^string name-str]
  (if (or (str/starts-with? name-str "aria-")
          (str/starts-with? name-str "data-"))
    name-str
    (.replace name-str cc-regexp cc-fn)))

(defonce prop-name-cache #js {})

(defn- cached-prop-name [k f]
  (if (keyword? k)
    (let [name-str (-name ^not-native k)]
      (if-some [k' (aget prop-name-cache name-str)]
        k'
        (let [v (f name-str)]
          (aset prop-name-cache name-str v)
          v)))
    k))

(declare convert-prop-value)

(defn- kv-conv [o k v]
  (aset o (cached-prop-name k dash-to-camel) (convert-prop-value v))
  o)

(defn- kv-style-conv [o k v]
  (aset o (cached-prop-name k name) (convert-prop-value v))
  o)

(defn- js-val? [x]
  (not (identical? "object" (goog/typeOf x))))

(defn- convert-prop-value [x]
  (cond
    (js-val? x) x
    (keyword? x) (-name ^not-native x)
    (map? x) (reduce-kv kv-conv #js {} x)
    (coll? x) (clj->js x)
    (ifn? x) #(apply x %&)
    :else (clj->js x)))

(defn- convert-style-prop-value [x]
  (cond
    (js-val? x) x
    (keyword? x) (-name ^not-native x)
    (map? x) (reduce-kv kv-style-conv #js {} x)
    (coll? x) (clj->js x)
    (ifn? x) #(apply x %&)
    :else (clj->js x)))

(defn interpret-style-map [m]
  (convert-style-prop-value m))

(def unit-less-css-props
  #{"animationIterationCount" "aspectRatio" "borderImageOutset"," borderImageSlice",
    "borderImageWidth"," boxFlex"," boxFlexGroup"," boxOrdinalGroup"," columnCount",
    "columns"," flex"," flexGrow"," flexPositive"," flexShrink"," flexNegative"," flexOrder",
    "gridArea"," gridRow"," gridRowEnd"," gridRowSpan"," gridRowStart"," gridColumn"," gridColumnEnd",
    "gridColumnSpan"," gridColumnStart"," fontWeight"," lineClamp"," lineHeight"," opacity"," order",
    "orphans"," tabSize"," widows"," zIndex"," zoom"," fillOpacity"," floodOpacity"," stopOpacity",
    "strokeDasharray"," strokeDashoffset"," strokeMiterlimit"," strokeOpacity"," strokeWidth"})

(defn with-numeric-props [^js props]
  (when (and (object? props) (object? (.-style props)))
    (reduce (fn [^js style key]
              (let [v (aget style key)]
                (when (and (number? v)
                           (not= 0 v)
                           (not (contains? unit-less-css-props key)))
                  (aset style key (str v "px")))
                style))
            (.-style props)
            (js/Object.keys (.-style props)))))
