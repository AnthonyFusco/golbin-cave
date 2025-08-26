(ns engine.entity-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [engine.core :as core]
            [engine.entity :as entity]))

(def entity-0 (entity/make-entity
               0
               (entity/make-coords 0 0 0)
               0))

(def entity-1 (entity/make-entity
               1
               (entity/make-coords 1 0 0)
               1))

(def world-value (core/make-world
                  [entity-0 entity-1]))
(def mock-world {:world world-value})

(def test-env (-> core/indexes))

(deftest entity-resolver-test
  (testing "get existing entity from env"
    (let [id 0
          entities (entity/entities-resolver test-env mock-world)
          entity (entity/entity-resolver
                  (conj {::entity/id id}
                        entities))]
      (is (= {::entity/entity entity-0} entity))))
  (testing "empty for unexisting id"
    (let [id 666
          entities (entity/entities-resolver test-env mock-world)
          entity (entity/entity-resolver
                  (conj {::entity/id id}
                        entities))]
      (is (= {::entity/entity nil} entity)))))

(deftest entities-resolver-test
  (testing "get all entities from world"
    (let [entities (entity/entities-resolver test-env mock-world)]
      (is (= {::core/entities [entity-0 entity-1]} entities))))
  ;; (testing "filter entities by coords"
  ;;   (let [world-atom (atom world-value)
  ;;         env (assoc test-env :world-atom world-atom)
  ;;         entities (p.eql/process
  ;;                   env
  ;;                   '[(:engine.core/entities {:engine.entity/x 0
  ;;                                             :engine.entity/y 0
  ;;                                             :engine.entity/z 0})])]
  ;;     (is (= {::core/entities [entity-0]} entities))))
  ;; (testing "finds nothing if nothing on coords"
  ;;   (let [world-atom (atom world-value)
  ;;         env (assoc test-env :world-atom world-atom)
  ;;         entities (p.eql/process
  ;;                   env
  ;;                   '[(:engine.core/entities {:engine.entity/x 666
  ;;                                             :engine.entity/y 0
  ;;                                             :engine.entity/z 0})])]
  ;;     (is (= {::core/entities []} entities))))
  (testing "filter entities by location"
    (let [world-atom (atom world-value)
          env (assoc test-env :world-atom world-atom)
          entities (p.eql/process
                    env
                    '[(:engine.core/entities {::entity/location 0})])]
      (is (= {::core/entities [entity-0]} entities)))))

(deftest location-resolver-test
  (testing "get location by id"
    (let [world-atom (atom world-value)
          env (assoc test-env :world-atom world-atom)
          location (p.eql/process-one
                    env
                    {::entity/id 1}
                    ::entity/location)]
      (is (= 1 location))))
  (testing "get all locations"
    (let [world-atom (atom world-value)
          env (assoc test-env :world-atom world-atom)
          entities (p.eql/process
                    env
                    [{::core/entities [::entity/location]}])]
      (is (= {::core/entities [{::entity/location 0}
                               {::entity/location 1}]} entities)))))

(deftest location-mutation-test
  (testing "changes location"
    (let [world-atom (atom world-value)
          env (assoc test-env :world-atom world-atom)
          new-location 6
          _ (p.eql/process
             env
             [`(engine.entity/update-entity-fn!
                ~{::entity/id 1
                  :fn entity/update-entity-location
                  :args [{:engine.entity/location new-location}]})])
          location (p.eql/process-one
                    env
                    {::entity/id 1}
                    ::entity/location)]
      (is (= new-location location)))))
