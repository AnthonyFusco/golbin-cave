(ns engine.entity
  (:require
   [com.wsscode.pathom3.connect.operation :as pco]
   [engine.utils :as utils]))

(def player
  {::id 0
   ::location 0
   ::coords {::x 0 ::y 0 ::z 0}})

(def other
  {::id 1
   ::location 1
   ::coords {::x 1 ::y 1 ::z 1}})

(defn make-entity [id coords location]
  {::id id
   ::coords coords
   ::location location})

(defn make-coords [x y z]
  {::x x ::y y ::z z})

(pco/defresolver entity->id
  [{:keys [:engine.entity/entity]}]
  {:engine.entity/id (do (prn "entity->id")
                         (:engine.entity/id entity))})

(pco/defresolver entity->coords
  [{:keys [:engine.entity/entity]}]
  {:engine.entity/coords (do (prn "entity to coords ")
                             (:engine.entity/coords entity))})

(pco/defresolver id->location
  [{::keys [entity id]}]
  {::pco/input [::entity ::id]
   ::pco/output [::location]}
  {::location
   (do (prn (str "id->location" " id:" id))
       (::location entity))})

(pco/defresolver id->coords
  [{:keys [:engine.core/entities]} {:keys [:engine.entity/id]}]
  {::pco/input [:engine.core/entities [::id ::coords]]}
  {::coords
   (do (prn (str "id->coords" " id:" id))
       (::coords (get entities id)))})

(defn update-entity-location-fn [entity location]
  (merge entity {::location location}))

(defn update-entity [world entity]
  (assoc-in world [:engine.core/entities (:engine.entity/id entity)] entity))

(pco/defmutation update-entity-location
  [{:keys [world]} {::keys [entity location]}]
  {::pco/input [::entity ::location]}
  (let [updated-entity (update-entity-location-fn entity location)
        updated-world (update-entity world updated-entity)]
    (utils/computation-valid updated-world)))

(pco/defresolver entity-resolver
  [{:keys [:engine.entity/id :engine.core/entities]}]
  {::entity (do (prn "entity resolver")
                (first (filter #(= (::id %) id) entities)))})

(pco/defresolver entities-resolver
  [env {:keys [world]}]
  {::pco/output [{:engine.core/entities [::entity]}]}
  {:engine.core/entities
   (do (prn "entities resolver")
       (prn (pco/params env))
       (let [entities (vals (:engine.core/entities world))
             location (pco/params env)]
         (if (seq location)
           (->> entities
                (filter #(= (::location %) (::location location))))
           entities)))})

(def resolvers [entity-resolver
                entity->id
                entity->coords
                entities-resolver
                id->location
                update-entity-location
                id->coords])
