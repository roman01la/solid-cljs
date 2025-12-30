# solid-cljs

[![Clojars Project](https://img.shields.io/clojars/v/com.github.roman01la/solid-cljs.svg)](https://clojars.org/com.github.roman01la/solid-cljs)

ClojureScript bindings to Solid. solid-cljs wraps Solid API documented at [docs.solidjs.com](https://docs.solidjs.com/).
Compatible with Solid 1.9

_Early alpha, unstable and highly experimental_

## How it works

Solid.js achieves high performance through fine-grained reactivity. Unlike React, Solid doesn't re-render entire components — instead, it updates only the specific DOM nodes that depend on changed values. To make this work, Solid's JSX compiler wraps reactive expressions in functions, creating precise reactive boundaries.

Since ClojureScript doesn't use JSX, solid-cljs provides macros that act as a compiler, automatically wrapping expressions to preserve Solid's fine-grained reactivity. This is why we provide Solid-specific versions of common Clojure macros like `if`, `when`, `or`, `if-let`, `if-some`, `some->`, etc.

**Why not just use Clojure's built-in macros?**

Using standard Clojure macros would break Solid's reactivity model:

```clojure
;; ❌ Using clojure.core/or - evaluates eagerly, loses reactivity
($ :h1 (or @title "Default"))

;; ✅ Using s/or - preserves reactive boundaries
($ :h1 (s/or @title "Default"))
```

The solid-cljs macros ensure that reactive expressions remain wrapped in functions, so Solid can track dependencies and update only what's necessary. This gives you Solid's performance benefits while writing idiomatic ClojureScript.

## Installation

1. `npm i solid-js -D`
2. `{:deps {com.github.roman01la/solid-cljs {:mvn/version "0.1.1"}}}`

## Example

```clojure
(ns app.core
  (:require [solid.core :as s :refer [$ defui]]))

(defui app []
  (let [value (s/signal 0)]
    (s/effect
      #(println "value:" @value))
    ($ :div
      ($ :button {:on-click #(swap! value inc)} "+")
      @value
      ($ :button {:on-click #(swap! value dec)} "-"))))

(s/render ($ app) (js/document.getElementById "root"))
```

## API

Functions and macros below are a part of `solid.core` namespace:

`defui` creates Solid component. Supports docstrings and metadata.

```clojure
(defui button
  "A styled button component."
  {:private true}
  [{:keys [on-click children]}]
  ($ :button {:on-click on-click} children))
```

`$` creates Solid element

```clojure
($ button {:on-click #(prn :pressed)} "press")
```

### Attributes

The `:class` attribute supports multiple formats:

```clojure
;; String
{:class "btn primary"}

;; Keyword
{:class :btn}

;; Vector of classes
{:class [:btn :btn-primary "active"]}

;; Map for conditional classes
{:class {:active @is-active?
         :disabled @is-disabled?}}
```
#### Passing attributes and child nodes

```clj
(defui custom-label [{:keys [input-name children]}  attrs]
  ($ :label {:for input-name} children))

;; ✅ Pass the attribute map as a literal
($ custom-label {:input-name "my-input"} "the label title")


(let [attrs {:input-name "my-input"} 
      children "the label title"] 
  ($ :<>                            ;; Fragment component
    ($ custom-label attrs children) ;; ✅ Pass the attribute map as a bound variable
    ($ custom-label children)       ;; ✅ Attribute map is optional, it may be elided entirely
    ($ custom-label)                ;; also valid
    ($ custom-label attrs)))        ;; also valid
```

Do not use bound variables for keyword tags:

```clj
;; ❌ Not supported for keyword tags,
;;    Only supported for `defui` tags
(let [attrs {:class "my-div"}
      title ($ "the label")
      input ($ :input)] 
  ($ :label attrs title input))

;; ✅ Be explicit with a map literal
(let [title ($ "the label")
      input ($ :input)] 
  ($ :label {:class "my-label"} title input))
;; <label class="my-label"> the label <input> </label>
```



### Rendering

Conditional rendering with `if` and `when`, via [<Show> component](https://docs.solidjs.com/reference/components/show)

```clojure
(s/if test ... ...)

(s/when test ...)
```

Conditional rendering with bindings via `if-let` and `when-let`:

```clojure
(s/if-let [user @current-user]
  ($ :div "Hello, " (:name user))
  ($ :div "Please log in"))

(s/when-let [user @current-user]
  ($ :div "Hello, " (:name user)))
```

Nil-checking (not falsiness) with `if-some` and `when-some`. Unlike `if-let`, these render for falsy values like `false` or `0`:

```clojure
(s/if-some [count @item-count]
  ($ :span count " items")  ;; shows "0 items" when count is 0
  ($ :span "Loading..."))

(s/when-some [count @item-count]
  ($ :span count " items"))
```

Fallback values with `or`:

```clojure
($ :h1 (s/or @custom-title "Default Title"))
```

Nil-safe threading with `some->` and `some->>`:

```clojure
;; Returns nil if any step is nil
(s/some-> @user :profile :settings :theme)

;; Thread as last argument
(s/some->> @items (filter :active) first :name)
```

Conditional rendering with `cond`, via [<Switch> and <Match> components](https://docs.solidjs.com/reference/components/switch-and-match)

```clojure
(s/cond
  (= x 1) ($ button {})
  (= y 2) ($ link {})
  :else ($ text {}))
```

List rendering via [`<For>` component](https://docs.solidjs.com/reference/components/for)

```clojure
(def xs [1 2 3])

(s/for [[x idx] xs] ;; <- index is added implicitly
  ($ :li x))
```

List rendering via [`<Index>` component](https://docs.solidjs.com/reference/components/index-component)

```clojure
(def xs [1 2 3])

(s/index [[x idx] xs] ;; <- index is added implicitly
  ($ :li @x))
```

Catching rendering errors via [<ErrorBoundary> component](https://docs.solidjs.com/reference/components/error-boundary)

```clojure
(s/try
  ($ my-component {})
  (catch err
    ($ error-view {})))
```

### Lifecycle

Run the code after initial rendering via [onMount](https://docs.solidjs.com/reference/lifecycle/on-mount)

```clojure
(s/on-mount (fn [] (prn :component-mounted)))
```

Clean up side effects via [onCleanup](https://docs.solidjs.com/reference/lifecycle/on-cleanup)

```clojure
(s/on-cleanup (fn [] (prn :component-unmounted)))
```

### Reactive utilities

Create atom-like signal via [createSignal](https://docs.solidjs.com/reference/basic-reactivity/create-signal)

```clojure
(let [state (s/signal [])]
  ...)
```

Access DOM element via [ref](https://docs.solidjs.com/reference/jsx-attributes/ref)

```clojure
(let [ref (s/ref)]
  (s/on-mount (fn [] (js/console.log @ref)))
  ($ :button {:ref ref} "press"))
```

Execute side effects after DOM update via [createEffect](https://docs.solidjs.com/reference/basic-reactivity/create-effect)

```clojure
(s/effect (fn [] (prn @value)))
```

Execute side effects before DOM update via [createRenderEffect](https://docs.solidjs.com/reference/secondary-primitives/create-render-effect)

```clojure
(s/render-effect (fn [] (prn @value)))
```

Batching multiple signals updates via [batch](https://docs.solidjs.com/reference/reactive-utilities/batch)

```clojure
(s/batch
  (swap! value1 inc)
  (swap! value2 inc))
```

Create reactive computation for side effects, via [createComputed](https://docs.solidjs.com/reference/secondary-primitives/create-computed)

```clojure
(s/computed
  (prn @value))
```

Separate tracking from re-execution, via [createReaction](https://docs.solidjs.com/reference/secondary-primitives/create-reaction)

```clojure
(let [track (s/reaction (prn :update))]
  ;; run the reaction next time `s` changes
  (track (fn [] @s)))
```

Created cached computation via [createMemo](https://docs.solidjs.com/reference/basic-reactivity/create-memo)

```clojure
(s/memo (fn [] ...))
```

### Store utilities

Create data store via [createStore](https://docs.solidjs.com/reference/store-utilities/create-store)

```clojure
(let [[store set-store] (s/store {:value 0})]
  ...)
```

## Playground

_source in [`dev/app/core.cljs`](src/app/core.cljs)_

1. Install NPM deps
2. Run local build `yarn example`
3. Go to [localhost:3000](htpp://localhost:3000)

## Testing

Run the test suite:

```bash
yarn test
```

Or watch tests during development:

```bash
yarn test:watch
```
