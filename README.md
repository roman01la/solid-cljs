# solid-cljs
ClojureScript bindings to SolidJS

_Early alpha, unstable and highly experimental_

## Installation
1. `yarn add solid-js --save-dev`
2. Use Clojure's Git deps

## Example
solid-cljs wraps SolidJS API documented at [solidjs.com/docs/latest/api](https://www.solidjs.com/docs/latest/api)

```clojure
(ns app.core
  (:require [solid.core :as s :refer [$ defui]]))

(defui app []
   (let [n (s/signal 0)]
     (s/effect
       (println "n:" @n))
     ($ :div
        ($ :button {:on-click #(swap! n inc)} "+")
        @n
        ($ :button {:on-click #(swap! n dec)} "-"))))

(s/render ($ app) (js/document.getElementById "root"))
```

## Playground

_source in [`src/app/core.cljs`](src/app/core.cljs)_

1. Install NPM deps `yarn`
2. Run local build `clojure -M -m shadow.cljs.devtools.cli watch app`
3. Go to [localhost:3000](htpp://localhost:3000)
