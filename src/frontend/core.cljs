(ns frontend.core
  (:require [reagent.dom :as rdom]
            [re-frame.core :as rf]
            [engine.core :as core]))

;; Events
(rf/reg-event-db
 :initialize
 (fn [_ [_ new-name]]
   {:name (or new-name "World")})) ;; handle optional value

;; Subscriptions
(rf/reg-sub
 :name
 (fn [db _] (:name db)))

;; Views
(defn app []
  (let [name @(rf/subscribe [:name])]
    [:div
     [:h1 (core/greet name)]
     [:input {:type "text"
              :value name
              :on-change #(rf/dispatch [:initialize (.. % -target -value)])}]]))

;; Entry point
(defonce root (atom nil))

(defn mount []
  (rdom/render [app] (.getElementById js/document "app")))

(defn init []
  (rf/dispatch-sync [:initialize])
  (mount))
