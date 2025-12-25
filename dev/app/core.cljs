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
                   :justify-content :center
                   :position :relative}}
     ($ :div {:style {:box-shadow "0 1px 2px rgba(0, 0, 0, 0.1)"
                      :padding "8px 16px"
                      :background "#fff"
                      :border-radius 16}}
       "solid-cljs playground")))

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

(defonce calculator-state
  (atom {:value []
         :pvalue []
         :op nil}))

(defui calculator []
  (let [state (s/atom-signal calculator-state)
        on-click (fn [v]
                   (let [{:keys [op value pvalue]} @state
                         key (if op :pvalue :value)]
                     (cond
                       (number? v) (swap! calculator-state update key conj v)
                       (= "," v) (swap! calculator-state update key conj v)
                       (= "AC" v) (reset! calculator-state {:value [] :pvalue [] :op nil})
                       (= "+-" v) (swap! calculator-state update-in update [key 0] -)
                       (= "=" v) (reset! calculator-state
                                         {:op nil
                                          :pvalue []
                                          :value (case op
                                                   "/" (exe / value pvalue)
                                                   "*" (exe * value pvalue)
                                                   "-" (exe - value pvalue)
                                                   "+" (exe + value pvalue))})
                       (= "%" v) (reset! calculator-state {:value (exe (fn [a b]
                                                                         (* (/ a 100) b))
                                                                       value pvalue)
                                                           :pvalue []
                                                           :op nil})
                       (#{"/" "*" "-" "+"} v) (swap! calculator-state assoc :op v))))]
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
          (let [{:keys [op value pvalue]} @state
                vs (if op pvalue value)]
            (if (seq vs)
              (str/join vs)
              0)))
       ($ :div {:style {:flex 1
                        :display :grid
                        :grid-template-columns "repeat(4, 1fr)"
                        :grid-template-rows "repeat(5, 1fr)"
                        :gap 1
                        :background-color "#6c6058"}}
          (s/for [[v] buttons]
            ($ button {:on-click #(on-click v)
                       :style {:grid-column (when (= v 0) "span 2")
                               :background-color (cond
                                                   (#{"AC" "+-" "%"} v) "#84827d"
                                                   (#{"," 0 1 2 3 4 5 6 7 8 9} v) "#afaea9"
                                                   (#{"/" "*" "-" "+" "="} v) "#f39c11")}}
               v))))))

(defui counter []
  (let [count (s/signal 0)
        id (js/setInterval #(swap! count inc) 500)]
    (s/on-cleanup #(js/clearInterval id))
    ($ :div "Count value is " @count)))

(defui todos []
  (let [title (s/signal "")
        todos (s/signal [])
        +todo (fn [event]
                (.preventDefault event)
                (s/batch
                  (swap! todos conj {:title @title :done? false})))]
    ($ :div
       ($ :h3 "Simple Todos Example")
       ($ :form {:on-submit +todo}
          ($ :input {:placeholder "enter todo and click +"
                     :required true
                     :value @title
                     :on-input #(reset! title (.. % -target -value))})
          ($ :button "+"))
       (s/for [[todo idx] @todos]
         ($ :div
            ($ :input {:type :checkbox
                       :checked (:done? todo)
                       :on-change #(swap! todos update-in [@idx :done?] not)})
            ($ :input {:value (:title todo)
                       :on-change #(swap! todos assoc-in [@idx :title] (.. % -target -value))})
            ($ :button {:on-click #(reset! todos (into (subvec @todos 0 @idx) (subvec @todos (inc @idx))))}
              "x"))))))

;; ============================================
;; Clock Example
;; ============================================

(defn create-animation-loop
  "Creates an animation loop using requestAnimationFrame.
  Returns a dispose function to stop the loop."
  [callback]
  (let [tick-id (volatile! nil)
        running? (volatile! true)
        tick (fn work [timestamp]
              (when @running?
                (callback timestamp)
                (vreset! tick-id (js/requestAnimationFrame work))))]
    (js/requestAnimationFrame tick)
    ;; Return dispose function
    (fn []
      (vreset! running? false)
      (when-let [id @tick-id]
        (js/cancelAnimationFrame id)))))

(defn get-seconds-since-midnight
  "Returns the number of seconds since midnight."
  []
  (/ (- (js/Date.now)
        (.setHours (js/Date.) 0 0 0 0))
     1000))

(defui hand
  "SVG line component for clock hands."
  [{:keys [rotate length width fixed class]}]
  ($ :line
     {:y1 (when fixed (- length 95))
      :y2 (- (if fixed 95 length))
      :stroke "currentColor"
      :stroke-width width
      :stroke-linecap "round"
      ;; call rotate here for fine-grained reactivity
      :transform (rotate)
      :class class}))

(defui lines
  "Renders multiple tick marks around the clock."
  [{:keys [number-of-lines class length width]}]
  (let [rotate (fn [index]
                 (str "rotate(" (/ (* 360 index) number-of-lines) ")"))]
    (s/for [[_ idx] (range number-of-lines)]
      ($ hand {:rotate #(rotate @idx)
               :class class
               :length length
               :width width
               :fixed true}))))

(defui clock-face
  "SVG clock face with hands."
  [{:keys [hour minute second subsecond]}]
  ($ :svg {:viewBox "0 0 200 200" :width "95vh"}
     ($ :g {:transform "translate(100, 100)"}
        ;; Static elements
        ($ :circle {:class "text-neutral-900"
                    :r 99
                    :fill "white"
                    :stroke "currentColor"})
        ($ lines {:number-of-lines 60 :class "subsecond" :length 2 :width 1})
        ($ lines {:number-of-lines 12 :class "hour" :length 5 :width 2})
        ($ hand {:rotate subsecond :class "subsecond" :length 85 :width 5})
        ($ hand {:rotate hour :class "hour" :length 50 :width 4})
        ($ hand {:rotate minute :class "minute" :length 70 :width 3})
        ($ hand {:rotate second :class "second" :length 80 :width 2}))))

(defui clock
  "Animated analog clock."
  []
  (let [time (s/signal (get-seconds-since-midnight))
        dispose (create-animation-loop
                  (fn [_]
                    (reset! time (get-seconds-since-midnight))))
        
        rotate (fn [value & [fixed]]
                 (str "rotate(" (.toFixed (* value 360) (or fixed 1)) ")"))
        
        ;; These are functions that read @time - pass them down, don't call them
        subsecond #(rotate (mod @time 1))
        second #(rotate (/ (mod @time 60) 60))
        minute #(rotate (/ (mod (/ @time 60) 60) 60))
        hour #(rotate (/ (mod (/ @time 3600) 12) 12))]
    
    (s/on-cleanup dispose)
    
    ($ :div {:class "clock" :style {:max-width 480}}
       ;; Pass functions, not their results - reactivity happens at the leaf
       ($ clock-face {:hour hour
                      :minute minute
                      :second second
                      :subsecond subsecond}))))

(def examples
  {:calculator calculator
   :counter counter
   :todos todos
   :clock clock})

(defui sidebar [{:keys [example]}]
  ($ :aside {:style {:max-width 240
                     :padding 16
                     :position :absolute}}
     ($ :ul {:style {:list-style :none
                     :padding "6px 12px"
                     :margin 0
                     :background "#fff"
                     :border-radius 12
                     :box-shadow "0 1px 2px rgba(0, 0, 0, 0.1)"}}
        (s/for [[item _] (keys examples)]
          ($ :li.menu-item {:style {:padding 8
                                    :cursor :pointer}
                            :on-click #(reset! example item)}
             (name item))))))

(defui canvas [{:keys [example children]}]
  ($ :div
     {:style {:flex 1
              :display :flex}}
     ($ sidebar {:example example})
     ($ :div
        {:style {:flex 1
                 :display :flex
                 :align-items :center
                 :justify-content :center
                 :padding 16}}
        children)))

(defui app []
  (let [example (s/signal :calculator)]
    ($ :div {:style {:flex 1
                     :font "normal 14px Inter, sans-serif"
                     :display :flex
                     :flex-direction :column
                     :background-color "#f0f0f0"}}
       ($ tool-bar)
       ($ canvas {:example example}
          ($ (examples @example))))))

(defn render-app []
  (s/render app (js/document.getElementById "root")))

;; hot-reloading setup
(defonce dispose (atom (render-app)))

(defn ^:dev/after-load reload []
  (@dispose)
  (reset! dispose (render-app)))
