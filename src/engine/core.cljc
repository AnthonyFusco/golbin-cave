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
   [engine.utils :refer [mutation]]))

(defn greet
  "Callable entry point to the application."
  [data]
  (str "Hello, " (or data "World") "!"))

(def world-init
  {::player {::id 0}
   ::entities (into {}
                    (map (juxt ::entity/id identity)
                         [entity/player entity/other]))
   ::dungeon room/rooms})

(defn make-world [entities rooms]
  {::player {::id 0}
   ::dungeon rooms
   ::entities (into {}
                    (map (juxt ::entity/id identity)
                         entities))})

(def world-atom (atom world-init))

(pco/defmutation update-world!
  [env new-world]
  (assoc env :world new-world))

(pco/defresolver world-value
  [{:keys [world-atom]} _]
  {::pco/output [:world]}
  {:world (do (prn "@world-atom")
              @world-atom)})

(def indexes (-> (pci/register [entity/resolvers
                                room/resolvers
                                world-value
                                update-world!])
                 (p.plugin/register pbip/mutation-resolve-params)))

(def env (-> indexes
             (assoc :world-atom world-atom)))

(defn query
  [env args]
  (p.eql/process env args))

(defn query-one
  ([env arg]
   (query-one env {} arg))
  ([env ctx arg]
   (p.eql/process-one env ctx arg)))

(defn get-world
  [env]
  (query-one env :world))

(p.eql/process-one env {} :world)

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
  (p.eql/process
   env
   [(mutation `entity/update-entity-location!
              {:engine.entity/id entity-id ::entity/location location})]))

(defn compute-view
  [{:keys [current-room]}]
  (let [description (describe-room current-room)]
    {:description description}))

(defn view!
  [{:keys [description]}]
  (prn description))

(defn tick
  [env]
  (let [player-id 0
        player-location (get-player-location env player-id)
        player-room (get-room env player-location)
        view (compute-view {:current-room player-room})]
    (view! view)
    view))

(comment

  (tick env)
  (teleport env 0 1)

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
                          :fn entity/update-entity-location
                          :args [{:engine.entity/location 4}]})])
  (p.eql/process env [{`(engine.entity/update-entity-fn!
                         ~{::entity/id 1
                           :fn entity/update-entity-location
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
  @world-atom
  :end)

;; (def player-entity (psm/smart-map env {::entity/id 0}))
;; (::entity/entity player-entity)
;; (::entity/coords player-entity)
