(ns solid.core
  (:require-macros [solid.core])
  (:require ["solid-js" :as solid]
            ["solid-js/web" :as sw]
            ["solid-js/store" :as store]
            ["solid-js/h" :as h]
            [solid.compiler :as sc]))

(defn create-element [tag & args]
  (when (string? tag) (sc/with-numeric-props (first args)))
  (apply h tag args))

(defn -wrap [value]
  (reify
    IDeref
    (-deref [this]
      (value))))

(defn signal
  ([v]
   (signal v nil))
  ([v {:keys [equals]}]
   (let [[value set-value] (solid/createSignal v #js {:equals equals})]
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
         (-reset! this (apply f (value) a b xs)))))))

(defn ref []
  (let [value (volatile! nil)
        f #(vreset! value %)]
    (specify! f
      IDeref
      (-deref [this]
        @value))))

(defn effect
  ([f]
   (solid/createEffect f))
  ([f v]
   (solid/createEffect f v)))

(defn render-effect
  ([f]
   (solid/createRenderEffect f))
  ([f v]
   (solid/createRenderEffect f v)))

(defn on-mount [f]
  (solid/onMount f))

(defn on-cleanup [f]
  (solid/onCleanup f))

(defn memo [f]
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

(defn -lazy [f]
  (solid/lazy f))

(defn -batch [f]
  (solid/batch f))

(defn -computed [f]
  (solid/createComputed f))

(defn -reaction [f]
  (solid/createReaction f))

(defn store [v]
  (store/createStore v))

(defn deferred
  ([f]
   (deferred f nil))
  ([f {:keys [timeout-ms equals]}]
   (solid/createDeferred f #js {:timeoutMs timeout-ms :equals equals})))

(def uid solid/createUniqueId)

(def portal sw/Portal)

(def error-boundary solid/ErrorBoundary)

(defn untrack [f]
  (solid/untrack f))

(defn resource
  ([f]
   (resource f nil))
  ([f {:keys [initial-value name defer-stream? ssr-load-from storage on-hydrated]}]
   (solid/createResource f #js {:initialValue initial-value
                                :name name
                                :deferStream defer-stream?
                                :ssrLoadFrom ssr-load-from
                                :storage storage
                                :onHydrated on-hydrated})))

(defn create-context [value]
  (solid/createContext value))

(defn use-context [context]
  (solid/useContext context))

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
