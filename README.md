# solid-cljs
ClojureScript bindings to Solid. solid-cljs wraps Solid API documented at [docs.solidjs.com](https://docs.solidjs.com/). Compatible with Solid 1.9

_Early alpha, unstable and highly experimental_

## Installation
1. `npm i solid-js -D`
2. `{:deps {com.github.roman01la/solid-cljs {:mvn/version "0.1.0"}}}`

## Example

```clojure
(ns app.core
  (:require [solid.core :as s :refer [$ defui]]))

(defui app []
  (let [value (s/signal 0)]
    (s/effect
      (println "value:" @value))
    ($ :div
      ($ :button {:on-click #(swap! value inc)} "+")
      @value
      ($ :button {:on-click #(swap! value dec)} "-"))))

(s/render ($ app) (js/document.getElementById "root"))
```

## API

Functions and macros below are a part of `solid.core` namespace:

`defui` creates Solid component
```clojure
(defui button [props] ...)
```

`$` creates Solid element
```clojure
($ button {:on-click #(prn :pressed)} "press")
```

### Rendering

Conditional rendering with `if` and `when`, via [<Show> component](https://docs.solidjs.com/reference/components/show)
```clojure
(s/if test ... ...)

(s/when test ...)
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
