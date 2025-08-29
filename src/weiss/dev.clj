(ns weiss.dev
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
   [engine.core :as core]
   [engine.utils :refer [mutation] :as utils]))

(def entitylist [entity/player entity/other])
(map #(select-keys % [::entity/id]) entitylist)
(map ::entity/id entitylist)
(:world core/env)
(core/teleport core/env 0 777)

(p.eql/process
 core/env
 '[(:engine.core/entities {::entity/location 0})])

(p.eql/process
 core/env
 [`(engine.entity/update-entity-location
    ~{::entity/id 1
      ::entity/location 777})])

(p.eql/process-one
 core/env
 `(engine.entity/update-entity-location
   ~{::entity/id 1
     ::entity/location 777}))

(core/query-one core/env {::entity/location 0} ::room/room)

(def player (psm/smart-map core/env {::entity/id 0}))
(::entity/location player)
(::room/room player)

(core/teleport-command 0 1)
(def tmp (core/process core/env (core/teleport-command 0 1)))
(core/get-location core/env 0)
(core/get-location tmp 0)
(def tmp3
  (core/tick core/env
             [(core/teleport-command 0 7)
              (core/teleport-command 0 666)]))
(core/get-location tmp3 0)
(first (core/get-history tmp3))

(core/query-one core/env {::entity/id 0} :engine.view/view)

(core/query-one core/env ::core/acting)
(core/query-one tmp3 ::core/acting)
(core/query-one core/env ::core/initiatives)
(core/query-one tmp3 ::core/initiatives)

(room/room-actions room/room2)

(::core/actions player)

(derive ::toto ::person)
(derive ::toto ::con)
(isa? ::toto ::con)
(isa? ::toto ::person)
(isa? ::toto ::jambon)

:semantics/exit ;; looking for an exit, size ?
:semantics/food
:semantics/purify
:intrinsic/breakable
:intrinsic/flammable
:intrinsic/poisoned ;; strong ? weak ? level ?
:object/potato
:object/wooden-door
:object/iron-door
:action/eat
:action/open
:action/burn
:action/hit
:action/wash ;; level ?
:state/opened
:state/closed ;; locked ? broken ?

:action/open

(def semantic-rules
  {:semantics/exit
   {:action/open (fn [{:keys [name]}] {:describe (str "You go through the " name)})
    :action/close (fn [{:keys [instance]}]
                    (let [{:keys [state]} instance]
                      (if (= state :open)
                        {:describe "You close the door"}
                        {:describe "Already closed"
                         :something "more"})))}

   :intrinsic/flammable
   {:action/burn (fn [{:keys [name]}] {:describe (str "The " name " bursts into flames!")})}})

(def object-rules
  {:object/wooden-door
   {:name "wooden door"
    :intrinsic #{:intrinsic/flammable}
    :semantics #{:semantics/exit}
    :actions   {:action/open (fn [_] {:describe "You push open the creaky wooden door"})
                :action/close (fn [_] {:describe "You close the creaky wooden door"})}}

   :object/door
   {:semantics #{:semantics/exit}}

   :object/iron-door
   {:name "iron door"
    :intrinsic #{}
    :semantics #{:semantics/exit}
    :actions   {:action/open (fn [{:keys [name]}] {:describe (str "You heave the " name " open")})}}})

(defn make-handler-args
  [rule-key rule instance]
  {:rule-key rule-key
   :rule rule
   :name (or (:name rule) (name rule-key))
   :instance (or instance {})})

(defn perform
  [action rule-key & {:as instance :or {}}]
  (let [object-rule (object-rules rule-key)
        object-handler (get-in object-rule [:actions action])
        semantic-handler (some (fn [semantic-or-intrinsic-tag]
                                 (get-in semantic-rules [semantic-or-intrinsic-tag action]))
                               (concat (:semantics object-rule)
                                       (:intrinsic object-rule)))
        handler-args (make-handler-args rule-key object-rule instance)
        semantic-handler-result (when semantic-handler (semantic-handler handler-args))
        object-handler-result (when object-handler (object-handler handler-args))]
    (if (or semantic-handler-result object-handler-result)
      (merge semantic-handler-result object-handler-result)
      {:describe (str "Nothing happens when you " (name action) " the " (name rule-key))})))

(perform :action/open :object/wooden-door)

(perform :action/open :object/iron-door)

(perform :action/burn :object/wooden-door)

(perform :action/burn :object/iron-door)

(perform :action/open :object/door)
(perform :action/close :object/wooden-door {:state :close})
(perform :action/close :object/door {:state :close})
(perform :action/close :object/door {:state :open})
