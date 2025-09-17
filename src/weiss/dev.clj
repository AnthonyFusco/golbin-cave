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
  [state effect]
  (let [type (:engine.action/type effect)
        args (:engine.action/args effect)]
    {:engine.action/type type :engine.action/args (merge args state)}))

(defn make-effects
  [state effects]
  (map (partial make-effect state) effects))

(defn make-additional-effects
  [state]
  (make-effects state (get state :engine.action/additional-effects [])))

(defn default-activate-action
  [state]
  {:pre [(contains? state :engine.action/target)]}
  {:engine.action/type :engine.action/activate :engine.action/args (assoc state :description "default activate action")})

(defn lever-default-self-mutation
  [id state]
  {:pre [(contains? state :switched?)]}
  (make-mutation {::object/id id}
                 (assoc (update state :switched? not)
                        :description "self-mutation => lever switched state")))

(defn compute-full-effects
  [state {:keys [default-effects additional-effects self-effects]}]
  (let [prevent-default? (boolean (:engine.action/prevent-default? state))]
    (flatten [(if prevent-default? [] default-effects) self-effects additional-effects])))

(defn activate-default-handler
  [{:keys [:engine.action/state]}]
  (let [additional-effects (make-additional-effects state)
        default-effects [(default-activate-action state)]
        self-effects []
        effects (compute-full-effects state {:default-effects default-effects
                                             :additional-effects additional-effects
                                             :self-effects self-effects})]
    {:engine.action/effects effects}))

(defn lever-activate-handler
  [{:keys [:engine.action/state ::object/id] :as instance}]
  (let [self-effects [(lever-default-self-mutation id state)]
        default-activate-handler (activate-default-handler instance)]
    (-> default-activate-handler
        (update-in [:engine.action/effects] conj self-effects)
        (assoc :description "You heard a click somewhere"))))

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
   {:action/open (fn [{:keys [name instance]}]
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
                             onActivate (or onActivate activate-default-handler)]
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
   {:semantics #{:semantics/activable}
    :actions {:action/activate (fn [{:keys [instance]}]
                                 (let [{:keys [onActivate]} instance
                                       onActivate (or onActivate lever-activate-handler)]
                                   (onActivate instance)))}}

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
  (let [state (:engine.action/state instance)
        name (or (:name state) (:name rule) (name rule-key))]
    {:rule-key rule-key
     :rule rule
     :name name
     :instance (or instance {})
     :action-params action-params}))

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

(defn perform-on-object
  [rules action action-params {::object/keys [object] :as instance}]
  (perform rules action action-params object instance))

(perform object-rules :action/eat {} "runtime generated object 1" {})

(perform object-rules :action/activate {}
         ::object/lever {::object/id "toto"
                         :engine.action/state {:switched? true
                                               :engine.action/target 0}})

(perform object-rules :action/activate {}
         ::object/lever {::object/id "tata"
                         :engine.action/state {:switched? false
                                               :engine.action/target "toto"
                                               :engine.action/prevent-default? true}})

(perform-on-object object-rules :action/activate {} room/switched-off-lever-of-healing)
(perform-on-object (assoc object-rules ::object/special {:semantics #{:semantics/activable}})
                   :action/activate {} room/special-object)

(perform object-rules :action/open {} ::object/wooden-door {})

(perform object-rules :action/open {} ::object/iron-door {})

(perform object-rules :action/burn {:color "blue"} ::object/wooden-door {})

(perform object-rules :action/burn {} ::object/wooden-door {})

(perform object-rules :action/burn {} ::object/iron-door {})

(perform object-rules :action/open {} ::object/door {:engine.action/state {:name "Titouan la porte"}})
(perform object-rules :action/open {} ::object/door {})
(perform object-rules :action/close {} ::object/wooden-door {:state :close})
(perform object-rules :action/close {} ::object/door {:state :close})
(perform object-rules :action/close {} ::object/door {:state :open})
