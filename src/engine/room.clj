(ns engine.room
  (:require [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [com.wsscode.pathom3.interface.smart-map :as psm]
            [com.wsscode.pathom3.plugin :as p.plugin]
            [com.wsscode.pathom3.connect.built-in.plugins :as pbip]
            [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]))

(def room1 {::id 0
            ::desc {::text "a small room"}
            ::exits [{:exit/cardinal :exit.cardinal/north
                      :exit/type :exit.type/door
                      :exit/state :exit.state/open
                      ::next 1}]})
(def room2 {::id 1
            ::desc {::text "a big hall"}
            ::exits [{:exit/cardinal :exit.cardinal/north
                      :exit/type :exit.type/door
                      :exit/state :exit.state/closed
                      ::next 0}
                     {:exit/cardinal :exit.cardinal/south
                      :exit/type :exit.type/door
                      :exit/state :exit.state/open
                      ::next 0}]})

(def rooms (into {}
                 (map (juxt ::id identity)
                      [room1 room2])))

(def room-id-equivalence
  (pbir/equivalence-resolver :engine.entity/location :engine.room/id))

(pco/defresolver room-resolver
  [{:keys [world ::id]}]
  {::room (do (prn "room resolver")
              (get (:engine.core/dungeon world) id))})

(def resolvers [room-resolver room-id-equivalence])

(defmulti exit-to-action :exit/type)
(defmulti door-to-action :exit/state)

(defmethod exit-to-action :exit.type/door
  [exit]
  (door-to-action exit))

(defmethod door-to-action :exit.state/closed
  [exit]
  (let [common-args exit]
    [{:engine.action/action :engine.action/open
      :engine.action/args common-args}
     {:engine.action/action :engine.action/open-and-advance
      :engine.action/args common-args}]))

(defmethod door-to-action :exit.state/open
  [exit]
  (let [common-args exit]
    [{:engine.action/action :engine.action/close
      :engine.action/args common-args}
     {:engine.action/action :engine.action/advance
      :engine.action/args common-args}]))

(defn room-actions
  [room]
  (let [{::keys [exits]} room]
    (mapcat exit-to-action exits)))

(comment

  rooms

  :end)
