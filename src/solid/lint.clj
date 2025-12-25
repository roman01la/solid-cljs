(ns solid.lint
  "Compile-time linting for Solid reactivity rules.
  Helps developers avoid common reactivity mistakes.
  
  Integrates with ClojureScript analyzer for proper error reporting."
  (:require [clojure.walk :as walk]
            [clojure.string :as str]
            [cljs.analyzer :as ana]))

;; -----------------------------------------------------------------------------
;; Configuration
;; -----------------------------------------------------------------------------

(def ^:dynamic *lint-enabled* true)
(def ^:dynamic *component-context* nil)

;; -----------------------------------------------------------------------------
;; Register Custom Warnings with ClojureScript Analyzer
;; Enable our warnings by binding them when calling ana/warning
;; -----------------------------------------------------------------------------

(def custom-warnings
  {::untracked-reactive true
   ::untracked-event-handler true
   ::async-in-tracked-scope true
   ::signal-not-derefed true
   ::props-destructured-early true})

;; -----------------------------------------------------------------------------
;; Error Message Definitions
;; These integrate with ClojureScript's analyzer warning system
;; -----------------------------------------------------------------------------

(defmethod ana/error-message ::untracked-reactive [_ {:keys [form component-name]}]
  (str "Reactive read `" (pr-str form) "` outside tracked scope in component '" component-name "'.\n"
       "This value will be read once and won't update.\n"
       "Move inside s/effect, s/computed, s/memo, or use in ($ ...) element attributes/children.\n"
       "Read https://docs.solidjs.com/concepts/intro-to-reactivity for more context."))

(defmethod ana/error-message ::untracked-event-handler [_ {:keys [form attr tag]}]
  (str "Reactive read in event handler :" attr " on <" tag ">.\n"
       "Event handlers are called imperatively, not reactively.\n"
       "The reactive value will be captured once when the component mounts.\n"
       "If intentional, wrap in a function: #(do " (pr-str form) " ...)"))

(defmethod ana/error-message ::async-in-tracked-scope [_ {:keys [form effect-type]}]
  (str "Async operation in tracked scope `" effect-type "`.\n"
       "Async operations (go blocks, Promises) break reactivity tracking.\n"
       "Use s/resource for async data fetching instead.\n"
       "Read https://docs.solidjs.com/reference/basic-reactivity/create-resource for more context."))

(defmethod ana/error-message ::signal-not-derefed [_ {:keys [form]}]
  (str "Signal `" (pr-str form) "` used without dereferencing.\n"
       "Signals must be dereferenced with @ to read their value.\n"
       "Did you mean `@" (pr-str form) "`?"))

(defmethod ana/error-message ::props-destructured-early [_ {:keys [prop-name]}]
  (str "Props destructuring for `" prop-name "` loses reactivity.\n"
       "Access props directly in ($ ...) elements or use inside tracked scopes.\n"
       "Read https://docs.solidjs.com/concepts/components/props for more context."))

;; -----------------------------------------------------------------------------
;; Tracked Scopes
;; These are contexts where reactive reads (deref) are allowed
;; -----------------------------------------------------------------------------

