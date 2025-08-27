(ns engine.mocks
  (:require [engine.entity :as entity]
            [engine.core :as core]
            [clojure.test :as t]))

(def entity-0 (entity/make-entity
               0
               (entity/make-coords 0 0 0)
               0))

(def entity-1 (entity/make-entity
               1
               (entity/make-coords 1 0 0)
               1))

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

(def world-value (core/make-world
                  [entity-0 entity-1]
                  [room1 room2]))

(def mock-world {:world world-value})

(def env (-> core/indexes (assoc :world world-value)))
