(ns solid.core
  (:require-macros [solid.core])
  (:require ["solid-js" :as solid]
            ["solid-js/web" :as sw]
            ["solid-js/h" :as h]
            [cljs-bean.core :as bean]
            [clojure.string :as str]))

(def unit-less-css-props
  #{"animationIterationCount" "aspectRatio","borderImageOutset","borderImageSlice",
    "borderImageWidth","boxFlex","boxFlexGroup","boxOrdinalGroup","columnCount",
    "columns","flex","flexGrow","flexPositive","flexShrink","flexNegative","flexOrder",
    "gridArea","gridRow","gridRowEnd","gridRowSpan","gridRowStart","gridColumn","gridColumnEnd",
    "gridColumnSpan","gridColumnStart","fontWeight","lineClamp","lineHeight","opacity","order",
    "orphans","tabSize","widows","zIndex","zoom","fillOpacity","floodOpacity","stopOpacity",
    "strokeDasharray","strokeDashoffset","strokeMiterlimit","strokeOpacity","strokeWidth"})

(defn- with-numeric-props [^js props]
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

(defn create-element [tag & args]
  (when (string? tag) (with-numeric-props (first args)))
  (apply h tag args))

(defn -wrap [value]
  (reify
    IDeref
    (-deref [this]
      (value))))

(defn signal [v]
  (let [[value set-value] (solid/createSignal v)]
    (reify
      IDeref
      (-deref [this]
        (value))
      IReset
      (-reset! [this v]
        (set-value v))
      ISwap
      (-swap! [this f]
        (-reset! this (f (value))))
      (-swap! [this f a]
        (-reset! this (f (value) a)))
      (-swap! [this f a b]
        (-reset! this (f (value) a b)))
      (-swap! [this f a b xs]
        (-reset! this (apply f (value) a b xs))))))

(defn ref []
  (let [value (volatile! nil)
        f #(vreset! value %)]
    (specify! f
      IDeref
      (-deref [this]
        @value))))

(defn -effect [f]
  (solid/createEffect f))

(defn -on-mount [f]
  (solid/onMount f))

(defn -on-cleanup [f]
  (solid/onCleanup f))

(defn -memo [f]
  (solid/createMemo f))

(defn -show [test then else]
  (h solid/Show
     #js {:when test
          :fallback else}
     then))

(defn -for [coll body]
  (h solid/For
     #js {:each coll}
     body))

(defn -index [coll body]
  (h solid/Index
     #js {:each coll}
     body))

(defn -switch [else pairs]
  (apply h solid/Switch
     #js {:fallback else}
     (for [[test then] pairs]
       (h solid/Match
          #js {:when test}
          then))))

(defn -dynamic [f]
  (h sw/Dynamic #js {:component f}))

(defn -children [f]
  (solid/children f))

(defn -props [^js props]
  (let [clj-props (.-props props)
        children (-children #(.-children props))]
    (cond-> clj-props
            children (assoc :children children))))

(defn -error-boundary [fallback & children]
  (apply h solid/ErrorBoundary
     #js {:fallback fallback}
     children))

(defn -batch [f]
  (solid/batch f))

(defn -computed [f]
  (solid/createComputed f))

(defn -reaction [f]
  (solid/createReaction f))

(def uid solid/createUniqueId)

(def portal sw/Portal)

(def error-boundary solid/ErrorBoundary)

(def untrack solid/untrack)

(def resource solid/createResource)

(defn render [el node]
  (sw/render (constantly el) node))

(defn render-to-string [el {:keys [nonce render-id]}]
  (sw/renderToString (fn [] el) #js {:nonce nonce :renderId render-id}))

(defn render-to-string-async [el {:keys [timeout-ms nonce render-id]}]
  (sw/renderToStringAsync (fn [] el) #js {:timeoutMs timeout-ms
                                          :nonce nonce
                                          :renderId render-id}))

(defn render-to-stream [el {:keys [nonce render-id on-complete-shell on-complete-all]}]
  (sw/renderToStream (fn [] el) #js {:nonce nonce
                                     :renderId render-id
                                     :onCompleteShell on-complete-shell
                                     :onCompleteAll on-complete-all}))

(defn pipe [stream res]
  (.pipe ^js stream res))

(defn pipe-to [stream res]
  (.pipeTo ^js stream res))

(defn server? []
  (sw/isServer))

(defn dev? []
  solid/DEV)




(def ^:private cc-regexp (js/RegExp. "-(\\w)" "g"))

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
