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
     ::history []
     ::initiatives initiatives
     ::entities entities}))

(def world-init
  (make-world [entity/other entity/player] room/rooms))

(pco/defresolver world-resolver
  [{:keys [world]} _]
  {::pco/output [:world]}
  {:world (do (prn "get world")
              world)})

(def indexes (-> (pci/register [entity/resolvers
                                room/resolvers
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

(defn get-world
  [env]
  (query-one env :world))

(defn get-player-location
  [env player-id]
  (query-one env {::entity/id player-id} ::entity/location))

(defn get-room
  [env room-id]
  (query-one env {::room/id room-id} ::room/room))

(defn describe-room
  [room]
  (str "Description: " (-> room
                           ::room/desc
                           ::room/text)))

(defn teleport
  [env entity-id location]
  (process
   env
   (mutation `entity/update-entity-location
             {:engine.entity/id entity-id ::entity/location location})))

(defn compute-view
  [{:keys [current-room]}]
  (let [description (describe-room current-room)]
    {:description description}))

(defn view!
  [{:keys [description]}]
  (prn description))

(defn tick
  [env actions]
  (let [player-id 0
        player-location (get-player-location env player-id)
        player-room (get-room env player-location)
        view (compute-view {:current-room player-room})]
    (view! view)
    view))

(comment

  (tick env [])
  (teleport env 0 666)
  (get-world env)

  (p.eql/process
   env
   '[(:engine.core/entities {:engine.entity/x 0
                             :engine.entity/y 0
                             :engine.entity/z 0})])

  (p.eql/process
   env
   '[(:engine.core/entities {::entity/location 0})])

  (p.eql/process
   env
   '[(:engine.core/entities {:engine.entity/x 0
                             :engine.entity/y 0
                             :engine.entity/z 0})
     {:engine.core/entities [:engine.entity/id ::entity/coords ::entity/location]}])

  (p.eql/process
   env
   '[{(:engine.core/entities {:engine.entity/x 0
                              :engine.entity/y 0
                              :engine.entity/z 0})
      [::entity/id ::entity/coords ::entity/entity]}])

  (p.eql/process
   env
   '[::entities])

  (p.eql/process
   env
   '[{::entities [::entity/id]}])

  (p.eql/process
   env
   [{::entities [::entity/location]}])

  (p.eql/process-one
   env
   {::entity/id 1}
   ::entity/location)

  (p.eql/process
   env
   '[{::entities [::entity/id ::entity/location]}])

  (p.eql/process env [`(engine.entity/update-entity-fn!
                        ~{::entity/id 1
                          :fn entity/update-entity-location-fn
                          :args [{:engine.entity/location 4}]})])
  (p.eql/process env [{`(engine.entity/update-entity-fn!
                         ~{::entity/id 1
                           :fn entity/update-entity-location-fn
                           :args [{:engine.entity/location 4}]})
                       [::entity/location]}])

  :end)

(comment
  (p.eql/process-one env {::entity/id 0} ::entity/entity)

  (p.eql/process env [`(update-entity-position!
                        {::entity/entity {::entity/id 4 ::entity/coords {::entity/y 8}} ::entity/coords {::entity/x 56}})])

  (p.eql/process env [{`(update-entity-position! {::entity/entity {::entity/id 4 ::entity/coords {::entity/y 8}} ::entity/coords {::entity/x 56}})
                       [::entity/id ::entity/entity ::entity/coords]}])

  (let [entity (p.eql/process-one env {::entity/id 0} ::entity/entity)]
    (p.eql/process env [`(update-entity-position! ~{::entity/entity entity ::entity/coords {::entity/y 48}})]))

  (let [entity (p.eql/process-one env {::entity/id 0} ::entity/entity)]
    (p.eql/process env [(mutation 'engine.core/update-entity-position! {::entity/entity entity ::entity/coords {::entity/y 8}})]))

  (p.eql/process env [`(update-entity-position! {::entity/id 0 ::entity/coords {::entity/z 666}})])
  (p.eql/process env [(mutation `update-entity-position! {::entity/id 0 ::entity/coords {::entity/z 667}})])

  (p.eql/process env [`(update-entity-fn! ~{::entity/id 1
                                            :fn entity/update-entity-position
                                            :args [{::entity/x 7 ::entity/z 99}]})])
  (p.eql/process env [(mutation `update-entity-fn! {::entity/id 1
                                                    :fn entity/update-entity-position
                                                    :args [{::entity/x 7 ::entity/z 92}]})])

  (:world env)
  :end)
