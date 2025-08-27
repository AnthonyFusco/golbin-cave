(ns engine.entity-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [engine.core :as core]
            [engine.mocks :as mock]
            [engine.entity :as entity]))

(deftest entity-resolver-test
  (testing "get existing entity from env"
    (let [id 0
          entities (entity/entities-resolver mock/env mock/mock-world)
          entity (entity/entity-resolver
                  (conj {::entity/id id}
                        entities))]
      (is (= {::entity/entity mock/entity-0} entity))))
  (testing "empty for unexisting id"
    (let [id 666
          entities (entity/entities-resolver mock/env mock/mock-world)
          entity (entity/entity-resolver
                  (conj {::entity/id id}
                        entities))]
      (is (= {::entity/entity nil} entity)))))

(deftest entities-resolver-test
  (testing "get all entities from world"
    (let [entities (entity/entities-resolver mock/env mock/mock-world)]
      (is (= {::core/entities [mock/entity-0 mock/entity-1]} entities))))
  (testing "filter entities by location"
    (let [world-atom (atom mock/world-value)
          env (assoc mock/env :world-atom world-atom)
          entities (p.eql/process
                    env
                    '[(:engine.core/entities {::entity/location 0})])]
      (is (= {::core/entities [mock/entity-0]} entities)))))

(deftest location-resolver-test
  (testing "get location by id"
    (let [world-atom (atom mock/world-value)
          env (assoc mock/env :world-atom world-atom)
          location (p.eql/process-one
                    env
                    {::entity/id 1}
                    ::entity/location)]
      (is (= 1 location))))
  (testing "get all locations"
    (let [world-atom (atom mock/world-value)
          env (assoc mock/env :world-atom world-atom)
          entities (p.eql/process
                    env
                    [{::core/entities [::entity/location]}])]
      (is (= {::core/entities [{::entity/location 0}
                               {::entity/location 1}]} entities)))))

(deftest location-mutation-test
  (testing "changes location"
    (let [new-location 6
          new-env (core/process
             mock/env
             `(engine.entity/update-entity-location
               ~{::entity/id 1
                 ::entity/location new-location}))
          location (core/query-one
                    new-env
                    {::entity/id 1}
                    ::entity/location)]
      (is (= new-location location)))))
