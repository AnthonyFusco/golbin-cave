(ns engine.room-test
  (:require [engine.room :as room]
            [clojure.test :refer [deftest is testing]]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [engine.mocks :as mock]))

(deftest room-resolver-test
  (testing "resolves existing room"
    (let [roomid 0
          world-atom (atom mock/world-value)
          env (assoc mock/test-env :world-atom world-atom)
          result (p.eql/process-one env {::room/id roomid} ::room/room)]
      (is (= mock/room1 result))))
  (testing "do not resolve unexisting room"
    (let [roomid 666
          world-atom (atom mock/world-value)
          env (assoc mock/test-env :world-atom world-atom)
          result (p.eql/process-one env {::room/id roomid} ::room/room)]
      (is (= nil result)))))
