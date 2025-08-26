(ns engine.utils)

(defn mutation [sym args]
  `(~sym ~args))
