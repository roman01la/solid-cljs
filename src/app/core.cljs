(ns app.core
  (:require
    [clojure.string :as str]
    [solid.core :as s :refer [$ defui]]))

(defui button [{:keys [style children on-click] :as m}]
  ($ :button
     {:on-click on-click
      :style (merge {:border :none
                     :border-radius 0
                     :color "#fafafa"
                     :font-size 22}
                    style)}
     children))

(defui tool-bar []
  ($ :div {:style {:padding "8px 16px"
                   :height 48
                   :display :flex
                   :align-items :center
                   :position :relative
                   :box-shadow "0 1px 2px rgba(0, 0, 0, 0.1)"}}
     "solid-cljs playground"))

(defui canvas [{:keys [children]}]
  ($ :div
     {:style {:flex 1
              :background-color "#f0f0f0"
              :padding 16}}
     children))

(def buttons
  ["AC" "+-" "%" "/"
   7 8 9 "*"
   4 5 6 "-"
   1 2 3 "+"
   0 "," "="])

(defn exe [op a b]
  (str/split
    (str
      (op
        (js/parseFloat (str/join a) 10)
        (js/parseFloat (str/join b) 10)))
    #""))

(defui calculator []
  (let [value (s/signal [])
        pvalue (s/signal [])
        op (s/signal nil)
        pick (fn []
               (if @op
                 pvalue
                 value))
        on-click (fn [v]
                   (cond
                     (number? v) (swap! (pick) conj v)
                     (= "," v) (swap! (pick) conj v)
                     (= "AC" v) (do (reset! value [])
                                    (reset! pvalue [])
                                    (reset! op nil))
                     (= "+-" v) (swap! (pick) update 0 -)
                     (= "=" v) (do (reset! value (case @op
                                                   "/" (exe / @value @pvalue)
                                                   "*" (exe * @value @pvalue)
                                                   "-" (exe - @value @pvalue)
                                                   "+" (exe + @value @pvalue)))
                                   (reset! pvalue [])
                                   (reset! op nil))
                     (= "%" v) (do (reset! value (exe (fn [a b]
                                                        (* (/ a 100) b))
                                                      @value @pvalue))
                                   (reset! pvalue [])
                                   (reset! op nil))
                     (#{"/" "*" "-" "+"} v) (reset! op v)))]
    ($ :div {:style {:width 240
                     :height 320
                     :box-shadow "0px 1px 6px rgba(0, 0, 0, 0.3)"
                     :border-radius 7
                     :display :flex
                     :flex-direction :column
                     :overflow :hidden}}
       ($ :div {:style {:background-color "#54504d"
                        :padding "8px 16px"
                        :color "#fafafa"
                        :font-size 32
                        :line-height 32
                        :text-align :right
                        :align-items :center}}
          (if (seq @(pick))
            (str/join @(pick))
            0))
       ($ :div {:style {:flex 1
                        :display :grid
                        :grid-template-columns "repeat(4, 1fr)"
                        :grid-template-rows "repeat(5, 1fr)"
                        :gap 1
                        :background-color "#6c6058"}}
          (s/for [[v idx] buttons]
            ($ button {:on-click #(on-click v)
                       :style {:grid-column (when (= v 0) "span 2")
                               :background-color (cond
                                                   (#{"AC" "+-" "%"} v) "#84827d"
                                                   (#{"," 0 1 2 3 4 5 6 7 8 9} v) "#afaea9"
                                                   (#{"/" "*" "-" "+" "="} v) "#f39c11")}}
               v))))))

(defui app []
  ($ :div {:style {:flex 1
                   :font "normal 14px Inter, sans-serif"
                   :display :flex
                   :flex-direction :column}}
     ($ tool-bar)
     ($ canvas
        ($ calculator))))

(defn render-app []
  (s/render ($ app) (js/document.getElementById "root")))

;; hot-reloading setup
(defonce dispose (atom (render-app)))

(defn ^:dev/after-load reload []
  (@dispose)
  (reset! dispose (render-app)))
