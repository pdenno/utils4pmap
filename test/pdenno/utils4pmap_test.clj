(ns pdenno.utils4pmap-test
  (:require [clojure.test :refer :all]
            [pdenno.utils4pmap :refer :all]))

(defn sleepy-fn
  "Sleeps, returns argument."
  [n]
  (Thread/sleep n)
  n)

(defn busy-fn
  "Stays busy, never finishes."
  [_]
  (while (and (< (rand-int 10) 100)
              (not (.isInterrupted (Thread/currentThread))))
    (+ 1 (rand-int 5))))

(deftest sleepy-pmap-timeout-test
  (testing "that pmap-timeout1 finishes for sleepy processes."
    (is (= 10
           (count (repeatedly
                   10
                   (fn [] (let [times (pmap-timeout sleepy-fn
                                                     (repeatedly 8 #(+ 10 (* 10 (rand-int 6))))
                                                     50)]
                            (apply + (map #(if (number? %) % (:timeout %)) times))))))))))

(deftest busy-pmap-timeout-test
  (testing "that pmap-timeout1 finishes for busy processes."
    (let [result (repeatedly 10 #(pmap-timeout busy-fn (range 10) 50))]
      (is (== (count result) 10))
      (is (every? #(== (count %) 10) result))
      (is (every? (fn [t] (every? #(contains? % :timeout) t)) result)))))
