(ns pdenno.utils4pmap-test
  (:require [clojure.test :refer :all]
            [pdenno.utils4pmap :refer :all]))

(deftest sleepy-pmap-timeout1-test
  (testing "that pmap-timeout1 finishes for sleepy processes."
    (is (= 100
           (count (repeatedly
                   100
                   (fn [] (let [times (util/pmap-timeout1 (fn [n] (Thread/sleep n) n)
                                                         (repeatedly 8 #(+ 10 (* 10 (rand-int 6))))
                                                         50)]
                            (apply + (map #(if (number? %) % (:timeout %)) times))))))))))

#_(deftest busy-pmap-timeout1-test
  (testing "that pmap-timeout1 finishes for busy processes."
    (let [result (repeatedly
                   10
                   #(util/pmap-timeout1
                     (fn [_] (while (< (rand-int 10) 100)
                               (+ 1 (rand-int 5))))
                     (range 10)
                     50))]
      (is (== (count result) 100))
      (is (every? #(== (count %) 10) result)
          (is (every? (fn [t] (every? #(contains? % :timeout) t)) result))))))

(deftest sleepy-pmap-timeout2-test
  (testing "that pmap-timeout2 finishes for sleepy processes."
    (is (= 100
           (count (repeatedly
                   100
                   (fn [] (let [times (util/pmap-timeout2 (fn [n] (Thread/sleep n) n)
                                                         (repeatedly 8 #(+ 10 (* 10 (rand-int 6))))
                                                         50)]
                            (apply + (map #(if (number? %) % (:timeout %)) times))))))))))

#_(deftest busy-pmap-timeout2-test
  (testing "that pmap-timeout2 finishes for busy processes."
    (let [result (repeatedly
                   10
                   #(util/pmap-timeout2
                     (fn [_] (while (< (rand-int 10) 100)
                               (+ 1 (rand-int 5))))
                     (range 10)
                     50))]
      (is (== (count result) 100))
      (is (every? #(== (count %) 10) result)
      (is (every? (fn [t] (every? #(contains? % :timeout) t)) result))))))

