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

(defn lever-activate-default
  [this]
  (let [linked-to (:linked-to this)
        linked-to-effect {:action/activate linked-to}
        effects [linked-to-effect]]
    {:describe "You heard a click somewhere."
     ::object/state {:switched? true}
     :engine.action/effects effects}))

(defn lever-deactivate-default
  [this]
  {:describe "You heard a muffled noise."
   ::object/state {:switched? false}})

(defn default-failed-action
  []
  {:describe "Nothing happens"})

(def semantic-rules
  {:semantics/exit
   {:action/open (fn [{:keys [instance]}]
                   (let [{:keys [state]} instance]
                     (if (= state :open)
                       {:describe "Already opened."}
                       {:describe (str "You open the " name)
                        :engine.action/mutation {:state :open}})))
    :action/close (fn [{:keys [instance]}]
                    (let [{:keys [state]} instance]
                      (if (= state :open)
                        {:describe "You close the door"
                         :state :closed}
                        {:describe "Already closed"})))}
   :semantics/activable
   {:action/activate (fn [{:keys [instance]}]
                       (let [{:keys [switched? onActivate onDeactivate]} instance
                             onActivate (or onActivate lever-activate-default)
                             onDeactivate (or onDeactivate lever-deactivate-default)]
                         (case switched?
                           true (onDeactivate instance)
                           false (onActivate instance)
                           (default-failed-action))))}
   :semantics/food
   {:action/eat (fn [{:keys [name]}] {:describe (str "Nom! No more " name " :(")})}

   #{:semantics/food :intrinsic/poisoned}
   {:action/eat (fn [{:keys [name]}] {:describe (str "Ouch! The " name " is poisoned :(")})}

   :intrinsic/flammable
   {:action/burn (fn [{:keys [name]}] {:describe (str "The " name " bursts into flames!")})}})

(def rules
  {::object/wooden-door
   {:name "wooden door"
    :intrinsic #{:intrinsic/flammable}
    :semantics #{:semantics/exit}
    :actions   {:action/open (fn [_] {:describe "You push open the creaky wooden door"})
                :action/close (fn [_] {:describe "You close the creaky wooden door"})}}

   ::object/door
   {:semantics #{:semantics/exit}}

   ::object/lever
   {:semantics #{:semantics/activable}}

   "runtime generated object 1"
   {:name "boulgiboulga"
    :semantics #{:semantics/food}
    :intrinsic #{:intrinsic/poisoned}}

   ::object/iron-door
   {:name "iron door"
    :intrinsic #{}
    :semantics #{:semantics/exit}
    :actions   {:action/open (fn [{:keys [name]}] {:describe (str "You heave the " name " open")})}}})

(defn choose-rule
  "Chooses the most specific rule from a list of applicable rules"
  [applicable-rule-keys]
  (case (count applicable-rule-keys)
    0  nil
    1 (first applicable-rule-keys)
    (->> applicable-rule-keys
         (map #(if (seqable? %) % (list %)))
         (sort-by count)
         reverse
         first)))

(defn make-handler-args
  [rule-key rule instance]
  {:rule-key rule-key
   :rule rule
   :name (or (:name rule) (name rule-key))
   :instance (or instance {})})

(defn mutation-handler
  [mutation])

(defn match-rules
  [xs target-set]
  (filter #(or (= (key %) target-set)
               (contains? target-set (key %)))
          (seq xs)))

(defn perform
  [action rule-key & {:as instance :or {}}]
  (let [rule (rules rule-key)
        rule-handler (get-in rule [:actions action])
        applicable-semantic-rules (match-rules semantic-rules
                                               (set/union (:semantics rule)
                                                          (:intrinsic rule)))
        selected-semantic-rule-key (choose-rule (keys applicable-semantic-rules))
        selected-semantic-rule (get semantic-rules selected-semantic-rule-key)
        handler-args (make-handler-args rule-key rule instance)
        semantic-handler (get selected-semantic-rule action)
        semantic-handler-result (when semantic-handler (semantic-handler handler-args))
        rule-handler-result (when rule-handler (rule-handler handler-args))]
    (if (or semantic-handler-result rule-handler-result)
      (merge semantic-handler-result rule-handler-result)
      {:describe (str "Nothing happens when you " (name action) " the " (name rule-key))})))

(defn object-perform
  [action {::object/keys [object state]}]
  (perform action object state))

(get-in semantic-rules [#{:intrinsic/poisoned :semantics/food} :action/eat])

(perform :action/eat "runtime generated object 1")

(perform :action/activate ::object/lever {:switched? true})
(perform :action/activate ::object/lever {:switched? false})
(object-perform :action/activate room/lever)

(perform :action/open ::object/wooden-door)

(perform :action/open ::object/iron-door)

(perform :action/burn ::object/wooden-door)

(perform :action/burn ::object/iron-door)

(perform :action/open ::object/door)
(perform :action/close ::object/wooden-door {:state :close})
(perform :action/close ::object/door {:state :close})
(perform :action/close ::object/door {:state :open})
