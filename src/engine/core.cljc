(ns engine.core
  (:require
   [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
   [com.wsscode.pathom3.connect.built-in.plugins :as pbip]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.interface.eql :as p.eql]
   [com.wsscode.pathom3.interface.smart-map :as psm]
   [com.wsscode.pathom3.plugin :as p.plugin]
   [engine.room :as room]
   [engine.entity :as entity]
   [engine.utils :refer [mutation] :as utils]))

(defn greet
  "Callable entry point to the application."
  [data]
  (str "Hello, " (or data "World") "!"))

(defn make-world [entities rooms]
  (let [entity-ids (map ::entity/id entities)
        entities (utils/index-by ::entity/id entities)
        initiatives entity-ids]
    {::player {::id 0}
     ::dungeon rooms
     ::history nil
     ::initiatives initiatives
     ::entities entities}))

(def world-init
  (make-world [entity/other entity/player] room/rooms))

(pco/defresolver world-resolver
  [{:keys [world]} _]
  {::pco/output [:world]}
  {:world (do (prn "get world")
              world)})

(pco/defresolver history-resolver
  [{:keys [world]} _]
  {::history (do (prn "get history")
                 (::history world))})

(pco/defresolver initiatives-resolver
  [{:keys [world]} _]
  {::initiatives (do (prn "get initiatives")
                     (::initiatives world))})

(pco/defresolver acting-resolver
  [{:keys [world]} _]
  {::acting (do (prn "get acting")
                (first (::initiatives world)))})

(defn update-initiatives
  [world initiatives]
  (assoc world ::initiatives initiatives))

(pco/defmutation advance-initiative
  [{:keys [world]}]
  (let [initiatives (::initiatives world)
        updated-initiatives (if (>= (count initiatives) 2)
                              (concat (rest initiatives) [(first initiatives)])
                              initiatives)
        updated-world (update-initiatives world updated-initiatives)]
    (utils/computation-valid updated-world)))

(defn describe-room
  [room]
  (if room
    (str "Description: " (-> room
                             ::room/desc
                             ::room/text))
    "The Abyss"))

(pco/defresolver compute-entity-view
  [{:keys [::room/room]}]
  {:engine.view/view
   (let [description (describe-room room)]
     {:description description})})

(pco/defresolver actions?
  [{:keys [::room/room]}]
  {::actions
   (let [{::room/keys [exits]} room]
     (mapcat room/exit-to-action exits))})

(def indexes (-> (pci/register [entity/resolvers
                                room/resolvers
                                actions?
                                advance-initiative
                                initiatives-resolver
                                acting-resolver
                                compute-entity-view
                                history-resolver
                                world-resolver])
                 (p.plugin/register pbip/mutation-resolve-params)))

(defn with-world
  [env world]
  (assoc env :world world))

(def env (-> indexes
             (with-world world-init)))

(defn query
  [env args]
  (p.eql/process env args))

(defn query-one
  ([env arg]
   (query-one env {} arg))
  ([env ctx arg]
   (p.eql/process-one env ctx arg)))

(defn with-history
  [env mutation]
  (update-in env [:world ::history] conj mutation))

(defn process
  [env mutation]
  (let [computation (query-one env mutation)
        {:keys [result]} computation]
    (when (utils/computation-valid? computation)
      (-> env
          (with-world result)
          (with-history mutation)))))

(defn get-history
  [env]
  (query-one env ::history))

(defn get-world
  [env]
  (query-one env :world))

(defn get-location
  [env player-id]
  (query-one env {::entity/id player-id} ::entity/location))

(defn get-room
  [env room-id]
  (query-one env {::room/id room-id} ::room/room))

(defn teleport-command
  [entity-id location]
  (mutation `entity/update-entity-location
            {:engine.entity/id entity-id ::entity/location location}))

(def advance-initiative-command
  '(engine.core/advance-initiative))

(defn teleport
  [env entity-id location]
  (process
   env
   (teleport-command entity-id location)))

(defn view!
  [{:keys [description]}]
  (prn description))

(defn tick
  [env actions]
  (let [actions-then-advance-initiative (conj actions advance-initiative-command)]
    (reduce process env actions-then-advance-initiative)))
