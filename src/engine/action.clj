(ns engine.action
  (:require [engine.room :as room]
            [engine.entity :as entity]
            [engine.core :as core]
            [engine.utils :refer [mutation] :as utils]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [com.wsscode.pathom3.interface.smart-map :as psm]
            [com.wsscode.pathom3.plugin :as p.plugin]
            [com.wsscode.pathom3.connect.built-in.plugins :as pbip]
            [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]))

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

(comment
  ;; open -> closed -> open
  ;; secret -> closed -> closed -> open
  ;; secret -> closed -> secret
  :end)
