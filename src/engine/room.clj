(ns engine.room
  (:require [com.wsscode.pathom3.connect.indexes :as pci]
            [engine.utils :as utils]
            [engine.object :as object]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [com.wsscode.pathom3.interface.smart-map :as psm]
            [com.wsscode.pathom3.plugin :as p.plugin]
            [com.wsscode.pathom3.connect.built-in.plugins :as pbip]
            [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]))

(def switched-off-lever-of-healing
  {::object/id 0
   ::object/object ::object/lever
   :engine.action/state {:name "Lever of Healing"
                         :engine.action/additional-effects [{:engine.action/type :engine.action/heal
                                                             :engine.action/args {:engine.action/target :self}}]
                         :engine.action/target {::object/id 1}
                         :switched? false}})
(def closed-door
  {::object/id 1
   ::object/object ::object/wooden-door
   :engine.action/state {:exit-to 1
                         :open? false}})
(def special-object
  {::object/id 2
   ::object/object ::object/special
   :engine.action/state {:engine.action/target "toto"
                         :engine.action/effects [{:engine.action/type :engine.action/open
                                                  :engine.action/args {:engine.action/target {::object/id 1}}}]}})

(def room1 {::id 0
            ::desc {::text "a small room"}
            ::objects [switched-off-lever-of-healing closed-door]})
(def room2 {::id 1
            ::desc {::text "a big hall"}
            ::objects []})
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

(defn update-room
  [world room]
  (assoc-in world [::dungeon (::id room)] room))

(defn mutate-room-fn
  [room args]
  (merge room args))

(pco/defmutation mutate-room
  [{:keys [world ::room args]}]
  (let [updated-room (mutate-room-fn room args)
        updated-world (update-room world updated-room)]
    (utils/computation-valid updated-world)))

(def resolvers [room-resolver room-id-equivalence])
