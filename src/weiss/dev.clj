(ns weiss.dev
  (:require
   [clojure.set :as set]
   [com.wsscode.pathom3.interface.eql :as p.eql]
   [com.wsscode.pathom3.interface.smart-map :as psm]
   [engine.object :as object]
   [engine.core :as core]
   [engine.entity :as entity]
   [engine.room :as room]))

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

(defn make-mutation
  [target args]
  {:engine.action/type :engine.action/mutation
   :engine.action/target target
   :engine.action/args args})

(defn make-effect
  [instance effect]
  (let [type (:engine.action/type effect)
        args (:engine.action/args effect)]
    {:engine.action/type type :engine.action/args (merge args instance)}))

(defn lever-activate-default
  [instance]
  (let [state (:engine.action/state instance)
        id (::object/id instance)
        prevent-default? (or (:engine.action/prevent-default? state) false)
        linked-to (:linked-to state)
        additional-effects (map (partial make-effect instance) (or (:engine.action/additional-effects state) []))
        default-effects (if prevent-default?
                          []
                          [{:engine.action/type :engine.action/activate :engine.action/args {:linked-to linked-to}}])
        self-effect (make-mutation {::object/id id} {:engine.action/state {:switched? (not (:switched? state))}})
        effects (flatten [self-effect default-effects additional-effects])]
    {:describe "You heard a click somewhere."
     :engine.action/effects effects}))

(defn default-failed-action
  []
  {:describe "Nothing happens"})

(defn extend-object-rules
  [base extra]
  (let [update-fn (fn [base key]
                    (update-in base [key] set/union (get extra key)))]
    (-> base
        (update-fn :semantics)
        (update-fn :intrinsic)
        (update-fn :actions)
        (merge (dissoc extra :semantics :intrinsic :actions)))))

(defn door-rules
  [extra]
  (extend-object-rules {:semantics #{:semantics/exit}} extra))

(def semantic-rules
  {:semantics/exit
   {:action/open (fn [{:keys [instance]}]
                   (let [{:keys [:engine.action/state]} instance]
                     (if (= state :open)
                       {:describe "Already opened."}
                       {:describe (str "You open the " name)
                        :engine.action/mutation {:state :open}})))
    :action/close (fn [{:keys [instance]}]
                    (let [{:keys [:state]} instance]
                      (if (= state :open)
                        {:describe "You close the door"
                         :state :closed}
                        {:describe "Already closed"})))}

   :semantics/activable
   {:action/activate (fn [{:keys [instance]}]
                       (let [{:keys [onActivate]} instance
                             onActivate (or onActivate lever-activate-default)]
                         (onActivate instance)))}

   :semantics/food
   {:action/eat (fn [{:keys [name]}] {:describe (str "Nom! No more " name " :(")})}

   #{:semantics/food :intrinsic/poisoned}
   {:action/eat (fn [{:keys [name]}] {:describe (str "Ouch! The " name " is poisoned :(")})}

   :intrinsic/flammable
   {:action/burn (fn [{:keys [name action-params]}]
                   (let [{:keys [color]} action-params]
                     {:describe (str "The " name " bursts into " color (when color " ") "flames!")}))}})

(def object-rules
  {::object/wooden-door
   (door-rules
    {:name "wooden door"
     :intrinsic #{:intrinsic/flammable}
     :actions   {:action/open (fn [_] {:describe "You push open the creaky wooden door"})
                 :action/close (fn [_] {:describe "You close the creaky wooden door"})}})

   ::object/door
   (door-rules {})

   ::object/lever
   {:semantics #{:semantics/activable}}

   "runtime generated object 1"
   {:name "boulgiboulga"
    :semantics #{:semantics/food}
    :intrinsic #{:intrinsic/poisoned}}

   ::object/iron-door
   (door-rules
    {:name "iron door"
     :actions   {:action/open (fn [{:keys [name]}] {:describe (str "You heave the " name " open")})}})})

(defn choose-rule
  "Chooses the most specific rule from a list of applicable rules"
  [applicable-rule-keys]
  (case (count applicable-rule-keys)
    0  nil
    1 (first applicable-rule-keys)
    (->> applicable-rule-keys
         (map #(if (seqable? %)
                 {:count (count %) :value %}
                 {:count 1 :value %}))
         (sort-by :count >)
         first
         :value)))

(defn make-handler-args
  [rule-key rule instance action-params]
  {:rule-key rule-key
   :rule rule
   :name (or (:name rule) (name rule-key))
   :instance (or instance {})
   :action-params action-params})

(defn mutation-handler
  [mutation])

(defn filter-rules
  [xs target-set action-key]
  (let [match-rule-keys (fn [rule]
                          (or (= (key rule) target-set)
                              (contains? target-set (key rule))))
        match-action (fn [rule]
                       (not (nil? (get (val rule) action-key))))] ; (val rule) -> (get-semantic-rule-actions rule)
    (filter #(and (match-action %) (match-rule-keys %)) (seq xs))))

(defn perform
  [rules action action-params rule-key instance]
  (let [rule (rules rule-key)
        rule-handler (get-in rule [:actions action])
        applicable-semantic-rules (filter-rules semantic-rules
                                                (set/union (:semantics rule)
                                                           (:intrinsic rule))
                                                action)
        selected-semantic-rule-key (choose-rule (keys applicable-semantic-rules))
        selected-semantic-rule (get semantic-rules selected-semantic-rule-key)
        handler-args (make-handler-args rule-key rule instance action-params)
        semantic-handler (get selected-semantic-rule action)
        semantic-handler-result (when semantic-handler (semantic-handler handler-args))
        rule-handler-result (when rule-handler (rule-handler handler-args))]
    (if (or semantic-handler-result rule-handler-result)
      (merge semantic-handler-result rule-handler-result)
      {:describe (str "Nothing happens when you " (name action) " the " (name rule-key))})))

(defn object-perform
  [rules action action-params {::object/keys [object] :as instance}]
  (perform rules action action-params object instance))

(perform object-rules :action/eat {} "runtime generated object 1" {})

(perform object-rules :action/activate {}
         ::object/lever {::object/id "toto" :engine.action/state {:switched? true}})

(perform object-rules :action/activate {}
         ::object/lever {::object/id "tata"
                         :engine.action/state {:switched? false :linked-to "toto"
                                               :engine.action/prevent-default? true}})

(object-perform object-rules :action/activate {} room/switched-off-lever)
(object-perform object-rules :action/activate {} room/special-object)

(perform object-rules :action/open {} ::object/wooden-door {})

(perform object-rules :action/open {} ::object/iron-door {})

(perform object-rules :action/burn {:color "blue"} ::object/wooden-door {})

(perform object-rules :action/burn {} ::object/wooden-door {})

(perform object-rules :action/burn {} ::object/iron-door {})

(perform object-rules :action/open {} ::object/door {})
(perform object-rules :action/close {} ::object/wooden-door {:state :close})
(perform object-rules :action/close {} ::object/door {:state :close})
(perform object-rules :action/close {} ::object/door {:state :open})
