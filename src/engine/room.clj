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
            ::exits [{:exits/cardinal :exits.cardinal/north
                      :exits/type :exits.type/door
                      ::next 1}]})
(def room2 {::id 1
            ::desc {::text "a big hall"}
            ::exits [{:exits/cardinal :exits.cardinal/south
                      :exits/type :exits.type/door
                      ::next 0}]})

(def rooms (into {}
                 (map (juxt ::id identity)
                      [room1 room2])))

(pco/defresolver room-resolver
  [{:keys [world ::id]}]
  {::room (do (prn "room resolver")
              (get (:engine.core/dungeon world) id))})

(def resolvers [room-resolver])

(comment

  rooms

  :end)