(def tracked-scope-heads
  "Symbols that create tracked scopes where reactive reads are valid."
  #{'solid.core/effect 's/effect 'effect
    'solid.core/computed 's/computed 'computed
    'solid.core/memo 's/memo 'memo
    'solid.core/reaction 's/reaction 'reaction
    'solid.core/render-effect 's/render-effect 'render-effect
    'solid.core/for 's/for
    'solid.core/index 's/index
    'solid.core/if 's/if
    'solid.core/when 's/when
    'solid.core/if-let 's/if-let
    'solid.core/when-let 's/when-let
    'solid.core/if-some 's/if-some
    'solid.core/when-some 's/when-some
    'solid.core/cond 's/cond
    'solid.core/case 's/case
    'solid.core/or 's/or
    'solid.core/some-> 's/some->
    'solid.core/some->> 's/some->>
    'solid.core/batch 's/batch
    ;; The $ macro creates tracked scopes for element expressions
    'solid.core/$ 's/$ '$})

(def untracked-but-allowed-heads
  "Contexts where reactive reads won't update (called once), but are allowed.
  These are lifecycle hooks, timers, event handlers, etc."
  #{'solid.core/on-mount 's/on-mount 'on-mount
    'solid.core/on-cleanup 's/on-cleanup 'on-cleanup
    'js/setTimeout 'js/setInterval 'js/requestAnimationFrame
    'js/requestIdleCallback})

;; -----------------------------------------------------------------------------
;; AST Analysis Utilities
;; -----------------------------------------------------------------------------

(defn deref-form?
  "Returns true if form is a deref: (deref x), (clojure.core/deref x), @x"
  [form]
  (and (seq? form)
       (let [head (first form)]
         (or (= head 'deref)
             (= head 'clojure.core/deref)
             (= head 'cljs.core/deref)))))

(defn fn-form?
  "Returns true if form is a function literal."
  [form]
  (and (seq? form)
       (let [head (first form)]
         (or (= head 'fn)
             (= head 'fn*)))))

(defn async-form?
  "Returns true if form appears to be async (go block, Promise, async fn)."
  [form]
  (and (seq? form)
       (let [head (first form)]
         (or (= head 'cljs.core.async/go)
             (= head 'cljs.core.async/go-loop)
             (= head 'go)
             (= head 'go-loop)
             (= head 'js/Promise.)
             (= head '.then)))))

(defn tracked-scope?
  "Returns true if the form creates a tracked reactive scope."
  [form]
  (and (seq? form)
       (contains? tracked-scope-heads (first form))))

(defn untracked-allowed?
  "Returns true if the form is untracked but reactive reads are acceptable
  (e.g., lifecycle hooks that intentionally read once)."
  [form]
  (and (seq? form)
       (contains? untracked-but-allowed-heads (first form))))

(defn form->loc
  "Extracts location metadata from a form.
  Tries the form itself first, then looks at nested forms for metadata."
  [form]
  (let [loc (select-keys (meta form) [:line :column])]
    (if (and (:line loc) (:column loc))
      loc
      ;; Try to find location from nested forms
      (when (sequential? form)
        (some (fn [child]
                (let [child-loc (select-keys (meta child) [:line :column])]
                  (when (and (:line child-loc) (:column child-loc))
                    child-loc)))
              form)))))

;; -----------------------------------------------------------------------------
;; Error Collection
;; -----------------------------------------------------------------------------

(defn add-error!
  "Adds an error to the current component context."
  ([error-type form]
   (add-error! error-type form {}))
  ([error-type form opts]
   (when *component-context*
     (swap! *component-context* update :errors conj
            (merge {:type error-type
                    :form form
                    :loc (form->loc form)}
                   opts)))))

(defn report-errors!
  "Reports all collected errors via the ClojureScript analyzer."
  [env]
  (when *component-context*
    (let [{:keys [errors]} @*component-context*
          ;; Ensure env has required keys for ana/warning
          base-env (or env {})]
      (doseq [{:keys [type loc] :as error} errors]
        ;; Override base-env line/column with the specific error location
        ;; This ensures the warning points to the actual problem, not the component start
        (let [warning-env (if (and (:line loc) (:column loc))
                            (assoc base-env :line (:line loc) :column (:column loc))
                            base-env)]
          ;; Bind our custom warnings to enable them, then call ana/warning
          (try
            (binding [ana/*cljs-warnings* (merge ana/*cljs-warnings* custom-warnings)]
              (ana/warning type warning-env error))
            (catch Exception e
              ;; Fallback to stderr if analyzer warning fails
              (binding [*out* *err*]
                (println (str "[solid-cljs WARNING] " (name type)))
                (println (str "  " (ana/error-message type error)))
                (when-let [{:keys [line column]} loc]
                  (println (str "  at line " line ", column " column)))))))))))

;; -----------------------------------------------------------------------------
;; Lint Rules
;; -----------------------------------------------------------------------------

(defn collect-derefs
  "Walks form and collects all deref forms that are NOT inside a nested
  tracked scope or function."
  [form]
  (let [derefs (atom [])]
    (letfn [(walk-form [f in-tracked?]
              (cond
                ;; Found a deref
                (deref-form? f)
                (do
                  (when-not in-tracked?
                    (swap! derefs conj f))
                  f)
                
                ;; Entering a tracked scope
                (tracked-scope? f)
                (do
                  (doall (map #(walk-form % true) (rest f)))
                  f)
                
                ;; Entering an untracked-but-allowed scope
                (untracked-allowed? f)
                f ; Don't recurse - these are fine
                
                ;; Function form - don't analyze inside (it's a new scope)
                (fn-form? f)
                f
                
                ;; Recurse into sequences
                (seq? f)
                (do
                  (doall (map #(walk-form % in-tracked?) f))
                  f)
                
                ;; Recurse into vectors
                (vector? f)
                (do
                  (doall (map #(walk-form % in-tracked?) f))
                  f)
                
                ;; Recurse into maps
                (map? f)
                (do
                  (doall (map #(walk-form % in-tracked?) (vals f)))
                  f)
                
                :else f))]
      (walk-form form false))
    @derefs))

(defn check-async-in-tracked
  "Checks if async forms are used inside tracked scopes."
  [effect-type body]
  (let [issues (atom [])]
    (walk/prewalk
      (fn [f]
        (when (async-form? f)
          (swap! issues conj {:rule ::async-in-tracked-scope
                              :form f
                              :effect-type effect-type}))
        f)
      body)
    @issues))

;; -----------------------------------------------------------------------------
;; Public API for Macros
;; -----------------------------------------------------------------------------

(defn lint-component-body!
  "Lints a component body for reactivity issues.
  Call from defui macro."
  [component-name body env]
  (when *lint-enabled*
    (binding [*component-context* (atom {:errors []})]
      ;; body is a sequence of forms, collect derefs from all of them
      (doseq [form body]
        (let [untracked-derefs (collect-derefs form)]
          (doseq [deref-form untracked-derefs]
            (add-error! ::untracked-reactive deref-form
                        {:component-name component-name}))))
      (report-errors! env))))

(defn lint-effect-body!
  "Lints an effect body for issues like async operations."
  [effect-type body env]
  (when *lint-enabled*
    (binding [*component-context* (atom {:errors []})]
      ;; body is a sequence of forms
      (doseq [form body]
        (let [async-issues (check-async-in-tracked effect-type form)]
          (doseq [{:keys [form effect-type]} async-issues]
            (add-error! ::async-in-tracked-scope form
                        {:effect-type effect-type}))))
      (report-errors! env))))

(defn lint-element-attrs!
  "Lints element attributes for reactivity issues.
  E.g., warns if reactive expressions are in untracked on-* handlers."
  [tag attrs env]
  (when (and *lint-enabled* (map? attrs))
    (binding [*component-context* (atom {:errors []})]
      (doseq [[k v] attrs]
        (let [k-name (if (keyword? k) (name k) (str k))]
          ;; Check for derefs in event handlers
          (when (and (str/starts-with? k-name "on-")
                     (not (fn-form? v))
                     (some deref-form? (tree-seq coll? seq v)))
            (add-error! ::untracked-event-handler v
                        {:attr k-name
                         :tag (name tag)
                         :form v}))))
      (report-errors! env))))

