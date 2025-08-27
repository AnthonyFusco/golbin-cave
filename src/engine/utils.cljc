(ns engine.utils)

(defn mutation
  [sym args]
  `(~sym ~args))

(defn index-by
  [key items]
  (into {}
        (map (juxt key identity)
             items)))

(def valid-status :valid)
(def error-status :error)

(defn computation-valid
  [result]
  {:status valid-status
   :result result})

(defn computation-error
  [result error]
  {:status error-status
   :error error
   :result result})

(defn computation-valid?
  [{:keys [status]}]
  (= valid-status status))
