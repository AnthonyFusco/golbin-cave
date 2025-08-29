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
                      ::next 2}]})

(def room3 {::id 2
            ::desc {::text "a strange chamber"}
            ::exits [{:exit/cardinal :exit.cardinal/west
                      :exit/type :exit.type/secret-door
                      :exit/state :exit.state/hidden
                      ::next 1}]})

(def rooms (into {}
                 (map (juxt ::id identity)
                      [room1 room2 room3])))

(def room-id-equivalence
  (pbir/equivalence-resolver :engine.entity/location :engine.room/id))

(pco/defresolver room-resolver
  [{:keys [world ::id]}]
  {::room (do (prn "room resolver")
              (get (:engine.core/dungeon world) id))})

(def resolvers [room-resolver room-id-equivalence])

(defmulti exit-to-action :exit/type)
(defmulti door-to-action :exit/state)
(defmulti secret-door-to-action :exit/state)

(defmethod exit-to-action :exit.type/door
  [exit]
  (door-to-action exit))

(defmethod exit-to-action :exit.type/secret-door
  [exit]
  (secret-door-to-action exit))

(defmethod door-to-action :exit.state/closed
  [exit]
  [{:engine.action/action :engine.action/open
    :engine.action/args exit}
   {:engine.action/action :engine.action/open-and-advance
    :engine.action/args exit}])

(defmethod door-to-action :exit.state/open
  [exit]
  [{:engine.action/action :engine.action/close
    :engine.action/args exit}
   {:engine.action/action :engine.action/advance
    :engine.action/args exit}])

(defmethod secret-door-to-action :exit.state/hidden
  [exit]
  [{:engine.action/action :engine.action/reveal
    :engine.action/args exit}])

(defmethod secret-door-to-action :default
  [exit]
  (door-to-action exit))

(defn room-actions
  [room]
  (let [{::keys [exits]} room]
    (mapcat exit-to-action exits)))
